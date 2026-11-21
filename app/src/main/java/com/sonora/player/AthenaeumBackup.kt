package com.sonora.player

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Versioned JSON and portable ZIP archives for Athenaeum. JSON-only exports
 * preserve URI references; ZIP archives package files that the app can still read.
 */
object AthenaeumBackup {
    suspend fun writePortableZip(context: Context, dao: AthenaeumDao, output: OutputStream) {
        val root = JSONObject(export(dao))
        val preferences = context.getSharedPreferences("athenaeum_preferences", Context.MODE_PRIVATE)
        root.put("preferences", JSONObject().apply {
            preferences.all.forEach { (key, value) ->
                if (value is Set<*>) put(key, JSONArray().apply { value.filterIsInstance<String>().forEach { put(it) } })
                else put(key, value)
            }
        })
        val books = root.getJSONArray("books")
        ZipOutputStream(output).use { zip ->
            for (index in 0 until books.length()) {
                val book = books.getJSONObject(index)
                val id = book.optLong("id")
                val extension = book.optString("fileType").ifBlank { "book" }.replace(Regex("[^a-zA-Z0-9]"), "")
                val sourceUri = book.optString("fileUri")
                if (sourceUri.isNotBlank()) {
                    val entry = "files/books/$id.$extension"
                    if (copyUriIntoZip(context, Uri.parse(sourceUri), zip, entry)) book.put("fileUri", "zip:$entry")
                }
                val coverUri = book.optString("coverUri")
                if (coverUri.isNotBlank()) {
                    val entry = "files/covers/$id.cover"
                    if (copyUriIntoZip(context, Uri.parse(coverUri), zip, entry)) book.put("coverUri", "zip:$entry")
                }
            }
            zip.putNextEntry(ZipEntry("athenaeum.json"))
            zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun copyUriIntoZip(context: Context, uri: Uri, zip: ZipOutputStream, entryName: String): Boolean {
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return false
        var opened = false
        return try {
            zip.putNextEntry(ZipEntry(entryName))
            opened = true
            input.use { it.copyTo(zip) }
            zip.closeEntry()
            true
        } catch (_: Exception) {
            if (opened) runCatching { zip.closeEntry() }
            false
        } finally {
            runCatching { input.close() }
        }
    }

    suspend fun restorePortableZip(context: Context, input: InputStream, dao: AthenaeumDao) {
        val destination = File(context.filesDir, "athenaeum_import").apply { mkdirs() }.canonicalFile
        val extracted = mutableMapOf<String, File>()
        var metadata: String? = null
        var totalBytes = 0L
        var entryCount = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= 10_000) { "Backup contains too many files." }
                if (!entry.isDirectory) {
                    if (entry.name == "athenaeum.json") {
                        metadata = zip.readBytes().toString(Charsets.UTF_8)
                    } else if (entry.name.startsWith("files/")) {
                        val target = File(destination, entry.name).canonicalFile
                        require(target.path.startsWith(destination.path + File.separator)) { "Unsafe path in backup." }
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                totalBytes += read
                                require(totalBytes <= 2_000_000_000L) { "Backup exceeds the 2 GB import limit." }
                                output.write(buffer, 0, read)
                            }
                        }
                        extracted[entry.name] = target
                    }
                }
                zip.closeEntry()
            }
        }
        val root = JSONObject(metadata ?: error("The ZIP does not contain athenaeum.json"))
        root.optJSONObject("preferences")?.let { saved ->
            val editor = context.getSharedPreferences("athenaeum_preferences", Context.MODE_PRIVATE).edit().clear()
            saved.keys().forEach { key ->
                when (val value = saved.opt(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is JSONArray -> editor.putStringSet(key, buildSet { for (i in 0 until value.length()) add(value.optString(i)) })
                    is Number -> editor.putLong(key, value.toLong())
                }
            }
            editor.apply()
        }
        val books = root.optJSONArray("books") ?: JSONArray()
        for (index in 0 until books.length()) {
            val book = books.getJSONObject(index)
            listOf("fileUri", "coverUri").forEach { field ->
                val token = book.optString(field)
                if (token.startsWith("zip:")) {
                    val file = extracted[token.removePrefix("zip:")]
                    if (file != null) book.put(field, Uri.fromFile(file).toString())
                    else book.put(field, "")
                }
            }
        }
        restore(root.toString(), dao)
    }

    suspend fun exportCsv(dao: AthenaeumDao): String {
        fun cell(value: String) = "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
        val rows = mutableListOf("Title,Author,Publisher,Year,ISBN,Genre,Language,Status,Ownership,Rating,Progress,Digital URI")
        dao.backupBooks().forEach { book ->
            rows += listOf(book.title, book.author, book.publisher, book.publicationYear, book.isbn, book.genre, book.language, book.status, book.ownership, book.rating.toString(), book.progress.toString(), book.fileUri).joinToString(",") { cell(it) }
        }
        return rows.joinToString("\n")
    }

    suspend fun export(dao: AthenaeumDao): String {
        val root = JSONObject().put("format", "athenaeum-backup").put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
        root.put("books", JSONArray().apply {
            dao.backupBooks().forEach { b -> put(JSONObject()
                .put("id", b.id).put("title", b.title).put("author", b.author)
                .put("publisher", b.publisher).put("publicationYear", b.publicationYear)
                .put("isbn", b.isbn).put("genre", b.genre).put("language", b.language)
                .put("description", b.description).put("coverUri", b.coverUri).put("ocrText", b.ocrText)
                .put("rating", b.rating.toDouble()).put("status", b.status)
                .put("ownership", b.ownership).put("fileUri", b.fileUri).put("fileType", b.fileType)
                .put("addedAt", b.addedAt).put("lastOpenedAt", b.lastOpenedAt)
                .put("progress", b.progress).put("lastLocation", b.lastLocation)
                .put("notes", b.notes).put("favorite", b.favorite))
            }
        })
        root.put("shelves", JSONArray().apply {
            dao.backupShelves().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("description", it.description).put("createdAt", it.createdAt)) }
        })
        root.put("bookShelves", JSONArray().apply {
            dao.backupBookShelves().forEach { put(JSONObject().put("bookId", it.bookId).put("shelfId", it.shelfId)) }
        })
        root.put("bookmarks", JSONArray().apply {
            dao.backupBookmarks().forEach { put(JSONObject().put("id", it.id).put("bookId", it.bookId).put("location", it.location).put("label", it.label).put("createdAt", it.createdAt)) }
        })
        root.put("annotations", JSONArray().apply {
            dao.backupAnnotations().forEach { put(JSONObject().put("id", it.id).put("bookId", it.bookId).put("location", it.location).put("quote", it.quote).put("note", it.note).put("color", it.color).put("createdAt", it.createdAt)) }
        })
        root.put("commonplace", JSONArray().apply {
            dao.backupCommonplace().forEach { e -> put(JSONObject().put("id", e.id).put("bookId", e.bookId ?: JSONObject.NULL).put("source", e.source).put("quote", e.quote).put("reflection", e.reflection).put("tags", e.tags).put("createdAt", e.createdAt)) }
        })
        root.put("readingSessions", JSONArray().apply {
            dao.backupReadingSessions().forEach { put(JSONObject().put("id", it.id).put("bookId", it.bookId).put("startedAt", it.startedAt).put("durationMinutes", it.durationMinutes).put("pagesRead", it.pagesRead)) }
        })
        return root.toString(2)
    }

    suspend fun restore(json: String, dao: AthenaeumDao) {
        val root = JSONObject(json)
        require(root.optString("format") == "athenaeum-backup") { "This is not an Athenaeum backup." }
        require(root.optInt("version", 0) in 1..1) { "Unsupported backup version." }
        fun array(name: String) = root.optJSONArray(name) ?: JSONArray()
        val books = (0 until array("books").length()).map { i ->
            val o = array("books").getJSONObject(i)
            AthenaeumBook(
                id = o.optLong("id"), title = o.optString("title", "Untitled"),
                author = o.optString("author"), publisher = o.optString("publisher"),
                publicationYear = o.optString("publicationYear"), isbn = o.optString("isbn"),
                genre = o.optString("genre"), language = o.optString("language"),
                description = o.optString("description"), coverUri = o.optString("coverUri"),
                rating = o.optDouble("rating", 0.0).toFloat(), status = o.optString("status", "Want to Read"),
                ownership = o.optString("ownership", "Physical"), fileUri = o.optString("fileUri"),
                fileType = o.optString("fileType"), addedAt = o.optLong("addedAt"),
                lastOpenedAt = o.optLong("lastOpenedAt"), progress = o.optInt("progress"),
                lastLocation = o.optInt("lastLocation"), notes = o.optString("notes"),
                favorite = o.optBoolean("favorite"), ocrText = o.optString("ocrText")
            )
        }
        val shelves = (0 until array("shelves").length()).map { i ->
            val o = array("shelves").getJSONObject(i)
            AthenaeumShelf(o.optLong("id"), o.optString("name"), o.optString("description"), o.optLong("createdAt"))
        }
        val joins = (0 until array("bookShelves").length()).map { i ->
            val o = array("bookShelves").getJSONObject(i)
            AthenaeumBookShelf(o.optLong("bookId"), o.optLong("shelfId"))
        }
        val bookmarks = (0 until array("bookmarks").length()).map { i ->
            val o = array("bookmarks").getJSONObject(i)
            AthenaeumBookmark(o.optLong("id"), o.optLong("bookId"), o.optInt("location"), o.optString("label"), o.optLong("createdAt"))
        }
        val annotations = (0 until array("annotations").length()).map { i ->
            val o = array("annotations").getJSONObject(i)
            AthenaeumAnnotation(o.optLong("id"), o.optLong("bookId"), o.optInt("location"), o.optString("quote"), o.optString("note"), o.optString("color", "amber"), o.optLong("createdAt"))
        }
        val commonplace = (0 until array("commonplace").length()).map { i ->
            val o = array("commonplace").getJSONObject(i)
            AthenaeumCommonplaceEntry(o.optLong("id"), if (o.isNull("bookId")) null else o.optLong("bookId"), o.optString("source"), o.optString("quote"), o.optString("reflection"), o.optString("tags"), o.optLong("createdAt"))
        }
        val sessions = (0 until array("readingSessions").length()).map { i ->
            val o = array("readingSessions").getJSONObject(i)
            AthenaeumReadingSession(o.optLong("id"), o.optLong("bookId"), o.optLong("startedAt"), o.optInt("durationMinutes"), o.optInt("pagesRead"))
        }
        dao.restoreBooks(books)
        dao.restoreShelves(shelves)
        dao.restoreBookShelves(joins)
        dao.restoreBookmarks(bookmarks)
        dao.restoreAnnotations(annotations)
        dao.restoreCommonplace(commonplace)
        dao.restoreReadingSessions(sessions)
    }
}

package com.sonora.player

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned, portable JSON archive. File URIs are preserved as references;
 * the actual ebook files remain in their original Android document locations.
 */
object AthenaeumBackup {
    suspend fun export(dao: AthenaeumDao): String {
        val root = JSONObject().put("format", "athenaeum-backup").put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
        root.put("books", JSONArray().apply {
            dao.backupBooks().forEach { b -> put(JSONObject()
                .put("id", b.id).put("title", b.title).put("author", b.author)
                .put("publisher", b.publisher).put("publicationYear", b.publicationYear)
                .put("isbn", b.isbn).put("genre", b.genre).put("language", b.language)
                .put("description", b.description).put("coverUri", b.coverUri)
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
                favorite = o.optBoolean("favorite")
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

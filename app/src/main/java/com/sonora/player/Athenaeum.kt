package com.sonora.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

private val AthenaWalnut = Color(0xFF39261E)
private val AthenaMahogany = Color(0xFF512F27)
private val AthenaGold = Color(0xFFB69A62)
private val AthenaGreen = Color(0xFF183C32)
private val AthenaBurgundy = Color(0xFF592A32)
private val AthenaParchment = Color(0xFFE9DEC6)
private val AthenaIvory = Color(0xFFF5F0E5)
private val AthenaCharcoal = Color(0xFF242321)

@Entity(tableName = "athenaeum_books")
data class AthenaeumBook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val publisher: String = "",
    val publicationYear: String = "",
    val isbn: String = "",
    val genre: String = "",
    val language: String = "",
    val description: String = "",
    val coverUri: String = "",
    val rating: Float = 0f,
    val status: String = "Want to Read",
    val ownership: String = "Physical",
    val fileUri: String = "",
    val fileType: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0L,
    val progress: Int = 0,
    val lastLocation: Int = 0,
    val notes: String = "",
    val favorite: Boolean = false
)

@Entity(tableName = "athenaeum_shelves")
data class AthenaeumShelf(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "athenaeum_book_shelf",
    primaryKeys = ["bookId", "shelfId"],
    foreignKeys = [
        ForeignKey(entity = AthenaeumBook::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AthenaeumShelf::class, parentColumns = ["id"], childColumns = ["shelfId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("shelfId")]
)
data class AthenaeumBookShelf(val bookId: Long, val shelfId: Long)

@Entity(
    tableName = "athenaeum_bookmarks",
    foreignKeys = [ForeignKey(entity = AthenaeumBook::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")]
)
data class AthenaeumBookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val location: Int,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface AthenaeumDao {
    @Query("SELECT * FROM athenaeum_books ORDER BY title COLLATE NOCASE")
    fun observeBooks(): Flow<List<AthenaeumBook>>
    @Query("SELECT * FROM athenaeum_shelves ORDER BY name COLLATE NOCASE")
    fun observeShelves(): Flow<List<AthenaeumShelf>>
    @Query("SELECT * FROM athenaeum_books WHERE id = :id LIMIT 1")
    suspend fun book(id: Long): AthenaeumBook?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBook(book: AthenaeumBook): Long
    @Delete
    suspend fun deleteBook(book: AthenaeumBook)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun saveShelf(shelf: AthenaeumShelf): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToShelf(join: AthenaeumBookShelf)
    @Query("SELECT b.* FROM athenaeum_books b INNER JOIN athenaeum_book_shelf x ON b.id=x.bookId WHERE x.shelfId=:shelfId ORDER BY b.title COLLATE NOCASE")
    fun booksOnShelf(shelfId: Long): Flow<List<AthenaeumBook>>
    @Query("UPDATE athenaeum_books SET progress=:progress, lastLocation=:location, lastOpenedAt=:openedAt WHERE id=:id")
    suspend fun updateProgress(id: Long, progress: Int, location: Int, openedAt: Long = System.currentTimeMillis())
    @Query("UPDATE athenaeum_books SET favorite=:favorite WHERE id=:id")
    suspend fun setFavorite(id: Long, favorite: Boolean)
    @Insert
    suspend fun addBookmark(bookmark: AthenaeumBookmark)
    @Query("SELECT * FROM athenaeum_bookmarks WHERE bookId=:bookId ORDER BY createdAt DESC")
    suspend fun bookmarks(bookId: Long): List<AthenaeumBookmark>
    @Delete
    suspend fun deleteBookmark(bookmark: AthenaeumBookmark)
}

@Database(entities = [AthenaeumBook::class, AthenaeumShelf::class, AthenaeumBookShelf::class, AthenaeumBookmark::class], version = 2, exportSchema = false)
abstract class AthenaeumDatabase : RoomDatabase() {
    abstract fun dao(): AthenaeumDao
    companion object {
        @Volatile private var instance: AthenaeumDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS athenaeum_bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId INTEGER NOT NULL, location INTEGER NOT NULL, label TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(bookId) REFERENCES athenaeum_books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_athenaeum_bookmarks_bookId ON athenaeum_bookmarks(bookId)")
            }
        }
        fun get(context: Context): AthenaeumDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AthenaeumDatabase::class.java, "athenaeum.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

@Composable
fun AthenaeumScreen() {
    val context = LocalContext.current
    val dao = remember { AthenaeumDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val books by dao.observeBooks().collectAsState(initial = emptyList())
    val shelves by dao.observeShelves().collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All Books") }
    var selectedShelf by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(AthenaeumBook(title = "")) }
    var reading by remember { mutableStateOf<AthenaeumBook?>(null) }
    var shelfDialog by remember { mutableStateOf(false) }
    var shelfName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull() ?: "Untitled book"
            val ext = name.substringAfterLast('.', "").lowercase()
            val supported = ext in setOf("epub", "pdf", "txt", "html", "htm", "fb2")
            if (supported) {
                scope.launch {
                    dao.saveBook(AthenaeumBook(
                        title = name.substringBeforeLast('.', name),
                        ownership = "Digital",
                        fileUri = uri.toString(),
                        fileType = ext,
                        status = "Want to Read"
                    ))
                }
            } else message = "That file type is not supported yet: .$ext"
        }
    }

    val filteredBooks = books.filter { book ->
        val matchesSearch = search.isBlank() || listOf(book.title, book.author, book.isbn, book.publisher, book.genre).any { it.contains(search, true) }
        val matchesFilter = when (filter) {
            "Favourites" -> book.favorite
            "Currently Reading" -> book.status == "Currently Reading"
            "Finished" -> book.status == "Finished"
            "Want to Read" -> book.status == "Want to Read"
            "Physical" -> book.ownership.contains("Physical")
            "Digital" -> book.fileUri.isNotBlank()
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Column(Modifier.fillMaxSize().background(AthenaWalnut)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ATHENAEUM", color = AthenaGold, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                Text("Your private reading room.", color = AthenaIvory, fontSize = 27.sp, fontFamily = FontFamily.Serif)
                Text("${books.size} books  ·  ${shelves.size} shelves", color = AthenaParchment.copy(alpha = .75f), fontSize = 11.sp)
            }
            IconButton(onClick = { importLauncher.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "text/html", "application/x-fictionbook+xml", "application/octet-stream")) }) {
                Icon(Icons.Rounded.FileOpen, "Import books", tint = AthenaGold)
            }
            IconButton(onClick = { draft = AthenaeumBook(title = ""); editing = true }) {
                Icon(Icons.Rounded.Add, "Add book", tint = AthenaGold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(search, { search = it }, Modifier.weight(1f), placeholder = { Text("Search title, author, ISBN…", color = AthenaParchment.copy(.55f)) }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AthenaIvory, unfocusedTextColor = AthenaIvory, focusedBorderColor = AthenaGold, unfocusedBorderColor = AthenaGold.copy(.4f), cursorColor = AthenaGold))
            IconButton(onClick = { shelfDialog = true }) { Icon(Icons.Rounded.AddBox, "Create bookshelf", tint = AthenaGold) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("All Books", "Currently Reading", "Want to Read", "Finished", "Favourites", "Physical", "Digital").forEach { choice ->
                FilterChip(selected = filter == choice, onClick = { filter = choice; selectedShelf = null }, label = { Text(choice, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AthenaGold, selectedLabelColor = AthenaWalnut, labelColor = AthenaParchment))
            }
        }
        if (shelves.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shelves.forEach { shelf ->
                    AssistChip(onClick = { selectedShelf = if (selectedShelf == shelf.id) null else shelf.id }, label = { Text(shelf.name) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = if (selectedShelf == shelf.id) AthenaGold else AthenaGreen, labelColor = if (selectedShelf == shelf.id) AthenaWalnut else AthenaIvory))
                }
            }
        }
        if (selectedShelf != null) {
            val shelfBooks by dao.booksOnShelf(selectedShelf!!).collectAsState(initial = emptyList())
            BookCollectionList(shelfBooks, { book -> scope.launch { dao.saveBook(book.copy(status = if (book.status == "Finished") "Finished" else "Currently Reading", lastOpenedAt = System.currentTimeMillis())) }; reading = book }, { book -> draft = book; editing = true }, { book -> scope.launch { dao.setFavorite(book.id, !book.favorite) } }, ink = AthenaIvory, muted = AthenaParchment)
        } else if (filteredBooks.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.MenuBook, null, tint = AthenaGold, modifier = Modifier.size(54.dp))
                Text(if (books.isEmpty()) "Your shelves are waiting." else "No books found.", color = AthenaIvory, fontSize = 22.sp, fontFamily = FontFamily.Serif)
                Text(if (books.isEmpty()) "Add a physical volume or import an ebook to begin your collection." else "Try another search or collection.", color = AthenaParchment, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { draft = AthenaeumBook(title = ""); editing = true }, colors = ButtonDefaults.buttonColors(containerColor = AthenaGold, contentColor = AthenaWalnut)) { Text("ADD YOUR FIRST BOOK") }
            }
        } else BookCollectionList(filteredBooks, { book -> reading = book }, { book -> draft = book; editing = true }, { book -> scope.launch { dao.setFavorite(book.id, !book.favorite) } }, ink = AthenaIvory, muted = AthenaParchment)
        Spacer(Modifier.height(8.dp))
    }

    if (editing) {
        BookEditDialog(draft, shelves, onDismiss = { editing = false }, onSave = { book, shelfIds ->
            scope.launch {
                val id = dao.saveBook(book)
                shelfIds.forEach { dao.addToShelf(AthenaeumBookShelf(id, it)) }
            }
            editing = false
        })
    }
    if (shelfDialog) {
        AlertDialog(onDismissRequest = { shelfDialog = false }, title = { Text("New bookshelf") },
            text = { OutlinedTextField(shelfName, { shelfName = it }, label = { Text("Shelf name") }) },
            confirmButton = { TextButton(onClick = {
                if (shelfName.isNotBlank()) scope.launch { dao.saveShelf(AthenaeumShelf(name = shelfName.trim())) }
                shelfName = ""; shelfDialog = false
            }) { Text("CREATE") } }, dismissButton = { TextButton(onClick = { shelfDialog = false }) { Text("CANCEL") } })
    }
    if (message.isNotBlank()) AlertDialog(onDismissRequest = { message = "" }, title = { Text("Import notice") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { message = "" }) { Text("OK") } })
    reading?.let { book ->
        Dialog(onDismissRequest = { reading = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = AthenaParchment) {
                AthenaeumReader(book, dao, onClose = { reading = null })
            }
        }
    }
}

@Composable
private fun BookCollectionList(
    books: List<AthenaeumBook>,
    onOpen: (AthenaeumBook) -> Unit,
    onEdit: (AthenaeumBook) -> Unit,
    onFavorite: (AthenaeumBook) -> Unit,
    ink: Color,
    muted: Color
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items(books, key = { it.id }) { book ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(AthenaMahogany).clickable { onOpen(book) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(7.dp).height(68.dp).background(listOf(AthenaGreen, AthenaBurgundy, AthenaGold, Color(0xFF76513D))[((book.id % 4).toInt())]))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, color = ink, fontSize = 17.sp, fontFamily = FontFamily.Serif, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(book.author.ifBlank { "Author unknown" }, color = muted, fontSize = 11.sp)
                    Text("${book.status.uppercase()}  ·  ${if (book.fileUri.isNotBlank()) "DIGITAL" else book.ownership.uppercase()}${if (book.progress > 0) "  ·  ${book.progress}%" else ""}", color = AthenaGold, fontSize = 8.sp, letterSpacing = 1.sp)
                }
                IconButton(onClick = { onFavorite(book) }) { Icon(if (book.favorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "Favourite", tint = AthenaGold) }
                IconButton(onClick = { onEdit(book) }) { Icon(Icons.Rounded.Edit, "Edit book", tint = muted) }
            }
        }
    }
}

@Composable
private fun BookEditDialog(book: AthenaeumBook, shelves: List<AthenaeumShelf>, onDismiss: () -> Unit, onSave: (AthenaeumBook, List<Long>) -> Unit) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var publisher by remember(book.id) { mutableStateOf(book.publisher) }
    var year by remember(book.id) { mutableStateOf(book.publicationYear) }
    var isbn by remember(book.id) { mutableStateOf(book.isbn) }
    var genre by remember(book.id) { mutableStateOf(book.genre) }
    var language by remember(book.id) { mutableStateOf(book.language) }
    var description by remember(book.id) { mutableStateOf(book.description) }
    var status by remember(book.id) { mutableStateOf(book.status) }
    var ownership by remember(book.id) { mutableStateOf(book.ownership) }
    var notes by remember(book.id) { mutableStateOf(book.notes) }
    var coverUri by remember(book.id) { mutableStateOf(book.coverUri) }
    var rating by remember(book.id) { mutableStateOf(book.rating.toString()) }
    val context = LocalContext.current
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            coverUri = uri.toString()
        }
    }
    val chosen = remember(book.id) { mutableStateListOf<Long>() }
    AlertDialog(onDismissRequest = onDismiss, containerColor = AthenaIvory,
        title = { Text(if (book.id == 0L) "Add to Athenaeum" else "Edit volume", color = AthenaWalnut, fontFamily = FontFamily.Serif) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                @Composable fun field(label: String, value: String, update: (String) -> Unit) { OutlinedTextField(value, update, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = label != "Description" && label != "Personal notes") }
                field("Title", title, { title = it }); field("Author", author, { author = it }); field("Publisher", publisher, { publisher = it })
                field("Publication year", year, { year = it }); field("ISBN", isbn, { isbn = it }); field("Genre", genre, { genre = it })
                field("Language", language, { language = it }); field("Description", description, { description = it }); field("Personal notes", notes, { notes = it })
                field("Personal rating (0–5)", rating, { rating = it })
                OutlinedButton(onClick = { coverPicker.launch(arrayOf("image/*")) }) { Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(6.dp)); Text(if (coverUri.isBlank()) "CHOOSE COVER IMAGE" else "CHANGE COVER IMAGE") }
                Text(if (coverUri.isBlank()) "No cover selected" else "Custom cover attached", color = AthenaGreen, fontSize = 10.sp)
                Text("READING STATUS", color = AthenaWalnut, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Want to Read", "Currently Reading", "Finished", "Paused", "Abandoned").forEach { s ->
                        FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s, fontSize = 9.sp) })
                    }
                }
                Text("EDITION", color = AthenaWalnut, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Physical", "Digital", "Both").forEach { s -> FilterChip(selected = ownership == s, onClick = { ownership = s }, label = { Text(s) }) }
                }
                if (shelves.isNotEmpty()) {
                    Text("ADD TO SHELVES", color = AthenaWalnut, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    shelves.forEach { shelf -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shelf.id in chosen, onCheckedChange = { if (it) chosen.add(shelf.id) else chosen.remove(shelf.id) })
                        Text(shelf.name, color = AthenaWalnut)
                    } }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            if (title.isNotBlank()) onSave(book.copy(title = title.trim(), author = author.trim(), publisher = publisher, publicationYear = year, isbn = isbn, genre = genre, language = language, description = description, status = status, ownership = ownership, notes = notes, coverUri = coverUri, rating = rating.toFloatOrNull()?.coerceIn(0f, 5f) ?: 0f), chosen.toList())
        }) { Text("SAVE", color = AthenaGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun AthenaeumReader(book: AthenaeumBook, dao: AthenaeumDao, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = book.fileType.ifBlank { book.fileUri.substringBefore('?').substringAfterLast('.', "").lowercase() }
    var page by remember(book.id) { mutableIntStateOf(book.lastLocation.coerceAtLeast(0)) }
    var pageCount by remember { mutableIntStateOf(1) }
    var content by remember(book.id) { mutableStateOf("Loading your book…") }
    var bitmap by remember(book.id) { mutableStateOf<Bitmap?>(null) }
    var textSize by remember { mutableFloatStateOf(18f) }
    val textScroll = rememberScrollState(book.lastLocation.coerceAtLeast(0))
    var dark by remember { mutableStateOf(false) }
    var readerWebView by remember { mutableStateOf<WebView?>(null) }
    var showBookmarks by remember { mutableStateOf(false) }
    var savedBookmarks by remember(book.id) { mutableStateOf(emptyList<AthenaeumBookmark>()) }

    LaunchedEffect(book.id, showBookmarks) {
        if (showBookmarks) savedBookmarks = dao.bookmarks(book.id)
    }

    DisposableEffect(readerWebView) {
        onDispose {
            readerWebView?.let { web ->
                scope.launch { dao.updateProgress(book.id, book.progress, web.scrollY) }
            }
        }
    }

    LaunchedEffect(book.id) {
        if (book.fileUri.isBlank()) content = "This is a physical catalogue entry. Import a digital edition to read it here."
        else if (ext == "pdf") {
            withContext(Dispatchers.IO) {
                runCatching {
                    val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(book.fileUri), "r")!!
                    val renderer = PdfRenderer(descriptor)
                    pageCount = renderer.pageCount
                    val index = page.coerceIn(0, renderer.pageCount - 1)
                    val pdfPage = renderer.openPage(index)
                    val image = Bitmap.createBitmap(pdfPage.width * 2, pdfPage.height * 2, Bitmap.Config.ARGB_8888)
                    pdfPage.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pdfPage.close(); renderer.close(); descriptor.close()
                    bitmap = image
                }.onFailure { content = "Could not open this PDF: ${it.message}" }
            }
        } else {
            content = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(Uri.parse(book.fileUri))!!.use { it.readBytes() }
                    when (ext) {
                        "txt" -> bytes.toString(Charsets.UTF_8)
                        "html", "htm" -> bytes.toString(Charsets.UTF_8)
                        "fb2" -> {
                            val xml = bytes.toString(Charsets.UTF_8)
                            val body = xml.substringAfter("<body", xml).substringAfter(">", xml).substringBefore("</body>", xml)
                            "<html><body>" + body.replace(Regex("<p[^>]*>"), "<p>") + "</body></html>"
                        }
                        "epub" -> {
                            val output = StringBuilder()
                            ZipInputStream(bytes.inputStream()).use { zip ->
                                var entry = zip.nextEntry
                                while (entry != null) {
                                    if (!entry.isDirectory && (entry.name.endsWith(".xhtml", true) || entry.name.endsWith(".html", true) || entry.name.endsWith(".htm", true))) {
                                        val stream = ByteArrayOutputStream()
                                        zip.copyTo(stream)
                                        output.append(stream.toString("UTF-8")).append("<hr/>")
                                    }
                                    zip.closeEntry()
                                    entry = zip.nextEntry
                                }
                            }
                            output.toString().ifBlank { "This EPUB contains no readable XHTML spine content." }
                        }
                        else -> "This format is not supported by the current reader. Supported formats in this release: EPUB, PDF, TXT and HTML."
                    }
                }.getOrElse { "Unable to read this file: ${it.message}" }
            }
        }
        dao.updateProgress(book.id, book.progress, page, System.currentTimeMillis())
    }

    LaunchedEffect(textScroll.value, textScroll.maxValue) {
        if (ext == "txt" && textScroll.maxValue > 0) {
            kotlinx.coroutines.delay(350)
            dao.updateProgress(book.id, (textScroll.value * 100 / textScroll.maxValue).coerceIn(0, 100), textScroll.value)
        }
    }

    Column(Modifier.fillMaxSize().background(if (dark) AthenaCharcoal else AthenaParchment)) {
        Row(Modifier.fillMaxWidth().background(AthenaWalnut).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "Close reader", tint = AthenaIvory) }
            Column(Modifier.weight(1f)) {
                Text(book.title, color = AthenaIvory, fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author, color = AthenaGold, fontSize = 10.sp)
            }
            IconButton(onClick = { dark = !dark }) { Icon(Icons.Rounded.Contrast, "Toggle reading theme", tint = AthenaGold) }
            IconButton(onClick = { showBookmarks = true }) { Icon(Icons.Rounded.Bookmarks, "Bookmarks", tint = AthenaGold) }
            IconButton(onClick = {
                scope.launch {
                    val location = if (ext == "pdf") page else if (ext == "txt") textScroll.value else readerWebView?.scrollY ?: 0
                    dao.addBookmark(AthenaeumBookmark(bookId = book.id, location = location, label = if (ext == "pdf") "Page ${page + 1}" else "Position $location"))
                    savedBookmarks = dao.bookmarks(book.id)
                }
            }) { Icon(Icons.Rounded.BookmarkAdd, "Save bookmark", tint = AthenaGold) }
            IconButton(onClick = { textSize = (textSize + 1f).coerceAtMost(30f) }) { Icon(Icons.Rounded.TextIncrease, "Increase text size", tint = AthenaGold) }
        }
        if (ext == "pdf" && bitmap != null) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                Image(bitmap!!.asImageBitmap(), contentDescription = "PDF page ${page + 1}", modifier = Modifier.fillMaxSize())
            }
            Row(Modifier.fillMaxWidth().background(AthenaWalnut).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("PREVIOUS", color = AthenaGold) }
                Text("${page + 1} / ${pageCount}", color = AthenaIvory)
                TextButton(onClick = { if (page < pageCount - 1) page++ }, enabled = page < pageCount - 1) { Text("NEXT", color = AthenaGold) }
            }
            LaunchedEffect(page) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(book.fileUri), "r")!!
                        val renderer = PdfRenderer(descriptor)
                        val pdfPage = renderer.openPage(page)
                        val image = Bitmap.createBitmap(pdfPage.width * 2, pdfPage.height * 2, Bitmap.Config.ARGB_8888)
                        pdfPage.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pdfPage.close(); renderer.close(); descriptor.close(); bitmap = image
                    }
                }
                dao.updateProgress(book.id, ((page + 1) * 100 / pageCount.coerceAtLeast(1)), page)
            }
        } else if (ext == "epub" || ext == "html" || ext == "htm" || ext == "fb2") {
            AndroidView(factory = { ctx -> WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.scrollTo(0, book.lastLocation.coerceAtLeast(0))
                    }
                }
                setBackgroundColor(if (dark) android.graphics.Color.DKGRAY else android.graphics.Color.rgb(233, 222, 198))
                tag = content
                readerWebView = this
                loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
            } }, update = { web ->
                web.setBackgroundColor(if (dark) android.graphics.Color.DKGRAY else android.graphics.Color.rgb(233, 222, 198))
                if (web.tag != content) { web.tag = content; web.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null) }
                if (web.scrollY == 0 && book.lastLocation > 0) web.post { web.scrollTo(0, book.lastLocation) }
            }, modifier = Modifier.weight(1f).fillMaxWidth())
            Text("EPUB / HTML reader · adjustable theme and library position", color = if (dark) AthenaParchment else AthenaWalnut, fontSize = 10.sp, modifier = Modifier.padding(12.dp))
        } else {
            androidx.compose.foundation.rememberScrollState()
            androidx.compose.foundation.verticalScroll(androidx.compose.foundation.rememberScrollState())
            Text(content, color = if (dark) AthenaParchment else AthenaCharcoal, fontSize = textSize.sp, lineHeight = (textSize * 1.55f).sp,
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp))
            Row(Modifier.fillMaxWidth().background(AthenaWalnut).padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { textSize = (textSize - 1f).coerceAtLeast(12f) }) { Text("A−", color = AthenaGold) }
                TextButton(onClick = { textSize = (textSize + 1f).coerceAtMost(30f) }) { Text("A+", color = AthenaGold) }
                TextButton(onClick = { scope.launch { dao.updateProgress(book.id, if (textScroll.maxValue > 0) (textScroll.value * 100 / textScroll.maxValue).coerceIn(0, 100) else 0, textScroll.value) } }) { Text("SAVE POSITION", color = AthenaGold) }
            }
        }
    }
    if (showBookmarks) {
        AlertDialog(onDismissRequest = { showBookmarks = false }, title = { Text("Bookmarks · ${book.title}", fontFamily = FontFamily.Serif) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (savedBookmarks.isEmpty()) Text("No saved bookmarks yet.")
                    savedBookmarks.forEach { mark ->
                        Row(Modifier.fillMaxWidth().clickable {
                            if (ext == "pdf") page = mark.location else if (ext == "txt") scope.launch { textScroll.scrollTo(mark.location) } else readerWebView?.scrollTo(0, mark.location)
                            showBookmarks = false
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(mark.label, Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { dao.deleteBookmark(mark); savedBookmarks = dao.bookmarks(book.id) } }) {
                                Icon(Icons.Rounded.DeleteOutline, "Delete bookmark")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text("CLOSE") } })
    }
}

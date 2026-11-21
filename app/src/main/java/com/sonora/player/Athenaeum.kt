package com.sonora.player

import android.content.Context
import org.json.JSONTokener
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.room.migration.Migration
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
    @Query("UPDATE athenaeum_books SET coverUri=:coverUri WHERE id=:id")
    suspend fun updateCover(id: Long, coverUri: String)
    @Insert
    suspend fun addBookmark(bookmark: AthenaeumBookmark)
    @Query("SELECT * FROM athenaeum_bookmarks WHERE bookId=:bookId ORDER BY createdAt DESC")
    suspend fun bookmarks(bookId: Long): List<AthenaeumBookmark>
    @Delete
    suspend fun deleteBookmark(bookmark: AthenaeumBookmark)

    @Insert
    suspend fun addAnnotation(annotation: AthenaeumAnnotation)
    @Query("SELECT * FROM athenaeum_annotations ORDER BY createdAt DESC")
    fun observeAnnotations(): Flow<List<AthenaeumAnnotation>>
    @Query("SELECT * FROM athenaeum_annotations WHERE bookId=:bookId ORDER BY createdAt")
    suspend fun annotationsForBook(bookId: Long): List<AthenaeumAnnotation>
    @Delete
    suspend fun deleteAnnotation(annotation: AthenaeumAnnotation)

    @Insert
    suspend fun addCommonplace(entry: AthenaeumCommonplaceEntry)
    @Query("SELECT * FROM athenaeum_commonplace ORDER BY createdAt DESC")
    fun observeCommonplace(): Flow<List<AthenaeumCommonplaceEntry>>
    @Delete
    suspend fun deleteCommonplace(entry: AthenaeumCommonplaceEntry)

    @Insert
    suspend fun addReadingSession(session: AthenaeumReadingSession)
    @Query("SELECT * FROM athenaeum_reading_sessions ORDER BY startedAt DESC")
    fun observeReadingSessions(): Flow<List<AthenaeumReadingSession>>

    @Query("SELECT * FROM athenaeum_books")
    suspend fun backupBooks(): List<AthenaeumBook>
    @Query("SELECT * FROM athenaeum_shelves")
    suspend fun backupShelves(): List<AthenaeumShelf>
    @Query("SELECT * FROM athenaeum_book_shelf")
    suspend fun backupBookShelves(): List<AthenaeumBookShelf>
    @Query("SELECT * FROM athenaeum_bookmarks")
    suspend fun backupBookmarks(): List<AthenaeumBookmark>
    @Query("SELECT * FROM athenaeum_annotations")
    suspend fun backupAnnotations(): List<AthenaeumAnnotation>
    @Query("SELECT * FROM athenaeum_commonplace")
    suspend fun backupCommonplace(): List<AthenaeumCommonplaceEntry>
    @Query("SELECT * FROM athenaeum_reading_sessions")
    suspend fun backupReadingSessions(): List<AthenaeumReadingSession>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreBooks(items: List<AthenaeumBook>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreShelves(items: List<AthenaeumShelf>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreBookShelves(items: List<AthenaeumBookShelf>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreBookmarks(items: List<AthenaeumBookmark>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAnnotations(items: List<AthenaeumAnnotation>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreCommonplace(items: List<AthenaeumCommonplaceEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreReadingSessions(items: List<AthenaeumReadingSession>)
}

@Database(entities = [AthenaeumBook::class, AthenaeumShelf::class, AthenaeumBookShelf::class, AthenaeumBookmark::class, AthenaeumAnnotation::class, AthenaeumCommonplaceEntry::class, AthenaeumReadingSession::class], version = 3, exportSchema = false)
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
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS athenaeum_annotations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId INTEGER NOT NULL, location INTEGER NOT NULL, quote TEXT NOT NULL, note TEXT NOT NULL, color TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(bookId) REFERENCES athenaeum_books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_athenaeum_annotations_bookId ON athenaeum_annotations(bookId)")
                database.execSQL("CREATE TABLE IF NOT EXISTS athenaeum_commonplace (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId INTEGER, source TEXT NOT NULL, quote TEXT NOT NULL, reflection TEXT NOT NULL, tags TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(bookId) REFERENCES athenaeum_books(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_athenaeum_commonplace_bookId ON athenaeum_commonplace(bookId)")
                database.execSQL("CREATE TABLE IF NOT EXISTS athenaeum_reading_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId INTEGER NOT NULL, startedAt INTEGER NOT NULL, durationMinutes INTEGER NOT NULL, pagesRead INTEGER NOT NULL, FOREIGN KEY(bookId) REFERENCES athenaeum_books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_athenaeum_reading_sessions_bookId ON athenaeum_reading_sessions(bookId)")
            }
        }
        fun get(context: Context): AthenaeumDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AthenaeumDatabase::class.java, "athenaeum.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}

@Composable
fun AthenaeumScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val dao = remember { AthenaeumDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("athenaeum_preferences", Context.MODE_PRIVATE) }
    var readingGoal by remember { mutableIntStateOf(preferences.getInt("reading_goal_books", 12).coerceIn(1, 200)) }
    val books by dao.observeBooks().collectAsState(initial = emptyList())
    val shelves by dao.observeShelves().collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All books") }
    var selectedShelf by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(AthenaeumBook(title = "")) }
    var reading by remember { mutableStateOf<AthenaeumBook?>(null) }
    var shelfDialog by remember { mutableStateOf(false) }
    var shelfName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var gridMode by remember { mutableStateOf(preferences.getBoolean("grid_mode", true)) }
    var sortBy by remember { mutableStateOf(preferences.getString("sort_books_by", "Recently added") ?: "Recently added") }
    var showSort by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }
    var showBackupMenu by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = AthenaeumBackup.export(dao)
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                message = "Library metadata archive exported. Ebook files remain in their original locations."
            }.onFailure { message = "Backup failed: " + it.message }
        }
    }
    val portableExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: error("Could not create backup file")
                AthenaeumBackup.writePortableZip(context, dao, output)
                message = "Portable ZIP backup exported with accessible ebook and cover files."
            }.onFailure { message = "ZIP backup failed: " + it.message }
        }
    }
    val portableRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: error("Could not open backup file")
                AthenaeumBackup.restorePortableZip(context, input, dao)
                message = "Portable ZIP archive restored."
            }.onFailure { message = "ZIP restore failed: " + it.message }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val csv = AthenaeumBackup.exportCsv(dao)
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
                message = "Catalogue CSV exported."
            }.onFailure { message = "CSV export failed: " + it.message }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Could not read backup file")
                AthenaeumBackup.restore(json, dao)
                message = "Athenaeum backup restored."
            }.onFailure { message = "Restore failed: " + it.message }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull() ?: "Untitled book"
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in setOf("epub", "pdf", "txt", "html", "htm", "fb2", "mobi", "rtf", "md", "markdown", "cbz", "cbr")) {
                scope.launch {
                    val epubDocument = if (ext == "epub") withContext(Dispatchers.IO) {
                        runCatching {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read EPUB")
                            EpubParser.parse(bytes)
                        }.getOrNull()
                    } else null
                    val bookId = dao.saveBook(
                        AthenaeumBook(
                            title = epubDocument?.title?.ifBlank { name.substringBeforeLast('.', name) } ?: name.substringBeforeLast('.', name),
                            author = epubDocument?.author.orEmpty(),
                            publisher = epubDocument?.publisher.orEmpty(),
                            publicationYear = epubDocument?.publicationYear.orEmpty(),
                            isbn = epubDocument?.isbn.orEmpty(),
                            genre = epubDocument?.subject.orEmpty(),
                            language = epubDocument?.language.orEmpty(),
                            description = epubDocument?.description.orEmpty(),
                            ownership = "Digital", fileUri = uri.toString(), fileType = ext, status = "Want to Read"
                        )
                    )
                    val cover = epubDocument?.coverImage
                    if (cover != null) withContext(Dispatchers.IO) {
                        val directory = java.io.File(context.filesDir, "athenaeum_covers").apply { mkdirs() }
                        val file = java.io.File(directory, "$bookId.cover")
                        file.writeBytes(cover)
                        dao.updateCover(bookId, Uri.fromFile(file).toString())
                    }
                }
            } else message = "This format is not supported by the current reader: .$ext"
        }
    }

    val matchingBooks = books.filter { book ->
        val matchesSearch = search.isBlank() || listOf(book.title, book.author, book.isbn, book.publisher, book.genre, book.language, book.description, book.notes, book.publicationYear).any { it.contains(search, true) }
        val matchesFilter = when (filter) {
            "Currently reading" -> book.status == "Currently Reading"
            "Want to read" -> book.status == "Want to Read"
            "Finished" -> book.status == "Finished"
            "Favourites" -> book.favorite
            "Physical" -> book.ownership.contains("Physical")
            "Digital" -> book.fileUri.isNotBlank()
            else -> true
        }
        matchesSearch && matchesFilter
    }
    val visibleBooks = when (sortBy) {
        "Title A–Z" -> matchingBooks.sortedBy { it.title.lowercase() }
        "Author A–Z" -> matchingBooks.sortedBy { it.author.lowercase() }
        "Progress" -> matchingBooks.sortedByDescending { it.progress }
        "Highest rated" -> matchingBooks.sortedByDescending { it.rating }
        "Publication year" -> matchingBooks.sortedByDescending { it.publicationYear.toIntOrNull() ?: 0 }
        "Recently read" -> matchingBooks.sortedByDescending { it.lastOpenedAt }
        else -> matchingBooks.sortedByDescending { it.addedAt }
    }
    val currentlyReading = books.filter { it.status == "Currently Reading" }.sortedByDescending { it.lastOpenedAt }

    LaunchedEffect(books) { AthenaeumWidgetUpdater.refresh(context) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF6F5F1))) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("YOUR LIBRARY", color = Color(0xFF77766F), fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold)
                Text("Books", color = Color(0xFF20211F), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("${books.size} books in your collection", color = Color(0xFF77766F), fontSize = 12.sp)
            }
            IconButton(onClick = { showArchive = true }) { Icon(Icons.Rounded.AutoStories, "Open Commonplace Book and reading archive", tint = Color(0xFF454640)) }
            Box {
                IconButton(onClick = { showBackupMenu = true }) { Icon(Icons.Rounded.MoreVert, "Backup and export options", tint = Color(0xFF454640)) }
                DropdownMenu(expanded = showBackupMenu, onDismissRequest = { showBackupMenu = false }) {
                    DropdownMenuItem(text = { Text("Export portable ZIP (with book files)") }, onClick = { showBackupMenu = false; portableExportLauncher.launch("athenaeum-portable-backup.zip") })
                    DropdownMenuItem(text = { Text("Restore portable ZIP") }, onClick = { showBackupMenu = false; portableRestoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) })
                    DropdownMenuItem(text = { Text("Export library JSON archive") }, onClick = { showBackupMenu = false; exportLauncher.launch("athenaeum-backup.json") })
                    DropdownMenuItem(text = { Text("Export catalogue CSV") }, onClick = { showBackupMenu = false; csvLauncher.launch("athenaeum-catalogue.csv") })
                    DropdownMenuItem(text = { Text("Restore / merge JSON archive") }, onClick = { showBackupMenu = false; restoreLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) })
                }
            }
            IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "Close Athenaeum", tint = Color(0xFF454640)) }
            IconButton(onClick = { importLauncher.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "text/html", "application/x-fictionbook+xml", "application/x-mobipocket-ebook", "application/rtf", "text/markdown", "application/vnd.comicbook+zip", "application/octet-stream")) }) {
                Icon(Icons.Rounded.FileOpen, "Import books", tint = Color(0xFF30312D))
            }
            FilledIconButton(onClick = { draft = AthenaeumBook(title = ""); editing = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF252722), contentColor = Color.White)) {
                Icon(Icons.Rounded.Add, "Add book")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("YEARLY READING GOAL", color = Color(0xFF77766F), fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${books.count { it.status == "Finished" }} / $readingGoal books", color = Color(0xFF55564F), fontSize = 10.sp)
            IconButton(onClick = { readingGoal = (readingGoal - 1).coerceAtLeast(1); preferences.edit().putInt("reading_goal_books", readingGoal).apply() }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Remove, "Decrease reading goal", tint = Color(0xFF55564F), modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = { readingGoal = (readingGoal + 1).coerceAtMost(200); preferences.edit().putInt("reading_goal_books", readingGoal).apply() }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Add, "Increase reading goal", tint = Color(0xFF55564F), modifier = Modifier.size(15.dp))
            }
        }
        LinearProgressIndicator(
            progress = { (books.count { it.status == "Finished" }.toFloat() / readingGoal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(3.dp).clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF66816B), trackColor = Color(0xFFE5E5DE)
        )

        if (currentlyReading.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("PICK UP WHERE YOU LEFT OFF", color = Color(0xFF77766F), fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${currentlyReading.size} reading", color = Color(0xFF77766F), fontSize = 10.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    currentlyReading.take(8).forEach { book ->
                        Row(
                            Modifier.width(278.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                                .clickable { reading = book }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AthenaBookCover(book, Modifier.width(54.dp).height(76.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(book.title, color = Color(0xFF20211F), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(book.author.ifBlank { "Unknown author" }, color = Color(0xFF77766F), fontSize = 11.sp, maxLines = 1)
                                LinearProgressIndicator(progress = { book.progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)), color = Color(0xFF56745D), trackColor = Color(0xFFE8E8E2))
                                Text("${book.progress.coerceIn(0, 100)}% read", color = Color(0xFF77766F), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                placeholder = { Text("Search your books", fontSize = 13.sp, color = Color(0xFF85867F)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color(0xFF77766F)) },
                trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF20211F), unfocusedTextColor = Color(0xFF20211F),
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF777F70), unfocusedBorderColor = Color(0xFFE3E2DC),
                    cursorColor = Color(0xFF56745D)
                )
            )
            Spacer(Modifier.width(8.dp))
            Box {
                IconButton(onClick = { showSort = true }) { Icon(Icons.Rounded.Sort, "Sort books", tint = Color(0xFF454640)) }
                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                    listOf("Recently added", "Recently read", "Title A–Z", "Author A–Z", "Publication year", "Highest rated", "Progress").forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { sortBy = option; preferences.edit().putString("sort_books_by", option).apply(); showSort = false })
                    }
                }
            }
            IconButton(onClick = { gridMode = !gridMode; preferences.edit().putBoolean("grid_mode", gridMode).apply() }) {
                Icon(if (gridMode) Icons.Rounded.ViewList else Icons.Rounded.GridView, if (gridMode) "Switch to list" else "Switch to grid", tint = Color(0xFF454640))
            }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All books", "Currently reading", "Want to read", "Finished", "Favourites", "Physical", "Digital").forEach { choice ->
                FilterChip(
                    selected = filter == choice, onClick = { filter = choice; selectedShelf = null },
                    label = { Text(choice, fontSize = 11.sp) },
                    shape = RoundedCornerShape(9.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF252722), selectedLabelColor = Color.White,
                        containerColor = Color.Transparent, labelColor = Color(0xFF55564F)
                    ),
                )
            }
        }

        if (shelves.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                shelves.forEach { shelf ->
                    FilterChip(
                        selected = selectedShelf == shelf.id,
                        onClick = { selectedShelf = if (selectedShelf == shelf.id) null else shelf.id },
                        label = { Text(shelf.name, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE4E9E1), selectedLabelColor = Color(0xFF334B38), labelColor = Color(0xFF65665F))
                    )
                }
                TextButton(onClick = { shelfDialog = true }) { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(14.dp)); Text("New shelf", fontSize = 11.sp) }
            }
        }

        if (books.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selectedShelf != null) "SHELF" else filter.uppercase(), color = Color(0xFF77766F), fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${visibleBooks.size} titles · $sortBy", color = Color(0xFF85867F), fontSize = 10.sp)
            }
        }

        val shelfBooksFlow = remember(selectedShelf) {
            selectedShelf?.let { dao.booksOnShelf(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList<AthenaeumBook>())
        }
        val shelfBooks by shelfBooksFlow.collectAsState(initial = emptyList())

        val displayBooks = if (selectedShelf != null) {
            when (sortBy) {
                "Title A–Z" -> shelfBooks.sortedBy { it.title.lowercase() }
                "Author A–Z" -> shelfBooks.sortedBy { it.author.lowercase() }
                "Progress" -> shelfBooks.sortedByDescending { it.progress }
                "Highest rated" -> shelfBooks.sortedByDescending { it.rating }
                "Publication year" -> shelfBooks.sortedByDescending { it.publicationYear.toIntOrNull() ?: 0 }
                "Recently read" -> shelfBooks.sortedByDescending { it.lastOpenedAt }
                else -> shelfBooks.sortedByDescending { it.addedAt }
            }.filter { search.isBlank() || listOf(it.title, it.author, it.isbn, it.publisher, it.genre, it.language, it.description, it.notes, it.publicationYear).any { value -> value.contains(search, true) } }
        } else visibleBooks

        if (displayBooks.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.MenuBook, null, tint = Color(0xFF92938A), modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text(if (books.isEmpty()) "Your next favourite book starts here." else "No books found", color = Color(0xFF292A26), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(if (books.isEmpty()) "Add a book or import an EPUB, PDF or text file." else "Try a different search or filter.", color = Color(0xFF77766F), fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { draft = AthenaeumBook(title = ""); editing = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252722), contentColor = Color.White), shape = RoundedCornerShape(10.dp)) { Text("ADD A BOOK") }
            }
        } else {
            BookCollectionList(
                books = displayBooks,
                onOpen = { book ->
                    scope.launch {
                        dao.saveBook(book.copy(status = if (book.status == "Finished") "Finished" else "Currently Reading", lastOpenedAt = System.currentTimeMillis()))
                    }
                    reading = book
                },
                onEdit = { draft = it; editing = true },
                onFavorite = { book -> scope.launch { dao.setFavorite(book.id, !book.favorite) } },
                ink = Color(0xFF252722), muted = Color(0xFF77766F), gridMode = gridMode
            )
        }
    }

    if (editing) {
        BookEditDialog(draft, shelves, onDismiss = { editing = false }, onDelete = { book -> scope.launch { dao.deleteBook(book) }; editing = false }, onSave = { book, shelfIds ->
            scope.launch {
                val id = dao.saveBook(book)
                shelfIds.forEach { dao.addToShelf(AthenaeumBookShelf(id, it)) }
            }
            editing = false
        })
    }
    if (shelfDialog) {
        AlertDialog(
            onDismissRequest = { shelfDialog = false },
            title = { Text("New shelf", color = Color(0xFF252722)) },
            text = { OutlinedTextField(shelfName, { shelfName = it }, label = { Text("Shelf name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                if (shelfName.isNotBlank()) scope.launch { dao.saveShelf(AthenaeumShelf(name = shelfName.trim())) }
                shelfName = ""; shelfDialog = false
            }) { Text("CREATE", color = Color(0xFF425D48)) } },
            dismissButton = { TextButton(onClick = { shelfDialog = false }) { Text("CANCEL") } }
        )
    }
    if (message.isNotBlank()) AlertDialog(onDismissRequest = { message = "" }, title = { Text("Import notice") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { message = "" }) { Text("OK") } })
    if (showArchive) AthenaeumArchivePanel(dao, books, onClose = { showArchive = false })
    reading?.let { book ->
        Dialog(onDismissRequest = { reading = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFFF6F3EC)) {
                AthenaeumReader(book, dao, onClose = { reading = null })
            }
        }
    }
}

@Composable
private fun AthenaBookCover(book: AthenaeumBook, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(book.coverUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(book.coverUri) {
        bitmap = if (book.coverUri.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(book.coverUri))?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    val palettes = listOf(Color(0xFF354B43), Color(0xFF6A3D3D), Color(0xFF394D68), Color(0xFF94734D), Color(0xFF4D465F), Color(0xFF65745A))
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFFE7E5DE))) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = "Cover of ${book.title}", modifier = Modifier.fillMaxSize())
        } else {
            val coverColor = palettes[(book.id % palettes.size).toInt()]
            Column(
                Modifier.fillMaxSize().background(coverColor).padding(horizontal = 7.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(.45f)))
                Spacer(Modifier.height(7.dp))
                Text(book.title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(book.author.ifBlank { " " }, color = Color.White.copy(.8f), fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(.45f)))
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
    muted: Color,
    gridMode: Boolean
) {
    if (gridMode) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 142.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(books, key = { it.id }) { book ->
                Column(Modifier.fillMaxWidth().clickable { onOpen(book) }) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        AthenaBookCover(book, Modifier.fillMaxWidth().aspectRatio(.68f))
                        IconButton(
                            onClick = { onFavorite(book) },
                            modifier = Modifier.padding(4.dp).size(32.dp).clip(CircleShape).background(Color.White.copy(.92f))
                        ) {
                            Icon(if (book.favorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "Toggle favourite", tint = if (book.favorite) Color(0xFF9B4B45) else Color(0xFF4D504A), modifier = Modifier.size(17.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(book.title, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                    Text(book.author.ifBlank { "Unknown author" }, color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (book.progress > 0 && book.status != "Finished") {
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(progress = { book.progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)), color = Color(0xFF66816B), trackColor = Color(0xFFE5E5DE))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (book.status == "Currently Reading") "${book.progress}% read" else book.status, color = Color(0xFF85867F), fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = { onEdit(book) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.MoreHoriz, "Book options", tint = muted, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(books, key = { it.id }) { book ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(book) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AthenaBookCover(book, Modifier.width(52.dp).height(72.dp))
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(book.title, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(book.author.ifBlank { "Unknown author" }, color = muted, fontSize = 11.sp)
                        Text("${book.status} · ${if (book.fileUri.isNotBlank()) "Digital" else book.ownership}", color = muted, fontSize = 10.sp)
                        if (book.progress > 0) LinearProgressIndicator(progress = { book.progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth(.75f).height(3.dp), color = Color(0xFF66816B), trackColor = Color(0xFFE5E5DE))
                    }
                    IconButton(onClick = { onFavorite(book) }) { Icon(if (book.favorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "Toggle favourite", tint = if (book.favorite) Color(0xFF9B4B45) else muted) }
                    IconButton(onClick = { onEdit(book) }) { Icon(Icons.Rounded.MoreHoriz, "Book options", tint = muted) }
                }
                HorizontalDivider(color = Color(0xFFE8E7E1))
            }
        }
    }
}

@Composable
private fun BookEditDialog(book: AthenaeumBook, shelves: List<AthenaeumShelf>, onDismiss: () -> Unit, onDelete: (AthenaeumBook) -> Unit, onSave: (AthenaeumBook, List<Long>) -> Unit) {
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
    var confirmDelete by remember(book.id) { mutableStateOf(false) }
    var lookupLoading by remember { mutableStateOf(false) }
    var lookupMessage by remember { mutableStateOf("") }
    var dictionaryDefinition by remember { mutableStateOf("") }
    var dictionaryLoading by remember { mutableStateOf(false) }
    val lookupScope = rememberCoroutineScope()
    val context = LocalContext.current
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            coverUri = uri.toString()
        }
    }
    val chosen = remember(book.id) { mutableStateListOf<Long>() }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text(if (book.id == 0L) "Add to Athenaeum" else "Edit volume", color = Color(0xFF252722), fontFamily = FontFamily.Serif) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                @Composable fun field(label: String, value: String, update: (String) -> Unit) { OutlinedTextField(value, update, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = label != "Description" && label != "Personal notes") }
                field("Title", title, { title = it }); field("Author", author, { author = it }); field("Publisher", publisher, { publisher = it })
                field("Publication year", year, { year = it })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(isbn, { isbn = it }, Modifier.weight(1f), label = { Text("ISBN") }, singleLine = true)
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(enabled = !lookupLoading && isbn.isNotBlank(), onClick = {
                        lookupScope.launch {
                            lookupLoading = true
                            lookupMessage = "Looking up ISBN…"
                            val result = withContext(Dispatchers.IO) { runCatching { BookMetadataLookup.lookup(isbn) }.getOrNull() }
                            if (result == null) lookupMessage = "No matching record found."
                            else {
                                if (title.isBlank() || title == book.title) title = result.title.ifBlank { title }
                                if (author.isBlank() || author == book.author) author = result.authors.ifBlank { author }
                                if (publisher.isBlank() || publisher == book.publisher) publisher = result.publisher.ifBlank { publisher }
                                if (year.isBlank() || year == book.publicationYear) year = result.publishedDate.take(4).ifBlank { year }
                                if (description.isBlank() || description == book.description) description = result.description.ifBlank { description }
                                if (genre.isBlank() || genre == book.genre) genre = result.categories.ifBlank { genre }
                                if (language.isBlank() || language == book.language) language = result.language.ifBlank { language }
                                lookupMessage = "Metadata found. Review the fields before saving."
                            }
                            lookupLoading = false
                        }
                    }) { Text(if (lookupLoading) "…" else "LOOK UP", fontSize = 9.sp) }
                }
                if (lookupMessage.isNotBlank()) Text(lookupMessage, color = Color(0xFF56745D), fontSize = 10.sp)
                field("Genre", genre, { genre = it })
                field("Language", language, { language = it }); field("Description", description, { description = it }); field("Personal notes", notes, { notes = it })
                field("Personal rating (0–5)", rating, { rating = it })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AthenaBookCover(book.copy(title = title, author = author, coverUri = coverUri), Modifier.width(76.dp).height(108.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("BOOK COVER", color = Color(0xFF77766F), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = { coverPicker.launch(arrayOf("image/*")) }) {
                            Icon(Icons.Rounded.Image, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (coverUri.isBlank()) "Choose image" else "Change image")
                        }
                    }
                }
                Text(if (coverUri.isBlank()) "No cover selected" else "Custom cover attached", color = AthenaGreen, fontSize = 10.sp)
                Text("READING STATUS", color = Color(0xFF252722), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
        }) { Text("SAVE", color = Color(0xFF56745D)) } },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (book.id != 0L) TextButton(onClick = { confirmDelete = true }) { Text("DELETE", color = Color(0xFF9B4B45)) }
                TextButton(onClick = onDismiss) { Text("CANCEL") }
            }
        }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this book?") },
            text = { Text("${book.title} will be removed from your Athenaeum, including its saved bookmarks.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(book) }) { Text("REMOVE", color = Color(0xFF9B4B45)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("KEEP BOOK") } }
        )
    }
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
    val readerPreferences = remember { context.getSharedPreferences("athenaeum_preferences", Context.MODE_PRIVATE) }
    var textSize by remember { mutableFloatStateOf(readerPreferences.getFloat("reader_text_size", 18f).coerceIn(12f, 30f)) }
    val textScroll = rememberScrollState(book.lastLocation.coerceAtLeast(0))
    var dark by remember { mutableStateOf(readerPreferences.getBoolean("reader_dark", false)) }
    var readerWebView by remember { mutableStateOf<WebView?>(null) }
    var showBookmarks by remember { mutableStateOf(false) }
    var savedBookmarks by remember(book.id) { mutableStateOf(emptyList<AthenaeumBookmark>()) }
    var showAnnotationEditor by remember { mutableStateOf(false) }
    var annotationQuote by remember { mutableStateOf("") }
    var annotationNote by remember { mutableStateOf("") }
    var epubDocument by remember(book.id) { mutableStateOf<EpubDocument?>(null) }
    var comicPages by remember(book.id) { mutableStateOf(emptyList<Bitmap>()) }
    var comicPage by remember(book.id) { mutableIntStateOf(book.lastLocation.coerceAtLeast(0)) }
    var epubChapterIndex by remember(book.id) { mutableIntStateOf(book.lastLocation.coerceAtLeast(0)) }
    var showToc by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf("") }
    var ocrError by remember { mutableStateOf("") }
    var showOcr by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    val textToSpeech = remember {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(textToSpeech) { onDispose { textToSpeech.stop(); textToSpeech.shutdown() } }
    val sessionStartedAt = remember(book.id) { System.currentTimeMillis() }

    LaunchedEffect(book.id, showBookmarks) {
        if (showBookmarks) savedBookmarks = dao.bookmarks(book.id)
    }

    DisposableEffect(book.id) {
        onDispose {
            val elapsed = ((System.currentTimeMillis() - sessionStartedAt) / 60_000L).toInt().coerceAtLeast(1)
            scope.launch { dao.addReadingSession(AthenaeumReadingSession(bookId = book.id, startedAt = sessionStartedAt, durationMinutes = elapsed, pagesRead = if (ext == "pdf") (page + 1).coerceAtLeast(0) else if (ext == "epub") (epubChapterIndex + 1).coerceAtLeast(0) else 0)) }
        }
    }

    DisposableEffect(readerWebView) {
        onDispose {
            readerWebView?.let { web ->
                scope.launch { dao.updateProgress(book.id, book.progress, if (ext == "epub") epubChapterIndex else web.scrollY) }
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
        } else if (ext == "cbz" || ext == "cbr") {
            val pages = withContext(Dispatchers.IO) {
                runCatching { ComicArchiveReader.read(context.contentResolver, Uri.parse(book.fileUri), rar = ext == "cbr") }
            }
            pages.onSuccess {
                comicPages = it
                comicPage = book.lastLocation.coerceIn(0, (it.size - 1).coerceAtLeast(0))
                if (it.isEmpty()) content = "This comic archive contains no readable image pages."
            }.onFailure { content = "Unable to open this comic archive: " + it.message }
        } else if (ext == "epub") {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(Uri.parse(book.fileUri))!!.use { it.readBytes() }
                    EpubParser.parse(bytes)
                }
            }
            parsed.onSuccess { document ->
                epubDocument = document
                epubChapterIndex = book.lastLocation.coerceIn(0, document.chapters.lastIndex)
                content = document.chapters[epubChapterIndex].html
            }.onFailure { content = "Unable to open this EPUB: " + it.message }
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
                        "mobi" -> MobiParser.read(bytes)
                        "md", "markdown" -> PlainTextFormats.markdownToHtml(bytes.toString(Charsets.UTF_8))
                        "rtf" -> PlainTextFormats.rtfToHtml(bytes.toString(Charsets.UTF_8))
                        else -> "This format is not supported by the current reader. Supported formats in this release: EPUB, PDF, TXT and HTML."
                    }
                }.getOrElse { "Unable to read this file: ${it.message}" }
            }
        }
        dao.updateProgress(book.id, book.progress, if (ext == "epub") epubChapterIndex else page, System.currentTimeMillis())
    }

    LaunchedEffect(textScroll.value, textScroll.maxValue) {
        if (ext == "txt" && textScroll.maxValue > 0) {
            kotlinx.coroutines.delay(350)
            dao.updateProgress(book.id, (textScroll.value * 100 / textScroll.maxValue).coerceIn(0, 100), textScroll.value)
        }
    }

    val readerBackground = if (dark) Color(0xFF171916) else Color(0xFFF7F6F1)
    val readerForeground = if (dark) Color(0xFFECEDE7) else Color(0xFF252722)
    val readerBar = if (dark) Color(0xFF22251F) else Color.White
    Column(Modifier.fillMaxSize().background(readerBackground)) {
        Row(Modifier.fillMaxWidth().background(readerBar).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "Close reader", tint = readerForeground) }
            Column(Modifier.weight(1f)) {
                Text(book.title, color = readerForeground, fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author, color = if (dark) Color(0xFFB9C7B4) else Color(0xFF6D7968), fontSize = 10.sp)
            }
            IconButton(onClick = { dark = !dark; readerPreferences.edit().putBoolean("reader_dark", dark).apply() }) { Icon(Icons.Rounded.Contrast, "Toggle reading theme", tint = readerForeground) }
            if (ext == "epub") IconButton(onClick = { showToc = true }) { Icon(Icons.Rounded.List, "Table of contents", tint = readerForeground) }
            if (ext == "pdf" && bitmap != null) IconButton(onClick = {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(InputImage.fromBitmap(bitmap!!, 0))
                    .addOnSuccessListener { result -> ocrText = result.text; ocrError = ""; showOcr = true; recognizer.close() }
                    .addOnFailureListener { error -> ocrError = error.message ?: "OCR could not read this page."; showOcr = true; recognizer.close() }
            }) { Icon(Icons.Rounded.DocumentScanner, "Extract text from PDF page", tint = readerForeground) }
            IconButton(onClick = { showBookmarks = true }) { Icon(Icons.Rounded.Bookmarks, "Bookmarks", tint = readerForeground) }
            IconButton(onClick = {
                val web = readerWebView
                if (web == null) {
                    annotationQuote = ""; annotationNote = ""; showAnnotationEditor = true
                } else {
                    web.evaluateJavascript("(function(){return window.getSelection().toString();})()") { raw ->
                        annotationQuote = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
                        annotationNote = ""
                        showAnnotationEditor = true
                    }
                }
            }) { Icon(Icons.Rounded.Highlight, "Save selected passage or add a note", tint = readerForeground) }
            IconButton(onClick = {
                scope.launch {
                    val location = if (ext == "pdf") page else if (ext == "txt") textScroll.value else if (ext == "epub") epubChapterIndex else readerWebView?.scrollY ?: 0
                    dao.addBookmark(AthenaeumBookmark(bookId = book.id, location = location, label = if (ext == "pdf") "Page ${page + 1}" else if (ext == "epub") "Chapter ${epubChapterIndex + 1}" else "Position $location"))
                    savedBookmarks = dao.bookmarks(book.id)
                }
            }) { Icon(Icons.Rounded.BookmarkAdd, "Save bookmark", tint = readerForeground) }
            IconButton(onClick = {
                if (textToSpeech.isSpeaking) textToSpeech.stop() else {
                    val spoken = if (ext == "pdf") ocrText else content.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
                    if (spoken.isNotBlank()) spoken.chunked(3_500).forEachIndexed { index, part ->
                        textToSpeech.speak(part, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "athenaeum-reader-$index")
                    }
                }
            }, enabled = ttsReady) { Icon(Icons.Rounded.VolumeUp, "Read this book aloud", tint = readerForeground) }
            IconButton(onClick = { textSize = (textSize - 1f).coerceAtLeast(12f); readerPreferences.edit().putFloat("reader_text_size", textSize).apply() }) { Icon(Icons.Rounded.TextDecrease, "Decrease text size", tint = readerForeground) }
            IconButton(onClick = { textSize = (textSize + 1f).coerceAtMost(30f); readerPreferences.edit().putFloat("reader_text_size", textSize).apply() }) { Icon(Icons.Rounded.TextIncrease, "Increase text size", tint = readerForeground) }
        }
        if (ext == "pdf" && bitmap != null) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                Image(bitmap!!.asImageBitmap(), contentDescription = "PDF page ${page + 1}", modifier = Modifier.fillMaxSize())
            }
            Row(Modifier.fillMaxWidth().background(readerBar).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("PREVIOUS", color = readerForeground) }
                Text("${page + 1} / ${pageCount}", color = readerForeground)
                TextButton(onClick = { if (page < pageCount - 1) page++ }, enabled = page < pageCount - 1) { Text("NEXT", color = readerForeground) }
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
        } else if (ext == "cbz" && comicPages.isNotEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                Image(comicPages[comicPage.coerceIn(0, comicPages.lastIndex)].asImageBitmap(), contentDescription = "Comic page ${comicPage + 1}", modifier = Modifier.fillMaxSize())
            }
            Row(Modifier.fillMaxWidth().background(readerBar).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { comicPage = (comicPage - 1).coerceAtLeast(0) }, enabled = comicPage > 0) { Text("Previous", color = readerForeground) }
                Text("${comicPage + 1} / ${comicPages.size}", color = readerForeground)
                TextButton(onClick = { comicPage = (comicPage + 1).coerceAtMost(comicPages.lastIndex) }, enabled = comicPage < comicPages.lastIndex) { Text("Next", color = readerForeground) }
            }
            LaunchedEffect(comicPage) { dao.updateProgress(book.id, ((comicPage + 1) * 100 / comicPages.size.coerceAtLeast(1)), comicPage) }
        } else if (ext == "epub" || ext == "html" || ext == "htm" || ext == "fb2" || ext == "mobi" || ext == "md" || ext == "markdown" || ext == "rtf") {
            val themedContent = remember(content, dark, textSize) {
                content.replace("#252722", if (dark) "#ECEDE7" else "#252722")
                    .replace("#f7f6f1", if (dark) "#171916" else "#F7F6F1")
                    .replace("font-size:1.08em", "font-size:${textSize}px")
            }
            AndroidView(factory = { ctx -> WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.blockNetworkLoads = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.scrollTo(0, if (ext == "epub") 0 else book.lastLocation.coerceAtLeast(0))
                    }
                }
                setBackgroundColor(if (dark) android.graphics.Color.rgb(23, 25, 22) else android.graphics.Color.rgb(247, 246, 241))
                val safeHtml = sanitizeReaderHtml(themedContent)
                tag = safeHtml
                readerWebView = this
                loadDataWithBaseURL(null, safeHtml, "text/html", "UTF-8", null)
            } }, update = { web ->
                web.setBackgroundColor(if (dark) android.graphics.Color.rgb(23, 25, 22) else android.graphics.Color.rgb(247, 246, 241))
                val safeHtml = sanitizeReaderHtml(themedContent)
                if (web.tag != safeHtml) { web.tag = safeHtml; web.loadDataWithBaseURL(null, safeHtml, "text/html", "UTF-8", null) }
                if (web.scrollY == 0 && book.lastLocation > 0) web.post { web.scrollTo(0, book.lastLocation) }
            }, modifier = Modifier.weight(1f).fillMaxWidth())
            if (ext == "epub" && epubDocument != null) {
                Row(Modifier.fillMaxWidth().background(readerBar).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        epubChapterIndex = (epubChapterIndex - 1).coerceAtLeast(0)
                        content = epubDocument!!.chapters[epubChapterIndex].html
                        scope.launch { dao.updateProgress(book.id, (epubChapterIndex * 100 / epubDocument!!.chapters.size.coerceAtLeast(1)), epubChapterIndex) }
                    }, enabled = epubChapterIndex > 0) { Text("Previous chapter", color = readerForeground) }
                    Spacer(Modifier.weight(1f))
                    Text("${epubChapterIndex + 1} / ${epubDocument!!.chapters.size}", color = readerForeground, fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        epubChapterIndex = (epubChapterIndex + 1).coerceAtMost(epubDocument!!.chapters.lastIndex)
                        content = epubDocument!!.chapters[epubChapterIndex].html
                        scope.launch { dao.updateProgress(book.id, (epubChapterIndex * 100 / epubDocument!!.chapters.size.coerceAtLeast(1)), epubChapterIndex) }
                    }, enabled = epubChapterIndex < epubDocument!!.chapters.lastIndex) { Text("Next chapter", color = readerForeground) }
                }
            } else Text("HTML reader · adjustable theme and saved position", color = readerForeground, fontSize = 10.sp, modifier = Modifier.padding(12.dp))
        } else {
            Text(content, color = readerForeground, fontSize = textSize.sp, lineHeight = (textSize * 1.55f).sp,
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(textScroll).padding(horizontal = 24.dp, vertical = 22.dp))
            Row(Modifier.fillMaxWidth().background(readerBar).padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { textSize = (textSize - 1f).coerceAtLeast(12f) }) { Text("A−", color = readerForeground) }
                TextButton(onClick = { textSize = (textSize + 1f).coerceAtMost(30f) }) { Text("A+", color = readerForeground) }
                TextButton(onClick = { scope.launch { dao.updateProgress(book.id, if (textScroll.maxValue > 0) (textScroll.value * 100 / textScroll.maxValue).coerceIn(0, 100) else 0, textScroll.value) } }) { Text("SAVE POSITION", color = readerForeground) }
            }
        }
    }
    if (showOcr) {
        AlertDialog(
            onDismissRequest = { showOcr = false },
            title = { Text("Recognised text · page ${page + 1}") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (ocrError.isNotBlank()) Text(ocrError, color = Color(0xFF9B4B45))
                    else androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(ocrText.ifBlank { "No readable text was found on this page." }, color = readerForeground, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOcr = false }) { Text("CLOSE") } }
        )
    }
    if (showToc && epubDocument != null) {
        AlertDialog(
            onDismissRequest = { showToc = false },
            title = { Text("Contents · ${book.title}") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    epubDocument!!.chapters.forEachIndexed { index, chapter ->
                        TextButton(onClick = {
                            epubChapterIndex = index
                            content = chapter.html
                            showToc = false
                            scope.launch { dao.updateProgress(book.id, (index * 100 / epubDocument!!.chapters.size.coerceAtLeast(1)), index) }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(chapter.title, color = if (index == epubChapterIndex) Color(0xFF56745D) else readerForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showToc = false }) { Text("CLOSE") } }
        )
    }
    if (showAnnotationEditor) {
        AlertDialog(
            onDismissRequest = { showAnnotationEditor = false },
            title = { Text("Add to your archive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Save a passage and your own note. Copy the passage into the first field.", color = readerForeground, fontSize = 12.sp)
                    OutlinedTextField(annotationQuote, { annotationQuote = it; dictionaryDefinition = "" }, label = { Text("Passage / quotation") }, minLines = 2)
                    val selectedWord = annotationQuote.trim()
                    if (selectedWord.isNotBlank() && selectedWord.none { it.isWhitespace() } && selectedWord.all { it.isLetter() }) {
                        TextButton(enabled = !dictionaryLoading, onClick = {
                            lookupScope.launch {
                                dictionaryLoading = true
                                dictionaryDefinition = withContext(Dispatchers.IO) { runCatching { DictionaryLookup.define(selectedWord) }.getOrNull() } ?: "No definition found."
                                dictionaryLoading = false
                            }
                        }) { Text(if (dictionaryLoading) "LOOKING UP…" else "DEFINE SELECTED WORD") }
                    }
                    if (dictionaryDefinition.isNotBlank()) Text(dictionaryDefinition, color = readerForeground, fontSize = 12.sp, lineHeight = 17.sp)
                    OutlinedTextField(annotationNote, { annotationNote = it }, label = { Text("Your note (optional)") }, minLines = 2)
                }
            },
            confirmButton = { TextButton(onClick = {
                if (annotationQuote.isNotBlank()) scope.launch {
                    dao.addAnnotation(AthenaeumAnnotation(bookId = book.id, location = if (ext == "pdf") page else if (ext == "txt") textScroll.value else if (ext == "epub") epubChapterIndex else readerWebView?.scrollY ?: 0, quote = annotationQuote.trim(), note = annotationNote.trim()))
                    if (ext != "pdf" && ext != "txt") readerWebView?.let { highlightReaderQuote(it, annotationQuote.trim()) }
                }
                showAnnotationEditor = false
            }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { showAnnotationEditor = false }) { Text("CANCEL") } }
        )
    }
    if (showBookmarks) {
        AlertDialog(onDismissRequest = { showBookmarks = false }, title = { Text("Bookmarks · ${book.title}", fontFamily = FontFamily.Serif) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (savedBookmarks.isEmpty()) Text("No saved bookmarks yet.")
                    savedBookmarks.forEach { mark ->
                        Row(Modifier.fillMaxWidth().clickable {
                            if (ext == "pdf") page = mark.location else if (ext == "txt") scope.launch { textScroll.scrollTo(mark.location) } else if (ext == "epub" && epubDocument != null) { epubChapterIndex = mark.location.coerceIn(0, epubDocument!!.chapters.lastIndex); content = epubDocument!!.chapters[epubChapterIndex].html } else readerWebView?.scrollTo(0, mark.location)
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

private fun sanitizeReaderHtml(html: String): String = html
    .replace(Regex("""(?is)<script\b[^>]*>.*?</script>"""), "")
    .replace(Regex("""(?is)<iframe\b[^>]*>.*?</iframe>"""), "")
    .replace(Regex("""(?i)\s+on[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)"""), "")
    .replace(Regex("""(?i)(href|src)\s*=\s*(['"])\s*javascript:.*?\2"""), "")
    .replace(Regex("""(?i)(href|src)\s*=\s*javascript:[^\s>]+"""), "")

private fun highlightReaderQuote(webView: WebView, quote: String) {
    if (quote.isBlank()) return
    val safeQuote = JSONObject.quote(quote)
    val script = """(function(q){var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);while(w.nextNode()){var n=w.currentNode;var i=n.nodeValue.indexOf(q);if(i>=0){var r=document.createRange();r.setStart(n,i);r.setEnd(n,i+q.length);var m=document.createElement('mark');m.style.backgroundColor='#F2D98A';m.style.color='inherit';try{r.surroundContents(m);return true;}catch(e){return false;}}}return false;})($safeQuote);"""
    webView.evaluateJavascript(script, null)
}

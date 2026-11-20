package com.sonora.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.coroutines.launch

@Entity(
    tableName = "athenaeum_annotations",
    foreignKeys = [ForeignKey(entity = AthenaeumBook::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")]
)
data class AthenaeumAnnotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val location: Int = 0,
    val quote: String,
    val note: String = "",
    val color: String = "amber",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "athenaeum_commonplace",
    foreignKeys = [ForeignKey(entity = AthenaeumBook::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("bookId")]
)
data class AthenaeumCommonplaceEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long? = null,
    val source: String = "",
    val quote: String,
    val reflection: String = "",
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "athenaeum_reading_sessions",
    foreignKeys = [ForeignKey(entity = AthenaeumBook::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")]
)
data class AthenaeumReadingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val startedAt: Long = System.currentTimeMillis(),
    val durationMinutes: Int = 0,
    val pagesRead: Int = 0
)

@Composable
fun AthenaeumArchivePanel(dao: AthenaeumDao, books: List<AthenaeumBook>, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val annotations by dao.observeAnnotations().collectAsState(initial = emptyList())
    val entries by dao.observeCommonplace().collectAsState(initial = emptyList())
    val sessions by dao.observeReadingSessions().collectAsState(initial = emptyList())
    var section by remember { mutableStateOf("Commonplace") }
    var quote by remember { mutableStateOf("") }
    var reflection by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var selectedBookId by remember { mutableStateOf<Long?>(null) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF6F5F1)).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ATHENAEUM ARCHIVE", color = Color(0xFF77766F), fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("Commonplace Book", color = Color(0xFF20211F), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close archive", tint = Color(0xFF454640)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                listOf("Commonplace", "Highlights", "Statistics").forEach { item ->
                    FilterChip(selected = section == item, onClick = { section = item }, label = { Text(item, fontSize = 11.sp) })
                }
            }
            when (section) {
                "Commonplace" -> {
                    OutlinedTextField(value = quote, onValueChange = { quote = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Quotation or passage") }, minLines = 2)
                    OutlinedTextField(value = reflection, onValueChange = { reflection = it }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), label = { Text("Your reflection") }, minLines = 2)
                    OutlinedTextField(value = source, onValueChange = { source = it }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), label = { Text("Source / author (optional)") }, singleLine = true)
                    OutlinedTextField(value = tags, onValueChange = { tags = it }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), label = { Text("Tags (comma separated)") }, singleLine = true)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        var expanded by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { expanded = true }) {
                                Text(books.firstOrNull { it.id == selectedBookId }?.title ?: "No linked book", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("No linked book") }, onClick = { selectedBookId = null; expanded = false })
                                books.forEach { book -> DropdownMenuItem(text = { Text(book.title) }, onClick = { selectedBookId = book.id; expanded = false }) }
                            }
                        }
                        Button(onClick = {
                            if (quote.isNotBlank()) scope.launch {
                                dao.addCommonplace(AthenaeumCommonplaceEntry(bookId = selectedBookId, source = source.trim(), quote = quote.trim(), reflection = reflection.trim(), tags = tags.trim()))
                                quote = ""; reflection = ""; source = ""; tags = ""
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252722))) { Text("Save entry") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color(0xFFE4E3DD))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(entries, key = { it.id }) { entry ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(13.dp)) {
                                Text("“${entry.quote}”", color = Color(0xFF252722), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                if (entry.reflection.isNotBlank()) Text(entry.reflection, color = Color(0xFF66675F), fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                                Text(listOfNotNull(entry.source.takeIf { it.isNotBlank() }, books.firstOrNull { it.id == entry.bookId }?.title, entry.tags.takeIf { it.isNotBlank() }).joinToString(" · "), color = Color(0xFF85867F), fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    IconButton(onClick = { scope.launch { dao.deleteCommonplace(entry) } }) { Icon(Icons.Rounded.DeleteOutline, "Delete entry", tint = Color(0xFF9B4B45)) }
                                }
                            }
                        }
                    }
                }
                "Highlights" -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(annotations, key = { it.id }) { annotation ->
                        val book = books.firstOrNull { it.id == annotation.bookId }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(13.dp)) {
                            Text(book?.title ?: "Removed book", color = Color(0xFF77766F), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text("“${annotation.quote}”", color = Color(0xFF252722), fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp))
                            if (annotation.note.isNotBlank()) Text(annotation.note, color = Color(0xFF66675F), fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { scope.launch { dao.deleteAnnotation(annotation) } }) { Icon(Icons.Rounded.DeleteOutline, "Delete highlight", tint = Color(0xFF9B4B45)) }
                            }
                        }
                    }
                }
                else -> {
                    val finished = books.count { it.status == "Finished" }
                    val minutes = sessions.sumOf { it.durationMinutes }
                    val pages = sessions.sumOf { it.pagesRead }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ArchiveMetric("Books finished", finished.toString(), Modifier.weight(1f))
                            ArchiveMetric("In progress", books.count { it.status == "Currently Reading" }.toString(), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ArchiveMetric("Reading time", "${minutes / 60}h ${minutes % 60}m", Modifier.weight(1f))
                            ArchiveMetric("Pages logged", pages.toString(), Modifier.weight(1f))
                        }
                        ArchiveMetric("Annotations", annotations.size.toString(), Modifier.fillMaxWidth())
                        ArchiveMetric("Commonplace entries", entries.size.toString(), Modifier.fillMaxWidth())
                        Text("Reading sessions are recorded locally as you use the reader. Your library and archive remain on this device.", color = Color(0xFF77766F), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp)) {
        Text(value, color = Color(0xFF252722), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Color(0xFF77766F), fontSize = 10.sp)
    }
}

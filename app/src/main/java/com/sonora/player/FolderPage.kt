package com.sonora.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FolderPage(
    tracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    onPlay: (AudioTrack, List<AudioTrack>) -> Unit,
    ink: Color,
    panel: Color,
    muted: Color
) {
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    val folders = tracks.groupBy { it.folderPath.ifBlank { "Unknown location" } }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val selectedTracks = selectedFolder?.let { folders[it].orEmpty() }.orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectedFolder != null) IconButton(onClick = { selectedFolder = null }) { Icon(Icons.Rounded.ArrowBack, "Back to folders", tint = ink) }
            Column(Modifier.weight(1f)) {
                Text("BROWSE BY LOCATION", color = muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text(selectedFolder?.substringAfterLast('/') ?: "Folders", color = ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (selectedFolder == null) "${folders.size} folders" else "${selectedTracks.size} tracks", color = muted, fontSize = 12.sp)
            }
        }
        if (selectedFolder == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(folders.toList(), key = { it.first }) { (folder, songs) ->
                    Row(Modifier.fillMaxWidth().clickable { selectedFolder = folder }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = muted, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.substringAfterLast('/').ifBlank { folder }, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(folder, color = muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${songs.size}", color = muted, fontSize = 11.sp)
                        Icon(Icons.Rounded.ChevronRight, null, tint = muted)
                    }
                    HorizontalDivider(color = panel)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(selectedTracks, key = { it.id }) { track ->
                    Row(Modifier.fillMaxWidth().clickable { onPlay(track, selectedTracks) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        AlbumArtwork(track.albumId, Modifier.size(46.dp))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = if (currentTrack?.id == track.id) AccentDeep else ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = muted, fontSize = 10.sp, maxLines = 1)
                        }
                        Text(formatDuration(track.durationMs), color = muted, fontSize = 10.sp)
                    }
                    HorizontalDivider(color = panel)
                }
            }
        }
    }
}

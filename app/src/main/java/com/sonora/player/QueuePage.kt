package com.sonora.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QueuePage(
    tracks: List<AudioTrack>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    ink: Color,
    panel: Color,
    muted: Color
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
            Text("PLAYBACK", color = muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Text("Queue", color = ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("${tracks.size} tracks · changes apply immediately", color = muted, fontSize = 12.sp)
        }
        if (tracks.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.QueueMusic, null, tint = muted, modifier = Modifier.size(36.dp))
                Text("Your queue is empty", color = ink, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                itemsIndexed(tracks, key = { index, track -> "${track.id}-$index" }) { index, track ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AlbumArtwork(track.albumId, Modifier.size(48.dp))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = if (index == currentIndex) AccentDeep else ink, fontSize = 13.sp, fontWeight = if (index == currentIndex) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (index == currentIndex) Icon(Icons.Rounded.GraphicEq, "Currently playing", tint = AccentDeep, modifier = Modifier.size(18.dp))
                        IconButton(onClick = { if (index > 0) onMove(index, index - 1) }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = muted) }
                        IconButton(onClick = { if (index < tracks.lastIndex) onMove(index, index + 1) }, enabled = index < tracks.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down", tint = muted) }
                        IconButton(onClick = { onRemove(index) }) { Icon(Icons.Rounded.Close, "Remove from queue", tint = muted) }
                    }
                    HorizontalDivider(color = panel)
                }
            }
        }
    }
}

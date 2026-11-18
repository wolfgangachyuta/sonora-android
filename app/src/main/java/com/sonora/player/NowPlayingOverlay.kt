package com.sonora.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player

@Composable
fun NowPlayingOverlay(
    track: AudioTrack, playing: Boolean, position: Long, duration: Long,
    ink: Color, paper: Color, panel: Color, muted: Color, dark: Boolean,
    onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Long) -> Unit,
    onClose: () -> Unit, shuffle: Boolean, onShuffle: (Boolean) -> Unit, repeat: Int, onRepeat: (Int) -> Unit,
    favorite: Boolean, onFavorite: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = paper) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = ink) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", fontSize = 9.sp, color = muted, letterSpacing = 2.sp)
                    Text("SONORA SESSION", fontSize = 8.sp, color = muted, letterSpacing = 1.2.sp)
                }
                IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = if (favorite) Color(0xFFB36D6A) else ink) }
            }
            Spacer(Modifier.weight(.2f))
            AlbumArtwork(track.albumId, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)))
            Spacer(Modifier.weight(.2f))
            Column(Modifier.fillMaxWidth()) {
                Text(track.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, fontSize = 14.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                Slider(value = position.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat(), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..duration.coerceAtLeast(1L).toFloat())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(position), fontSize = 10.sp, color = muted)
                    Text(formatDuration(duration), fontSize = 10.sp, color = muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShuffle(!shuffle) }) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffle) AccentDeep else muted) }
                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = ink, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = onToggle, modifier = Modifier.size(72.dp).clip(CircleShape).background(ink)) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = paper, modifier = Modifier.size(38.dp) ) }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Next", tint = ink, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = { onRepeat(if (repeat == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else if (repeat == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF) }) {
                    Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", tint = if (repeat != Player.REPEAT_MODE_OFF) AccentDeep else muted)
                }
            }
            Spacer(Modifier.height(26.dp))
            Text("ON-DEVICE AUDIO · YOUR COLLECTION", fontSize = 8.sp, letterSpacing = 1.4.sp, color = muted)
            Spacer(Modifier.height(18.dp))
        }
    }
}

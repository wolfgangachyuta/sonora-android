package com.sonora.player

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
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
    favorite: Boolean, onFavorite: () -> Unit, onLyrics: () -> Unit
) {
    val safeDuration = duration.coerceAtLeast(1L)
    val progress = position.coerceIn(0L, safeDuration)
    val backdrop = if (dark) Color(0xFF10110F) else Color(0xFFF5F3ED)
    val foreground = if (dark) Color(0xFFF4F1E9) else Color(0xFF171817)
    val secondary = if (dark) Color(0xFFAAA99F) else Color(0xFF77796F)
    val accent = Color(0xFFB3261E)

    Surface(Modifier.fillMaxSize(), color = backdrop, contentColor = foreground) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(360.dp).background(Brush.verticalGradient(
                listOf(if (dark) Color(0xFF5B2928).copy(.30f) else Color(0xFFE7C9C4).copy(.58f), backdrop)
            )))
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = foreground, modifier = Modifier.size(29.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NOW PLAYING", fontSize = 9.sp, color = secondary, letterSpacing = 2.2.sp, fontWeight = FontWeight.SemiBold)
                        Text("YOUR LISTENING SPACE", fontSize = 8.sp, color = secondary, letterSpacing = 1.3.sp)
                    }
                    IconButton(onClick = onFavorite) {
                        Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = if (favorite) accent else foreground)
                    }
                }

                Spacer(Modifier.weight(.45f))
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF342B2B), Color(0xFF9A6960), Color(0xFFD7BCA0)))),
                    contentAlignment = Alignment.Center) {
                    AlbumArtwork(track.albumId, Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)))
                }
                Spacer(Modifier.weight(.45f))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(track.title, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(track.artist, fontSize = 14.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (track.album.isNotBlank()) Text(track.album, fontSize = 11.sp, color = secondary.copy(.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onFavorite) {
                        Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = if (favorite) accent else secondary)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Slider(value = progress.toFloat(), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..safeDuration.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = secondary.copy(.25f)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(progress), fontSize = 10.sp, color = secondary)
                    Text(formatDuration(safeDuration), fontSize = 10.sp, color = secondary)
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onShuffle(!shuffle) }) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffle) accent else secondary) }
                    IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = foreground, modifier = Modifier.size(34.dp)) }
                    IconButton(onClick = onToggle, modifier = Modifier.size(68.dp).clip(CircleShape).background(foreground)) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = backdrop, modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) { Icon(Icons.Rounded.SkipNext, "Next", tint = foreground, modifier = Modifier.size(34.dp)) }
                    IconButton(onClick = { onRepeat(if (repeat == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else if (repeat == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF) }) {
                        Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", tint = if (repeat != Player.REPEAT_MODE_OFF) accent else secondary)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    FilledTonalButton(onClick = onLyrics, shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = panel, contentColor = foreground)) {
                        Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("LYRICS", fontSize = 10.sp, letterSpacing = 1.4.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

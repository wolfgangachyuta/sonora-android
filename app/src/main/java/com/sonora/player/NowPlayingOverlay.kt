package com.sonora.player

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    favorite: Boolean, onFavorite: () -> Unit, onLyrics: () -> Unit,
    onMetadata: () -> Unit, onEqualizer: () -> Unit,
    sleepMinutes: Int?, onSleepTimer: (Int?) -> Unit,
    playbackSpeed: Float, onPlaybackSpeed: (Float) -> Unit
) {
    val safeDuration = duration.coerceAtLeast(1L)
    val progress = position.coerceIn(0L, safeDuration)
    val backdrop = if (dark) Color(0xFF0D1014) else Color(0xFFF5F2EB)
    val foreground = if (dark) Color(0xFFF5F1E8) else Color(0xFF191C1B)
    val secondary = if (dark) Color(0xFFA8AAA5) else Color(0xFF777B75)
    val accent = if (dark) Color(0xFFE7A6A0) else Color(0xFF9D3B35)
    val artworkShape = RoundedCornerShape(30.dp)
    var showSleepTimer by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = backdrop, contentColor = foreground) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(440.dp).background(
                Brush.verticalGradient(listOf(
                    if (dark) Color(0xFF563B3A).copy(.48f) else Color(0xFFE6C9BC).copy(.72f),
                    backdrop
                ))
            ))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = foreground, modifier = Modifier.size(29.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SONORA", fontSize = 10.sp, color = secondary, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                        Text("NOW PLAYING", fontSize = 8.sp, color = secondary, letterSpacing = 1.8.sp)
                    }
                    IconButton(onClick = onFavorite) {
                        Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            "Favourite", tint = if (favorite) accent else foreground)
                    }
                }

                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(artworkShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF263C36), Color(0xFF9A776C), Color(0xFFE2CBB0)))),
                    contentAlignment = Alignment.Center
                ) {
                    AlbumArtwork(track.albumId, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .40f)))
                    ))
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(20.dp)
                    ) {
                        Text("THE CURRENT RECORD", fontSize = 8.sp, color = Color.White.copy(.82f), letterSpacing = 2.sp)
                        Text(track.album.ifBlank { "SONORA SESSION" }, fontSize = 19.sp,
                            color = Color.White, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(track.title, fontSize = 25.sp, lineHeight = 30.sp,
                            fontWeight = FontWeight.SemiBold, color = foreground,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text(track.artist, fontSize = 14.sp, color = secondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (track.albumArtist.isNotBlank() && track.albumArtist != track.artist) {
                            Text("Album by ${track.albumArtist}", fontSize = 10.sp, color = secondary)
                        }
                    }
                    IconButton(onClick = onMetadata) {
                        Icon(Icons.Rounded.EditNote, "Edit audio metadata", tint = secondary, modifier = Modifier.size(25.dp))
                    }
                }

                Spacer(Modifier.height(22.dp))
                Slider(
                    value = progress.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..safeDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = accent, activeTrackColor = accent,
                        inactiveTrackColor = secondary.copy(.25f)
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(progress), fontSize = 10.sp, color = secondary)
                    Text(formatDuration(safeDuration), fontSize = 10.sp, color = secondary)
                }

                Spacer(Modifier.height(15.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onShuffle(!shuffle) }) {
                        Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffle) accent else secondary)
                    }
                    IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous", tint = foreground, modifier = Modifier.size(34.dp))
                    }
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(72.dp).clip(CircleShape).background(foreground)
                    ) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Pause" else "Play", tint = backdrop, modifier = Modifier.size(39.dp))
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) {
                        Icon(Icons.Rounded.SkipNext, "Next", tint = foreground, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = {
                        onRepeat(if (repeat == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL
                        else if (repeat == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ONE
                        else Player.REPEAT_MODE_OFF)
                    }) {
                        Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            "Repeat", tint = if (repeat != Player.REPEAT_MODE_OFF) accent else secondary)
                    }
                }

                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = onLyrics, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = panel, contentColor = foreground)
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("LYRICS", fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                    FilledTonalButton(
                        onClick = onEqualizer, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = panel, contentColor = foreground)
                    ) {
                        Icon(Icons.Rounded.Equalizer, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("EQUALIZER", fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onMetadata, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = foreground)
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("EDIT TRACK INFORMATION", fontSize = 9.sp, letterSpacing = 1.sp)
                }
                OutlinedButton(onClick = { showSpeedDialog = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = foreground)) {
                    Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("PLAYBACK SPEED · ${playbackSpeed}×", fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { showSleepTimer = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = foreground)) {
                    Icon(Icons.Rounded.Bedtime, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (sleepMinutes == null) "SLEEP TIMER" else "SLEEP TIMER · PAUSES IN ${sleepMinutes} MIN", fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback speed", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                        TextButton(onClick = { onPlaybackSpeed(speed); showSpeedDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (speed == 1f) "Normal · 1×" else "${speed}×", color = if (playbackSpeed == speed) accent else foreground)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeedDialog = false }) { Text("CLOSE") } }
        )
    }
    if (showSleepTimer) {
        AlertDialog(
            onDismissRequest = { showSleepTimer = false },
            title = { Text("Sleep timer", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pause playback automatically after a little while.", color = secondary, fontSize = 12.sp)
                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                        TextButton(onClick = { onSleepTimer(minutes); showSleepTimer = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("$minutes minutes", color = foreground)
                        }
                    }
                    if (sleepMinutes != null) TextButton(onClick = { onSleepTimer(null); showSleepTimer = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel active timer", color = accent)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepTimer = false }) { Text("CLOSE") } }
        )
    }
}

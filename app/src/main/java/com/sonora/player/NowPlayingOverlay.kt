package com.sonora.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.setValue
import kotlin.math.atan2
import kotlin.math.PI
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
    var vinylMode by remember { mutableStateOf(true) }
    var standardVinylSpeed by remember { mutableStateOf(false) }
    val vinylRevolutionMs = if (standardVinylSpeed) 1_800L else 24_000L

    Surface(Modifier.fillMaxSize(), color = backdrop, contentColor = foreground) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(360.dp).background(Brush.verticalGradient(
                listOf(if (dark) Color(0xFF5B2928).copy(.30f) else Color(0xFFE7C9C4).copy(.58f), backdrop)
            )))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
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

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(selected = vinylMode, onClick = { vinylMode = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("VINYL", fontSize = 9.sp, letterSpacing = 1.sp) }
                            SegmentedButton(selected = !vinylMode, onClick = { vinylMode = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("ARTWORK", fontSize = 9.sp, letterSpacing = 1.sp) }
                        }
                        if (vinylMode) TextButton(onClick = { standardVinylSpeed = !standardVinylSpeed }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) {
                            Text(if (standardVinylSpeed) "33 RPM" else "24s", fontSize = 8.sp, letterSpacing = .6.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (vinylMode) VinylRecord(track.albumId, position, safeDuration, vinylRevolutionMs, backdrop, onSeek)
                    else Box(Modifier.fillMaxSize(.88f).clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF342B2B), Color(0xFF9A6960), Color(0xFFD7BCA0))))) {
                        AlbumArtwork(track.albumId, Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)))
                    }
                }

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


@Composable
private fun VinylRecord(
    albumId: Long?, position: Long, duration: Long, revolutionDurationMs: Long,
    backdrop: Color, onSeek: (Long) -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = ((position % revolutionDurationMs).toFloat() / revolutionDurationMs.toFloat()) * 360f,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "vinyl-rotation"
    )
    val latestPosition = rememberUpdatedState(position)
    Box(
        Modifier.fillMaxSize(.92f)
            .graphicsLayer { rotationZ = rotation }
            .semantics { contentDescription = "Vinyl record. Drag around the record to seek through the track." }
            .pointerInput(duration, revolutionDurationMs) {
                val radius = minOf(size.width, size.height) / 2f
                var previousAngle: Float? = null
                var dragPosition = 0L
                var isDragging = false
                detectDragGestures(
                    onDragStart = { point ->
                        val dx = point.x - size.width / 2f
                        val dy = point.y - size.height / 2f
                        if (dx * dx + dy * dy <= radius * radius) {
                            isDragging = true
                            previousAngle = atan2(dy, dx)
                            dragPosition = latestPosition.value
                        }
                    },
                    onDragEnd = { isDragging = false; previousAngle = null },
                    onDragCancel = { isDragging = false; previousAngle = null }
                ) { change, _ ->
                    val dx = change.position.x - size.width / 2f
                    val dy = change.position.y - size.height / 2f
                    if (isDragging && dx * dx + dy * dy <= radius * radius) {
                        val angle = atan2(dy, dx)
                        val old = previousAngle
                        if (old != null) {
                            var delta = angle - old
                            if (delta > PI) delta -= (2.0 * PI).toFloat()
                            if (delta < -PI) delta += (2.0 * PI).toFloat()
                            dragPosition = (dragPosition + (delta / (2.0 * PI) * revolutionDurationMs.toDouble()).toLong())
                                .coerceIn(0L, duration.coerceAtLeast(1L))
                            onSeek(dragPosition)
                        }
                        previousAngle = angle
                    }
                    change.consume()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = minOf(size.width, size.height) / 2f
            drawCircle(Color(0xFF171719), radius = radius)
            drawCircle(Color(0xFF252528), radius = radius * .96f)
            listOf(.69f, .77f, .84f, .91f).forEachIndexed { index, ratio ->
                drawCircle(
                    color = Color.White.copy(alpha = if (index % 2 == 0) .11f else .055f),
                    radius = radius * ratio,
                    style = Stroke(width = (1.2f + index * .25f).dp.toPx())
                )
            }
            drawCircle(Color(0xFF101012), radius = radius * .505f)
            drawCircle(Color.White.copy(alpha = .12f), radius = radius * .505f, style = Stroke(1.dp.toPx()))
        }
        AlbumArtwork(albumId, Modifier.fillMaxSize(.49f).clip(CircleShape))
        Box(Modifier.fillMaxSize(.045f).clip(CircleShape).background(backdrop).padding(2.dp).clip(CircleShape).background(Color(0xFF777777)))
    }
}

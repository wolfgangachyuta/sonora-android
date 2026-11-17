package com.sonora.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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

private val PlayerInk = Color(0xFF171817)
private val PlayerPaper = Color(0xFFF4F3EF)
private val PlayerMuted = Color(0xFF85857E)
private val PlayerSage = Color(0xFFB8D2C2)

@Composable
fun NowPlayingScreen(
    track: AudioTrack,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = positionMs.coerceIn(0L, safeDuration)

    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = PlayerInk)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NOW PLAYING", fontSize = 9.sp, color = PlayerMuted, letterSpacing = 2.sp)
                Text("SONORA SESSION", fontSize = 8.sp, color = PlayerMuted, letterSpacing = 1.sp)
            }
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.weight(.45f))
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1D332C), Color(0xFF759783), Color(0xFFD8C4A9))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.fillMaxWidth(.72f).aspectRatio(1f)
                    .border(1.dp, Color.White.copy(alpha = .28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.fillMaxWidth(.68f).aspectRatio(1f)
                        .border(1.dp, Color.White.copy(alpha = .32f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = "SONORA artwork",
                        tint = Color.White,
                        modifier = Modifier.size(76.dp)
                    )
                }
            }
            Text(
                "S O N O R A",
                color = Color.White.copy(alpha = .72f),
                fontSize = 9.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)
            )
        }
        Spacer(Modifier.weight(.45f))

        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(
                track.title,
                fontSize = 23.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = PlayerInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                track.artist,
                fontSize = 14.sp,
                color = PlayerMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(18.dp))
            Slider(
                value = progress.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..safeDuration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlayerTime(progress), fontSize = 10.sp, color = PlayerMuted)
                Text(formatPlayerTime(safeDuration), fontSize = 10.sp, color = PlayerMuted)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(58.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous track", tint = PlayerInk, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(22.dp))
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(68.dp).clip(CircleShape).background(PlayerInk)
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = PlayerPaper,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.width(22.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(58.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next track", tint = PlayerInk, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("LOCAL AUDIO  ·  YOUR DEVICE  ·  YOUR SPACE", fontSize = 8.sp, color = PlayerMuted, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(10.dp))
    }
}

private fun formatPlayerTime(ms: Long): String {
    val seconds = (ms / 1000L).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

package com.sonora.player

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun AudioVisualizerPage(audioSessionId: Int, ink: Color, panel: Color, muted: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var unavailable by remember { mutableStateOf(false) }
    val levels = remember { mutableStateListOf<Float>().apply { repeat(48) { add(0.04f) } } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }

    DisposableEffect(audioSessionId, permitted) {
        if (!permitted || audioSessionId <= 0) {
            onDispose { }
        } else {
            val visualizer = runCatching { Visualizer(audioSessionId) }.getOrNull()
            if (visualizer == null) {
                unavailable = true
                onDispose { }
            } else {
                val mainHandler = Handler(Looper.getMainLooper())
                visualizer.captureSize = Visualizer.getCaptureSizeRange()[1]
                visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        val data = fft ?: return
                        val values = (0 until 48).map { index ->
                            val bin = 2 + index * 2
                            val real = data.getOrNull(bin)?.toInt()?.and(0xFF) ?: 0
                            val imag = data.getOrNull(bin + 1)?.toInt()?.and(0xFF) ?: 0
                            ((kotlin.math.sqrt((real * real + imag * imag).toDouble()) / 90.0).toFloat()).coerceIn(0.04f, 1f)
                        }
                        mainHandler.post { values.forEachIndexed { index, value -> if (index < levels.size) levels[index] = value } }
                    }
                }, (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1), false, true)
                runCatching { visualizer.enabled = true }.onFailure { unavailable = true }
                onDispose {
                    runCatching { visualizer.enabled = false }
                    runCatching { visualizer.release() }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = ink) }
            Column(Modifier.weight(1f)) {
                Text("SONORA", color = muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text("Visualizer", color = ink, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!permitted) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(panel).padding(18.dp)) {
                Text("Audio visualisation is optional.", color = ink, fontWeight = FontWeight.SemiBold)
                Text("Android requires microphone permission to capture the active playback session. SONORA uses the system Visualizer effect; it does not record or save audio.", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.padding(top = 12.dp)) { Text("ENABLE VISUALIZER") }
            }
        } else if (audioSessionId <= 0 || unavailable) {
            Text("The active playback session does not expose a visualizer on this device.", color = muted, fontSize = 12.sp)
        } else {
            Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 36.dp)) {
                val gap = size.width / (levels.size * 2f)
                val barWidth = gap
                levels.forEachIndexed { index, level ->
                    val height = size.height * level.coerceIn(0.04f, 1f)
                    val x = index * gap * 2f + gap / 2f
                    drawRoundRect(
                        color = ink,
                        topLeft = Offset(x, (size.height - height) / 2f),
                        size = Size(barWidth, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                    )
                }
            }
            Text("LIVE OUTPUT · FFT", color = muted, fontSize = 9.sp, letterSpacing = 1.6.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 18.dp))
        }
    }
}

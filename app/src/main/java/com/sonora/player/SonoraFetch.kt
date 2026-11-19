package com.sonora.player

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class FetchTrackInfo(val title: String, val durationSeconds: Long)
data class FetchStatus(val active: Boolean = false, val progress: Int = 0, val etaSeconds: Int = 0, val message: String = "", val savedPath: String? = null, val error: String? = null)

object SonoraFetch {
    @Volatile private var activeProcessId: String? = null
    @Volatile private var updaterChecked = false
    private val mutableStatus = MutableStateFlow(FetchStatus())
    val status = mutableStatus.asStateFlow()
    fun updateStatus(value: FetchStatus) { mutableStatus.value = value }

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        YoutubeDL.getInstance().init(appContext)

        // Refresh the bundled yt-dlp binary from the official stable channel once
        // per app process. Keep the bundled binary if GitHub is temporarily unavailable.
        if (!updaterChecked) {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(
                    appContext,
                    YoutubeDL.UpdateChannel.STABLE
                )
            } catch (_: Exception) {
                // Do not block authorised downloads when the update endpoint is unavailable.
            } finally {
                updaterChecked = true
            }
        }

        FFmpeg.getInstance().init(appContext)
    }

    suspend fun inspect(url: String): FetchTrackInfo = withContext(Dispatchers.IO) {
        val info = YoutubeDL.getInstance().getInfo(url)
        FetchTrackInfo(info.title.orEmpty().ifBlank { "Untitled audio" }, info.duration.toLong())
    }

    suspend fun downloadAudio(
        context: Context,
        url: String,
        onProgress: (Int, Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SONORA"
        )
        if (!folder.exists() && !folder.mkdirs()) error("Could not create Downloads/SONORA")
        val startedAt = System.currentTimeMillis()
        val processId = "sonora-fetch-" + UUID.randomUUID().toString()
        activeProcessId = processId
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
                addOption("--no-playlist")
                addOption("--embed-metadata")
                // Thumbnail requests can fail independently with HTTP 403; SONORA only needs audio.
                addOption("--no-write-thumbnail")
                addOption("--no-embed-thumbnail")
                addOption("--retries", "5")
                addOption("--fragment-retries", "5")
                addOption("--extractor-retries", "3")
                addOption("--socket-timeout", "20")
                addOption("--no-mtime")
                addOption("-o", File(folder, "%(title)s.%(ext)s").absolutePath)
            }
            YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ ->
                val percent = progress.toInt().coerceIn(0, 100)
                val remaining = eta.toInt().coerceAtLeast(0)
                mutableStatus.value = mutableStatus.value.copy(active = true, progress = percent, etaSeconds = remaining, message = "Extracting audio…")
                onProgress(percent, remaining)
            }
            val created = folder.listFiles()
                ?.filter { it.isFile && it.extension.equals("mp3", true) && it.lastModified() >= startedAt - 1_000L }
                ?.maxByOrNull { it.lastModified() }
                ?: error("Download completed but the MP3 file could not be located")
            suspendCancellableCoroutine<Unit> { continuation ->
                MediaScannerConnection.scanFile(context, arrayOf(created.absolutePath), arrayOf("audio/mpeg")) { _, _ ->
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            created
        } finally {
            activeProcessId = null
        }
    }

    fun cancel() {
        activeProcessId?.let { id ->
            runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
        }
    }
}

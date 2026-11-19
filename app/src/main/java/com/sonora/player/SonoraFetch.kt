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

data class FetchTrackInfo(val title: String, val durationSeconds: Long)
data class FetchStatus(val active: Boolean = false, val progress: Int = 0, val etaSeconds: Int = 0, val message: String = "", val savedPath: String? = null, val error: String? = null)

object SonoraFetch {
    @Volatile private var activeProcessId: String? = null
    private val mutableStatus = MutableStateFlow(FetchStatus())
    val status = mutableStatus.asStateFlow()
    fun updateStatus(value: FetchStatus) { mutableStatus.value = value }

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        YoutubeDL.getInstance().init(context.applicationContext)
        FFmpeg.getInstance().init(context.applicationContext)
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
        val before = folder.listFiles()?.map { it.absolutePath }?.toSet().orEmpty()
        val processId = "sonora-fetch-" + UUID.randomUUID().toString()
        activeProcessId = processId
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
                addOption("--no-playlist")
                addOption("--embed-metadata")
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
                ?.filter { it.isFile && it.absolutePath !in before && it.extension.equals("mp3", true) }
                ?.maxByOrNull { it.lastModified() }
                ?: error("Download completed but the MP3 file could not be located")
            MediaScannerConnection.scanFile(context, arrayOf(created.absolutePath), arrayOf("audio/mpeg"), null)
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

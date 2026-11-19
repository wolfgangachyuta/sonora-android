package com.sonora.player

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Handler
import android.os.Looper
import com.yausername.youtubedl_android.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FetchTrackInfo(val title: String, val durationSeconds: Long)

object SonoraFetch {
    @Volatile private var activeProcessId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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
            YoutubeDL.getInstance().execute(request, { progress, eta ->
                mainHandler.post { onProgress(progress.toInt().coerceIn(0, 100), eta) }
            }, processId)
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

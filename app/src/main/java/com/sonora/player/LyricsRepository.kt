package com.sonora.player

import java.io.File

data class LyricLine(val timeMs: Long, val text: String)
object LyricsRepository {
    private val stamp = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")
    fun parseLrc(content: String): List<LyricLine> = content.lineSequence().flatMap { line ->
        val matches = stamp.findAll(line).toList()
        val lyric = line.replace(stamp, "").trim()
        matches.map { m ->
            val mins = m.groupValues[1].toLongOrNull() ?: 0
            val secs = m.groupValues[2].toLongOrNull() ?: 0
            val millis = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
            LyricLine(mins * 60_000 + secs * 1_000 + millis, lyric)
        }
    }.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }.toList()
    fun siblingLrc(audioPath: String): File? {
        val audio = File(audioPath)
        return File(audio.parentFile, audio.nameWithoutExtension + ".lrc").takeIf { it.exists() && it.isFile() }
    }
}
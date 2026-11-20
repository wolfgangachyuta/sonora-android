package com.sonora.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.Locale

/**
 * Writes metadata into the audio file itself. Work is performed on a private
 * cache copy first so a parser/writer failure cannot damage the user's file.
 */
object AudioTagWriter {
    fun write(context: Context, uriString: String, metadata: EditableTrackMetadata) {
        val resolver = context.contentResolver
        val uri = Uri.parse(uriString)
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment ?: "audio"

        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension == "mp3" || extension == "flac") {
            "Embedded tag editing currently supports MP3 and FLAC files only."
        }

        val working = File.createTempFile("sonora-tags-", ".$extension", context.cacheDir)
        val original = File.createTempFile("sonora-backup-", ".$extension", context.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input ->
                working.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("SONORA could not read this audio file.")

            working.copyTo(original, overwrite = true)

            val audioFile = AudioFileIO.read(working)
            val tag = audioFile.getTagOrCreateAndSetDefault()
            setOrRemove(tag, FieldKey.TITLE, metadata.title)
            setOrRemove(tag, FieldKey.ARTIST, metadata.artist)
            setOrRemove(tag, FieldKey.ALBUM, metadata.album)
            setOrRemove(tag, FieldKey.ALBUM_ARTIST, metadata.albumArtist)
            setOrRemove(tag, FieldKey.COMPOSER, metadata.composer)
            setOrRemove(tag, FieldKey.YEAR, metadata.year)
            setOrRemove(tag, FieldKey.TRACK, metadata.trackNumber)
            setOrRemove(tag, FieldKey.DISC_NO, metadata.discNumber)
            AudioFileIO.write(audioFile)

            try {
                resolver.openOutputStream(uri, "wt")?.use { output ->
                    working.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Android did not provide write access to this audio file.")
            } catch (writeError: Exception) {
                // Best-effort rollback if the provider failed during replacement.
                runCatching {
                    resolver.openOutputStream(uri, "wt")?.use { output ->
                        original.inputStream().use { input -> input.copyTo(output) }
                    }
                }
                throw writeError
            }

            // Read the committed file back to catch providers that accepted a
            // stream but did not persist the replacement.
            val verification = File.createTempFile("sonora-verify-", ".$extension", context.cacheDir)
            try {
                resolver.openInputStream(uri)?.use { input ->
                    verification.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Could not verify the saved audio tags.")
                val saved = AudioFileIO.read(verification).tag
                check(saved.getFirst(FieldKey.TITLE) == metadata.title) {
                    "The file provider did not retain the updated tags."
                }
            } finally {
                verification.delete()
            }
        } finally {
            working.delete()
            original.delete()
        }
    }

    private fun setOrRemove(
        tag: org.jaudiotagger.tag.Tag,
        key: FieldKey,
        rawValue: String
    ) {
        val value = rawValue.trim()
        if (value.isEmpty()) tag.deleteField(key) else tag.setField(key, value)
    }
}

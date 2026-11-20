package com.sonora.player

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val albumId: Long,
    val albumArtist: String = "",
    val composer: String = "",
    val year: Int = 0,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val folderPath: String = ""
)

class AudioRepository(private val context: Context) {
    fun loadTracks(): List<AudioTrack> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.COMPOSER,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
                add("disc_number")
            }
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val tracks = mutableListOf<AudioTrack>()

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            val composerCol = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
            val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val discCol = cursor.getColumnIndex("disc_number")
            val pathCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                tracks += AudioTrack(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown title",
                    artist = cursor.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "Unknown artist",
                    album = cursor.getString(albumCol)?.takeUnless { it == "<unknown>" } ?: "Unknown album",
                    durationMs = cursor.getLong(durationCol),
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    albumId = cursor.getLong(albumIdCol),
                    albumArtist = if (albumArtistCol >= 0) cursor.getString(albumArtistCol).orEmpty() else "",
                    composer = if (composerCol >= 0) cursor.getString(composerCol).orEmpty() else "",
                    year = if (yearCol >= 0) cursor.getInt(yearCol) else 0,
                    trackNumber = if (trackCol >= 0) cursor.getInt(trackCol) else 0,
                    discNumber = if (discCol >= 0) cursor.getInt(discCol) else 0,
                    folderPath = if (pathCol >= 0) cursor.getString(pathCol)?.substringBeforeLast('/') ?: "" else ""
                )
            }
        }
        return tracks
    }
}

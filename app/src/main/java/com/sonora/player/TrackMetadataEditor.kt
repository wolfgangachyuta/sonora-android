package com.sonora.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class EditableTrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val composer: String,
    val year: String,
    val trackNumber: String,
    val discNumber: String
)

@Composable
fun TrackMetadataEditor(
    track: AudioTrack,
    onDismiss: () -> Unit,
    onSave: (EditableTrackMetadata) -> Unit
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var albumArtist by remember(track.id) { mutableStateOf(track.albumArtist) }
    var composer by remember(track.id) { mutableStateOf(track.composer) }
    var year by remember(track.id) { mutableStateOf(track.year.takeIf { it > 0 }?.toString().orEmpty()) }
    var trackNumber by remember(track.id) { mutableStateOf(track.trackNumber.takeIf { it > 0 }?.toString().orEmpty()) }
    var discNumber by remember(track.id) { mutableStateOf(track.discNumber.takeIf { it > 0 }?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit audio metadata") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("Changes are written into MP3 ID3 tags or FLAC Vorbis comments, then refreshed in your music library. Other audio formats are not supported yet.")
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(albumArtist, { albumArtist = it }, label = { Text("Album artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(composer, { composer = it }, label = { Text("Composer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(trackNumber, { trackNumber = it.filter(Char::isDigit).take(3) }, label = { Text("Track number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(discNumber, { discNumber = it.filter(Char::isDigit).take(2) }, label = { Text("Disc number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(EditableTrackMetadata(title.trim(), artist.trim(), album.trim(), albumArtist.trim(), composer.trim(), year, trackNumber, discNumber))
            }) { Text("Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

package com.sonora.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF171817)
private val Paper = Color(0xFFF4F3EF)
private val Card = Color(0xFFE8E7E1)
private val Muted = Color(0xFF85857E)
private val Sage = Color(0xFFB8D2C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SonoraApp() }
    }
}

@Composable
private fun SonoraApp() {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    var tracks by remember { mutableStateOf(emptyList<AudioTrack>()) }
    var currentTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var selectedTab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var hasPermission by remember {
        mutableStateOf(hasAudioPermission(context))
    }
    var loading by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(setOf<Long>()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) loading = true
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull()
                currentTrack = tracks.firstOrNull { it.id == id }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(currentTrack, isPlaying) {
        while (currentTrack != null) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(loading, hasPermission) {
        if (loading && hasPermission) {
            tracks = withContext(Dispatchers.IO) { AudioRepository(context).loadTracks() }
            loading = false
        }
    }

    fun playTrack(track: AudioTrack) {
        val index = tracks.indexOf(track)
        player.setMediaItems(
            tracks.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).build() },
            index.coerceAtLeast(0),
            0L
        )
        currentTrack = track
        player.prepare()
        player.play()
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Paper, background = Paper,
            surface = Paper, onSurface = Ink, secondary = Sage
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Paper, contentColor = Ink) {
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
                Header()
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "main-tabs",
                    modifier = Modifier.weight(1f)
                ) { tab ->
                    when (tab) {
                        "Now Playing" -> currentTrack?.let { track ->
                            NowPlayingScreen(
                                track = track,
                                isPlaying = isPlaying,
                                positionMs = positionMs,
                                durationMs = player.duration.takeIf { it > 0L } ?: track.durationMs,
                                onBack = { selectedTab = "Home" },
                                onToggle = { if (player.isPlaying) player.pause() else player.play() },
                                onPrevious = { player.seekToPreviousMediaItem() },
                                onNext = { player.seekToNextMediaItem() },
                                onSeek = { player.seekTo(it) }
                            )
                        } ?: HomeScreen(tracks, currentTrack, isPlaying, ::playTrack, onScan = {
                            if (hasPermission) loading = true else permissionLauncher.launch(audioPermission())
                        }, onLibrary = { selectedTab = "Library" })
                        "Library" -> LibraryScreen(
                            tracks, loading, hasPermission, query,
                            onQuery = { query = it },
                            onScan = {
                                if (hasPermission) loading = true
                                else permissionLauncher.launch(audioPermission())
                            },
                            onPlay = ::playTrack,
                            currentTrack = currentTrack,
                            favorites = favorites,
                            onFavorite = { id -> favorites = if (id in favorites) favorites - id else favorites + id }
                        )
                        "Search" -> SearchScreen(
                            tracks, query, { query = it }, ::playTrack, currentTrack
                        )
                        "You" -> ProfileScreen(tracks.size, favorites.size)
                        else -> HomeScreen(
                            tracks, currentTrack, isPlaying, ::playTrack,
                            onScan = {
                                if (hasPermission) loading = true
                                else permissionLauncher.launch(audioPermission())
                            },
                            onLibrary = { selectedTab = "Library" }
                        )
                    }
                }
                if (currentTrack != null) {
                    MiniPlayer(currentTrack!!, isPlaying,
                        onToggle = { if (player.isPlaying) player.pause() else player.play() },
                        onNext = { player.seekToNextMediaItem() },
                        onOpen = { selectedTab = "Now Playing" }
                    )
                }
                NavigationBar(selectedTab) { selectedTab = it }
            }
        }
    }
}

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasAudioPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("SONORA", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("YOUR MUSIC, YOUR SPACE.", fontSize = 8.sp, color = Muted, letterSpacing = 1.8.sp)
        }
        Box(Modifier.size(39.dp).clip(CircleShape).background(Ink), Alignment.Center) {
            Icon(Icons.Rounded.GraphicEq, null, tint = Sage, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun HomeScreen(
    tracks: List<AudioTrack>, current: AudioTrack?, playing: Boolean,
    onPlay: (AudioTrack) -> Unit, onScan: () -> Unit, onLibrary: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
        item {
            Column {
                Text("A QUIETER KIND OF LISTENING", fontSize = 9.sp, color = Muted, letterSpacing = 1.6.sp)
                Text("Good evening.", fontSize = 32.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
            }
        }
        item {
            Column {
                SectionLabel("PICK UP WHERE YOU LEFT OFF")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).clickable {
                        if (current != null) onPlay(current) else onScan()
                    }.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(Modifier.size(88.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (current == null) "YOUR LIBRARY" else "NOW IN ROTATION", fontSize = 8.sp, color = Muted, letterSpacing = 1.2.sp)
                        Text(current?.title ?: "Begin with a scan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current?.artist ?: "Find the music on this device", fontSize = 10.sp, color = Muted, maxLines = 1)
                    }
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(28.dp))
                }
            }
        }
        item {
            Column {
                SectionLabel("YOUR COLLECTION")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("TRACKS", tracks.size.toString(), Modifier.weight(1f))
                    StatCard("ALBUMS", tracks.map { it.albumId }.distinct().size.toString(), Modifier.weight(1f))
                    StatCard("ARTISTS", tracks.map { it.artist }.distinct().size.toString(), Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("RECENTLY ADDED", Modifier.weight(1f))
                Text("VIEW LIBRARY ↗", fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.clickable(onClick = onLibrary))
            }
        }
        if (tracks.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink).padding(20.dp)
                ) {
                    Text("Make room\nfor the music.", color = Paper, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text("SONORA plays audio stored on your device.", color = Color.LightGray, fontSize = 11.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onScan, colors = ButtonDefaults.buttonColors(containerColor = Sage, contentColor = Ink)) {
                        Icon(Icons.Rounded.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("SCAN MY MUSIC")
                    }
                }
            }
        } else {
            items(tracks.take(5), key = { it.id }) { TrackRow(it, current?.id == it.id, { onPlay(it) }) }
        }
    }
}

@Composable
private fun LibraryScreen(
    tracks: List<AudioTrack>, loading: Boolean, permission: Boolean, query: String,
    onQuery: (String) -> Unit, onScan: () -> Unit, onPlay: (AudioTrack) -> Unit,
    currentTrack: AudioTrack?, favorites: Set<Long>, onFavorite: (Long) -> Unit
) {
    val filtered = tracks.filter {
        it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
    }
    Column(Modifier.fillMaxSize()) {
        Text("Your library", fontSize = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
        Text("${tracks.size} TRACKS ON THIS DEVICE", fontSize = 9.sp, color = Muted, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(17.dp))
        SearchField(query, onQuery)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ALL SONGS", fontSize = 9.sp, color = Muted, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onScan) { Text(if (loading) "SCANNING…" else "SCAN DEVICE") }
        }
        when {
            !permission -> EmptyState("Your music, one scan away.", "Allow audio access to find music stored on this device.", "GRANT ACCESS", onScan)
            loading -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { CircularProgressIndicator(color = Ink) }
            filtered.isEmpty() -> EmptyState("Nothing here yet.", "Try another search or scan your device for audio files.", "SCAN AGAIN", onScan)
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(filtered, key = { it.id }) { track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { TrackRow(track, currentTrack?.id == track.id, { onPlay(track) }) }
                        IconButton(onClick = { onFavorite(track.id) }) {
                            Icon(if (track.id in favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                null, tint = if (track.id in favorites) Color(0xFF9D5C58) else Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    tracks: List<AudioTrack>, query: String, onQuery: (String) -> Unit,
    onPlay: (AudioTrack) -> Unit, current: AudioTrack?
) {
    Column(Modifier.fillMaxSize()) {
        Text("Find a feeling.", fontSize = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
        Text("SEARCH YOUR MUSIC", fontSize = 9.sp, color = Muted, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(20.dp))
        SearchField(query, onQuery)
        Spacer(Modifier.height(14.dp))
        val results = if (query.isBlank()) emptyList() else tracks.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
        if (query.isBlank()) {
            Text("Search by song, artist or album.", color = Muted, fontSize = 12.sp)
        } else if (results.isEmpty()) {
            Text("No matches found.", color = Muted, fontSize = 12.sp)
        } else {
            LazyColumn { items(results, key = { it.id }) { TrackRow(it, current?.id == it.id, { onPlay(it) }) } }
        }
    }
}

@Composable
private fun ProfileScreen(trackCount: Int, favoriteCount: Int) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your listening.", fontSize = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
        Text("A SMALL PORTRAIT OF YOUR LIBRARY", fontSize = 9.sp, color = Muted, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(8.dp))
        StatCard("TRACKS IN YOUR LIBRARY", trackCount.toString(), Modifier.fillMaxWidth())
        StatCard("FAVOURITES THIS SESSION", favoriteCount.toString(), Modifier.fillMaxWidth())
        Text("Listening history and deeper statistics are coming in the next milestone.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MiniPlayer(track: AudioTrack, playing: Boolean, onToggle: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp).clip(RoundedCornerShape(15.dp)).background(Card).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(Modifier.size(42.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, fontSize = 9.sp, color = Muted, maxLines = 1)
        }
        IconButton(onClick = onToggle) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause") }
        IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Next track") }
    }
}

@Composable
private fun NavigationBar(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceAround) {
        listOf("Home" to Icons.Rounded.Home, "Library" to Icons.Rounded.LibraryMusic, "Search" to Icons.Rounded.Search, "You" to Icons.Rounded.GraphicEq).forEach { (label, icon) ->
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(label) }.padding(horizontal = 13.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, label, tint = if (selected == label) Ink else Muted, modifier = Modifier.size(20.dp))
                Text(label, fontSize = 9.sp, color = if (selected == label) Ink else Muted)
            }
        }
    }
}

@Composable
private fun TrackRow(track: AudioTrack, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 7.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color(0xFF557B65) else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist}  ·  ${track.album}", fontSize = 9.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDuration(track.durationMs), fontSize = 9.sp, color = Muted)
    }
}

@Composable
private fun Artwork(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF243B35), Color(0xFF9BB9A7), Color(0xFFE2CDB3)))), Alignment.Center) {
        Icon(Icons.Rounded.GraphicEq, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Card).padding(13.dp)) {
        Text(value, fontSize = 23.sp, fontWeight = FontWeight.Medium)
        Text(label, fontSize = 8.sp, color = Muted, letterSpacing = .8.sp)
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 9.sp, color = Muted, letterSpacing = 1.5.sp, modifier = modifier.padding(bottom = 11.dp))
}

@Composable
private fun SearchField(value: String, onValue: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Card).padding(horizontal = 13.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        BasicTextField(value, onValue, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink), decorationBox = { inner ->
            if (value.isEmpty()) Text("Songs, artists, albums…", color = Muted, fontSize = 12.sp)
            inner()
        }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.LibraryMusic, null, tint = Muted, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).toInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

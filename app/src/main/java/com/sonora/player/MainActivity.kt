package com.sonora.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
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

private val Night = Color(0xFF090B10)
private val Cream = Color(0xFFF4F1E9)
private val PanelLight = Color(0xFFE3E4E7)
private val PanelDark = Color(0xFF171A21)
private val MutedLight = Color(0xFF77796F)
private val MutedDark = Color(0xFFA3AAA0)
private val Accent = Color(0xFFB9D0FF)
val AccentDeep = Color(0xFF536CBA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SonoraApp() }
    }
}

@Composable
private fun SonoraApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("sonora_library", Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(preferences.getBoolean("dark_mode", false)) }
    val ink = if (darkMode) Cream else Night
    val paper = if (darkMode) Night else Cream
    val panel = if (darkMode) PanelDark else PanelLight
    val muted = if (darkMode) MutedDark else MutedLight
    val player = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } }
    DisposableEffect(player) { onDispose { player.release() } }

    var tracks by remember { mutableStateOf(emptyList<AudioTrack>()) }
    var currentTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var tab by remember { mutableStateOf("Listen") }
    var query by remember { mutableStateOf("") }
    var libraryFilter by remember { mutableStateOf("All songs") }
    var loading by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(hasAudioPermission(context)) }
    var favorites by remember { mutableStateOf(preferences.getStringSet("favorite_ids", emptySet())?.toSet() ?: emptySet()) }
    var recentIds by remember { mutableStateOf(preferences.getString("recent_ids", "").orEmpty().split(",").filter { it.isNotBlank() }) }
    var playCounts by remember { mutableStateOf(preferences.getString("play_counts", "").orEmpty().split(",").filter { it.contains(":") }.associate { it.substringBefore(":") to (it.substringAfter(":").toIntOrNull() ?: 0) }) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    var playlists by remember { mutableStateOf(PlaylistStore(context).load()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) loading = true
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId
                currentTrack = tracks.firstOrNull { it.id.toString() == id }
                currentTrack?.let { t ->
                    recentIds = (listOf(t.id.toString()) + recentIds.filterNot { it == t.id.toString() }).take(40)
                    preferences.edit().putString("recent_ids", recentIds.joinToString(",")).apply()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(currentTrack, isPlaying) {
        while (currentTrack != null) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(400)
        }
    }
    LaunchedEffect(loading, hasPermission) {
        if (loading && hasPermission) {
            tracks = withContext(Dispatchers.IO) { AudioRepository(context).loadTracks() }
            loading = false
        }
    }

    fun startQueue(source: List<AudioTrack>, chosen: AudioTrack) {
        if (source.isEmpty()) return
        val queue = if (shuffle) (source.filterNot { it.id == chosen.id }.shuffled() + chosen) else source
        val startIndex = queue.indexOfFirst { it.id == chosen.id }.coerceAtLeast(0)
        player.setMediaItems(queue.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).build() }, startIndex, 0L)
        currentTrack = chosen
        player.repeatMode = repeatMode
        player.shuffleModeEnabled = shuffle
        player.prepare()
        player.play()
        playCounts = playCounts.toMutableMap().apply { put(chosen.id.toString(), (get(chosen.id.toString()) ?: 0) + 1) }
        preferences.edit().putString("play_counts", playCounts.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }
    fun play(track: AudioTrack) = startQueue(tracks, track)
    fun toggleFavorite(track: AudioTrack) {
        favorites = if (track.id.toString() in favorites) favorites - track.id.toString() else favorites + track.id.toString()
        preferences.edit().putStringSet("favorite_ids", favorites).apply()
    }
    fun requestScan() { if (hasPermission) loading = true else permissionLauncher.launch(audioPermission()) }

    MaterialTheme(colorScheme = if (darkMode) darkColorScheme(primary = Accent, background = Night, surface = PanelDark, onSurface = Cream, onPrimary = Night) else lightColorScheme(primary = Night, background = Cream, surface = Cream, onSurface = Night, secondary = Accent)) {
        Surface(Modifier.fillMaxSize(), color = paper, contentColor = ink) {
            Column(Modifier.fillMaxSize()) {
                TopBrand(ink, muted, onSettings = { tab = "Settings" })
                AnimatedContent(tab, label = "main-content", modifier = Modifier.weight(1f)) { destination ->
                    when (destination) {
                        "Library" -> LibraryPage(tracks, loading, hasPermission, query, { query = it }, { libraryFilter = it }, libraryFilter, ::requestScan, ::play, currentTrack, favorites, ::toggleFavorite, ink, paper, panel, muted)
                        "Albums" -> GroupedPage("Albums", tracks.groupBy { it.album }.toList().sortedBy { it.first.lowercase() }, { it.second.firstOrNull()?.artist ?: "Unknown artist" }, { group -> group.second.firstOrNull()?.let { startQueue(group.second, it) } }, ink, panel, muted)
                        "Artists" -> GroupedPage("Artists", tracks.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }, { "${it.second.size} tracks" }, { group -> group.second.firstOrNull()?.let { startQueue(group.second, it) } }, ink, panel, muted)
                        "Search" -> SearchPage(tracks, query, { query = it }, ::play, currentTrack, ink, panel, muted)
                        "Favourites" -> TrackCollectionPage("Favourites", tracks.filter { it.id.toString() in favorites }, ::play, currentTrack, favorites, ::toggleFavorite, ink, panel, muted)
                        "Statistics" -> StatsPage(tracks, playCounts, recentIds, ink, panel, muted)
                        "Playlists" -> PlaylistsPage(playlists, tracks, ink, panel, muted, { name ->
                            val clean = name.trim()
                            if (clean.isNotEmpty() && playlists.none { it.name.equals(clean, true) }) {
                                playlists = playlists + LocalPlaylist(java.util.UUID.randomUUID().toString(), clean, emptyList())
                                PlaylistStore(context).save(playlists)
                            }
                        }, { playlistId ->
                            playlists = playlists.filterNot { it.id == playlistId }
                            PlaylistStore(context).save(playlists)
                        }, { playlistId ->
                            playlists = playlists.map { p -> if (p.id == playlistId) p.copy(trackIds = tracks.map { it.id.toString() }) else p }
                            PlaylistStore(context).save(playlists)
                        }, { playlist ->
                            val songs = playlist.trackIds.mapNotNull { id -> tracks.firstOrNull { it.id.toString() == id } }
                            songs.firstOrNull()?.let { startQueue(songs, it) }
                        })
                        "Settings" -> SettingsPage(darkMode, { darkMode = it; preferences.edit().putBoolean("dark_mode", it).apply() }, shuffle, { shuffle = it; player.shuffleModeEnabled = it }, repeatMode, { repeatMode = it; player.repeatMode = it }, tracks.size, ink, panel, muted)
                        else -> HomePage(tracks, currentTrack, isPlaying, recentIds, ::play, ::requestScan, { tab = "Library" }, ink, panel, muted, darkMode)
                    }
                }
                if (currentTrack != null) {
                    MiniPlayer(currentTrack!!, isPlaying, positionMs, player.duration.takeIf { it > 0 } ?: currentTrack!!.durationMs, ink, panel, muted,
                        { if (player.isPlaying) player.pause() else player.play() }, { player.seekToNextMediaItem() }, { tab = "Now Playing" })
                }
                BottomNav(tab, ink, muted) { tab = it }
            }
            if (tab == "Now Playing" && currentTrack != null) {
                NowPlayingOverlay(currentTrack!!, isPlaying, positionMs, player.duration.takeIf { it > 0 } ?: currentTrack!!.durationMs, ink, paper, panel, muted, darkMode, { if (player.isPlaying) player.pause() else player.play() }, { player.seekToPreviousMediaItem() }, { player.seekToNextMediaItem() }, { player.seekTo(it) }, { tab = "Listen" }, shuffle, { shuffle = it; player.shuffleModeEnabled = it }, repeatMode, { repeatMode = it; player.repeatMode = it }, currentTrack!!.id.toString() in favorites, { toggleFavorite(currentTrack!!) })
            }
        }
    }
}

@Composable
private fun TopBrand(ink: Color, muted: Color, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("SONORA", fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, color = ink)
            Text("THE ART OF LISTENING", fontSize = 8.sp, letterSpacing = 1.6.sp, color = muted)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Settings", tint = ink) }
    }
}

@Composable
private fun HomePage(tracks: List<AudioTrack>, current: AudioTrack?, playing: Boolean, recent: List<String>, onPlay: (AudioTrack) -> Unit, onScan: () -> Unit, onLibrary: () -> Unit, ink: Color, panel: Color, muted: Color, dark: Boolean) {
    val recentTracks = recent.mapNotNull { id -> tracks.firstOrNull { it.id.toString() == id } }.take(8)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column {
                Text("A PERSONAL SOUNDTRACK", fontSize = 9.sp, color = muted, letterSpacing = 1.8.sp)
                Text("Listen deeply.", fontSize = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1.8).sp, color = ink)
            }
        }
        item {
            FeatureCard(current, playing, ink, panel, muted, onPlay, onScan)
        }
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("YOUR WORLD", fontSize = 10.sp, letterSpacing = 1.7.sp, color = muted, modifier = Modifier.weight(1f))
                Text("LOCAL COLLECTION", fontSize = 8.sp, color = muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                DashboardTile("SONGS", tracks.size.toString(), Modifier.weight(1f), ink, panel, muted)
                DashboardTile("ALBUMS", tracks.map { it.albumId }.distinct().size.toString(), Modifier.weight(1f), ink, panel, muted)
                DashboardTile("ARTISTS", tracks.map { it.artist }.distinct().size.toString(), Modifier.weight(1f), ink, panel, muted)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RECENTLY PLAYED", color = muted, fontSize = 9.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                Text("ALL MUSIC ↗", color = ink, fontSize = 9.sp, modifier = Modifier.clickable(onClick = onLibrary))
            }
        }
        if (recentTracks.isEmpty()) item { EmptyCard("Your listening story starts here.", "Scan the device to discover the music already on it.", "SCAN MUSIC", onScan, ink, panel, muted) }
        else items(recentTracks, key = { it.id }) { TrackRow(it, current?.id == it.id, { onPlay(it) }, ink, muted) }
        if (tracks.isEmpty() && recentTracks.isEmpty()) item { EmptyCard("A library of your own.", "SONORA plays audio stored locally on this device.", "FIND MY MUSIC", onScan, ink, panel, muted) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun FeatureCard(current: AudioTrack?, playing: Boolean, ink: Color, panel: Color, muted: Color, onPlay: (AudioTrack) -> Unit, onScan: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(ink).padding(18.dp)) {
        Text("THE CURRENT", fontSize = 9.sp, letterSpacing = 1.8.sp, color = Accent)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArtwork(current?.albumId, Modifier.size(112.dp).clip(RoundedCornerShape(18.dp)), darkFallback = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(current?.title ?: "Music, waiting.", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(current?.artist ?: "Scan your device to begin.", fontSize = 11.sp, color = Cream.copy(.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { current?.let(onPlay) ?: onScan() }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Night), shape = RoundedCornerShape(50)) {
                    Icon(if (playing) Icons.Rounded.PlayArrow else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (current == null) "EXPLORE" else "PLAY", fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun LibraryPage(tracks: List<AudioTrack>, loading: Boolean, permission: Boolean, query: String, onQuery: (String) -> Unit, onFilter: (String) -> Unit, filter: String, onScan: () -> Unit, onPlay: (AudioTrack) -> Unit, current: AudioTrack?, favorites: Set<String>, onFavorite: (AudioTrack) -> Unit, ink: Color, paper: Color, panel: Color, muted: Color) {
    val filtered = tracks.filter { (filter != "Favourites" || it.id.toString() in favorites) && (query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Your library", fontSize = 31.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1.3).sp, color = ink)
        Text("${tracks.size} TRACKS · ON THIS DEVICE", fontSize = 9.sp, letterSpacing = 1.5.sp, color = muted)
        Spacer(Modifier.height(16.dp))
        SearchField(query, onQuery, ink, panel, muted)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("All songs", "Favourites").forEach { label ->
                FilterPill(label, filter == label, ink, panel, { onFilter(label) })
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onScan) { Text(if (loading) "SCANNING" else "REFRESH", fontSize = 9.sp, color = ink) }
        }
        when {
            !permission -> EmptyCard("Permission needed", "Allow access so SONORA can index your local audio.", "GRANT ACCESS", onScan, ink, panel, muted)
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = ink) }
            filtered.isEmpty() -> EmptyCard("Nothing found.", "Try a different search or refresh the library.", "SCAN AGAIN", onScan, ink, panel, muted)
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 14.dp)) { items(filtered, key = { it.id }) { track ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TrackRow(track, current?.id == track.id, { onPlay(track) }, ink, muted) }
                    IconButton(onClick = { onFavorite(track) }) { Icon(if (track.id.toString() in favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (track.id.toString() in favorites) Color(0xFFB36D6A) else muted) }
                }
            } }
        }
    }
}

@Composable
private fun SearchPage(tracks: List<AudioTrack>, query: String, onQuery: (String) -> Unit, onPlay: (AudioTrack) -> Unit, current: AudioTrack?, ink: Color, panel: Color, muted: Color) {
    val results = if (query.isBlank()) emptyList() else tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Search", fontSize = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1.4).sp, color = ink)
        Text("FIND THE SOUND", fontSize = 9.sp, letterSpacing = 1.6.sp, color = muted)
        Spacer(Modifier.height(18.dp))
        SearchField(query, onQuery, ink, panel, muted)
        Spacer(Modifier.height(16.dp))
        if (query.isBlank()) Text("Songs · albums · artists", fontSize = 12.sp, color = muted)
        else if (results.isEmpty()) Text("No results. Try another search.", fontSize = 12.sp, color = muted)
        else LazyColumn { items(results, key = { it.id }) { TrackRow(it, current?.id == it.id, { onPlay(it) }, ink, muted) } }
    }
}

@Composable
private fun GroupedPage(title: String, groups: List<Pair<String, List<AudioTrack>>>, subtitle: (Pair<String, List<AudioTrack>>) -> String, onPlay: (Pair<String, List<AudioTrack>>) -> Unit, ink: Color, panel: Color, muted: Color) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Column { Text(title, fontSize = 32.sp, fontWeight = FontWeight.Medium, color = ink, letterSpacing = (-1).sp); Text("${groups.size} ${title.uppercase()}", fontSize = 9.sp, color = muted, letterSpacing = 1.4.sp) } }
        if (groups.isEmpty()) item { EmptyCard("Your collection is empty.", "Scan your device to build your collection.", "GO TO LIBRARY", {}, ink, panel, muted) }
        items(groups, key = { it.first }) { group ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(panel).clickable { onPlay(group) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArtwork(group.second.firstOrNull()?.albumId, Modifier.size(58.dp).clip(RoundedCornerShape(13.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.first, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle(group), color = muted, fontSize = 10.sp, maxLines = 1)
                }
                Icon(Icons.Rounded.PlayArrow, null, tint = ink)
            }
        }
    }
}

@Composable
private fun TrackCollectionPage(title: String, tracks: List<AudioTrack>, onPlay: (AudioTrack) -> Unit, current: AudioTrack?, favorites: Set<String>, onFavorite: (AudioTrack) -> Unit, ink: Color, panel: Color, muted: Color) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.Medium, color = ink)
        Text("${tracks.size} SAVED TRACKS", fontSize = 9.sp, letterSpacing = 1.4.sp, color = muted)
        if (tracks.isEmpty()) EmptyCard("Keep the songs close.", "Favourite a track from your library to find it here.", "OPEN LIBRARY", {}, ink, panel, muted)
        else LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) { items(tracks, key = { it.id }) { TrackRow(it, current?.id == it.id, { onPlay(it) }, ink, muted) } }
    }
}

@Composable
private fun PlaylistsPage(
    playlists: List<LocalPlaylist>,
    tracks: List<AudioTrack>,
    ink: Color,
    panel: Color,
    muted: Color,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPopulate: (String) -> Unit,
    onPlay: (LocalPlaylist) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Playlists", fontSize = 32.sp, fontWeight = FontWeight.Medium, color = ink, letterSpacing = (-1).sp)
                    Text("CURATE YOUR OWN SOUNDTRACKS", fontSize = 9.sp, color = muted, letterSpacing = 1.3.sp)
                }
                Button(onClick = { name = ""; showCreate = true }, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = Cream)) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("NEW", fontSize = 10.sp)
                }
            }
        }
        if (playlists.isEmpty()) item { EmptyCard("Make a playlist.", "Create a collection for a mood, moment, journey, or album of memories.", "CREATE PLAYLIST", { showCreate = true }, ink, panel, muted) }
        items(playlists, key = { it.id }) { playlist ->
            val contained = playlist.trackIds.mapNotNull { id -> tracks.firstOrNull { it.id.toString() == id } }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(panel).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Accent, Color(0xFF52618B)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.QueueMusic, null, tint = Night, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${contained.size} tracks", fontSize = 10.sp, color = muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onPopulate(playlist.id) }, contentPadding = PaddingValues(0.dp)) { Text("ADD LIBRARY", fontSize = 8.sp) }
                        Text("·", color = muted)
                        TextButton(onClick = { onDelete(playlist.id) }, contentPadding = PaddingValues(0.dp)) { Text("DELETE", fontSize = 8.sp) }
                    }
                }
                IconButton(onClick = { onPlay(playlist) }) { Icon(Icons.Rounded.PlayArrow, "Play playlist", tint = ink) }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    if (showCreate) {
        AlertDialog(onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onCreate(name); showCreate = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StatsPage(tracks: List<AudioTrack>, counts: Map<String, Int>, recentIds: List<String>, ink: Color, panel: Color, muted: Color) {
    val totalPlays = counts.values.sum()
    val mostPlayed = tracks.sortedByDescending { counts[it.id.toString()] ?: 0 }.take(5).filter { (counts[it.id.toString()] ?: 0) > 0 }
    val topArtists = tracks.groupBy { it.artist }.map { it.key to it.value.sumOf { t -> counts[t.id.toString()] ?: 0 } }.sortedByDescending { it.second }.take(5)
    val recent = recentIds.mapNotNull { id -> tracks.firstOrNull { it.id.toString() == id } }.distinctBy { it.id }.take(6)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Column { Text("Your listening", fontSize = 31.sp, fontWeight = FontWeight.Medium, color = ink); Text("A RECORD OF YOUR SONORA SESSIONS", fontSize = 9.sp, letterSpacing = 1.4.sp, color = muted) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashboardTile("TRACKS", tracks.size.toString(), Modifier.weight(1f), ink, panel, muted); DashboardTile("PLAY COUNTS", totalPlays.toString(), Modifier.weight(1f), ink, panel, muted); DashboardTile("ARTISTS", tracks.map { it.artist }.distinct().size.toString(), Modifier.weight(1f), ink, panel, muted) } }
        item { Text("MOST PLAYED", fontSize = 9.sp, color = muted, letterSpacing = 1.5.sp) }
        if (mostPlayed.isEmpty()) item { Text("Play some music. Your favourites in rotation will appear here.", color = muted, fontSize = 12.sp) }
        items(mostPlayed, key = { it.id }) { TrackRow(it, false, {}, ink, muted, counts[it.id.toString()] ?: 0) }
        item { Text("TOP ARTISTS", fontSize = 9.sp, color = muted, letterSpacing = 1.5.sp) }
        if (topArtists.isEmpty()) item { Text("Artist stats will grow as you listen.", color = muted, fontSize = 12.sp) }
        items(topArtists.filter { it.second > 0 }, key = { it.first }) { row ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(panel).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.first, Modifier.weight(1f), color = ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text("${row.second} plays", color = muted, fontSize = 10.sp)
            }
        }
        item { Text("RECENTLY PLAYED", fontSize = 9.sp, color = muted, letterSpacing = 1.5.sp) }
        items(recent, key = { it.id }) { TrackRow(it, false, {}, ink, muted) }
    }
}

@Composable
private fun SettingsPage(dark: Boolean, onDark: (Boolean) -> Unit, shuffle: Boolean, onShuffle: (Boolean) -> Unit, repeat: Int, onRepeat: (Int) -> Unit, trackCount: Int, ink: Color, panel: Color, muted: Color) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Preferences", fontSize = 32.sp, fontWeight = FontWeight.Medium, color = ink)
        Text("MAKE SONORA YOUR OWN", fontSize = 9.sp, color = muted, letterSpacing = 1.4.sp)
        SettingToggle("Dark appearance", "A softer interface after dark.", dark, onDark, ink, panel, muted)
        SettingToggle("Shuffle", "Randomise the listening order.", shuffle, onShuffle, ink, panel, muted)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(panel).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Repeat", fontWeight = FontWeight.SemiBold, color = ink); Text("Playback repeat mode", fontSize = 10.sp, color = muted) }
            TextButton(onClick = { onRepeat(if (repeat == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else if (repeat == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF) }) { Text(when (repeat) { Player.REPEAT_MODE_ALL -> "ALL"; Player.REPEAT_MODE_ONE -> "ONE"; else -> "OFF" }) }
        }
        InfoSetting("Local music", "Your library stays on your device.", ink, panel, muted)
        InfoSetting("Library indexed", "$trackCount audio tracks found.", ink, panel, muted)
        InfoSetting("SONORA", "A personal, local-first listening space.", ink, panel, muted)
    }
}

@Composable
private fun SettingToggle(title: String, description: String, value: Boolean, onChange: (Boolean) -> Unit, ink: Color, panel: Color, muted: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(panel).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, color = ink); Text(description, fontSize = 10.sp, color = muted) }
        Switch(value, onChange)
    }
}
@Composable
private fun InfoSetting(title: String, desc: String, ink: Color, panel: Color, muted: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(panel).padding(14.dp)) { Text(title, fontWeight = FontWeight.SemiBold, color = ink); Text(desc, fontSize = 10.sp, color = muted) }
}

@Composable
private fun MiniPlayer(track: AudioTrack, playing: Boolean, position: Long, duration: Long, ink: Color, panel: Color, muted: Color, onToggle: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(panel).clickable(onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArtwork(track.albumId, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(track.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, fontSize = 9.sp, color = muted, maxLines = 1) }
            IconButton(onClick = onToggle) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = ink) }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, null, tint = ink) }
        }
        val safe = duration.coerceAtLeast(1)
        LinearProgressIndicator(progress = { position.coerceIn(0, safe).toFloat() / safe }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(2.dp), color = AccentDeep, trackColor = panel)
    }
}

@Composable
private fun BottomNav(selected: String, ink: Color, muted: Color, onSelect: (String) -> Unit) {
    val destinations = listOf("Listen" to Icons.Rounded.Home, "Library" to Icons.Rounded.LibraryMusic, "Albums" to Icons.Rounded.Album, "Playlists" to Icons.Rounded.QueueMusic, "You" to Icons.Rounded.Person)
    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        destinations.forEach { (label, icon) ->
            val actual = if (label == "You") "Statistics" else label
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(actual) }.padding(horizontal = 9.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, label, tint = if (selected == actual) ink else muted, modifier = Modifier.size(20.dp))
                Text(label, fontSize = 8.sp, color = if (selected == actual) ink else muted)
            }
        }
    }
}

@Composable
private fun TrackRow(track: AudioTrack, active: Boolean, onClick: () -> Unit, ink: Color, muted: Color, plays: Int? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 7.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArtwork(track.albumId, Modifier.size(47.dp).clip(RoundedCornerShape(11.dp)))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) AccentDeep else ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist} · ${track.album}", fontSize = 9.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (plays != null) Text("${plays}×", fontSize = 10.sp, color = muted)
        Text(formatDuration(track.durationMs), fontSize = 9.sp, color = muted, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun AlbumArtwork(albumId: Long?, modifier: Modifier = Modifier, darkFallback: Boolean = false) {
    val context = LocalContext.current
    var bitmap by remember(albumId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(albumId) {
        bitmap = withContext(Dispatchers.IO) {
            albumId?.let { id ->
                try {
                    val uri = Uri.parse("content://media/external/audio/albumart/$id")
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                } catch (_: Exception) { null }
            }
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = "Album artwork", modifier = modifier)
    } else {
        Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF243B35), Color(0xFF799885), Color(0xFFE1C9A8)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.GraphicEq, null, tint = Color.White.copy(.9f), modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
private fun DashboardTile(label: String, value: String, modifier: Modifier, ink: Color, panel: Color, muted: Color) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(panel).padding(12.dp)) {
        Text(value, fontSize = 23.sp, fontWeight = FontWeight.Medium, color = ink)
        Text(label, fontSize = 8.sp, letterSpacing = .8.sp, color = muted)
    }
}
@Composable
private fun FilterPill(label: String, active: Boolean, ink: Color, panel: Color, onClick: () -> Unit) {
    Text(label, modifier = Modifier.clip(RoundedCornerShape(50)).background(if (active) ink else panel).clickable { onClick() }.padding(horizontal = 13.dp, vertical = 8.dp), color = if (active) Cream else ink, fontSize = 9.sp)
}
@Composable
private fun SearchField(value: String, onValue: (String) -> Unit, ink: Color, panel: Color, muted: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(panel).padding(horizontal = 13.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(9.dp))
        BasicTextField(value, onValue, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink), decorationBox = { inner -> if (value.isEmpty()) Text("Songs, artists, albums…", color = muted, fontSize = 12.sp); inner() }, modifier = Modifier.fillMaxWidth())
    }
}
@Composable
private fun EmptyCard(title: String, desc: String, action: String, onAction: () -> Unit, ink: Color, panel: Color, muted: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(panel).padding(20.dp)) {
        Icon(Icons.Rounded.LibraryMusic, null, tint = muted, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(9.dp)); Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = ink)
        Spacer(Modifier.height(5.dp)); Text(desc, fontSize = 11.sp, color = muted)
        Spacer(Modifier.height(12.dp)); Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = Cream)) { Text(action, fontSize = 10.sp) }
    }
}
private fun audioPermission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
private fun hasAudioPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED
fun formatDuration(ms: Long): String { val total = (ms / 1000).toInt(); return "${total / 60}:${(total % 60).toString().padStart(2, '0')}" }

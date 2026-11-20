package com.sonora.player

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.ComponentName
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private val Night = Color(0xFF121317)
private val Cream = Color(0xFFF7F5F0)
private val PanelLight = Color(0xFFECE9E2)
private val PanelDark = Color(0xFF1C1E25)
private val MutedLight = Color(0xFF8A887F)
private val MutedDark = Color(0xFFA0A0A0)
private val Accent = Color(0xFFC96F52)
val AccentDeep = Accent

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
    SideEffect {
        context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkMode
                isAppearanceLightNavigationBars = !darkMode
            }
        }
    }
    val fallbackPlayer = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } }
    var player by remember { mutableStateOf<Player>(fallbackPlayer) }
    DisposableEffect(context, fallbackPlayer) {
        val token = SessionToken(context, ComponentName(context, SonoraPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                val controller = future.get()
                if (player === fallbackPlayer) {
                    fallbackPlayer.release()
                    player = controller
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            MediaController.releaseFuture(future)
            if (player === fallbackPlayer) fallbackPlayer.release()
        }
    }

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
    var playbackSpeed by remember { mutableFloatStateOf(preferences.getFloat("playback_speed", 1f).coerceIn(0.5f, 2f)) }
    var sleepTimerMinutes by remember { mutableStateOf<Int?>(null) }
    var playlists by remember { mutableStateOf(PlaylistStore(context).load()) }
    var lyricLines by remember { mutableStateOf(emptyList<LyricLine>()) }
    val fetchStatus by SonoraFetch.status.collectAsState()

    var editingTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var pendingTagTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var pendingTagMetadata by remember { mutableStateOf<EditableTrackMetadata?>(null) }
    val metadataScope = rememberCoroutineScope()

    suspend fun persistEmbeddedTags(track: AudioTrack, metadata: EditableTrackMetadata) {
        withContext(Dispatchers.IO) {
            AudioTagWriter.write(context, track.uri, metadata)
        }
        val indexedValues = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, metadata.title.ifBlank { "Unknown title" })
            put(MediaStore.Audio.Media.ARTIST, metadata.artist.ifBlank { "Unknown artist" })
            put(MediaStore.Audio.Media.ALBUM, metadata.album.ifBlank { "Unknown album" })
            put("album_artist", metadata.albumArtist)
            put(MediaStore.Audio.Media.COMPOSER, metadata.composer)
            put(MediaStore.Audio.Media.YEAR, metadata.year.toIntOrNull() ?: 0)
            put(MediaStore.Audio.Media.TRACK, metadata.trackNumber.toIntOrNull() ?: 0)
            put("disc_number", metadata.discNumber.toIntOrNull() ?: 0)
        }
        runCatching { context.contentResolver.update(Uri.parse(track.uri), indexedValues, null, null) }
        loading = true
        android.widget.Toast.makeText(context, "Embedded audio tags saved.", android.widget.Toast.LENGTH_SHORT).show()
    }

    val metadataWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val track = pendingTagTrack
        val metadata = pendingTagMetadata
        pendingTagTrack = null
        pendingTagMetadata = null
        if (result.resultCode == Activity.RESULT_OK && track != null && metadata != null) {
            metadataScope.launch {
                runCatching { persistEmbeddedTags(track, metadata) }
                    .onFailure { error ->
                        android.widget.Toast.makeText(
                            context,
                            error.message ?: "Could not write tags to this audio file.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
            }
        } else {
            android.widget.Toast.makeText(context, "Audio tag edit cancelled.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val legacyMetadataPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val track = pendingTagTrack
        val metadata = pendingTagMetadata
        pendingTagTrack = null
        pendingTagMetadata = null
        if (granted && track != null && metadata != null) {
            metadataScope.launch {
                runCatching { persistEmbeddedTags(track, metadata) }
                    .onFailure { error ->
                        android.widget.Toast.makeText(
                            context,
                            error.message ?: "Could not write tags to this audio file.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
            }
        } else {
            android.widget.Toast.makeText(context, "Storage permission is required to edit this file.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun saveMetadata(track: AudioTrack, metadata: EditableTrackMetadata) {
        val uri = Uri.parse(track.uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pendingTagTrack = track
            pendingTagMetadata = metadata
            val request = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
            metadataWriteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingTagTrack = track
            pendingTagMetadata = metadata
            legacyMetadataPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            metadataScope.launch {
                try {
                    persistEmbeddedTags(track, metadata)
                } catch (error: Exception) {
                    val recoverable = error as? android.app.RecoverableSecurityException
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && recoverable != null) {
                        pendingTagTrack = track
                        pendingTagMetadata = metadata
                        metadataWriteLauncher.launch(
                            IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build()
                        )
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            error.message ?: "Could not write tags to this audio file.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    val lyricLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { LyricsRepository.parseLrc(it.readText()) } }.getOrNull()?.let { lyricLines = it }
    }

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
                    playCounts = playCounts.toMutableMap().apply { put(t.id.toString(), (get(t.id.toString()) ?: 0) + 1) }
                    preferences.edit().putString("play_counts", playCounts.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(playbackSpeed, player) {
        player.setPlaybackParameters(androidx.media3.common.PlaybackParameters(playbackSpeed, 1f))
        preferences.edit().putFloat("playback_speed", playbackSpeed).apply()
    }

    LaunchedEffect(sleepTimerMinutes) {
        val minutes = sleepTimerMinutes ?: return@LaunchedEffect
        delay(minutes * 60_000L)
        player.pause()
        sleepTimerMinutes = null
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
            currentTrack = currentTrack?.let { active ->
                tracks.firstOrNull { it.id == active.id } ?: active
            }
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

    }
    fun play(track: AudioTrack) = startQueue(tracks, track)
    fun toggleFavorite(track: AudioTrack) {
        favorites = if (track.id.toString() in favorites) favorites - track.id.toString() else favorites + track.id.toString()
        preferences.edit().putStringSet("favorite_ids", favorites).apply()
    }
    fun requestScan() { if (hasPermission) loading = true else permissionLauncher.launch(audioPermission()) }
    LaunchedEffect(fetchStatus.savedPath) {
        if (fetchStatus.savedPath != null) requestScan()
    }

    MaterialTheme(colorScheme = if (darkMode) darkColorScheme(primary = Accent, background = Night, surface = PanelDark, onSurface = Cream, onPrimary = Night) else lightColorScheme(primary = Night, background = Cream, surface = Cream, onSurface = Night, secondary = Accent)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), color = paper, contentColor = ink) {
            Column(Modifier.fillMaxSize()) {
                TopBrand(ink, muted, onSearch = { tab = "Search" }, onFavourites = { tab = "Favourites" }, onSettings = { tab = "Settings" }, onBooks = { tab = "Athenaeum" })
                AnimatedContent(tab, label = "main-content", modifier = Modifier.weight(1f)) { destination ->
                    when (destination) {
                        "Athenaeum" -> AthenaeumScreen(onBack = { tab = "Listen" })
                        "Library" -> LibraryPage(tracks, loading, hasPermission, query, { query = it }, { libraryFilter = it }, libraryFilter, ::requestScan, ::play, currentTrack, favorites, ::toggleFavorite, ink, paper, panel, muted)
                        "Albums" -> GroupedPage("Albums", tracks.groupBy { it.album }.toList().sortedBy { it.first.lowercase() }, { it.second.firstOrNull()?.artist ?: "Unknown artist" }, { group -> group.second.firstOrNull()?.let { startQueue(group.second, it) } }, { tab = "Artists" }, ink, panel, muted)
                        "Artists" -> GroupedPage("Artists", tracks.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }, { "${it.second.size} tracks" }, { group -> group.second.firstOrNull()?.let { startQueue(group.second, it) } }, { tab = "Albums" }, ink, panel, muted)
                        "Search" -> SearchPage(tracks, query, { query = it }, ::play, currentTrack, ink, panel, muted)
                        "Fetch" -> FetchPage(ink, panel, muted)
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
                        }, { playlistId, trackIds ->
                            playlists = playlists.map { p -> if (p.id == playlistId) p.copy(trackIds = (p.trackIds + trackIds).distinct()) else p }
                            PlaylistStore(context).save(playlists)
                        }, { playlistId, trackId ->
                            playlists = playlists.map { p -> if (p.id == playlistId) p.copy(trackIds = p.trackIds.filterNot { it == trackId }) else p }
                            PlaylistStore(context).save(playlists)
                        }, { playlistId, newName ->
                            val clean = newName.trim()
                            if (clean.isNotEmpty() && playlists.none { it.id != playlistId && it.name.equals(clean, true) }) {
                                playlists = playlists.map { p -> if (p.id == playlistId) p.copy(name = clean) else p }
                                PlaylistStore(context).save(playlists)
                            }
                        }, { playlist ->
                            val songs = playlist.trackIds.mapNotNull { id -> tracks.firstOrNull { it.id.toString() == id } }
                            songs.firstOrNull()?.let { startQueue(songs, it) }
                        })
                        "Lyrics" -> LyricsPage(lyricLines, positionMs, ink, panel, muted, { lyricLauncher.launch(arrayOf("text/*", "application/octet-stream")) }, { tab = "Now Playing" })
                        "Equalizer" -> EqualizerPage(SonoraPlaybackService.audioSessionId, ink, panel, muted, { tab = "Settings" })
                        "Settings" -> SettingsPage(darkMode, { darkMode = it; preferences.edit().putBoolean("dark_mode", it).apply() }, shuffle, { shuffle = it; player.shuffleModeEnabled = it }, repeatMode, { repeatMode = it; player.repeatMode = it }, tracks.size, ink, panel, muted, { tab = "Equalizer" })
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
                NowPlayingOverlay(currentTrack!!, isPlaying, positionMs, player.duration.takeIf { it > 0 } ?: currentTrack!!.durationMs, ink, paper, panel, muted, darkMode, { if (player.isPlaying) player.pause() else player.play() }, { player.seekToPreviousMediaItem() }, { player.seekToNextMediaItem() }, { player.seekTo(it) }, { tab = "Listen" }, shuffle, { shuffle = it; player.shuffleModeEnabled = it }, repeatMode, { repeatMode = it; player.repeatMode = it }, currentTrack!!.id.toString() in favorites, { toggleFavorite(currentTrack!!) }, { tab = "Lyrics" }, { editingTrack = currentTrack }, { tab = "Equalizer" }, sleepTimerMinutes, { sleepTimerMinutes = it }, playbackSpeed, { speed -> playbackSpeed = speed })
            }
            editingTrack?.let { track ->
                TrackMetadataEditor(track, onDismiss = { editingTrack = null }, onSave = { metadata ->
                    saveMetadata(track, metadata)
                    editingTrack = null
                })
            }
        }
    }
}

@Composable
private fun TopBrand(ink: Color, muted: Color, onSearch: () -> Unit, onFavourites: () -> Unit, onSettings: () -> Unit, onBooks: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Sonora", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = ink, modifier = Modifier.weight(1f))
        IconButton(onClick = onBooks) { Icon(Icons.Rounded.MenuBook, "Open Athenaeum book library", tint = muted) }
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search your library", tint = muted) }
        IconButton(onClick = onFavourites) { Icon(Icons.Rounded.FavoriteBorder, "Favourite tracks", tint = muted) }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Settings", tint = muted) }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String? = null, ink: Color, muted: Color) {
    Column(Modifier.padding(top = 4.dp, bottom = 14.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, color = ink)
        if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = muted)
    }
}

@Composable
private fun HomePage(tracks: List<AudioTrack>, current: AudioTrack?, playing: Boolean, recent: List<String>, onPlay: (AudioTrack) -> Unit, onScan: () -> Unit, onLibrary: () -> Unit, ink: Color, panel: Color, muted: Color, dark: Boolean) {
    val recentTracks = recent.mapNotNull { id -> tracks.firstOrNull { it.id.toString() == id } }.take(8)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column {
                Text("Good to see you.", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, color = ink)
                Text("Here's what's in your library.", fontSize = 13.sp, color = muted)
            }
        }
        item {
            FeatureCard(current, playing, ink, panel, muted, onPlay, onScan)
        }
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("YOUR COLLECTION", fontSize = 10.sp, letterSpacing = 1.7.sp, color = muted, modifier = Modifier.weight(1f))
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
                Column(Modifier.weight(1f)) { Text("PICK UP WHERE YOU LEFT OFF", color = muted, fontSize = 9.sp, letterSpacing = 1.4.sp); Text("Recently played", color = ink, fontSize = 21.sp, fontWeight = FontWeight.Medium) }
                Text("SEE ALL ↗", color = ink, fontSize = 9.sp, modifier = Modifier.clickable(onClick = onLibrary))
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(panel).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArtwork(current?.albumId, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (current != null) "Continue listening" else "Start listening", fontSize = 11.sp, color = muted)
            Text(current?.title ?: "Scan your device to begin", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (current != null) Text(current.artist, fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { current?.let(onPlay) ?: onScan() }, modifier = Modifier.size(46.dp).clip(CircleShape).background(Accent)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = if (current == null) "Scan for music" else "Play", tint = Color.White)
        }
    }
}

@Composable
private fun LibraryPage(tracks: List<AudioTrack>, loading: Boolean, permission: Boolean, query: String, onQuery: (String) -> Unit, onFilter: (String) -> Unit, filter: String, onScan: () -> Unit, onPlay: (AudioTrack) -> Unit, current: AudioTrack?, favorites: Set<String>, onFavorite: (AudioTrack) -> Unit, ink: Color, paper: Color, panel: Color, muted: Color) {
    var sortBy by remember { mutableStateOf("Recently added") }
    var showSort by remember { mutableStateOf(false) }
    val matching = tracks.filter { (filter != "Favourites" || it.id.toString() in favorites) && (query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)) }
    val filtered = when (sortBy) {
        "Title A–Z" -> matching.sortedBy { it.title.lowercase() }
        "Artist A–Z" -> matching.sortedBy { it.artist.lowercase() }
        "Album A–Z" -> matching.sortedBy { it.album.lowercase() }
        "Longest first" -> matching.sortedByDescending { it.durationMs }
        else -> matching
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PageHeader("Your library", "${tracks.size} tracks on this device", ink, muted)
        Spacer(Modifier.height(16.dp))
        SearchField(query, onQuery, ink, panel, muted)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("All songs", "Favourites").forEach { label ->
                FilterPill(label, filter == label, ink, panel, { onFilter(label) })
                Spacer(Modifier.width(8.dp))
            }
            Box {
                TextButton(onClick = { showSort = true }) { Icon(Icons.Rounded.Sort, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("SORT", fontSize = 9.sp, color = ink) }
                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                    listOf("Recently added", "Title A–Z", "Artist A–Z", "Album A–Z", "Longest first").forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { sortBy = option; showSort = false })
                    }
                }
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
private fun FetchPage(ink: Color, panel: Color, muted: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var details by remember { mutableStateOf<FetchTrackInfo?>(null) }
    var analysing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var engineReady by remember { mutableStateOf(false) }
    var engineError by remember { mutableStateOf<String?>(null) }
    val fetchStatus by SonoraFetch.status.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        SonoraFetch.updateStatus(FetchStatus(active = true, message = "Starting download…"))
        SonoraFetchService.start(context, url.trim())
    }
    LaunchedEffect(context) {
        runCatching { SonoraFetch.initialize(context) }.onSuccess { engineReady = true }.onFailure { engineError = it.message ?: "The download engine could not initialise." }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            PageHeader("Fetch", "Bring authorised audio into your local library.", ink, muted)
        }
        item { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SOURCE LINK", fontSize = 9.sp, letterSpacing = 1.5.sp, color = muted)
            OutlinedTextField(value = url, onValueChange = { url = it; details = null; error = null }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://…") }, singleLine = true, shape = RoundedCornerShape(15.dp), enabled = !fetchStatus.active && !analysing)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = {
                    error = null; details = null
                    if (!url.trim().startsWith("https://") && !url.trim().startsWith("http://")) error = "Enter a valid HTTPS or HTTP link."
                    else scope.launch { analysing = true; runCatching { SonoraFetch.inspect(url.trim()) }.onSuccess { details = it }.onFailure { error = it.message ?: "Could not read this source." }; analysing = false }
                }, enabled = engineReady && !fetchStatus.active && !analysing && url.isNotBlank(), modifier = Modifier.weight(1f)) { Text(if (analysing) "ANALYSING…" else "ANALYSE", fontSize = 10.sp) }
                Button(onClick = { error = null; if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else { SonoraFetch.updateStatus(FetchStatus(active = true, message = "Starting download…")); SonoraFetchService.start(context, url.trim()) } }, enabled = engineReady && !fetchStatus.active && !analysing && details != null, modifier = Modifier.weight(1f)) { Text("DOWNLOAD MP3", fontSize = 10.sp) }
            }
        } }
        if (details != null) item { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("READY TO DOWNLOAD", fontSize = 9.sp, color = muted, letterSpacing = 1.4.sp)
            Text(details!!.title, fontSize = 17.sp, color = ink, fontWeight = FontWeight.SemiBold)
            Text(if (details!!.durationSeconds > 0) formatDuration(details!!.durationSeconds * 1000L) else "Duration unavailable", fontSize = 10.sp, color = muted)
            Text("Audio · MP3 · best available quality", fontSize = 10.sp, color = muted)
        } }
        if (fetchStatus.active) item { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = AccentDeep); Spacer(Modifier.width(10.dp)); Text(fetchStatus.message, fontSize = 11.sp, color = ink, modifier = Modifier.weight(1f)); TextButton(onClick = { SonoraFetchService.cancel(context) }) { Text("CANCEL", fontSize = 9.sp) } }
            LinearProgressIndicator(progress = { fetchStatus.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = AccentDeep)
            Text("${fetchStatus.progress}% · ${if (fetchStatus.etaSeconds > 0) "ETA ${fetchStatus.etaSeconds}s" else "Estimating time"}", fontSize = 9.sp, color = muted)
        } }
        if (fetchStatus.error != null) item { Text(fetchStatus.error!!, color = Color(0xFFB3261E), fontSize = 11.sp) }
        if (error != null) item { Text(error!!, color = Color(0xFFB3261E), fontSize = 11.sp) }
        if (fetchStatus.savedPath != null) item { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("DOWNLOAD COMPLETE", fontSize = 9.sp, color = AccentDeep, letterSpacing = 1.4.sp)
            Text(fetchStatus.savedPath!!.substringAfterLast('/'), fontSize = 13.sp, color = ink, fontWeight = FontWeight.SemiBold)
            Text("Saved in Downloads/SONORA. Refresh your library to see it.", fontSize = 10.sp, color = muted)
        } }
        if (engineError != null) item { Text(engineError!!, color = Color(0xFFB3261E), fontSize = 11.sp) }
        item { Text("SONORA Fetch uses yt-dlp and FFmpeg. Download only material you own or have permission to save, and respect the source platform’s terms.", fontSize = 10.sp, color = muted, lineHeight = 15.sp) }
    }
}
@Composable
private fun SearchPage(tracks: List<AudioTrack>, query: String, onQuery: (String) -> Unit, onPlay: (AudioTrack) -> Unit, current: AudioTrack?, ink: Color, panel: Color, muted: Color) {
    val results = if (query.isBlank()) emptyList() else tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PageHeader("Search", "Find songs, albums and artists", ink, muted)
        Spacer(Modifier.height(18.dp))
        SearchField(query, onQuery, ink, panel, muted)
        Spacer(Modifier.height(16.dp))
        if (query.isBlank()) Text("Search songs, albums and artists in your local collection.", fontSize = 12.sp, color = muted)
        else if (results.isEmpty()) Text("No matches yet. Try another title, artist or album.", fontSize = 12.sp, color = muted)
        else LazyColumn { items(results, key = { it.id }) { TrackRow(it, current?.id == it.id, { onPlay(it) }, ink, muted) } }
    }
}

@Composable
private fun GroupedPage(title: String, groups: List<Pair<String, List<AudioTrack>>>, subtitle: (Pair<String, List<AudioTrack>>) -> String, onPlay: (Pair<String, List<AudioTrack>>) -> Unit, onSwitch: () -> Unit, ink: Color, panel: Color, muted: Color) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Column { PageHeader(title, "${groups.size} ${title.lowercase()} in your collection", ink, muted); FilterPill(if (title == "Albums") "View artists" else "View albums", false, ink, panel, onSwitch) } }
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
        PageHeader(title, "${tracks.size} tracks", ink, muted)
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
    onPopulate: (String, List<String>) -> Unit,
    onRemoveTrack: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onPlay: (LocalPlaylist) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var renameId by remember { mutableStateOf<String?>(null) }
    var pickerId by remember { mutableStateOf<String?>(null) }
    var pickedIds by remember { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PageHeader("Playlists", "${playlists.size} personal collections", ink, muted)
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
                        TextButton(onClick = { pickedIds = emptySet(); pickerId = playlist.id }, contentPadding = PaddingValues(0.dp)) { Text("ADD LIBRARY", fontSize = 8.sp) }
                        Text("·", color = muted)
                        TextButton(onClick = { name = playlist.name; renameId = playlist.id }, contentPadding = PaddingValues(0.dp)) { Text("RENAME", fontSize = 8.sp) }
                        TextButton(onClick = { onDelete(playlist.id) }, contentPadding = PaddingValues(0.dp)) { Text("DELETE", fontSize = 8.sp) }
                    }
                }
                IconButton(onClick = { onPlay(playlist) }) { Icon(Icons.Rounded.PlayArrow, "Play playlist", tint = ink) }
            }
            contained.forEach { song ->
                Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(song.title, Modifier.weight(1f), fontSize = 10.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onRemoveTrack(playlist.id, song.id.toString()) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.Close, "Remove song", tint = muted, modifier = Modifier.size(14.dp)) }
                }
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
    if (renameId != null) {
        AlertDialog(onDismissRequest = { renameId = null }, title = { Text("Rename playlist") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(renameId!!, name); renameId = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text("Cancel") } })
    }
    if (pickerId != null) {
        AlertDialog(onDismissRequest = { pickerId = null }, title = { Text("Add tracks") },
            text = { LazyColumn(Modifier.heightIn(max = 420.dp)) { items(tracks, key = { it.id }) { track ->
                Row(Modifier.fillMaxWidth().clickable { pickedIds = if (track.id.toString() in pickedIds) pickedIds - track.id.toString() else pickedIds + track.id.toString() }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = track.id.toString() in pickedIds, onCheckedChange = { checked -> pickedIds = if (checked) pickedIds + track.id.toString() else pickedIds - track.id.toString() })
                    Column(Modifier.weight(1f)) { Text(track.title, color = ink, fontSize = 12.sp); Text(track.artist, color = muted, fontSize = 9.sp) }
                }
            } } },
            confirmButton = { TextButton(onClick = { onPopulate(pickerId!!, pickedIds.toList()); pickerId = null }) { Text("Add " + pickedIds.size) } },
            dismissButton = { TextButton(onClick = { pickerId = null }) { Text("Cancel") } })
    }
}

@Composable
private fun StatsPage(tracks: List<AudioTrack>, counts: Map<String, Int>, recentIds: List<String>, ink: Color, panel: Color, muted: Color) {
    val totalPlays = counts.values.sum()
    val mostPlayed = tracks.sortedByDescending { counts[it.id.toString()] ?: 0 }.take(5).filter { (counts[it.id.toString()] ?: 0) > 0 }
    val topArtists = tracks.groupBy { it.artist }.map { it.key to it.value.sumOf { t -> counts[t.id.toString()] ?: 0 } }.sortedByDescending { it.second }.take(5)
    val recent = recentIds.mapNotNull { id -> tracks.firstOrNull { it.id.toString() == id } }.distinctBy { it.id }.take(6)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader("Listening statistics", "${totalPlays} plays across ${tracks.size} tracks", ink, muted) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashboardTile("TRACKS", tracks.size.toString(), Modifier.weight(1f), ink, panel, muted); DashboardTile("PLAY COUNTS", totalPlays.toString(), Modifier.weight(1f), ink, panel, muted); DashboardTile("ARTISTS", tracks.map { it.artist }.distinct().size.toString(), Modifier.weight(1f), ink, panel, muted) } }
        item { Text("IN HEAVY ROTATION", fontSize = 9.sp, color = muted, letterSpacing = 1.5.sp) }
        if (mostPlayed.isEmpty()) item { Text("Play some music. Your favourites in rotation will appear here.", color = muted, fontSize = 12.sp) }
        items(mostPlayed, key = { it.id }) { TrackRow(it, false, {}, ink, muted, counts[it.id.toString()] ?: 0) }
        item { Text("FAVOURITE ARTISTS", fontSize = 9.sp, color = muted, letterSpacing = 1.5.sp) }
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
private fun LyricsPage(lines: List<LyricLine>, position: Long, ink: Color, panel: Color, muted: Color, onOpen: () -> Unit, onBack: () -> Unit) {
    val current = lines.indexOfLast { it.timeMs <= position }.coerceAtLeast(0)
    val state = rememberLazyListState()
    LaunchedEffect(current, lines.size) { if (lines.isNotEmpty()) state.animateScrollToItem(current) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = ink) }; Column(Modifier.weight(1f)) { Text("NOW PLAYING", fontSize = 8.sp, color = muted, letterSpacing = 1.4.sp); Text("Lyrics", fontSize = 30.sp, color = ink) }; Button(onClick = onOpen) { Text("OPEN .LRC") } }
        if (lines.isEmpty()) { EmptyCard("Lyrics are missing.", "Choose a synced .lrc file from your device to display and follow lyrics.", "OPEN LYRICS", onOpen, ink, panel, muted) }
        else LazyColumn(state = state, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 22.dp)) { items(lines.size) { index -> Text(lines[index].text, fontSize = if (index == current) 22.sp else 16.sp, fontWeight = if (index == current) FontWeight.SemiBold else FontWeight.Normal, color = if (index == current) ink else muted.copy(alpha = .65f), modifier = Modifier.fillMaxWidth()) } }
    }
}
@Composable
private fun EqualizerPage(audioSessionId: Int, ink: Color, panel: Color, muted: Color, onBack: () -> Unit) {
    val eq = remember(audioSessionId) { EqualizerController(audioSessionId) }
    DisposableEffect(eq) { onDispose { eq.release() } }
    var enabled by remember(eq) { mutableStateOf(eq.enabled) }
    var levels by remember(eq) { mutableStateOf((0 until eq.bands).map { eq.level(it).toFloat() }) }
    val presets = eq.presetNames
    val range = eq.range
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = ink) }
                Column(Modifier.weight(1f)) {
                    PageHeader("Equalizer", "Adjust the sound of your music", ink, muted)
                }
                Icon(Icons.Rounded.GraphicEq, null, tint = ink, modifier = Modifier.size(30.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(panel).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Audio processing", color = ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("System equalizer · current playback session", color = muted, fontSize = 10.sp)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it; eq.setEnabled(it) })
                }
                Spacer(Modifier.height(12.dp))
                Text(if (enabled) "ACTIVE" else "BYPASSED", color = if (enabled) AccentDeep else muted,
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LISTENING PROFILES", color = muted, fontSize = 9.sp, letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (eq.bands > 0) {
                        levels = (0 until eq.bands).map { 0f }
                        levels.indices.forEach { eq.setBandLevel(it, 0) }
                    }
                }, enabled = enabled && eq.bands > 0) { Text("RESET") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEachIndexed { index, preset ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            eq.usePreset(index)
                            levels = (0 until eq.bands).map { eq.level(it).toFloat() }
                            enabled = true
                            eq.setEnabled(true)
                        },
                        label = { Text(preset, fontSize = 11.sp) },
                        enabled = eq.bands > 0
                    )
                }
            }
        }
        if (eq.bands == 0) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(panel).padding(20.dp)) {
                    Text("Equalizer unavailable", color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("This device or playback session does not expose a system equalizer. Try again after playback has started.", color = muted, fontSize = 11.sp)
                }
            }
        } else {
            item { Text("FREQUENCY CONTROL", color = muted, fontSize = 9.sp, letterSpacing = 1.6.sp) }
            items((0 until eq.bands).toList()) { band ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(panel).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (eq.frequency(band) >= 1000) String.format("%.1f kHz", eq.frequency(band) / 1000f) else "${eq.frequency(band)} Hz",
                                color = ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                            )
                            Text(
                                when (band) { 0 -> "LOW FREQUENCY"; eq.bands - 1 -> "HIGH FREQUENCY"; else -> "MID FREQUENCY" },
                                color = muted, fontSize = 8.sp, letterSpacing = 1.1.sp
                            )
                        }
                        Text(String.format("%+.1f dB", levels[band] / 100f), color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Slider(
                        value = levels[band],
                        onValueChange = { value ->
                            levels = levels.toMutableList().also { it[band] = value }
                            eq.setBandLevel(band, value.toInt())
                        },
                        valueRange = range[0].toFloat()..range[1].toFloat(),
                        enabled = enabled
                    )
                }
            }
        }
        item {
            Text("Equalizer availability and frequency bands are supplied by Android and vary by device. Changes affect the active SONORA playback session.", color = muted, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}
@Composable
private fun SettingsPage(dark: Boolean, onDark: (Boolean) -> Unit, shuffle: Boolean, onShuffle: (Boolean) -> Unit, repeat: Int, onRepeat: (Int) -> Unit, trackCount: Int, ink: Color, panel: Color, muted: Color, onEqualizer: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Settings", "Preferences for listening", ink, muted)
        SettingToggle("Dark appearance", "A softer interface after dark.", dark, onDark, ink, panel, muted)
        SettingToggle("Shuffle", "Randomise the listening order.", shuffle, onShuffle, ink, panel, muted)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(panel).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Repeat", fontWeight = FontWeight.SemiBold, color = ink); Text("Playback repeat mode", fontSize = 10.sp, color = muted) }
            TextButton(onClick = { onRepeat(if (repeat == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else if (repeat == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF) }) { Text(when (repeat) { Player.REPEAT_MODE_ALL -> "ALL"; Player.REPEAT_MODE_ONE -> "ONE"; else -> "OFF" }) }
        }
        Button(onClick = onEqualizer) { Icon(Icons.Rounded.Equalizer, null); Spacer(Modifier.width(6.dp)); Text("OPEN EQUALIZER") }
        InfoSetting("Local music", "Your library stays on your device.", ink, panel, muted)
        InfoSetting("Library indexed", "$trackCount audio tracks found.", ink, panel, muted)
        InfoSetting("SONORA", "A personal, local-first listening space.", ink, panel, muted)
    }
}

@Composable
private fun SettingToggle(title: String, description: String, value: Boolean, onChange: (Boolean) -> Unit, ink: Color, panel: Color, muted: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(panel).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, color = ink); Text(description, fontSize = 10.sp, color = muted) }
        Switch(value, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
@Composable
private fun InfoSetting(title: String, desc: String, ink: Color, panel: Color, muted: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(panel).padding(14.dp)) { Text(title, fontWeight = FontWeight.SemiBold, color = ink); Text(desc, fontSize = 10.sp, color = muted) }
}

@Composable
private fun MiniPlayer(track: AudioTrack, playing: Boolean, position: Long, duration: Long, ink: Color, panel: Color, muted: Color, onToggle: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit) {
    val safe = duration.coerceAtLeast(1L)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(panel).clickable(onClick = onOpen).padding(start = 7.dp, end = 8.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArtwork(track.albumId, Modifier.size(48.dp).clip(CircleShape))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, fontSize = 10.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggle) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause playback" else "Start playback", tint = ink) }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Next track", tint = ink) }
        }
        LinearProgressIndicator(progress = { position.coerceIn(0L, safe).toFloat() / safe }, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(2.dp), color = AccentDeep, trackColor = panel)
    }
}

@Composable
private fun BottomNav(selected: String, ink: Color, muted: Color, onSelect: (String) -> Unit) {
    val destinations = listOf(
        Triple("Listen", Icons.Rounded.Home, "Listen"),
        Triple("Library", Icons.Rounded.LibraryMusic, "Library"),
        Triple("Albums", Icons.Rounded.Album, "Albums"),
        Triple("Fetch", Icons.Rounded.DownloadForOffline, "Fetch"),
        Triple("Playlists", Icons.Rounded.QueueMusic, "Playlists"),
        Triple("You", Icons.Rounded.Person, "Statistics")
    )
    val barBg = if (ink == Night) PanelDark else PanelLight
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp)).background(barBg).padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        destinations.forEach { (label, icon, destination) ->
            val active = selected == destination
            Column(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(18.dp)).background(if (active) ink else Color.Transparent)
                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(destination) }).padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(icon, contentDescription = null, tint = if (active) barBg else muted, modifier = Modifier.size(19.dp))
                Spacer(Modifier.height(2.dp))
                Text(label, fontSize = 9.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal, color = if (active) barBg else muted, maxLines = 1)
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
        val bg = if (darkFallback) Color(0xFF23262E) else Color(0xFFE7E4DC)
        val fg = if (darkFallback) Color(0xFFCFCFC9) else Color(0xFF9B9890)
        Box(modifier.background(bg), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = fg, modifier = Modifier.size(22.dp))
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
    Text(label, modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(if (active) ink else panel).selectable(selected = active, role = Role.Tab, onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp), color = if (active) Cream else ink, fontSize = 9.sp)
}
@Composable
private fun SearchField(value: String, onValue: (String) -> Unit, ink: Color, panel: Color, muted: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(panel).padding(horizontal = 13.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(9.dp))
        BasicTextField(value, onValue, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink), decorationBox = { inner -> if (value.isEmpty()) Text("Songs, artists, albums…", color = muted, fontSize = 12.sp); inner() }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search songs, artists and albums" })
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

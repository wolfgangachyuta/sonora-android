# SONORA architecture

## Scope

SONORA is a native Android application. The current implementation is intentionally small: one activity hosts Compose screens, an audio repository reads the Android media catalogue, and Media3 ExoPlayer handles playback.

This document describes the code that exists today and identifies planned boundaries. It does not imply that planned modules have already been implemented.

## Runtime components

### MainActivity / SonoraApp

- Creates the ExoPlayer instance for the lifetime of the Compose app.
- Owns screen selection, loaded tracks, current track, playback state, search query, and session favourites.
- Requests the platform audio-read permission.
- Invokes AudioRepository on an IO dispatcher when a scan is requested.
- Creates the player media-item list from the scanned tracks.
- Releases ExoPlayer when the composable owner is disposed.

### AudioRepository

AudioRepository queries `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` for music entries. It maps the media ID, title, artist, album, duration, album ID, and content URI into AudioTrack records.

The repository currently performs a complete catalogue query when a scan is requested. A future repository layer should support paging, sorting, filtering, and database-backed user data.

### Compose UI

- HomeScreen: welcome, collection counts, recently available tracks, and scan entry point.
- LibraryScreen: searchable track list, scan action, and session favourites.
- SearchScreen: focused search across title, artist, and album.
- ProfileScreen: current library/session counts.
- MiniPlayer: compact playback controls and entry to Now Playing.
- NowPlayingScreen: artwork treatment, track metadata, seek slider, and transport controls.

The UI is currently hosted in MainActivity.kt plus the dedicated NowPlayingScreen.kt file. As the app grows, screen state and playback orchestration should move out of the activity into view models.

## Data and playback flow

1. The user requests a library scan.
2. The app checks or requests `READ_MEDIA_AUDIO` on Android 13+ or `READ_EXTERNAL_STORAGE` on older supported Android versions.
3. AudioRepository queries MediaStore on Dispatchers.IO.
4. AudioTrack objects populate the Compose library state.
5. Selecting a track builds Media3 MediaItems from the track content URIs.
6. ExoPlayer prepares and plays the selected item and the remaining library items form the playback sequence.
7. Player.Listener updates the current track and play/pause state; the Now Playing screen samples the current position for its progress display.

## Permissions and privacy

The manifest declares audio-read permissions only. SONORA's current core feature does not require network access or a user account. The permission is used to discover and play audio exposed through Android's media provider. The app should explain the permission in plain language and should remain usable without granting it, with a clear empty/permission state.

Any future feature that introduces network access, analytics, or additional sensitive permissions must document why it is needed and minimise collection.

## Phase 3 groundwork (in progress)

- Added the Media3 Session dependency.
- Declared a foreground media-playback service in the Android manifest with the media-playback service permissions.
- Added SonoraPlaybackService to host ExoPlayer and a MediaSession.
- Persisted favourite track IDs using SharedPreferences so they survive process restarts.

**Important:** The new service is groundwork only. The current screen still creates and controls its own ExoPlayer; MediaController connection and transferring playback into the service are the remaining integration steps. Background playback is not yet considered complete.

## Planned architecture evolution

The next architectural step is to separate responsibilities:

- **PlaybackController / MediaSessionService:** own playback independently of a visible screen and expose a MediaSession for notification, lock-screen, headset, and system controls.
- **Room database:** persist playlists, favourites, play history, and user settings.
- **Repository interfaces:** isolate MediaStore, Room, and playback data sources.
- **ViewModels:** expose immutable screen state and user actions to Compose.
- **Artwork and lyrics providers:** resolve local album artwork and local LRC files without coupling those concerns to the UI.

## Current technical caveats

- Player lifetime is currently tied to the Compose app owner, not a foreground service.
- Favourites are held in Compose memory and are not durable.
- Position is sampled while a track is selected; robust lifecycle-aware progress and seek handling still need device testing.
- MediaStore metadata and availability vary by device and file provider.
- Placeholder artwork is used instead of actual embedded or MediaStore artwork.

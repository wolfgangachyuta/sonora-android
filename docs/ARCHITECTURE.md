# Application architecture

## Product scope

This Android package contains two connected local-first experiences:

- **SONORA** — music discovery, playback, playlists and listening tools.
- **Athenaeum** — personal physical/digital library, ebook reader, annotations, Commonplace Book and widgets.

The launcher activity hosts SONORA. Athenaeum is reachable from the SONORA top bar and from Android home-screen widgets.

## SONORA runtime

### MainActivity / SonoraApp

- Owns Compose navigation and presentation state.
- Connects to SonoraPlaybackService through Media3 MediaController; the service owns the ExoPlayer and MediaSession.
- Scans MediaStore on Dispatchers.IO through AudioRepository.
- Maintains screen state for the current track, queue, favourites, playlists, lyrics, filters, playback preferences and statistics.
- Uses Room as durable storage for favourites, playlists and playlist tracks, play history, and the queue snapshot. Existing SharedPreferences data is migrated once into Room.
- Keeps lightweight UI preferences such as theme, accent, speed, shuffle and repeat in SharedPreferences.

### Playback service

SonoraPlaybackService owns ExoPlayer and MediaSession. The player is configured for media audio focus, local wake mode and automatic pause when audio becomes noisy (for example, when headphones are disconnected). MediaSessionService supplies the system media notification and lock-screen transport surface.

Queue items carry title, artist, album and MediaStore album-art URI metadata. The queue snapshot stores ordered track IDs, current index and playback position. The UI can reorder and remove queued items without tying playback to a visible screen.

### Library data

AudioRepository queries MediaStore for track identifiers, title, artist, album, duration, album ID, composer, year, track/disc numbers and local file path. Folder browsing groups tracks by parent directory. AlbumArtwork resolves MediaStore album art; the current track's artwork can determine the accent palette.

### Additional SONORA components

- LyricsRepository parses local LRC files and discovers an adjacent LRC file for a track.
- EqualizerController wraps the Android system equalizer for the active playback session.
- AudioVisualizerPage uses Android Visualizer FFT capture with an explicit runtime RECORD_AUDIO permission request; it does not record or save audio.
- AudioTagWriter writes supported embedded audio metadata.
- PlaylistStore remains as a compatibility/migration layer while Room is the durable store.
- SonoraFetch and SonoraFetchService provide the optional authorised-media download workflow.

## Athenaeum data and UI

AthenaeumDatabase is a separate Room database (athenaeum.db) with versioned migrations. It stores books, shelves, book/shelf assignments, bookmarks, annotations, Commonplace Book entries and reading sessions.

AthenaeumBackup exports/restores the structured catalogue and archive records as versioned JSON and exports catalogue CSV. Digital file and cover URIs are preserved as references; the ebook binaries are not copied into the JSON backup.

The reader currently includes:
- PDF rendering through Android PdfRenderer.
- EPUB 2/3 ZIP/OPF manifest and spine parsing, NCX/EPUB3 navigation and inlining of local images/stylesheets.
- TXT, HTML, FB2, legacy PalmDOC MOBI 6, RTF, Markdown and CBZ reading paths.
- Persisted reading location, bookmarks, annotations, OCR for PDF pages and Android text-to-speech.

Three AppWidgetProvider implementations expose Continue Reading, library overview and random-book widgets. Widget taps deep-link to Athenaeum inside MainActivity.

## Permissions, privacy and network

SONORA requests Android media-read permission to index local audio. Athenaeum uses the Storage Access Framework and persistable document URI permissions for imported books. PDF OCR uses bundled on-device ML Kit text recognition; text-to-speech uses Android's installed TTS engine. ISBN lookup makes an explicit request to Google Books. SONORA Fetch uses network access for its user-initiated download workflow. The core libraries and playback remain local-first; there is no account or cloud sync.

## Verification status

Source implementation does not prove runtime correctness. Physical-device checks remain required for background playback, notifications, lock-screen controls, audio focus, widgets, reader format compatibility, OCR, TTS, accessibility, large fonts and Android-version differences. GitHub Actions is manual-only and must not be triggered by pushes or pull requests.

# SONORA

**Your music, your space.** SONORA is a local-first Android music player with a restrained editorial interface. It indexes audio already available to Android's media library and plays it on-device.

> Development version: 0.3.0 — active development; physical-device verification remains necessary.

## Current implementation

- Kotlin, Jetpack Compose and Material 3 interface with light/dark appearance.
- MediaStore discovery, library browsing, albums, artists, search and favourites.
- Media3 playback queue, mini-player, full-screen Now Playing, seek, shuffle and repeat.
- Persistent favourites, recent-play history and play counts using SharedPreferences.
- Persistent playlists: create, rename, delete, add selected tracks, remove individual tracks and play.
- LRC file import and timed lyric display.
- Native Android Equalizer controls where supported by the device.
- Media3 MediaSessionService and UI MediaController connection for playback outside the activity.
- Now Playing vinyl mode with rotating grooves, centred album art and circular drag-to-seek, alongside the square artwork view.
- Safe-drawing insets for status/navigation bars, accessible navigation semantics, larger touch targets and scrollable settings/player screens.
- SONORA Fetch foundation: yt-dlp source analysis, MP3 audio extraction via FFmpeg, foreground download service, progress/cancel notifications and MediaStore scan after download. Files are saved under `Downloads/SONORA`.

## Known limitations

This is not yet release-ready. Storage is SharedPreferences rather than Room; playlists do not support drag reordering; queue restoration, folder browsing, advanced artwork extraction, full listening-time analytics, automated tests and signed release packaging remain future work. SONORA Fetch is an initial audio-only implementation and still needs device/network testing; source sites can change and may reject extraction. Equalizer support, foreground-service notifications, media indexing and background playback vary by device. The vinyl interaction and all system-inset behaviour need physical-device validation.

## Build an installable debug APK

GitHub Actions is **manual-only**. It does not run on pushes or pull requests.

1. Open the repository's Actions tab.
2. Select Android APK.
3. Press Run workflow, choose the main branch, then confirm Run workflow.
4. Wait for a green Success result.
5. Open that run and download the SONORA-debug artifact.
6. Extract the ZIP and install app-debug.apk.

This is a debug build, not a signed Play Store release. See docs/BUILD_AND_TEST.md.

Third-party licensing: SONORA Fetch currently includes the GPL-3.0-licensed `youtubedl-android` and its FFmpeg companion. Read `THIRD_PARTY_NOTICES.md` before distributing builds; GPL obligations may apply to the combined application.

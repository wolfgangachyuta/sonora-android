# SONORA

**Your music, your space.** SONORA is a local-first Android music player with a restrained editorial interface. It indexes audio already available to Android's media library and plays it on-device.

> Development version: 0.2.0 — active development; physical-device verification remains necessary.

## Current implementation

- Kotlin, Jetpack Compose and Material 3 interface with light/dark appearance.
- MediaStore discovery, library browsing, albums, artists, search and favourites.
- Media3 playback queue, mini-player, full-screen Now Playing, seek, shuffle and repeat.
- Persistent favourites, recent-play history and play counts using SharedPreferences.
- Persistent playlists: create, rename, delete, add selected tracks, remove individual tracks and play.
- LRC file import and timed lyric display.
- Native Android Equalizer controls where supported by the device.
- Media3 MediaSessionService and UI MediaController connection for playback outside the activity.

## Known limitations

This is not yet release-ready. Storage is SharedPreferences rather than Room; playlists do not support drag reordering; queue restoration, folder browsing, advanced artwork extraction, full listening-time analytics, automated tests and signed release packaging remain future work. Equalizer support and notification presentation vary by device. Background playback, notification/lock-screen controls, lyrics timing and equalizer operation still require physical-device validation.

## Build an installable debug APK

GitHub Actions is **manual-only**. It does not run on pushes or pull requests.

1. Open the repository's Actions tab.
2. Select Android APK.
3. Press Run workflow, choose the main branch, then confirm Run workflow.
4. Wait for a green Success result.
5. Open that run and download the SONORA-debug artifact.
6. Extract the ZIP and install app-debug.apk.

This is a debug build, not a signed Play Store release. See docs/BUILD_AND_TEST.md.

No open-source license has been declared; treat this repository as all rights reserved.

# SONORA

A thoughtful, local-first Android music player. Your music, your space.

## Current build — 0.2.0
- Native Android app built with Kotlin and Jetpack Compose
- Minimal monochrome interface with sage accents
- Device audio permission and MediaStore library scanning
- Search across track title, artist, and album
- Local playback powered by AndroidX Media3 ExoPlayer
- Queue-based next-track playback and mini-player controls
- Library overview with track, album, and artist counts
- Session favourites (persistent playlists and saved listening data are next)

## Build an APK
Every push to `main` runs the GitHub Actions workflow. Download the `SONORA-debug` artifact from the successful run, extract it, and install the APK on an Android device.

## Stack
Kotlin · Jetpack Compose · Material 3 · AndroidX Media3 · Gradle · GitHub Actions

## Roadmap
1. Persistent library, favourites, playlists, and listening history (Room)
2. Background playback, media notification, lock-screen controls, headset actions
3. Album artwork extraction, artist/album views, queue editor
4. Lyrics (.lrc), equalizer, themes, and listening statistics

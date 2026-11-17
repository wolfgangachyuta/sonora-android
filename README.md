# SONORA

**Your music, your space.**

SONORA is a thoughtful Android music player for the music you already own. Built around a quiet, editorial interface, it puts your local library first.

## Project

- Native Android app built with Kotlin and Jetpack Compose
- Minimum Android version: Android 8.0 (API 26)
- UI direction: minimalist, monochrome, artwork-led
- Automated debug APK builds with GitHub Actions

## Build

The GitHub Actions workflow builds a debug APK on every push to `main`, on pull requests, and when manually triggered.

1. Open the **Actions** tab in this repository.
2. Select **Android APK**.
3. Open the latest successful workflow run.
4. Download the **SONORA-debug** artifact.

The APK is currently a UI foundation. Local media scanning and playback will be added in subsequent milestones.

## Roadmap

- [x] Initial Android project and Compose UI foundation
- [x] Automated debug APK workflow
- [ ] Local audio discovery and folder selection
- [ ] Playback engine and queue
- [ ] Library, albums, artists, and playlists
- [ ] Lyrics, equalizer, and listening history
- [ ] Statistics, themes, and visual polish

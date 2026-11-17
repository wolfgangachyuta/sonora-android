# SONORA

**Your music, your space.** SONORA is a local-first Android music player focused on a calm, deliberate listening experience. It indexes audio already available to Android's media library and plays it on-device; it is not a streaming service.

> Current development version: **0.2.0**  
> Status: early foundation / active development. Features marked as planned are not yet implemented.

## Product principles

- **Local first:** playback is sourced from audio on the device.
- **Quiet interface:** typography, spacing, and artwork take priority over visual clutter.
- **Ownership:** no account or streaming catalogue is required for the core experience.
- **Honest functionality:** documentation distinguishes working features from roadmap items.

## Current functionality

- Native Android application using Kotlin and Jetpack Compose.
- Requests Android audio permission and queries the MediaStore audio catalogue.
- Displays a device library and supports title, artist, and album search.
- Plays local tracks with AndroidX Media3 ExoPlayer.
- Provides a compact mini-player and a dedicated Now Playing screen with seek, play/pause, previous, and next controls.
- Shows basic collection counts for tracks, albums, and artists.
- Offers favourites during the current app session (not yet persisted).

### Known limitations

The current milestone is not a complete production music player. Favourites reset when the app process is recreated. There is no Room persistence, background playback service, media notification, lock-screen integration, editable queue, real album-art rendering, lyrics, equalizer, or listening-history analytics yet. Artwork in the player is currently a designed placeholder. Playback controls are foreground UI controls.

## Technology

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Playback | AndroidX Media3 / ExoPlayer |
| Device catalogue | Android MediaStore |
| Build | Gradle 8.13, Android Gradle Plugin 8.13.2 |
| Runtime | JDK 17 |
| Automation | GitHub Actions |

## Repository layout

```text
.
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/sonora/player/
│           ├── MainActivity.kt
│           ├── NowPlayingScreen.kt
│           └── AudioRepository.kt
├── docs/
│   ├── ARCHITECTURE.md
│   ├── BUILD_AND_TEST.md
│   ├── DESIGN_SYSTEM.md
│   └── ROADMAP.md
├── .github/workflows/android.yml
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

## Build an installable debug APK

Every push to `main`, pull request targeting `main`, or manual workflow dispatch runs the Android build.

1. Open the repository's **Actions** tab.
2. Open the latest successful **Android APK** run.
3. In **Artifacts**, download `SONORA-debug`.
4. Extract the ZIP and install `app-debug.apk` on an Android device. Android may ask you to allow installation from the app used to open the file.
5. Grant audio access when prompted, then scan the library.

The artifact is a debug build intended for testing, not a signed Play Store release. See [Build and test](docs/BUILD_AND_TEST.md).

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — app structure, data flow, playback lifecycle, and permissions.
- [Build and test](docs/BUILD_AND_TEST.md) — local/CI builds, installation, and verification checklist.
- [Design system](docs/DESIGN_SYSTEM.md) — visual principles, colour tokens, typography, and components.
- [Roadmap](docs/ROADMAP.md) — milestone plan and acceptance criteria.

## Development workflow

1. Make a focused change.
2. Build with `gradle --no-daemon assembleDebug` (or use the GitHub Actions workflow).
3. Install and test on a physical Android device.
4. Update documentation when behaviour, permissions, or setup changes.
5. Do not mark a feature complete until it has been built and manually verified.

## License

No open-source license has been declared yet. Until a license is added, the repository should be treated as all rights reserved.

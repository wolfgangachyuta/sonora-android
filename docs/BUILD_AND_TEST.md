# Build and test

## Requirements

- JDK 17
- Android SDK with platform/build tools compatible with compileSdk 36
- Gradle 8.13 (the CI workflow installs this version)
- Android device or emulator running Android 8.0 / API 26 or newer

The Android Gradle Plugin is 8.13.2 and Kotlin is 2.2.20. The project uses the Gradle plugin DSL in the root build file.

## Build a debug APK locally

From the repository root:

```bash
gradle --no-daemon assembleDebug
```

Expected output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a clean rebuild:

```bash
gradle --no-daemon clean assembleDebug
```

## Build with GitHub Actions

Workflow: `.github/workflows/android.yml`

Triggers:
- Push to `main`
- Pull request targeting `main`
- Manual dispatch from the Actions tab

The workflow checks out the repository, installs Temurin JDK 17, configures Gradle 8.13, runs `assembleDebug`, and uploads the APK as `SONORA-debug` for 14 days.

### Download on Android

1. Open the repository in GitHub.
2. Tap **Actions** and select the latest successful **Android APK** run.
3. Find the **Artifacts** section and download `SONORA-debug`.
4. Extract the ZIP archive.
5. Open `app-debug.apk` and follow Android's install prompts.

If Android blocks the install, allow the browser or file manager to install unknown apps for this one-time operation. Only install builds you trust.

## Manual device test checklist

### First launch and permission
- [ ] App launches without crashing.
- [ ] Denying audio access leaves the app navigable and explains how to grant access.
- [ ] Granting audio access allows a scan.
- [ ] A device with no indexed music shows a useful empty state.

### Library and search
- [ ] Track count matches the indexed music available to the app.
- [ ] Titles, artists, albums, and durations render without layout overflow.
- [ ] Search matches title, artist, and album case-insensitively.
- [ ] Empty search results are understandable.
- [ ] Re-scanning refreshes the visible library.

### Playback
- [ ] Selecting a track starts playback.
- [ ] Mini-player shows the selected track and toggles play/pause.
- [ ] Next and previous controls move through the current media-item sequence.
- [ ] Now Playing opens from the mini-player.
- [ ] Seek slider changes playback position.
- [ ] Back navigation returns to Home.
- [ ] Switching tabs while audio is playing does not unexpectedly stop playback.

### Visual and lifecycle
- [ ] Small and large screen sizes remain usable.
- [ ] Long song and artist names truncate rather than overlap controls.
- [ ] App can be backgrounded and resumed during playback (background continuity is not guaranteed by the current architecture).
- [ ] Rotation and process recreation do not leave the UI in a broken state.

## What CI does not prove

A successful `assembleDebug` verifies compilation and APK packaging. It does not verify runtime playback, permissions on every Android version, audio focus, device-specific MediaStore behaviour, accessibility, or visual quality. Those require emulator/physical-device testing.

## Troubleshooting

- **No songs appear:** confirm audio files are visible to Android's media library, grant audio access, then scan again.
- **A file is not listed:** some file types or folders may not be indexed by the device's media provider.
- **APK cannot be installed:** remove an older incompatible debug build and retry; check Android's unknown-app installation permission.
- **Build fails in CI:** open the failed job's logs, identify the first compiler/Gradle error, fix that cause, and rerun the workflow. Do not treat a queued or running job as a successful build.

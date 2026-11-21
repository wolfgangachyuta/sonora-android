# Build and test

## GitHub Actions — automatic CI and APK

Workflow: .github/workflows/android.yml

The workflow runs on pushes to main, pull requests targeting main, and manual workflow_dispatch. Each run executes unit tests, assembles the debug APK and uploads the APK artifact.

1. Push a change to main or open a pull request targeting main; CI starts automatically.
2. For a manual run, open Actions, choose Android CI and APK, then select Run workflow.
3. Wait for unit tests and assembleDebug to complete. Only a green Success run is a successful build.
4. Open the run's Artifacts section and download SONORA-debug.
5. Extract the archive and install app-debug.apk.

## Local build

Requirements: JDK 17, Android SDK compatible with compileSdk 36, Gradle 8.13.

From the repository root run:

    gradle --no-daemon assembleDebug

Output: app/build/outputs/apk/debug/app-debug.apk.

## Device verification checklist

- [ ] First launch, audio permission grant/denial, empty library and rescan.
- [ ] Search, album/artist grouping and favourites persistence after relaunch.
- [ ] Create, rename, delete playlists; add selected tracks; remove and reorder tracks; play playlist.
- [ ] Queue restore after process death; reorder/remove queue items; verify current index and position.
- [ ] Playback, seek, next/previous, shuffle and repeat.
- [ ] Background playback after leaving the app and screen lock.
- [ ] Notification and lock-screen transport controls.
- [ ] Bluetooth/headset controls, audio focus and unplugging headphones.
- [ ] Import a synced LRC file and verify timing against playback.
- [ ] Equalizer presets and band controls on the target device.
- [ ] Optional visualizer permission grant/denial, FFT capture, pause/resume and release on headphone disconnect.
- [ ] Dark/light appearance, large text, TalkBack labels, 48dp touch targets and small screens.
- [ ] Bottom navigation with gesture navigation and three-button system navigation; verify it remains above the system bar.
- [ ] Open the keyboard on Search and Fetch; verify content and navigation are not obscured by the IME.
- [ ] Artwork-derived and fixed accent palettes, queue screen, folder browsing and large-font layouts.
- [ ] SONORA Fetch: analyse an authorised test URL, download MP3, verify progress/ETA notification, cancel from the screen and notification, and confirm the resulting file appears in the local library after refresh.
- [ ] Athenaeum: import multiple EPUB/PDF/TXT/HTML/FB2/MOBI/RTF/Markdown/CBZ documents; verify persisted URI access after relaunch.
- [ ] Athenaeum: EPUB 2/3 spine order, TOC, images/CSS, chapter navigation and saved progress.
- [ ] Athenaeum: PDF page navigation, OCR, bookmarks, annotations, Commonplace Book, TTS and reading-session statistics.
- [ ] Athenaeum: JSON archive, portable ZIP backup/restore with ebook/cover files, CSV export, ISBN lookup and all five home-screen widgets.
- [ ] Athenaeum: verify legacy database migrations from schema versions 1 and 2.

## What CI does not prove

A successful assembleDebug confirms compilation and APK packaging. It does not prove device-specific playback, notification permissions, audio focus, lock-screen controls, equalizer compatibility, lyrics synchronisation, accessibility or visual quality. Fetch also depends on upstream source-site behaviour and network access. Those require physical-device testing.

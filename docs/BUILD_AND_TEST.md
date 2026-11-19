# Build and test

## GitHub Actions — manual only

Workflow: .github/workflows/android.yml

The workflow has only a workflow_dispatch trigger. It does not run on pushes or pull requests.

1. Open the repository on GitHub and select Actions.
2. Choose Android APK.
3. Select Run workflow, choose main, and confirm.
4. Wait for the run to complete. Only a green Success run is a successful build.
5. Open the run's Artifacts section and download SONORA-debug.
6. Extract the archive and install app-debug.apk.

## Local build

Requirements: JDK 17, Android SDK compatible with compileSdk 36, Gradle 8.13.

From the repository root run:

    gradle --no-daemon assembleDebug

Output: app/build/outputs/apk/debug/app-debug.apk.

## Device verification checklist

- [ ] First launch, audio permission grant/denial, empty library and rescan.
- [ ] Search, album/artist grouping and favourites persistence after relaunch.
- [ ] Create, rename, delete playlists; add selected tracks; remove tracks; play playlist.
- [ ] Playback, seek, next/previous, shuffle and repeat.
- [ ] Background playback after leaving the app and screen lock.
- [ ] Notification and lock-screen transport controls.
- [ ] Bluetooth/headset controls, audio focus and unplugging headphones.
- [ ] Import a synced LRC file and verify timing against playback.
- [ ] Equalizer presets and band controls on the target device.
- [ ] Dark/light appearance, large text, TalkBack labels, 48dp touch targets and small screens.
- [ ] Bottom navigation with gesture navigation and three-button system navigation; verify it remains above the system bar.
- [ ] Open the keyboard on Search and Fetch; verify content and navigation are not obscured by the IME.
- [ ] Now Playing vinyl mode: rotation while playing, freeze while paused, drag-to-seek in both directions including across the 12 o’clock boundary, and switch to square artwork.
- [ ] SONORA Fetch: analyse an authorised test URL, download MP3, verify progress/ETA notification, cancel from the screen and notification, and confirm the resulting file appears in the local library after refresh.

## What CI does not prove

A successful assembleDebug confirms compilation and APK packaging. It does not prove device-specific playback, notification permissions, audio focus, lock-screen controls, equalizer compatibility, lyrics synchronisation, accessibility or visual quality. Fetch also depends on upstream source-site behaviour and network access. Those require physical-device testing.

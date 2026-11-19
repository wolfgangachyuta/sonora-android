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
- [ ] Dark/light appearance, large text, accessibility and small screens.

## What CI does not prove

A successful assembleDebug confirms compilation and APK packaging. It does not prove device-specific playback, notification permissions, audio focus, lock-screen controls, equalizer compatibility, lyrics synchronisation, accessibility or visual quality. Those require physical-device testing.

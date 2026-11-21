# Privacy policy — SONORA + Athenaeum

Last updated: 21 November 2026

SONORA and Athenaeum are local-first Android applications. The developer does not operate an account service, advertising network, analytics service or application backend for these apps.

## Information stored on your device

- SONORA reads audio metadata and artwork from the device's media library and stores favourites, playlists, playback queue, listening history and listening-duration statistics in app-private storage.
- Athenaeum stores catalogue records, shelves, bookmarks, annotations, reading progress, OCR text, reading sessions and imported offline dictionary entries in app-private storage.
- Imported books and user-selected cover images are accessed through Android's document-provider URIs. The app retains access where the provider permits persistent URI grants.
- Playback and reading features process content on the device. PDF OCR uses Google ML Kit's on-device text-recognition library.

## Optional network features

Network access is used only when a user invokes a feature that needs it:

- **Book metadata lookup:** the entered ISBN is sent to Google Books to retrieve catalogue metadata.
- **Online dictionary:** the selected word is sent to the dictionary API. Imported offline dictionary packs are queried locally.
- **SONORA Fetch:** the URL entered by the user is processed by yt-dlp and the source service. Only download material the user is authorised to save.
- **WebDAV backup:** when configured by the user, Athenaeum uploads or downloads a portable ZIP archive to the HTTPS WebDAV URL supplied by the user. The username and password/app token are encrypted at rest using Android Keystore. The backup archive itself is not encrypted by Athenaeum; rely on the WebDAV provider's storage security and use a trusted server.

The app does not silently synchronise a library to a developer-operated server.

## Permissions

Depending on Android version and features used, the app may request access to local audio, notifications, microphone input for the optional audio visualizer, and document-provider access to files selected by the user. Microphone permission is optional and is used only by the visualizer.

## Sharing and retention

The developer does not receive the local library, audio files, book files, annotations, reading history or listening history. Information is sent to the optional third-party services described above only when the corresponding user action is invoked. Those services process requests under their own privacy policies and terms.

Local app data remains on the device until removed by the user or cleared by Android. Uninstalling the app removes its app-private data; files selected from external document providers may remain in their original locations.

## Children's privacy

The apps do not knowingly collect personal information from children or any other users. No user account is required.

## Changes and contact

This policy may be updated as features or third-party services change. For questions or to report a privacy issue, contact the project maintainer through the repository: https://github.com/wolfgangachyuta/sonora-android

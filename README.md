# SONORA + Athenaeum

A local-first Android application with two connected experiences:

- **SONORA** — a music library and player for audio already available on the device.
- **Athenaeum** — a personal physical/digital book catalogue, ebook reader and literary archive.

Both experiences currently live in the same Android application/package (com.sonora.player); Athenaeum is opened from SONORA's top bar and from its home-screen widgets.

## SONORA

- Kotlin, Jetpack Compose, Material 3 and Media3.
- MediaStore library scanning, album/artist/folder browsing, search, sorting and refresh.
- Album artwork, metadata editing, favourites and playlists.
- MediaSessionService playback with system media controls, audio focus, headset route handling and lock-screen integration.
- Persistent Room data for favourites, playlists, listening history and the playback queue.
- Editable playback queue and reorderable playlist tracks.
- Seek, shuffle, repeat, playback speed and sleep timer.
- Local LRC import and adjacent LRC discovery, timed lyrics and device equalizer.
- Listening statistics, monthly summaries, light/dark themes and artwork-derived or fixed accent palettes.

## Athenaeum

- Physical, digital and mixed-ownership catalogue entries with metadata, ratings, covers, notes and custom shelves.
- Multi-file import, search, sorting, grid/list views, reading progress and yearly reading goal.
- Reader support currently implemented for PDF, TXT, HTML, FB2, EPUB 2/3, legacy PalmDOC MOBI 6, RTF, Markdown and CBZ.
- EPUB OPF spine ordering, NCX/EPUB3 table of contents, chapter navigation and local image/CSS inlining.
- Bookmarks, manual passage annotations, Commonplace Book, reading-session statistics, PDF OCR and text-to-speech.
- Optional ISBN metadata lookup, JSON archive export/restore, portable ZIP backup/restore with accessible ebook and cover files, and catalogue CSV export.
- Three Android widgets: Continue Reading, library overview and random book.

## Important limitations

This is an active development build, not a release-verified product.

- JSON-only Athenaeum archives preserve catalogue/archive records and URI references. Use the portable ZIP backup to package accessible ebook and cover files; files the app can no longer read are retained as URI references only.
- EPUB support is an offline in-app parser/renderer, not a complete Readium implementation; DRM, some EPUB resources and advanced pagination are not supported.
- MOBI support is limited to legacy PalmDOC-compressed MOBI 6; AZW3/KF8 and CBR are not currently supported.
- Passage annotations are entered manually; true text-selection highlighting and dictionary lookup are not yet implemented.
- Cloud sync, OCR batch indexing, audio visualisation and broader automated UI/integration tests remain outstanding. Initial unit tests cover Markdown, RTF and legacy MOBI utility paths; they have not been run in this session.
- Physical-device verification is still required for background playback, widgets, OCR, TTS, reader formats, accessibility and Android-version differences.
- ISBN lookup uses Google Books when the user explicitly requests a lookup; core libraries and playback remain local-first.

## Build a debug APK

GitHub Actions is **manual-only** (workflow_dispatch). Pushes and pull requests do not start builds.

1. Open the repository's Actions tab.
2. Select **Android APK**.
3. Press **Run workflow** for the main branch.
4. Wait for a successful run.
5. Download and extract the **SONORA-debug** artifact.
6. Install app-debug.apk on an Android device.

The debug APK is not a signed Play Store release. See docs/BUILD_AND_TEST.md.

## Third-party notices

SONORA Fetch includes GPL-3.0-licensed yt-dlp Android and FFmpeg components. On-device OCR uses Google ML Kit Text Recognition. Review THIRD_PARTY_NOTICES.md and the relevant upstream licences before redistribution.

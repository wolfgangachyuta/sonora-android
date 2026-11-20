# Product roadmap — SONORA + Athenaeum

The repository currently ships one Android package containing two connected experiences: SONORA (music player) and Athenaeum (personal library and reader). A feature is not considered release-verified until the debug build succeeds and it has been exercised on supported Android devices. Source implementation and runtime verification are tracked separately.

## SONORA — music player

### Playback and system integration
- [x] Kotlin + Jetpack Compose application and MediaStore audio discovery.
- [x] Media3 ExoPlayer playback, mini-player, full-screen Now Playing, seek, previous/next.
- [x] MediaSessionService + MediaController playback ownership outside the activity.
- [x] System media metadata and album-art URI attached to queue items.
- [x] Audio focus handling, headset/noisy-route pause handling, local wake mode.
- [x] Playback notification and lock-screen integration through MediaSessionService.
- [x] Queue persistence in Room, including current index and playback position.
- [x] Editable queue with move-up/down, remove, and play-from-position.
- [x] Playback speed, shuffle, repeat, and sleep timer.
- [ ] Verify notification, lock-screen, Bluetooth/headset and process-death behaviour on physical devices.

### Library and personalisation
- [x] Tracks, albums, artists, folders, search, favourites, refresh and sorting.
- [x] MediaStore album artwork and embedded audio tag editing.
- [x] Persistent favourites, playlists, playlist tracks, listening history and queue in Room.
- [x] Playlist creation, rename, delete, add/remove tracks and track reordering.
- [x] Local LRC import, adjacent LRC discovery and timed lyrics.
- [x] System equalizer where available.
- [x] Listening statistics, artist/track rotation, monthly and rolling 30-day summaries.
- [x] Light/dark appearance and artwork-derived accent with fixed user-selectable palettes.
- [x] Optional live FFT audio visualiser using Android Visualizer and a user-requested RECORD_AUDIO permission.
- [ ] Device-specific visualiser compatibility and permission-flow verification.
- [ ] More complete play-duration analytics and richer monthly reports.
- [x] Initial unit tests for Markdown, RTF and legacy MOBI utility paths.
- [ ] Broader unit/instrumentation/UI tests and accessibility audit.

### Release readiness
- [ ] Device matrix testing (Android 8 through current Android).
- [ ] Small-screen, tablet and large-font verification.
- [ ] Signed release configuration, versioning and distribution checklist.
- [ ] Privacy policy and store listing before public distribution.

## Athenaeum — personal library and reader

### Catalogue and collections
- [x] Physical, digital and mixed-ownership catalogue records.
- [x] Metadata editor, cover picker, ratings, reading status, favourites and notes.
- [x] Multiple-file import, persistent URI permissions, search and sorting.
- [x] Custom shelves and book-to-shelf assignments.
- [x] Continue Reading, progress, yearly goal, grid/list layouts and deletion.
- [x] Optional ISBN metadata lookup using Google Books.
- [x] Dedicated Athenaeum launcher entry and icon, separate from the SONORA launcher entry within the same APK.
- [x] EPUB package metadata and cover-image extraction during import.
- [x] Five Android home-screen widgets: Continue Reading, library overview, random pick, favourites and a custom shelf.

### Reader
- [x] PDF, TXT, HTML, FB2, EPUB 2/3 (OPF manifest/spine and NCX/EPUB3 TOC), legacy PalmDOC MOBI 6, RTF, Markdown and CBZ.
- [x] EPUB chapter navigation and offline inlining of local chapter images and CSS.
- [x] PDF page navigation, reader themes, text sizing, bookmarks and saved progress.
- [x] Manual passage annotations and notes, Commonplace Book and reading-session statistics.
- [x] On-device OCR for scanned PDF pages and Android text-to-speech.
- [ ] AZW/AZW3/KF8, CBR and broader comic/archive format support.
- [ ] Full EPUB resource/DRM compatibility and a production-grade Readium-based rendering engine.
- [x] Capture selected WebView passages and re-render saved highlights in EPUB/HTML reader content.
- [ ] Annotation colour choices, PDF-page highlight overlays and exportable highlight collections.
- [x] On-demand online dictionary lookup for a selected single word.
- [ ] Offline dictionary packs, robust pagination and typography controls across every format.
- [ ] Accessibility audit and screen-reader/large-font verification.

### Archive, portability and future services
- [x] Room persistence and versioned migrations for books, shelves, bookmarks, annotations, commonplace entries and reading sessions.
- [x] JSON archive export/restore and catalogue CSV export.
- [x] Portable ZIP backup/restore packages accessible ebook and cover files with the catalogue/archive database; JSON-only backup remains metadata/URI based.
- [ ] OCR batch workflow and searchable OCR text archive.
- [ ] Optional cloud sync with conflict handling and explicit user-controlled provider setup.
- [ ] More advanced search, quotation/tag indexing and reading timelines.
- [ ] Additional configurable widgets (favourites, custom shelf and compact formats).

## Build and verification policy

GitHub Actions remains manual-only (workflow_dispatch). Pushes and pull requests must not trigger builds. Do not run a build unless explicitly requested. Source changes in this roadmap are not a claim that a successful build or device test has occurred.

# SONORA roadmap

This roadmap separates shipped foundations from planned work. A milestone is complete only after code is merged, CI passes, and the feature has been exercised on an Android device.

## Phase 1 — Local playback foundation

**Status: implemented; initial device verification still needed**

- [x] Kotlin + Jetpack Compose Android app scaffold
- [x] MediaStore-based audio discovery
- [x] Permission request for local audio access
- [x] Track library and basic search
- [x] Media3 ExoPlayer playback sequence
- [x] Mini-player and basic transport actions
- [ ] Physical-device verification across supported Android versions

## Phase 2 — Listening experience and project documentation

**Status: implementation submitted; CI/device verification pending**

- [x] Dedicated Now Playing screen
- [x] Playback progress and seek interaction
- [x] Consistent visual direction and reusable screen components
- [x] Architecture, build/test, design-system, and roadmap documentation
- [ ] Verify seek, previous/next, and layout on a physical device
- [ ] Review accessibility and small-screen behaviour

## Phase 3 — Durable library and playback service

**Status: underway — service/session groundwork and persistent favourites committed; controller migration still pending**

- [x] Add Media3 Session dependency
- [x] Declare media playback service and foreground-service permissions
- [x] Create MediaSessionService host with ExoPlayer + MediaSession
- [x] Persist favourite IDs across restarts (SharedPreferences interim storage)
- [ ] Wire UI to MediaController and migrate active playback to service
- [ ] Add media notification and lock-screen controls
- [ ] Handle audio focus, noisy-route events, headset actions, and service lifecycle
- [ ] Move persistence to Room and create playlists
- [ ] Persist and restore the queue
- [ ] Add robust sorting and library refresh

## Phase 4 — Rich music library

- [ ] Introduce Room for favourites, playlists, play history, and preferences
- [ ] Move playback into MediaSessionService
- [ ] Add media notification and lock-screen controls
- [ ] Handle audio focus, noisy-route events, headset actions, and service lifecycle
- [ ] Persist and restore the queue
- [ ] Add robust sorting and library refresh

## Phase 4 — Rich music library

- [ ] Real album artwork from MediaStore and embedded metadata where available
- [ ] Dedicated Albums and Artists browsing
- [ ] Folder browsing
- [ ] Editable queue and play-next actions
- [ ] Playlist creation, editing, and reordering
- [ ] Better handling for compilation albums and unknown metadata

## Phase 5 — Personal listening tools

- [ ] Local `.lrc` lyric discovery and timed display
- [ ] Equalizer integration where supported by the platform/device
- [ ] Shuffle and repeat modes with visible state
- [ ] Listening history and play counts
- [ ] Listening-time and artist/album statistics
- [ ] Monthly listening summaries

## Phase 6 — Polish and release readiness

- [ ] Complete light and dark themes
- [ ] Dynamic accent derived from artwork, with user override
- [ ] Motion and visualisation settings
- [ ] Accessibility audit and large-font testing
- [ ] Unit and UI test coverage
- [ ] Signed release build and versioning process
- [ ] Privacy policy and release checklist if distribution requires them

## Priority order

The next highest-value engineering work after this UI milestone is a proper MediaSessionService. It removes the current dependency on a visible screen for reliable background playback and provides the system integration expected of a music player. Persistence should follow so favourites, playlists, and history survive restarts.

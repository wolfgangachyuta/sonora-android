# SONORA design system

## Direction

SONORA uses a restrained editorial visual language: warm paper surfaces, near-black typography, muted sage accents, generous whitespace, and compact uppercase labels. The interface should feel composed and tactile rather than like a dense settings dashboard.

## Core palette

| Token | Hex | Intended use |
|---|---|---|
| Ink | `#171817` | Primary text, dark surfaces, primary controls |
| Paper | `#F4F3EF` | Main light background |
| Card | `#E8E7E1` | Secondary surfaces and input backgrounds |
| Muted | `#85857E` | Supporting labels and metadata |
| Sage | `#B8D2C2` | Accent, selected controls, contrast against Ink |
| Warm accent | `#D8C4A9` | Artwork gradient highlight |

The palette is currently implemented as local Compose colour constants. A later theme milestone should centralise these as Material 3 light/dark colour schemes and add contrast checks.

## Typography

- Brand wordmark: bold, spaced uppercase.
- Screen titles: medium weight, large scale, slightly tight tracking.
- Section labels: small uppercase text with increased letter spacing.
- Track title: semibold and visually dominant over metadata.
- Metadata: smaller size and muted colour.
- Avoid long blocks of all-caps copy.

## Layout and shape

- Use a consistent horizontal page inset (currently 22 dp on the main shell).
- Prefer 12–18 dp corner radii for cards and compact controls.
- Reserve larger rounded geometry for immersive artwork.
- Keep section spacing generous and align labels with the content they describe.
- Ensure controls have touch targets suitable for one-handed use.
- Long metadata should ellipsize; it must not push transport controls off-screen.

## Components

### Artwork tile
Square artwork treatment with rounded corners. The current library uses a gradient placeholder; actual album art is a planned implementation.

### Track row
Artwork thumbnail, title, artist/album metadata, and duration. Active track title uses the sage/green family for a restrained state cue.

### Mini-player
A compact card anchored above bottom navigation. Tapping the card opens Now Playing; play/pause and next remain directly accessible.

### Now Playing
A focused screen with back navigation, large square artwork treatment, track metadata, a seek slider, elapsed/total time, and previous/play-pause/next controls.

### Empty state
A clear statement, one short explanation, and a single action. Avoid empty screens that provide no next step.

## Interaction principles

- Animate screen changes subtly; transitions should not delay interaction.
- Keep playback actions immediate and clearly labelled for accessibility.
- Use descriptive content descriptions for icon-only controls.
- Preserve a stable layout when track titles or metadata change.
- Provide meaningful permission-denied and no-results states.

## Accessibility review still required

- Verify text contrast against WCAG guidance.
- Test system font scaling and display size.
- Confirm all icon-only controls have content descriptions.
- Check TalkBack order and slider announcements.
- Review touch targets and reduced-motion behaviour.
- Add a proper dark theme before claiming theme support.

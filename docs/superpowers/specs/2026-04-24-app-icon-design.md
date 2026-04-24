# App Icon Design — SoundBoard

## Summary

Replace the default Android launcher icon with a custom flat/minimalist icon representing an audio equalizer. Five vertical bars of varying heights on a solid purple background.

## Visual Design

**Style:** Flat, minimalist — no shadows, no gradients, no outlines.

**Symbol:** Five vertical equalizer bars aligned to the bottom, alternating in height to suggest audio activity.

**Colors:**
- Background: `#6200EE` (Purple 500)
- Bars (odd positions): `#BB86FC` (Purple 200)
- Bars (even positions): `#EAB8FF` (lighter lavender)

**Bar dimensions** (within 108×108dp viewport, bars aligned to bottom):

| Bar | Height | Width | Color |
|-----|--------|-------|-------|
| 1 (left) | 26dp | 10dp | #BB86FC |
| 2 | 42dp | 10dp | #EAB8FF |
| 3 (center) | 34dp | 10dp | #BB86FC |
| 4 | 50dp | 10dp | #EAB8FF |
| 5 (right) | 20dp | 10dp | #BB86FC |

Bars have `4dp` rounded top corners. Gap between bars: `6dp`. The group is centered horizontally in the 108×108dp canvas with `18dp` padding on each side.

## Android Adaptive Icon

The icon uses the existing adaptive icon structure (`mipmap-anydpi/ic_launcher.xml`), which already defines `background`, `foreground`, and `monochrome` layers. Only the two drawable files need replacing:

- `ic_launcher_background.xml` — solid fill `#6200EE`, no grid lines
- `ic_launcher_foreground.xml` — the five equalizer bars as a VectorDrawable

The `monochrome` layer reuses `ic_launcher_foreground.xml`; Android tints it automatically on API 33+.

## Files Changed

| File | Change |
|------|--------|
| `app/src/main/res/drawable/ic_launcher_background.xml` | Replace with solid `#6200EE` fill |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Replace with 5-bar equalizer VectorDrawable |

The `.webp` raster files in `mipmap-*` are not used — `minSdk = 30` means all supported devices render adaptive icons. No changes needed to those files.

## Out of Scope

- Notification icon (uses a separate resource)
- Splash screen branding
- Any UI changes inside the app

# Pause Audio — Design Spec

**Date:** 2026-04-04
**Status:** Approved

## Overview

Add per-button pause/resume and a global pause-all action to the SoundBoard. Each sound button reflects its playback state visually and its tap action adapts accordingly.

## Interface Contract

`SoundPoolPlayer` gains three new methods:

```kotlin
interface SoundPoolPlayer {
    fun play(filePath: String)   // new-play or resume, manager decides
    fun pause(filePath: String)
    fun pauseAll()
    fun release()
}
```

- `play(filePath)` — starts playback or resumes if paused; manager decides internally
- `pause(filePath)` — pauses the player associated with that path, preserving position
- `pauseAll()` — pauses every active player in the pool

## SoundPoolManager

Replace the anonymous pool `MutableList<MediaPlayer>` with a keyed map:

```kotlin
activePlayers: MutableMap<String, MediaPlayer>  // filePath → player
```

### play() behaviour
1. If `filePath` already exists in `activePlayers`:
   - Player is paused → call `resume(filePath)`
   - Player is playing → no-op
2. Otherwise → acquire a free or new player, assign it to `filePath`, `prepareAsync()` → `start()`

### pause(filePath)
- Look up player in `activePlayers`, call `player.pause()`

### resume(filePath)
- Look up player in `activePlayers`, call `player.start()`

### pauseAll()
- Iterate `activePlayers.values`, call `pause()` on each playing player

### Player acquisition (max 8)
- Prefer a player not present in `activePlayers` values
- If all slots are taken, stop and evict the oldest entry

## ViewModel

```kotlin
private val _playingPaths = MutableStateFlow<Set<String>>(emptySet())
val playingPaths: StateFlow<Set<String>> = _playingPaths.asStateFlow()

fun playSound(filePath: String)    // soundPoolPlayer.play(); adds to set (handles new-play and resume)
fun pauseSound(filePath: String)   // soundPoolPlayer.pause(); removes from set
fun pauseAll()                     // soundPoolPlayer.pauseAll(); clears set
```

`SoundBoardScreen` decides the action on tap:
```kotlin
if (filePath in playingPaths) pauseSound(filePath) else playSound(filePath)
```

`playSound()` internally handles both new-play and resume via the manager — no need for a separate `resumeSound()` call from the UI.

## UI

### SoundButtonItem states

| State | Background | Icon overlay |
|-------|-----------|--------------|
| Playing | `primaryContainer` | ▐▐ (pause) top-right corner |
| Stopped / Paused | `secondaryContainer` | — |

### Pause All button

- Shown in `TopAppBar` as an `IconButton` with pause icon
- Visible only when `playingPaths.isNotEmpty()`
- Calls `viewModel.pauseAll()`

## Error handling

- All `MediaPlayer` calls wrapped in `try/catch`; errors silently reset the player
- If `filePath` not found in `activePlayers` on pause/resume → no-op

## Testing

- `FakeSoundPoolPlayer` gains `pause()`, `pauseAll()` implementations that update tracked sets
- ViewModel tests verify `playingPaths` state transitions for: play, pause, resume, pauseAll
- No UI tests required for this change

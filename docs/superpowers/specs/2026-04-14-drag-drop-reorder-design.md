# Drag & Drop Reordering of Samples within a Group

**Date:** 2026-04-14  
**Status:** Approved

## Overview

Allow users to reorder sound buttons within a group by long-pressing a button and dragging it to a new position in the grid. A "⋮" menu button on each card replaces the previous long-press context menu.

## Interaction Design

| Gesture | Action |
|---------|--------|
| Tap | Play / pause the sample |
| Long-press | Initiate drag & drop reorder |
| Tap "⋮" | Open context menu (Rename, Delete) |

The "⋮" (`MoreVert`) icon is always visible in the top-right corner of each `SoundButtonItem`.

## Architecture

### Layer 1 — Data (`core:database`, `core:data`)

**`SoundButtonDao`** — add a new query to update a single button's position:

```kotlin
@Query("UPDATE sound_buttons SET position = :position WHERE id = :id")
suspend fun updatePosition(id: Long, position: Int)
```

**`SoundButtonRepository`** — add reorder method to interface:

```kotlin
suspend fun reorderButtons(buttons: List<SoundButton>)
```

**`SoundButtonRepositoryImpl`** — implement by iterating the reordered list and calling `updatePosition` for each entry:

```kotlin
override suspend fun reorderButtons(buttons: List<SoundButton>) {
    buttons.forEach { soundButtonDao.updatePosition(it.id, it.position) }
}
```

No Room schema migration is needed — `position: Int` already exists in `SoundButtonEntity`.

### Layer 2 — ViewModel (`feature:soundboard:impl`)

**`GroupDetailViewModel`** — add `reorderButtons(from: Int, to: Int)`:

```kotlin
fun reorderButtons(from: Int, to: Int) {
    val current = group.value?.buttons ?: return
    val reordered = current.toMutableList()
        .apply { add(to, removeAt(from)) }
        .mapIndexed { index, btn -> btn.copy(position = index) }
    viewModelScope.launch { soundButtonRepository.reorderButtons(reordered) }
}
```

Receives 0-based grid indices from the UI, rebuilds the list with updated `position` values, and persists via the repository. The existing `group` StateFlow refreshes automatically from Room.

### Layer 3 — UI (`GroupDetailScreen.kt`)

**New dependency** — add `sh.calvin.reorderable` v2.4.3 to `libs.versions.toml` and to the `feature:soundboard:impl` module's `build.gradle.kts`.

**`ButtonGrid`** — migrate from manual `Row + chunked(3)` to `LazyVerticalGrid(columns = Fixed(3))`:

```kotlin
val reorderState = rememberReorderableLazyGridState(
    onMove = { from, to -> viewModel.reorderButtons(from.index, to.index) }
)

LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    state = reorderState.gridState,
    ...
) {
    items(buttons, key = { it.id }) { button ->
        ReorderableItem(reorderState, key = button.id) { isDragging ->
            SoundButtonItem(
                button = button,
                isDragging = isDragging,
                reorderableScope = this,
                ...
            )
        }
    }
    item { AddSoundButton(...) }
}
```

**`SoundButtonItem`** — changes:
- Receives `ReorderableItemScope` and `isDragging: Boolean`
- Long-press gesture on the `Card` uses `detectReorderAfterLongPress` from the scope
- Adds a visible `IconButton(MoreVert)` in the top-right corner that opens the `DropdownMenu`
- Elevates the card shadow when `isDragging == true` for visual feedback
- Removes the previous `combinedClickable(onLongClick = { showMenu = true })` handler

## Dependencies

```toml
# gradle/libs.versions.toml
[versions]
reorderable = "2.4.3"

[libraries]
reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }
```

```kotlin
// feature/soundboard/impl/build.gradle.kts
implementation(libs.reorderable)
```

## Files Changed

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add `reorderable` version + library entry |
| `feature/soundboard/impl/build.gradle.kts` | Add reorderable dependency |
| `core/database/.../SoundButtonDao.kt` | Add `updatePosition` query |
| `core/data/.../SoundButtonRepository.kt` | Add `reorderButtons` to interface |
| `core/data/.../SoundButtonRepositoryImpl.kt` | Implement `reorderButtons` |
| `core/data/.../FakeSoundButtonDao.kt` | Add `updatePosition` stub for tests |
| `feature/soundboard/impl/.../GroupDetailViewModel.kt` | Add `reorderButtons(from, to)` |
| `feature/soundboard/impl/.../GroupDetailScreen.kt` | Migrate to `LazyVerticalGrid` + reorderable, add "⋮" button |
| `feature/soundboard/impl/test/.../GroupDetailViewModelTest.kt` | Add reorder test |

## Out of Scope

- Reordering groups on the main `SoundBoardScreen` (separate feature)
- Drag between groups
- Undo/redo of reorder operations

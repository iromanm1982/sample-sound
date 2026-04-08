# Rename Sample — Design Spec

**Date:** 2026-04-08

## Overview

Allow the user to rename an existing `SoundButton` (sample) from the soundboard. The action is accessed via the existing long-press `DropdownMenu` on each button.

## User Flow

1. User long-presses a `SoundButtonItem` → `DropdownMenu` appears.
2. User taps "Renombrar" → dialog appears pre-filled with the current label.
3. User edits the name and taps "Guardar" → button label is updated in Room and the UI reacts via the existing Flow.
4. Tapping "Cancelar" or dismissing the dialog makes no change.

## Architecture

### `SoundButtonDao` (core/database)
Add a new query:
```kotlin
@Query("UPDATE sound_buttons SET label = :label WHERE id = :id")
suspend fun updateLabel(id: Long, label: String)
```

### `SoundButtonRepository` (core/data)
Add method to the interface:
```kotlin
suspend fun renameButton(id: Long, newLabel: String)
```

### `SoundButtonRepositoryImpl` (core/data)
Implement by delegating to the DAO:
```kotlin
override suspend fun renameButton(id: Long, newLabel: String) {
    soundButtonDao.updateLabel(id, newLabel.trim())
}
```

### `SoundBoardViewModel` (feature/soundboard/impl)
Add:
```kotlin
fun renameButton(id: Long, newLabel: String) {
    viewModelScope.launch { soundButtonRepository.renameButton(id, newLabel) }
}
```

### `SoundBoardScreen` (feature/soundboard/impl)

**`SoundButtonItem`** — changes:
- Add `onRename: (Long, String) -> Unit` parameter.
- Add `showRenameDialog: Boolean` state variable (alongside existing `showMenu` and `showConfirmDialog`).
- Add "Renombrar" `DropdownMenuItem` in the existing menu; on click: close menu, set `showRenameDialog = true`.
- Add a `RenameDialog` composable (private) showing an `OutlinedTextField` pre-filled with `button.label`; confirm calls `onRename(button.id, newLabel)`.

**`ButtonGrid`** — add `onRenameButton: (Long, String) -> Unit` parameter, thread down to `SoundButtonItem`.

**`GroupCard`** — add `onRenameButton: (Long, String) -> Unit`, thread down to `ButtonGrid`.

**`GroupList`** — add `onRenameButton: (Long, String) -> Unit`, thread down to `GroupCard`.

**`SoundBoardScreen`** — pass `onRenameButton = { id, label -> viewModel.renameButton(id, label) }` to `GroupList`.

## Error Handling

No validation beyond blank-check (confirm button disabled when field is blank). The label is trimmed before saving. No other error cases: Room updates are fire-and-forget within `viewModelScope`.

## Testing

- Unit test in `SoundBoardViewModelTest`: verify `renameButton` calls `soundButtonRepository.renameButton` with correct args.
- Unit test in `SoundButtonRepositoryImplTest`: verify `renameButton` calls `soundButtonDao.updateLabel`.

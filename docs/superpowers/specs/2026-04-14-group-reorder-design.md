# Group Reorder via Long Press — Design Spec

**Date:** 2026-04-14
**Status:** Approved

## Summary

Allow the user to reorder groups in `SoundBoardScreen` by long-pressing anywhere on a `GroupCard` and dragging it to the desired position. The new order is persisted in Room immediately on drop.

## Constraints & Decisions

- Long press on the card body activates drag — no drag handle icon.
- Elevated card (8 dp) while dragging; default elevation (1 dp) otherwise.
- Persist order immediately on drop (same behavior as button reorder in `GroupDetailScreen`).
- Uses the existing `sh.calvin.reorderable` library already in the project.

## Files Changed

| File | Change |
|------|--------|
| `core/database/GroupDao.kt` | Add `updatePosition(id, position)` query |
| `core/data/GroupRepository.kt` | Add `suspend fun reorderGroups(groups: List<Group>)` |
| `core/data/GroupRepositoryImpl.kt` | Implement `reorderGroups` with `database.withTransaction`; inject `AppDatabase` |
| `feature/soundboard/impl/.../SoundBoardViewModel.kt` | Add `reorderGroups(from: Int, to: Int)` |
| `feature/soundboard/impl/.../SoundBoardScreen.kt` | Convert `GroupList` to `ReorderableLazyColumn`; update `GroupCard` signature |

## Layer-by-Layer Design

### 1. Database — `GroupDao`

Add a single query, identical in shape to `SoundButtonDao.updatePosition`:

```kotlin
@Query("UPDATE groups SET position = :position WHERE id = :id")
suspend fun updatePosition(id: Long, position: Int)
```

### 2. Data — `GroupRepository` + `GroupRepositoryImpl`

**Interface:**
```kotlin
suspend fun reorderGroups(groups: List<Group>)
```

**Implementation** (mirrors `SoundButtonRepositoryImpl.reorderButtons`):
```kotlin
override suspend fun reorderGroups(groups: List<Group>) {
    database.withTransaction {
        groups.forEach { groupDao.updatePosition(it.id, it.position) }
    }
}
```

`AppDatabase` must be injected into `GroupRepositoryImpl` (add constructor parameter).

### 3. ViewModel — `SoundBoardViewModel`

Add `reorderGroups`, mirroring `GroupDetailViewModel.reorderButtons`:

```kotlin
fun reorderGroups(from: Int, to: Int) {
    val current = groups.value
    val reordered = current.toMutableList()
        .apply { add(to, removeAt(from)) }
        .mapIndexed { index, grp -> grp.copy(position = index) }
    viewModelScope.launch { groupRepository.reorderGroups(reordered) }
}
```

No new StateFlow needed — the updated list flows back from Room via the existing `getGroupsWithButtons()` flow.

### 4. UI — `SoundBoardScreen`

**`GroupList`** receives a new `onReorder: (Int, Int) -> Unit` parameter and switches to the reorderable pattern:

```kotlin
val lazyListState = rememberLazyListState()
val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
    onReorder(from.index, to.index)
}

LazyColumn(state = lazyListState, ...) {
    items(groups, key = { it.id }) { group ->
        ReorderableItem(reorderState, key = group.id) { isDragging ->
            GroupCard(
                ...,
                isDragging = isDragging,
                dragModifier = Modifier.longPressDraggableHandle()
            )
        }
    }
}
```

**`GroupCard`** gains two new parameters:
- `isDragging: Boolean` — drives elevation (`if (isDragging) 8.dp else 1.dp`)
- `dragModifier: Modifier` — chained onto the `Card` modifier to capture the long-press gesture

**`SoundBoardScreen`** passes:
```kotlin
onReorder = { from, to -> viewModel.reorderGroups(from, to) }
```

No changes to the context menu, rename dialog, delete dialog, or any other existing logic.

## Out of Scope

- Animated reorder persistence (optimistic UI — Room update is the source of truth)
- Haptic feedback on drag start
- Accessibility / TalkBack reorder support

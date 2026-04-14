# Drag & Drop Reordering of Samples Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to reorder sound buttons within a group by long-pressing and dragging within the existing 3-column grid; a "⋮" button replaces the previous long-press context menu.

**Architecture:** Add `updatePosition` + `reorderButtons` to the DAO/repository layers (field already exists in schema), expose `reorderButtons(from, to)` in the ViewModel, and migrate `ButtonGrid` from a manual `Row+chunked(3)` to a `LazyVerticalGrid` powered by `sh.calvin.reorderable`.

**Tech Stack:** Kotlin, Jetpack Compose, Room, `sh.calvin.reorderable:reorderable:2.4.3`

---

## File Map

| File | Action |
|------|--------|
| `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt` | Add `updatePosition` query |
| `core/data/src/test/java/org/role/samples_button/core/data/FakeSoundButtonDao.kt` | Add `updatePosition` stub |
| `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt` | Add `reorderButtons` to interface |
| `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepositoryImpl.kt` | Implement `reorderButtons` |
| `core/data/src/test/java/org/role/samples_button/core/data/SoundButtonRepositoryImplTest.kt` | Add reorder test |
| `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt` | Update `FakeSoundButtonRepository` + `FakeGroupRepository` |
| `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModel.kt` | Add `reorderButtons(from, to)` |
| `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModelTest.kt` | Add reorder test |
| `gradle/libs.versions.toml` | Add `reorderable` version + library entry |
| `feature/soundboard/impl/build.gradle.kts` | Add reorderable dependency |
| `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailScreen.kt` | Migrate to `LazyVerticalGrid`, add drag, add "⋮" button |

---

## Task 1: Add `updatePosition` to DAO and fake

**Files:**
- Modify: `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt`
- Modify: `core/data/src/test/java/org/role/samples_button/core/data/FakeSoundButtonDao.kt`

- [ ] **Step 1: Add `updatePosition` to `SoundButtonDao`**

Replace the contents of `SoundButtonDao.kt`:

```kotlin
package org.role.samples_button.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundButtonDao {
    @Query("SELECT * FROM sound_buttons WHERE groupId = :groupId ORDER BY position ASC")
    fun getByGroupId(groupId: Long): Flow<List<SoundButtonEntity>>

    @Insert
    suspend fun insert(button: SoundButtonEntity): Long

    @Query("DELETE FROM sound_buttons WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sound_buttons SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String)

    @Query("UPDATE sound_buttons SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
}
```

- [ ] **Step 2: Add `updatePosition` stub to `FakeSoundButtonDao`**

Add the override to `FakeSoundButtonDao.kt` (after `updateLabel`):

```kotlin
override suspend fun updatePosition(id: Long, position: Int) {
    map.forEach { (groupId, list) ->
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(position = position)
            flowFor(groupId).value = list.toList()
        }
    }
}
```

- [ ] **Step 3: Run existing tests to verify nothing is broken**

```
./gradlew :core:data:test
```

Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt
git add core/data/src/test/java/org/role/samples_button/core/data/FakeSoundButtonDao.kt
git commit -m "feat: add updatePosition query to SoundButtonDao"
```

---

## Task 2: Add `reorderButtons` to repository layer

**Files:**
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt`
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepositoryImpl.kt`
- Modify: `core/data/src/test/java/org/role/samples_button/core/data/SoundButtonRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `SoundButtonRepositoryImplTest.kt` (inside the class body, after the existing tests):

```kotlin
@Test
fun `reorderButtons updates position for each button in dao`() = runTest {
    val dao = FakeSoundButtonDao()
    val repo = SoundButtonRepositoryImpl(dao)
    repo.addButton("A", "/a.mp3", 1L)
    repo.addButton("B", "/b.mp3", 1L)
    repo.addButton("C", "/c.mp3", 1L)
    val original = dao.getByGroupId(1L).first()
    // Simulate moving first item to last: [A,B,C] -> [B,C,A]
    val reordered = listOf(
        org.role.samples_button.core.model.SoundButton(
            id = original[1].id, label = "B", filePath = "/b.mp3", groupId = 1L, position = 0
        ),
        org.role.samples_button.core.model.SoundButton(
            id = original[2].id, label = "C", filePath = "/c.mp3", groupId = 1L, position = 1
        ),
        org.role.samples_button.core.model.SoundButton(
            id = original[0].id, label = "A", filePath = "/a.mp3", groupId = 1L, position = 2
        ),
    )

    repo.reorderButtons(reordered)

    val updated = dao.getByGroupId(1L).first().sortedBy { it.position }
    assertEquals(original[1].id, updated[0].id)
    assertEquals(0, updated[0].position)
    assertEquals(original[2].id, updated[1].id)
    assertEquals(1, updated[1].position)
    assertEquals(original[0].id, updated[2].id)
    assertEquals(2, updated[2].position)
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```
./gradlew :core:data:test --tests "*.SoundButtonRepositoryImplTest.reorderButtons*"
```

Expected: FAIL — compilation error "Unresolved reference: reorderButtons".

- [ ] **Step 3: Add `reorderButtons` to `SoundButtonRepository` interface**

Replace the contents of `SoundButtonRepository.kt`:

```kotlin
package org.role.samples_button.core.data

import org.role.samples_button.core.model.SoundButton

interface SoundButtonRepository {
    suspend fun addButton(label: String, filePath: String, groupId: Long)
    suspend fun deleteButton(id: Long)
    suspend fun renameButton(id: Long, newLabel: String)
    suspend fun reorderButtons(buttons: List<SoundButton>)
}
```

- [ ] **Step 4: Add `reorderButtons` to `FakeSoundButtonRepository` in `SoundBoardViewModelTest.kt`**

`FakeSoundButtonRepository` is defined at the bottom of `SoundBoardViewModelTest.kt`. Replace the class with:

```kotlin
class FakeSoundButtonRepository : SoundButtonRepository {
    val deletedIds = mutableListOf<Long>()
    val renamedButtons = mutableListOf<Pair<Long, String>>()
    val reorderedLists = mutableListOf<List<org.role.samples_button.core.model.SoundButton>>()
    override suspend fun addButton(label: String, filePath: String, groupId: Long) = Unit
    override suspend fun deleteButton(id: Long) { deletedIds.add(id) }
    override suspend fun renameButton(id: Long, newLabel: String) { renamedButtons.add(id to newLabel) }
    override suspend fun reorderButtons(buttons: List<org.role.samples_button.core.model.SoundButton>) {
        reorderedLists.add(buttons)
    }
}
```

- [ ] **Step 5: Implement `reorderButtons` in `SoundButtonRepositoryImpl`**

Add this import at the top of `SoundButtonRepositoryImpl.kt` (after the existing imports):

```kotlin
import org.role.samples_button.core.model.SoundButton
```

Add the method after `renameButton`:

```kotlin
override suspend fun reorderButtons(buttons: List<SoundButton>) {
    buttons.forEach { soundButtonDao.updatePosition(it.id, it.position) }
}
```

- [ ] **Step 6: Run the tests to confirm they pass**

```
./gradlew :core:data:test
```

Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt
git add core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepositoryImpl.kt
git add core/data/src/test/java/org/role/samples_button/core/data/SoundButtonRepositoryImplTest.kt
git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt
git commit -m "feat: add reorderButtons to SoundButtonRepository"
```

---

## Task 3: Add `reorderButtons(from, to)` to ViewModel

**Files:**
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModel.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModelTest.kt`

- [ ] **Step 1: Add `seedGroups` helper to `FakeGroupRepository`**

`FakeGroupRepository` is defined in `SoundBoardViewModelTest.kt`. Make two changes:

Change `private val _groups` to `val _groups` (remove the `private` modifier):

```kotlin
val _groups = MutableStateFlow<List<Group>>(emptyList())
```

Add `seedGroups` after the `reorderButtons` override:

```kotlin
fun seedGroups(groups: List<Group>) {
    _groups.value = groups
}
```

- [ ] **Step 2: Write the failing test**

Add this test to `GroupDetailViewModelTest.kt` (inside the class body, after the existing tests):

```kotlin
@Test
fun `reorderButtons moves item and delegates to soundButtonRepository`() = runTest {
    val buttons = listOf(
        org.role.samples_button.core.model.SoundButton(
            id = 1L, label = "A", filePath = "/a.mp3", groupId = 1L, position = 0
        ),
        org.role.samples_button.core.model.SoundButton(
            id = 2L, label = "B", filePath = "/b.mp3", groupId = 1L, position = 1
        ),
        org.role.samples_button.core.model.SoundButton(
            id = 3L, label = "C", filePath = "/c.mp3", groupId = 1L, position = 2
        ),
    )
    val groupRepo = FakeGroupRepository()
    groupRepo.seedGroups(
        listOf(
            org.role.samples_button.core.model.Group(
                id = 1L, name = "Test", position = 0, buttons = buttons
            )
        )
    )
    val soundButtonRepo = FakeSoundButtonRepository()
    val handle = SavedStateHandle(mapOf("groupId" to 1L))
    val vm = GroupDetailViewModel(handle, groupRepo, soundButtonRepo, FakeSoundPoolPlayer())

    vm.reorderButtons(from = 0, to = 2)

    val reordered = soundButtonRepo.reorderedLists.last()
    assertEquals(listOf(2L, 3L, 1L), reordered.map { it.id })
    assertEquals(listOf(0, 1, 2), reordered.map { it.position })
}
```

- [ ] **Step 3: Run the test to confirm it fails**

```
./gradlew :feature:soundboard:impl:test --tests "*.GroupDetailViewModelTest.reorderButtons*"
```

Expected: FAIL — compilation error "Unresolved reference: reorderButtons".

- [ ] **Step 4: Add `reorderButtons` to `GroupDetailViewModel`**

Add this method after `renameButton`, before `onCleared`:

```kotlin
fun reorderButtons(from: Int, to: Int) {
    val current = group.value?.buttons ?: return
    val reordered = current.toMutableList()
        .apply { add(to, removeAt(from)) }
        .mapIndexed { index, btn -> btn.copy(position = index) }
    viewModelScope.launch { soundButtonRepository.reorderButtons(reordered) }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```
./gradlew :feature:soundboard:impl:test
```

Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModel.kt
git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModelTest.kt
git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt
git commit -m "feat: add reorderButtons to GroupDetailViewModel"
```

---

## Task 4: Add `sh.calvin.reorderable` dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `feature/soundboard/impl/build.gradle.kts`

- [ ] **Step 1: Add version entry to `libs.versions.toml`**

In the `[versions]` block, add after `accompanist = "0.37.3"`:

```toml
reorderable = "2.4.3"
```

- [ ] **Step 2: Add library entry to `libs.versions.toml`**

In the `[libraries]` block, add after `hilt-navigation-compose = ...`:

```toml
reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }
```

- [ ] **Step 3: Add implementation dependency to `feature/soundboard/impl/build.gradle.kts`**

In the `dependencies` block, add after `implementation(libs.compose.material.icons.extended)`:

```kotlin
implementation(libs.reorderable)
```

- [ ] **Step 4: Sync and verify build**

```
./gradlew :feature:soundboard:impl:assembleDebug
```

Expected: BUILD SUCCESSFUL (library downloaded and compiled).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml
git add feature/soundboard/impl/build.gradle.kts
git commit -m "build: add sh.calvin.reorderable dependency"
```

---

## Task 5: Migrate UI — `LazyVerticalGrid` + drag & drop + "⋮" button

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailScreen.kt`

- [ ] **Step 1: Replace the entire contents of `GroupDetailScreen.kt`**

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.role.samples_button.core.model.SoundButton
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    viewModel: GroupDetailViewModel,
    onNavigateToFileBrowser: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val playingPaths by viewModel.playingPaths.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (playingPaths.isNotEmpty()) {
                        IconButton(onClick = { viewModel.pauseAll() }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pausar todo")
                        }
                    }
                }
            )
        }
    ) { padding ->
        group?.let { g ->
            ButtonGrid(
                buttons = g.buttons,
                playingPaths = playingPaths,
                onAddSound = { onNavigateToFileBrowser(g.id) },
                onSoundButtonClick = { filePath ->
                    if (filePath in playingPaths) viewModel.pauseSound(filePath)
                    else viewModel.playSound(filePath)
                },
                onDeleteButton = { viewModel.deleteButton(it) },
                onRenameButton = { id, newLabel -> viewModel.renameButton(id, newLabel) },
                onReorder = { from, to -> viewModel.reorderButtons(from, to) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ButtonGrid(
    buttons: List<SoundButton>,
    playingPaths: Set<String>,
    onAddSound: () -> Unit,
    onSoundButtonClick: (String) -> Unit,
    onDeleteButton: (Long) -> Unit,
    onRenameButton: (Long, String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val reorderState = rememberReorderableLazyGridState { from, to ->
        onReorder(from.index, to.index)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = reorderState.gridState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        items(buttons, key = { it.id }) { button ->
            ReorderableItem(reorderState, key = button.id) { isDragging ->
                SoundButtonItem(
                    button = button,
                    isPlaying = button.filePath in playingPaths,
                    isDragging = isDragging,
                    dragModifier = Modifier.longPressDraggableHandle(),
                    onClick = { onSoundButtonClick(button.filePath) },
                    onDelete = { onDeleteButton(button.id) },
                    onRename = onRenameButton
                )
            }
        }
        item {
            AddSoundButton(onClick = onAddSound)
        }
    }
}

@Composable
private fun SoundButtonItem(
    button: SoundButton,
    isPlaying: Boolean,
    isDragging: Boolean,
    dragModifier: Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val elevation = if (isDragging) 8.dp else 1.dp

    Box(modifier = modifier.aspectRatio(1f)) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .then(dragModifier)
                .clickable { onClick() },
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            colors = CardDefaults.cardColors(
                containerColor = if (isPlaying)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(4.dp)
                )
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(2.dp)
                            .size(12.dp)
                    )
                }
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Renombrar") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { showMenu = false; showRenameDialog = true }
            )
            DropdownMenuItem(
                text = { Text("Eliminar") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { showMenu = false; showConfirmDialog = true }
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Eliminar sample") },
            text = { Text("¿Eliminar \"${button.label}\"?") },
            confirmButton = {
                TextButton(onClick = { showConfirmDialog = false; onDelete() }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showRenameDialog) {
        RenameDialog(
            currentLabel = button.label,
            onConfirm = { newLabel -> showRenameDialog = false; onRename(button.id, newLabel) },
            onDismiss = { showRenameDialog = false }
        )
    }
}

@Composable
private fun RenameDialog(
    currentLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(currentLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar sample") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (label.isNotBlank()) onConfirm(label.trim()) },
                enabled = label.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AddSoundButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = "Agregar sonido")
        }
    }
}
```

- [ ] **Step 2: Build the module**

```
./gradlew :feature:soundboard:impl:assembleDebug
```

Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 3: Run all tests**

```
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailScreen.kt
git commit -m "feat: drag & drop reorder in GroupDetailScreen with LazyVerticalGrid"
```

---

## Final Verification

- [ ] **Run the full test suite one last time**

```
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests PASS.

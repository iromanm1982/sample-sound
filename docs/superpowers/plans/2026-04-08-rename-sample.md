# Rename Sample Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow the user to rename a SoundButton from the existing long-press DropdownMenu on the soundboard.

**Architecture:** Add `updateLabel` to the DAO, expose `renameButton` through the repository interface and impl, add a ViewModel function, and wire a "Renombrar" menu item + dialog in `SoundButtonItem`, threading the callback up through `ButtonGrid → GroupCard → GroupList → SoundBoardScreen`.

**Tech Stack:** Kotlin, Room, Hilt, Jetpack Compose, Coroutines, JUnit 4, kotlinx-coroutines-test

---

## Files

| Action | Path |
|--------|------|
| Modify | `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt` |
| Modify | `core/data/src/test/java/org/role/samples_button/core/data/FakeSoundButtonDao.kt` |
| Modify | `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt` |
| Modify | `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepositoryImpl.kt` |
| Modify | `core/data/src/test/java/org/role/samples_button/core/data/SoundButtonRepositoryImplTest.kt` |
| Modify | `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt` |
| Modify | `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt` |
| Modify | `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt` |

---

## Task 1: Add `updateLabel` to DAO and fake

**Files:**
- Modify: `core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt`
- Modify: `core/data/src/test/java/org/role/samples_button/core/data/FakeSoundButtonDao.kt`

- [ ] **Step 1: Add `updateLabel` to `SoundButtonDao`**

Replace the full content of `SoundButtonDao.kt`:

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
}
```

- [ ] **Step 2: Add `updateLabel` to `FakeSoundButtonDao`**

Replace the full content of `FakeSoundButtonDao.kt`:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.role.samples_button.core.database.SoundButtonDao
import org.role.samples_button.core.database.SoundButtonEntity

class FakeSoundButtonDao : SoundButtonDao {
    private val map = mutableMapOf<Long, MutableList<SoundButtonEntity>>()
    private val flows = mutableMapOf<Long, MutableStateFlow<List<SoundButtonEntity>>>()
    private var nextId = 1L

    private fun flowFor(groupId: Long): MutableStateFlow<List<SoundButtonEntity>> =
        flows.getOrPut(groupId) { MutableStateFlow(emptyList()) }

    override fun getByGroupId(groupId: Long): Flow<List<SoundButtonEntity>> = flowFor(groupId)

    override suspend fun insert(button: SoundButtonEntity): Long {
        val entity = button.copy(id = nextId++)
        map.getOrPut(entity.groupId) { mutableListOf() }.add(entity)
        flowFor(entity.groupId).value = map[entity.groupId]!!.toList()
        return entity.id
    }

    override suspend fun deleteById(id: Long) {
        map.forEach { (groupId, list) ->
            if (list.removeAll { it.id == id }) {
                flowFor(groupId).value = list.toList()
            }
        }
    }

    override suspend fun updateLabel(id: Long, label: String) {
        map.forEach { (groupId, list) ->
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = list[index].copy(label = label)
                flowFor(groupId).value = list.toList()
            }
        }
    }
}
```

- [ ] **Step 3: Build to verify compilation**

```
./gradlew :core:database:compileReleaseKotlin :core:data:compileReleaseKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add core/database/src/main/java/org/role/samples_button/core/database/SoundButtonDao.kt
git add core/data/src/test/java/org/role/samples_button/core/data/FakeSoundButtonDao.kt
git commit -m "feat: add updateLabel query to SoundButtonDao"
```

---

## Task 2: Add `renameButton` to repository interface, impl, and test

**Files:**
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt`
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepositoryImpl.kt`
- Modify: `core/data/src/test/java/org/role/samples_button/core/data/SoundButtonRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `SoundButtonRepositoryImplTest.kt` (inside the class, after `deleteButton removes button from dao`):

```kotlin
@Test
fun `renameButton updates label in dao`() = runTest {
    val dao = FakeSoundButtonDao()
    val repo = SoundButtonRepositoryImpl(dao)
    repo.addButton("Kick", "/sdcard/kick.mp3", 1L)
    val id = dao.getByGroupId(1L).first()[0].id
    repo.renameButton(id, "  Bass Drum  ")
    val buttons = dao.getByGroupId(1L).first()
    assertEquals("Bass Drum", buttons[0].label)
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :core:data:test --tests "org.role.samples_button.core.data.SoundButtonRepositoryImplTest.renameButton updates label in dao"
```

Expected: FAIL — `renameButton` not defined on `SoundButtonRepository`.

- [ ] **Step 3: Add `renameButton` to the interface**

Replace the full content of `SoundButtonRepository.kt`:

```kotlin
package org.role.samples_button.core.data

interface SoundButtonRepository {
    suspend fun addButton(label: String, filePath: String, groupId: Long)
    suspend fun deleteButton(id: Long)
    suspend fun renameButton(id: Long, newLabel: String)
}
```

- [ ] **Step 4: Implement `renameButton` in `SoundButtonRepositoryImpl`**

Replace the full content of `SoundButtonRepositoryImpl.kt`:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.first
import org.role.samples_button.core.database.SoundButtonDao
import org.role.samples_button.core.database.SoundButtonEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundButtonRepositoryImpl @Inject constructor(
    private val soundButtonDao: SoundButtonDao
) : SoundButtonRepository {

    override suspend fun addButton(label: String, filePath: String, groupId: Long) {
        val position = soundButtonDao.getByGroupId(groupId).first().size
        soundButtonDao.insert(
            SoundButtonEntity(label = label, filePath = filePath, groupId = groupId, position = position)
        )
    }

    override suspend fun deleteButton(id: Long) {
        soundButtonDao.deleteById(id)
    }

    override suspend fun renameButton(id: Long, newLabel: String) {
        soundButtonDao.updateLabel(id, newLabel.trim())
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```
./gradlew :core:data:test --tests "org.role.samples_button.core.data.SoundButtonRepositoryImplTest.renameButton updates label in dao"
```

Expected: PASS

- [ ] **Step 6: Run all data tests to check no regressions**

```
./gradlew :core:data:test
```

Expected: all tests PASS

- [ ] **Step 7: Commit**

```bash
git add core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepository.kt
git add core/data/src/main/java/org/role/samples_button/core/data/SoundButtonRepositoryImpl.kt
git add core/data/src/test/java/org/role/samples_button/core/data/SoundButtonRepositoryImplTest.kt
git commit -m "feat: add renameButton to SoundButtonRepository"
```

---

## Task 3: Add `renameButton` to ViewModel and test

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

In `SoundBoardViewModelTest.kt`:

1. Update `FakeSoundButtonRepository` (at the bottom of the file) to add `renameButton`:

```kotlin
class FakeSoundButtonRepository : SoundButtonRepository {
    val deletedIds = mutableListOf<Long>()
    val renamedButtons = mutableListOf<Pair<Long, String>>()
    override suspend fun addButton(label: String, filePath: String, groupId: Long) = Unit
    override suspend fun deleteButton(id: Long) { deletedIds.add(id) }
    override suspend fun renameButton(id: Long, newLabel: String) { renamedButtons.add(id to newLabel) }
}
```

2. Add this test inside `SoundBoardViewModelTest`:

```kotlin
@Test
fun `renameButton delegates to repository`() = runTest {
    val soundButtonRepo = FakeSoundButtonRepository()
    val viewModel = SoundBoardViewModel(FakeGroupRepository(), soundButtonRepo, FakeSoundPoolPlayer())
    viewModel.renameButton(7L, "New Name")
    assertEquals(listOf(7L to "New Name"), soundButtonRepo.renamedButtons)
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :feature:soundboard:impl:test --tests "org.role.samples_button.feature.soundboard.impl.SoundBoardViewModelTest.renameButton delegates to repository"
```

Expected: FAIL — `renameButton` not defined on `SoundBoardViewModel`.

- [ ] **Step 3: Add `renameButton` to `SoundBoardViewModel`**

Add the following function to `SoundBoardViewModel.kt` after `deleteButton`:

```kotlin
fun renameButton(id: Long, newLabel: String) {
    viewModelScope.launch { soundButtonRepository.renameButton(id, newLabel) }
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :feature:soundboard:impl:test --tests "org.role.samples_button.feature.soundboard.impl.SoundBoardViewModelTest.renameButton delegates to repository"
```

Expected: PASS

- [ ] **Step 5: Run all soundboard tests to check no regressions**

```
./gradlew :feature:soundboard:impl:test
```

Expected: all tests PASS

- [ ] **Step 6: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt
git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt
git commit -m "feat: add renameButton to SoundBoardViewModel"
```

---

## Task 4: Wire rename UI in SoundBoardScreen

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`

- [ ] **Step 1: Add `onRename` to `SoundButtonItem` and new `RenameDialog`**

Replace the entire `SoundButtonItem` composable and add a `RenameDialog` composable after it:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundButtonItem(
    button: SoundButton,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.height(64.dp)) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
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
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(12.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Renombrar") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    showMenu = false
                    showRenameDialog = true
                }
            )
            DropdownMenuItem(
                text = { Text("Eliminar") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    showMenu = false
                    showConfirmDialog = true
                }
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Eliminar sample") },
            text = { Text("¿Eliminar \"${button.label}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onDelete()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showRenameDialog) {
        RenameDialog(
            currentLabel = button.label,
            onConfirm = { newLabel ->
                showRenameDialog = false
                onRename(button.id, newLabel)
            },
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
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

- [ ] **Step 2: Add `Icons.Default.Edit` import**

At the top of `SoundBoardScreen.kt`, add the missing import alongside the existing `Icons.Default.*` imports:

```kotlin
import androidx.compose.material.icons.filled.Edit
```

- [ ] **Step 3: Add `onRenameButton` parameter to `ButtonGrid`**

Replace the `ButtonGrid` signature and the call to `SoundButtonItem` inside it:

```kotlin
@Composable
private fun ButtonGrid(
    buttons: List<SoundButton>,
    playingPaths: Set<String>,
    onAddSound: () -> Unit,
    onSoundButtonClick: (String) -> Unit,
    onDeleteButton: (Long) -> Unit,
    onRenameButton: (Long, String) -> Unit
) {
    val allItems: List<SoundButton?> = buttons + listOf(null)
    allItems.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { button ->
                if (button != null) {
                    val filePath = button.filePath
                    SoundButtonItem(
                        button = button,
                        isPlaying = filePath in playingPaths,
                        onClick = { onSoundButtonClick(filePath) },
                        onDelete = { onDeleteButton(button.id) },
                        onRename = onRenameButton,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    AddSoundButton(onClick = onAddSound, modifier = Modifier.weight(1f))
                }
            }
            repeat(3 - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
```

- [ ] **Step 4: Add `onRenameButton` parameter to `GroupCard`**

Replace the `GroupCard` composable:

```kotlin
@Composable
private fun GroupCard(
    group: Group,
    playingPaths: Set<String>,
    onDelete: () -> Unit,
    onAddSound: () -> Unit,
    onSoundButtonClick: (String) -> Unit,
    onDeleteButton: (Long) -> Unit,
    onRenameButton: (Long, String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = group.name, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar ${group.name}")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ButtonGrid(
                buttons = group.buttons,
                playingPaths = playingPaths,
                onAddSound = onAddSound,
                onSoundButtonClick = onSoundButtonClick,
                onDeleteButton = onDeleteButton,
                onRenameButton = onRenameButton
            )
        }
    }
}
```

- [ ] **Step 5: Add `onRenameButton` parameter to `GroupList`**

Replace the `GroupList` composable:

```kotlin
@Composable
private fun GroupList(
    groups: List<Group>,
    playingPaths: Set<String>,
    onDelete: (Long) -> Unit,
    onAddSound: (Long) -> Unit,
    onSoundButtonClick: (String) -> Unit,
    onDeleteButton: (Long) -> Unit,
    onRenameButton: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(groups, key = { it.id }) { group ->
            GroupCard(
                group = group,
                playingPaths = playingPaths,
                onDelete = { onDelete(group.id) },
                onAddSound = { onAddSound(group.id) },
                onSoundButtonClick = onSoundButtonClick,
                onDeleteButton = onDeleteButton,
                onRenameButton = onRenameButton
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 6: Pass `onRenameButton` from `SoundBoardScreen`**

In `SoundBoardScreen`, update the `GroupList` call inside the `else` branch:

```kotlin
GroupList(
    groups = groups,
    playingPaths = playingPaths,
    onDelete = { viewModel.deleteGroup(it) },
    onAddSound = { groupId -> onNavigateToFileBrowser(groupId) },
    onSoundButtonClick = { filePath ->
        if (filePath in playingPaths) viewModel.pauseSound(filePath)
        else viewModel.playSound(filePath)
    },
    onDeleteButton = { viewModel.deleteButton(it) },
    onRenameButton = { id, newLabel -> viewModel.renameButton(id, newLabel) },
    modifier = Modifier.padding(padding)
)
```

- [ ] **Step 7: Build to verify compilation**

```
./gradlew :feature:soundboard:impl:compileReleaseKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Run all tests**

```
./gradlew test
```

Expected: all tests PASS

- [ ] **Step 9: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt
git commit -m "feat: add rename option to SoundButtonItem context menu"
```

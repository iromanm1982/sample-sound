# Group Detail Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the soundboard UI so the main screen shows only a list of groups, and group buttons live in a separate `GroupDetailScreen` reachable by tapping a group; samples can be added directly from the list via a "+" button on each group card.

**Architecture:** `SoundBoardScreen` becomes a pure group list with a compact `GroupCard` (name + count badge + "+" + "⋮" menu). A new `GroupDetailScreen` within `feature/soundboard/impl` handles button playback. `SoundBoardViewModel` sheds all audio logic and gains `renameGroup`; a new `GroupDetailViewModel` takes over audio responsibilities.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Kotlin Flow, Jetpack Navigation Compose (`androidx.navigation.compose`), `androidx.hilt.navigation.compose`.

---

### Task 1: Add `renameGroup` to the data layer

**Files:**
- Modify: `core/database/src/main/java/org/role/samples_button/core/database/GroupDao.kt`
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/GroupRepository.kt`
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/GroupRepositoryImpl.kt`
- Modify: `core/data/src/test/java/org/role/samples_button/core/data/FakeGroupDao.kt`
- Modify: `core/data/src/test/java/org/role/samples_button/core/data/GroupRepositoryImplTest.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`

- [ ] **Step 1: Add `updateName` query to `GroupDao`**

Replace the entire file:

```kotlin
package org.role.samples_button.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY position ASC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE groups SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)
}
```

- [ ] **Step 2: Add `renameGroup` to `GroupRepository` interface**

Replace the entire file:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

interface GroupRepository {
    fun getGroupsWithButtons(): Flow<List<Group>>
    suspend fun createGroup(name: String)
    suspend fun deleteGroup(id: Long)
    suspend fun reorderButtons(groupId: Long, buttons: List<SoundButton>)
    suspend fun renameGroup(id: Long, newName: String)
}
```

- [ ] **Step 3: Write failing test for `renameGroup` in `GroupRepositoryImplTest`**

Add this test to `GroupRepositoryImplTest.kt` (inside the class, after the last test):

```kotlin
@Test
fun `renameGroup updates group name`() = runTest {
    val repo = GroupRepositoryImpl(FakeGroupDao(), FakeSoundButtonDao())
    repo.createGroup("Old Name")
    val id = repo.getGroupsWithButtons().first()[0].id
    repo.renameGroup(id, "New Name")
    val groups = repo.getGroupsWithButtons().first()
    assertEquals("New Name", groups[0].name)
}
```

- [ ] **Step 4: Implement `updateName` in `FakeGroupDao`**

Add to `FakeGroupDao.kt` (inside the class, after `deleteById`):

```kotlin
override suspend fun updateName(id: Long, name: String) {
    val index = groups.indexOfFirst { it.id == id }
    if (index >= 0) {
        groups[index] = groups[index].copy(name = name)
        flow.value = groups.toList()
    }
}
```

- [ ] **Step 5: Implement `renameGroup` in `GroupRepositoryImpl`**

Add to `GroupRepositoryImpl.kt` (inside the class, after `deleteGroup`):

```kotlin
override suspend fun renameGroup(id: Long, newName: String) {
    groupDao.updateName(id, newName)
}
```

- [ ] **Step 6: Run data layer tests to verify they pass**

Run: `./gradlew :core:data:test`  
Expected: BUILD SUCCESSFUL, all tests pass including the new `renameGroup` test.

- [ ] **Step 7: Add `renameGroup` to `FakeGroupRepository` in `SoundBoardViewModelTest.kt`**

In the `FakeGroupRepository` class at the bottom of `SoundBoardViewModelTest.kt`, add these two items (the field and the override):

```kotlin
val renamedGroups = mutableListOf<Pair<Long, String>>()

override suspend fun renameGroup(id: Long, newName: String) {
    renamedGroups.add(id to newName)
    _groups.value = _groups.value.map { if (it.id == id) it.copy(name = newName) else it }
}
```

- [ ] **Step 8: Commit**

```bash
git add core/database/src/main/java/org/role/samples_button/core/database/GroupDao.kt
git add core/data/src/main/java/org/role/samples_button/core/data/GroupRepository.kt
git add core/data/src/main/java/org/role/samples_button/core/data/GroupRepositoryImpl.kt
git add core/data/src/test/java/org/role/samples_button/core/data/FakeGroupDao.kt
git add core/data/src/test/java/org/role/samples_button/core/data/GroupRepositoryImplTest.kt
git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt
git commit -m "feat: add renameGroup to data layer"
```

---

### Task 2: Refactor `SoundBoardViewModel` — remove audio, add `renameGroup`

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`

- [ ] **Step 1: Replace `SoundBoardViewModel.kt` with the simplified version**

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.model.Group
import javax.inject.Inject

@HiltViewModel
class SoundBoardViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {

    val groups: StateFlow<List<Group>> = groupRepository
        .getGroupsWithButtons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createGroup(name: String) {
        viewModelScope.launch { groupRepository.createGroup(name) }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch { groupRepository.deleteGroup(id) }
    }

    fun renameGroup(id: Long, newName: String) {
        viewModelScope.launch { groupRepository.renameGroup(id, newName) }
    }
}
```

- [ ] **Step 2: Write failing test for `renameGroup`**

Add this test to `SoundBoardViewModelTest.kt` (inside the class):

```kotlin
@Test
fun `renameGroup delegates to repository`() = runTest {
    val repo = FakeGroupRepository()
    val viewModel = SoundBoardViewModel(repo)
    viewModel.renameGroup(5L, "Renamed")
    assertEquals(listOf(5L to "Renamed"), repo.renamedGroups)
}
```

- [ ] **Step 3: Replace the full `SoundBoardViewModelTest.kt` to fix constructor calls and remove audio tests**

The ViewModel no longer takes `SoundButtonRepository` or `SoundPoolPlayer`. Remove the audio tests and update all constructor calls. `FakeSoundButtonRepository` stays because `GroupDetailViewModelTest` (Task 3) will use it.

```kotlin
package org.role.samples_button.feature.soundboard.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.data.SoundButtonRepository
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

@OptIn(ExperimentalCoroutinesApi::class)
class SoundBoardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial groups state is empty list`() = runTest {
        val viewModel = SoundBoardViewModel(FakeGroupRepository())
        assertEquals(emptyList<Group>(), viewModel.groups.value)
    }

    @Test
    fun `createGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        val viewModel = SoundBoardViewModel(repo)
        viewModel.createGroup("Percusión")
        assertEquals(listOf("Percusión"), repo.createdGroups)
    }

    @Test
    fun `deleteGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        val viewModel = SoundBoardViewModel(repo)
        viewModel.deleteGroup(42L)
        assertEquals(listOf(42L), repo.deletedIds)
    }

    @Test
    fun `renameGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        val viewModel = SoundBoardViewModel(repo)
        viewModel.renameGroup(5L, "Renamed")
        assertEquals(listOf(5L to "Renamed"), repo.renamedGroups)
    }
}

class FakeGroupRepository : GroupRepository {
    val createdGroups = mutableListOf<String>()
    val deletedIds = mutableListOf<Long>()
    val renamedGroups = mutableListOf<Pair<Long, String>>()
    private val _groups = MutableStateFlow<List<Group>>(emptyList())

    override fun getGroupsWithButtons(): Flow<List<Group>> = _groups

    override suspend fun createGroup(name: String) {
        createdGroups.add(name)
        _groups.value = _groups.value + Group(
            id = createdGroups.size.toLong(),
            name = name,
            position = createdGroups.size - 1,
            buttons = emptyList()
        )
    }

    override suspend fun deleteGroup(id: Long) {
        deletedIds.add(id)
        _groups.value = _groups.value.filter { it.id != id }
    }

    override suspend fun renameGroup(id: Long, newName: String) {
        renamedGroups.add(id to newName)
        _groups.value = _groups.value.map { if (it.id == id) it.copy(name = newName) else it }
    }

    override suspend fun reorderButtons(groupId: Long, buttons: List<SoundButton>) = Unit
}

class FakeSoundButtonRepository : SoundButtonRepository {
    val deletedIds = mutableListOf<Long>()
    val renamedButtons = mutableListOf<Pair<Long, String>>()
    override suspend fun addButton(label: String, filePath: String, groupId: Long) = Unit
    override suspend fun deleteButton(id: Long) { deletedIds.add(id) }
    override suspend fun renameButton(id: Long, newLabel: String) { renamedButtons.add(id to newLabel) }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :feature:soundboard:impl:test`  
Expected: BUILD SUCCESSFUL, 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt
git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt
git commit -m "refactor: simplify SoundBoardViewModel — remove audio logic, add renameGroup"
```

---

### Task 3: Create `GroupDetailViewModel`

**Files:**
- Create: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModel.kt`
- Create: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModelTest.kt`

- [ ] **Step 1: Write failing tests in `GroupDetailViewModelTest.kt`**

`FakeGroupRepository`, `FakeSoundButtonRepository`, and `FakeSoundPoolPlayer` are defined in other files in the same package — they are visible here without import.

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun makeVm(
        groupId: Long,
        repo: FakeGroupRepository = FakeGroupRepository(),
        player: FakeSoundPoolPlayer = FakeSoundPoolPlayer()
    ): GroupDetailViewModel {
        val handle = SavedStateHandle(mapOf("groupId" to groupId))
        return GroupDetailViewModel(handle, repo, FakeSoundButtonRepository(), player)
    }

    @Test
    fun `group is null when groupId not found`() = runTest {
        val vm = makeVm(groupId = 999L)
        assertNull(vm.group.value)
    }

    @Test
    fun `group emits correct group when found`() = runTest {
        val repo = FakeGroupRepository()
        repo.createGroup("Percusión")
        val vm = makeVm(groupId = 1L, repo = repo)
        assertEquals("Percusión", vm.group.value?.name)
    }

    @Test
    fun `playSound adds filePath to playingPaths`() = runTest {
        val vm = makeVm(1L)
        vm.playSound("/storage/kick.mp3")
        assertTrue(vm.playingPaths.value.contains("/storage/kick.mp3"))
    }

    @Test
    fun `pauseSound removes filePath from playingPaths`() = runTest {
        val vm = makeVm(1L)
        vm.playSound("/storage/kick.mp3")
        vm.pauseSound("/storage/kick.mp3")
        assertFalse(vm.playingPaths.value.contains("/storage/kick.mp3"))
    }

    @Test
    fun `pauseAll clears playingPaths`() = runTest {
        val vm = makeVm(1L)
        vm.playSound("/storage/kick.mp3")
        vm.playSound("/storage/snare.mp3")
        vm.pauseAll()
        assertTrue(vm.playingPaths.value.isEmpty())
    }

    @Test
    fun `playSound delegates to player`() = runTest {
        val player = FakeSoundPoolPlayer()
        val vm = makeVm(groupId = 1L, player = player)
        vm.playSound("/storage/kick.mp3")
        assertEquals(listOf("/storage/kick.mp3"), player.playedPaths)
    }

    @Test
    fun `pauseSound delegates to player`() = runTest {
        val player = FakeSoundPoolPlayer()
        val vm = makeVm(groupId = 1L, player = player)
        vm.pauseSound("/storage/kick.mp3")
        assertEquals(listOf("/storage/kick.mp3"), player.pausedPaths)
    }

    @Test
    fun `pauseAll delegates to player`() = runTest {
        val player = FakeSoundPoolPlayer()
        val vm = makeVm(groupId = 1L, player = player)
        vm.pauseAll()
        assertTrue(player.pauseAllCalled)
    }

    @Test
    fun `deleteButton delegates to repository`() = runTest {
        val soundButtonRepo = FakeSoundButtonRepository()
        val handle = SavedStateHandle(mapOf("groupId" to 1L))
        val vm = GroupDetailViewModel(handle, FakeGroupRepository(), soundButtonRepo, FakeSoundPoolPlayer())
        vm.deleteButton(99L)
        assertEquals(listOf(99L), soundButtonRepo.deletedIds)
    }

    @Test
    fun `renameButton delegates to repository`() = runTest {
        val soundButtonRepo = FakeSoundButtonRepository()
        val handle = SavedStateHandle(mapOf("groupId" to 1L))
        val vm = GroupDetailViewModel(handle, FakeGroupRepository(), soundButtonRepo, FakeSoundPoolPlayer())
        vm.renameButton(7L, "New Name")
        assertEquals(listOf(7L to "New Name"), soundButtonRepo.renamedButtons)
    }

    @Test
    fun `onCleared releases player`() = runTest {
        val player = FakeSoundPoolPlayer()
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GroupDetailViewModel(
                    SavedStateHandle(mapOf("groupId" to 1L)),
                    FakeGroupRepository(),
                    FakeSoundButtonRepository(),
                    player
                ) as T
        }
        ViewModelProvider(store, factory)[GroupDetailViewModel::class.java]
        store.clear()
        assertTrue(player.released)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:soundboard:impl:test`  
Expected: FAIL — "Unresolved reference: GroupDetailViewModel"

- [ ] **Step 3: Create `GroupDetailViewModel.kt`**

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.role.samples_button.core.audio.SoundPoolPlayer
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.data.SoundButtonRepository
import org.role.samples_button.core.model.Group
import javax.inject.Inject

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val soundButtonRepository: SoundButtonRepository,
    private val soundPoolPlayer: SoundPoolPlayer
) : ViewModel() {

    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    val group: StateFlow<Group?> = groupRepository
        .getGroupsWithButtons()
        .map { groups -> groups.find { it.id == groupId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _playingPaths = MutableStateFlow<Set<String>>(emptySet())
    val playingPaths: StateFlow<Set<String>> = _playingPaths.asStateFlow()

    fun playSound(filePath: String) {
        soundPoolPlayer.play(filePath)
        _playingPaths.update { it + filePath }
    }

    fun pauseSound(filePath: String) {
        soundPoolPlayer.pause(filePath)
        _playingPaths.update { it - filePath }
    }

    fun pauseAll() {
        soundPoolPlayer.pauseAll()
        _playingPaths.value = emptySet()
    }

    fun deleteButton(id: Long) {
        viewModelScope.launch { soundButtonRepository.deleteButton(id) }
    }

    fun renameButton(id: Long, newLabel: String) {
        viewModelScope.launch { soundButtonRepository.renameButton(id, newLabel) }
    }

    override fun onCleared() {
        soundPoolPlayer.release()
    }
}
```

- [ ] **Step 4: Run tests and verify they pass**

Run: `./gradlew :feature:soundboard:impl:test`  
Expected: BUILD SUCCESSFUL, all 12 tests pass (4 from SoundBoardViewModelTest + 12 from GroupDetailViewModelTest).

- [ ] **Step 5: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModel.kt
git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModelTest.kt
git commit -m "feat: add GroupDetailViewModel with audio and button management"
```

---

### Task 4: Refactor `SoundBoardScreen` — new compact GroupCard

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`

- [ ] **Step 1: Replace `SoundBoardScreen.kt` with the refactored version**

`ButtonGrid`, `SoundButtonItem`, `AddSoundButton`, and `RenameDialog` are removed from this file (they move to `GroupDetailScreen.kt` in Task 5). The Pause icon is also removed from the TopAppBar — audio state lives only in the detail screen now.

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.role.samples_button.core.model.Group

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundBoardScreen(
    viewModel: SoundBoardViewModel,
    onNavigateToGroup: (Long) -> Unit = {},
    onNavigateToFileBrowser: (Long) -> Unit = {}
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("SoundBoard") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Crear grupo")
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            GroupList(
                groups = groups,
                onNavigateToGroup = onNavigateToGroup,
                onAddSound = onNavigateToFileBrowser,
                onRenameGroup = { id, newName -> viewModel.renameGroup(id, newName) },
                onDeleteGroup = { viewModel.deleteGroup(it) },
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onConfirm = { name ->
                viewModel.createGroup(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Sin grupos todavía")
            Text("Toca + para crear uno")
        }
    }
}

@Composable
private fun GroupList(
    groups: List<Group>,
    onNavigateToGroup: (Long) -> Unit,
    onAddSound: (Long) -> Unit,
    onRenameGroup: (Long, String) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(groups, key = { it.id }) { group ->
            GroupCard(
                group = group,
                onNavigateToGroup = { onNavigateToGroup(group.id) },
                onAddSound = { onAddSound(group.id) },
                onRename = { newName -> onRenameGroup(group.id, newName) },
                onDelete = { onDeleteGroup(group.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    onNavigateToGroup: () -> Unit,
    onAddSound: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToGroup() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = group.buttons.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            IconButton(onClick = onAddSound) {
                Icon(Icons.Default.Add, contentDescription = "Añadir sample a ${group.name}")
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${group.name}")
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
                        onClick = { showMenu = false; showDeleteDialog = true }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameGroupDialog(
            currentName = group.name,
            onConfirm = { newName -> showRenameDialog = false; onRename(newName) },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar grupo") },
            text = { Text("¿Eliminar \"${group.name}\" y todos sus samples?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun RenameGroupDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar grupo") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun CreateGroupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo grupo") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del grupo") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :feature:soundboard:impl:assembleDebug`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt
git commit -m "feat: refactor SoundBoardScreen — group list with badge, quick-add and rename/delete menu"
```

---

### Task 5: Create `GroupDetailScreen`

**Files:**
- Create: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailScreen.kt`

- [ ] **Step 1: Create `GroupDetailScreen.kt`**

This contains the composables that were removed from `SoundBoardScreen.kt` (ButtonGrid, SoundButtonItem, AddSoundButton, RenameDialog) plus the new screen scaffold.

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ButtonGrid(
                    buttons = g.buttons,
                    playingPaths = playingPaths,
                    onAddSound = { onNavigateToFileBrowser(g.id) },
                    onSoundButtonClick = { filePath ->
                        if (filePath in playingPaths) viewModel.pauseSound(filePath)
                        else viewModel.playSound(filePath)
                    },
                    onDeleteButton = { viewModel.deleteButton(it) },
                    onRenameButton = { id, newLabel -> viewModel.renameButton(id, newLabel) }
                )
            }
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
        modifier = modifier.height(64.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = "Agregar sonido")
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :feature:soundboard:impl:assembleDebug`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailScreen.kt
git commit -m "feat: add GroupDetailScreen with button grid and audio controls"
```

---

### Task 6: Wire navigation in `MainActivity`

**Files:**
- Modify: `app/src/main/java/org/role/samples_button/MainActivity.kt`

- [ ] **Step 1: Replace `MainActivity.kt` with the updated navigation graph**

```kotlin
package org.role.samples_button

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import org.role.samples_button.core.designsystem.SamplesButtonTheme
import org.role.samples_button.feature.browser.impl.FileBrowserScreen
import org.role.samples_button.feature.soundboard.impl.GroupDetailScreen
import org.role.samples_button.feature.soundboard.impl.GroupDetailViewModel
import org.role.samples_button.feature.soundboard.impl.SoundBoardScreen
import org.role.samples_button.feature.soundboard.impl.SoundBoardViewModel

@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamplesButtonTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "soundboard") {
                    composable("soundboard") {
                        val viewModel: SoundBoardViewModel =
                            androidx.hilt.navigation.compose.hiltViewModel()
                        SoundBoardScreen(
                            viewModel = viewModel,
                            onNavigateToGroup = { groupId ->
                                navController.navigate("group_detail/$groupId")
                            },
                            onNavigateToFileBrowser = { groupId ->
                                navController.navigate("file_browser/$groupId")
                            }
                        )
                    }
                    composable(
                        route = "group_detail/{groupId}",
                        arguments = listOf(navArgument("groupId") { type = NavType.LongType })
                    ) {
                        val viewModel: GroupDetailViewModel =
                            androidx.hilt.navigation.compose.hiltViewModel()
                        GroupDetailScreen(
                            viewModel = viewModel,
                            onNavigateToFileBrowser = { groupId ->
                                navController.navigate("file_browser/$groupId")
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "file_browser/{groupId}",
                        arguments = listOf(navArgument("groupId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val groupId = backStackEntry.arguments!!.getLong("groupId")
                        FileBrowserScreen(
                            groupId = groupId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build the full app**

Run: `./gradlew assembleDebug`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`  
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/org/role/samples_button/MainActivity.kt
git commit -m "feat: wire group detail navigation — completes group detail redesign"
```

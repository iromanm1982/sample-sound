# Group Reorder via Long Press — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permit dragging `GroupCard` items in `SoundBoardScreen` via long press to reorder groups, persisting the new order in Room immediately on drop.

**Architecture:** Follow the identical pattern already used for button reorder in `GroupDetailScreen`: add `updatePosition` to the DAO, add `reorderGroups` to the repository, add `reorderGroups(from, to)` to the ViewModel, and convert `GroupList`'s `LazyColumn` to use `sh.calvin.reorderable`'s `ReorderableLazyColumn`.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, `sh.calvin.reorderable`, Coroutines, JUnit + `kotlinx-coroutines-test`.

---

## File Map

| File | Action |
|------|--------|
| `core/database/src/main/.../GroupDao.kt` | Add `updatePosition` query |
| `core/data/src/test/.../FakeGroupDao.kt` | Add `updatePosition` implementation |
| `core/data/src/main/.../GroupRepository.kt` | Add `suspend fun reorderGroups(groups: List<Group>)` |
| `core/data/src/main/.../GroupRepositoryImpl.kt` | Implement `reorderGroups`; inject `AppDatabase` |
| `core/data/src/test/.../GroupRepositoryImplTest.kt` | Refactor to `TestGroupRepository`; add reorder test |
| `feature/soundboard/impl/src/test/.../SoundBoardViewModelTest.kt` | Extend `FakeGroupRepository`; add reorder test |
| `feature/soundboard/impl/src/main/.../SoundBoardViewModel.kt` | Add `reorderGroups(from, to)` |
| `feature/soundboard/impl/src/main/.../SoundBoardScreen.kt` | Convert `GroupList`; update `GroupCard` |

---

## Task 1: Add `updatePosition` to `GroupDao` and `FakeGroupDao`

**Files:**
- Modify: `core/database/src/main/java/org/role/samples_button/core/database/GroupDao.kt`
- Modify: `core/data/src/test/java/org/role/samples_button/core/data/FakeGroupDao.kt`

- [ ] **Step 1: Add `updatePosition` to `GroupDao`**

Replace the contents of `GroupDao.kt`:

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

    @Query("UPDATE groups SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
}
```

- [ ] **Step 2: Add `updatePosition` to `FakeGroupDao`**

Replace the contents of `FakeGroupDao.kt`:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.role.samples_button.core.database.GroupDao
import org.role.samples_button.core.database.GroupEntity

class FakeGroupDao : GroupDao {
    private val groups = mutableListOf<GroupEntity>()
    private val flow = MutableStateFlow<List<GroupEntity>>(emptyList())
    private var nextId = 1L

    override fun getAllGroups(): Flow<List<GroupEntity>> = flow

    override suspend fun insert(group: GroupEntity): Long {
        val entity = group.copy(id = nextId++)
        groups.add(entity)
        flow.value = groups.toList()
        return entity.id
    }

    override suspend fun deleteById(id: Long) {
        groups.removeAll { it.id == id }
        flow.value = groups.toList()
    }

    override suspend fun updateName(id: Long, name: String) {
        val index = groups.indexOfFirst { it.id == id }
        if (index >= 0) {
            groups[index] = groups[index].copy(name = name)
            flow.value = groups.toList()
        }
    }

    override suspend fun updatePosition(id: Long, position: Int) {
        val index = groups.indexOfFirst { it.id == id }
        if (index >= 0) {
            groups[index] = groups[index].copy(position = position)
            flow.value = groups.sortedBy { it.position }
        }
    }
}
```

- [ ] **Step 3: Build to verify no compilation errors**

```
./gradlew :core:database:compileDebugKotlin :core:data:compileDebugUnitTestKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/database/src/main/java/org/role/samples_button/core/database/GroupDao.kt \
        core/data/src/test/java/org/role/samples_button/core/data/FakeGroupDao.kt
git commit -m "feat: add updatePosition to GroupDao and FakeGroupDao"
```

---

## Task 2: Add `reorderGroups` to repository layer

**Files:**
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/GroupRepository.kt`
- Modify: `core/data/src/main/java/org/role/samples_button/core/data/GroupRepositoryImpl.kt`
- Modify: `core/data/src/test/java/org/role/samples_button/core/data/GroupRepositoryImplTest.kt`

- [ ] **Step 1: Add `reorderGroups` to the `GroupRepository` interface**

Replace the contents of `GroupRepository.kt`:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import org.role.samples_button.core.model.Group

interface GroupRepository {
    fun getGroupsWithButtons(): Flow<List<Group>>
    suspend fun createGroup(name: String)
    suspend fun deleteGroup(id: Long)
    suspend fun renameGroup(id: Long, newName: String)
    suspend fun reorderGroups(groups: List<Group>)
}
```

- [ ] **Step 2: Refactor `GroupRepositoryImplTest` to use `TestGroupRepository` and add the failing reorder test**

Replace the contents of `GroupRepositoryImplTest.kt`:

```kotlin
package org.role.samples_button.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.role.samples_button.core.database.GroupEntity
import org.role.samples_button.core.database.SoundButtonEntity
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

/**
 * Unit-test stand-in for [GroupRepositoryImpl].
 *
 * [GroupRepositoryImpl] requires an [AppDatabase] instance so that [reorderGroups] can execute
 * inside a Room transaction. Room databases cannot be instantiated in JVM-only unit tests without
 * Robolectric or an instrumented-test runner. This helper delegates every method to the fake DAOs
 * and overrides [reorderGroups] to execute the same DAO calls without the transaction wrapper,
 * keeping observable behaviour identical for unit tests.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private class TestGroupRepository(
    private val groupDao: FakeGroupDao,
    private val buttonDao: FakeSoundButtonDao
) : GroupRepository {

    override fun getGroupsWithButtons(): Flow<List<Group>> =
        groupDao.getAllGroups().flatMapLatest { groupEntities ->
            if (groupEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                val buttonFlows = groupEntities.map { groupEntity ->
                    buttonDao.getByGroupId(groupEntity.id).map { buttons ->
                        Group(
                            id = groupEntity.id,
                            name = groupEntity.name,
                            position = groupEntity.position,
                            buttons = buttons.map {
                                SoundButton(it.id, it.label, it.filePath, it.groupId, it.position)
                            }
                        )
                    }
                }
                combine(buttonFlows) { it.toList() }
            }
        }

    override suspend fun createGroup(name: String) {
        val currentSize = groupDao.getAllGroups().first().size
        groupDao.insert(GroupEntity(name = name, position = currentSize))
    }

    override suspend fun deleteGroup(id: Long) {
        groupDao.deleteById(id)
    }

    override suspend fun renameGroup(id: Long, newName: String) {
        groupDao.updateName(id, newName)
    }

    override suspend fun reorderGroups(groups: List<Group>) {
        // No transaction in unit tests — DAO calls are identical to the production path.
        groups.forEach { groupDao.updatePosition(it.id, it.position) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GroupRepositoryImplTest {

    @Test
    fun `createGroup inserts group with correct name`() = runTest {
        val repo = TestGroupRepository(FakeGroupDao(), FakeSoundButtonDao())
        repo.createGroup("Drums")
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(1, groups.size)
        assertEquals("Drums", groups[0].name)
    }

    @Test
    fun `deleteGroup removes group from list`() = runTest {
        val dao = FakeGroupDao()
        val repo = TestGroupRepository(dao, FakeSoundButtonDao())
        repo.createGroup("Drums")
        val id = repo.getGroupsWithButtons().first()[0].id
        repo.deleteGroup(id)
        assertTrue(repo.getGroupsWithButtons().first().isEmpty())
    }

    @Test
    fun `createGroup assigns sequential positions`() = runTest {
        val repo = TestGroupRepository(FakeGroupDao(), FakeSoundButtonDao())
        repo.createGroup("First")
        repo.createGroup("Second")
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(0, groups[0].position)
        assertEquals(1, groups[1].position)
    }

    @Test
    fun `getGroupsWithButtons includes buttons for each group`() = runTest {
        val groupDao = FakeGroupDao()
        val buttonDao = FakeSoundButtonDao()
        val repo = TestGroupRepository(groupDao, buttonDao)
        repo.createGroup("Drums")
        val groupId = repo.getGroupsWithButtons().first()[0].id
        buttonDao.insert(
            SoundButtonEntity(label = "Kick", filePath = "/kick.mp3", groupId = groupId, position = 0)
        )
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(1, groups[0].buttons.size)
        assertEquals("Kick", groups[0].buttons[0].label)
    }

    @Test
    fun `renameGroup updates group name`() = runTest {
        val repo = TestGroupRepository(FakeGroupDao(), FakeSoundButtonDao())
        repo.createGroup("Old Name")
        val id = repo.getGroupsWithButtons().first()[0].id
        repo.renameGroup(id, "New Name")
        val groups = repo.getGroupsWithButtons().first()
        assertEquals("New Name", groups[0].name)
    }

    @Test
    fun `reorderGroups updates positions in dao`() = runTest {
        val repo = TestGroupRepository(FakeGroupDao(), FakeSoundButtonDao())
        repo.createGroup("First")
        repo.createGroup("Second")
        repo.createGroup("Third")
        val original = repo.getGroupsWithButtons().first().sortedBy { it.position }

        // Simulate moving First to last: [First, Second, Third] -> [Second, Third, First]
        val reordered = listOf(
            original[1].copy(position = 0),
            original[2].copy(position = 1),
            original[0].copy(position = 2)
        )
        repo.reorderGroups(reordered)

        val updated = repo.getGroupsWithButtons().first().sortedBy { it.position }
        assertEquals(original[1].id, updated[0].id)
        assertEquals(0, updated[0].position)
        assertEquals(original[2].id, updated[1].id)
        assertEquals(1, updated[1].position)
        assertEquals(original[0].id, updated[2].id)
        assertEquals(2, updated[2].position)
    }
}
```

- [ ] **Step 3: Run the new test to confirm it fails**

```
./gradlew :core:data:testDebugUnitTest --tests "*.GroupRepositoryImplTest.reorderGroups*"
```

Expected: FAIL — `reorderGroups` not yet in `GroupRepository`

- [ ] **Step 4: Implement `reorderGroups` in `GroupRepositoryImpl` and inject `AppDatabase`**

Replace the contents of `GroupRepositoryImpl.kt`:

```kotlin
package org.role.samples_button.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.role.samples_button.core.database.AppDatabase
import org.role.samples_button.core.database.GroupDao
import org.role.samples_button.core.database.GroupEntity
import org.role.samples_button.core.database.SoundButtonDao
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val soundButtonDao: SoundButtonDao,
    private val database: AppDatabase
) : GroupRepository {

    override fun getGroupsWithButtons(): Flow<List<Group>> =
        groupDao.getAllGroups().flatMapLatest { groupEntities ->
            if (groupEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                val buttonFlows = groupEntities.map { groupEntity ->
                    soundButtonDao.getByGroupId(groupEntity.id).map { buttons ->
                        Group(
                            id = groupEntity.id,
                            name = groupEntity.name,
                            position = groupEntity.position,
                            buttons = buttons.map {
                                SoundButton(it.id, it.label, it.filePath, it.groupId, it.position)
                            }
                        )
                    }
                }
                combine(buttonFlows) { it.toList() }
            }
        }

    override suspend fun createGroup(name: String) {
        val currentSize = groupDao.getAllGroups().first().size
        groupDao.insert(GroupEntity(name = name, position = currentSize))
    }

    override suspend fun deleteGroup(id: Long) {
        groupDao.deleteById(id)
    }

    override suspend fun renameGroup(id: Long, newName: String) {
        groupDao.updateName(id, newName)
    }

    override suspend fun reorderGroups(groups: List<Group>) {
        database.withTransaction {
            groups.forEach { groupDao.updatePosition(it.id, it.position) }
        }
    }
}
```

- [ ] **Step 5: Run all repository tests**

```
./gradlew :core:data:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/data/src/main/java/org/role/samples_button/core/data/GroupRepository.kt \
        core/data/src/main/java/org/role/samples_button/core/data/GroupRepositoryImpl.kt \
        core/data/src/test/java/org/role/samples_button/core/data/GroupRepositoryImplTest.kt
git commit -m "feat: add reorderGroups to GroupRepository and GroupRepositoryImpl"
```

---

## Task 3: Add `reorderGroups` to `SoundBoardViewModel`

**Files:**
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`

- [ ] **Step 1: Extend `FakeGroupRepository` and write the failing test**

In `SoundBoardViewModelTest.kt`, add `reorderedLists` tracking to `FakeGroupRepository` and add the new test.

Replace `FakeGroupRepository` with:

```kotlin
class FakeGroupRepository : GroupRepository {
    val createdGroups = mutableListOf<String>()
    val deletedIds = mutableListOf<Long>()
    val renamedGroups = mutableListOf<Pair<Long, String>>()
    val reorderedLists = mutableListOf<List<Group>>()
    val _groups = MutableStateFlow<List<Group>>(emptyList())

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

    override suspend fun reorderGroups(groups: List<Group>) {
        reorderedLists.add(groups)
        _groups.value = groups
    }

    fun seedGroups(groups: List<Group>) {
        _groups.value = groups
    }
}
```

Add this test inside `SoundBoardViewModelTest`:

```kotlin
@Test
fun `reorderGroups delegates reordered list with updated positions to repository`() = runTest {
    val repo = FakeGroupRepository()
    val viewModel = SoundBoardViewModel(repo)
    val groupA = Group(id = 1L, name = "A", position = 0, buttons = emptyList())
    val groupB = Group(id = 2L, name = "B", position = 1, buttons = emptyList())
    val groupC = Group(id = 3L, name = "C", position = 2, buttons = emptyList())
    repo.seedGroups(listOf(groupA, groupB, groupC))

    // Move first item (index 0) to last (index 2): [A,B,C] -> [B,C,A]
    viewModel.reorderGroups(from = 0, to = 2)

    val reordered = repo.reorderedLists.last()
    assertEquals(3, reordered.size)
    assertEquals(2L, reordered[0].id)   // B is now first
    assertEquals(0, reordered[0].position)
    assertEquals(3L, reordered[1].id)   // C is second
    assertEquals(1, reordered[1].position)
    assertEquals(1L, reordered[2].id)   // A is last
    assertEquals(2, reordered[2].position)
}
```

- [ ] **Step 2: Run the failing test**

```
./gradlew :feature:soundboard:impl:testDebugUnitTest --tests "*.SoundBoardViewModelTest.reorderGroups*"
```

Expected: FAIL — `reorderGroups` not defined on `SoundBoardViewModel`

- [ ] **Step 3: Implement `reorderGroups` in `SoundBoardViewModel`**

Replace the contents of `SoundBoardViewModel.kt`:

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

    fun reorderGroups(from: Int, to: Int) {
        val current = groups.value
        val reordered = current.toMutableList()
            .apply { add(to, removeAt(from)) }
            .mapIndexed { index, grp -> grp.copy(position = index) }
        viewModelScope.launch { groupRepository.reorderGroups(reordered) }
    }
}
```

- [ ] **Step 4: Run all ViewModel tests**

```
./gradlew :feature:soundboard:impl:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 5: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt \
        feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt
git commit -m "feat: add reorderGroups to SoundBoardViewModel"
```

---

## Task 4: Reorderable UI — `GroupList` and `GroupCard`

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`

- [ ] **Step 1: Replace `SoundBoardScreen.kt` with the reorderable version**

Replace the entire file contents:

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
                onReorder = { from, to -> viewModel.reorderGroups(from, to) },
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
    onReorder: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(groups, key = { it.id }) { group ->
            ReorderableItem(reorderState, key = group.id) { isDragging ->
                GroupCard(
                    group = group,
                    isDragging = isDragging,
                    dragModifier = Modifier.longPressDraggableHandle(),
                    onNavigateToGroup = { onNavigateToGroup(group.id) },
                    onAddSound = { onAddSound(group.id) },
                    onRename = { newName -> onRenameGroup(group.id, newName) },
                    onDelete = { onDeleteGroup(group.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    isDragging: Boolean,
    dragModifier: Modifier,
    onNavigateToGroup: () -> Unit,
    onAddSound: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val elevation = if (isDragging) 8.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
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

- [ ] **Step 2: Build the feature module**

```
./gradlew :feature:soundboard:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run full test suite**

```
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 4: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt
git commit -m "feat: reorderable groups via long press in SoundBoardScreen"
```

package org.role.samples_button.feature.soundboard.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

    @Test
    fun `reorderGroups delegates reordered list with updated positions to repository`() = runTest {
        val repo = FakeGroupRepository()
        val groupA = Group(id = 1L, name = "A", position = 0, buttons = emptyList())
        val groupB = Group(id = 2L, name = "B", position = 1, buttons = emptyList())
        val groupC = Group(id = 3L, name = "C", position = 2, buttons = emptyList())
        repo.seedGroups(listOf(groupA, groupB, groupC))

        val viewModel = SoundBoardViewModel(repo)

        // Collect groups to activate the StateFlow
        val job = launch {
            viewModel.groups.collect { }
        }
        advanceUntilIdle()
        job.cancel()

        // Move first item (index 0) to last (index 2): [A,B,C] -> [B,C,A]
        viewModel.reorderGroups(from = 0, to = 2)
        advanceUntilIdle()

        val reordered = repo.reorderedLists.last()
        assertEquals(3, reordered.size)
        assertEquals(2L, reordered[0].id)   // B is now first
        assertEquals(0, reordered[0].position)
        assertEquals(3L, reordered[1].id)   // C is second
        assertEquals(1, reordered[1].position)
        assertEquals(1L, reordered[2].id)   // A is last
        assertEquals(2, reordered[2].position)
    }
}

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

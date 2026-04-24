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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.data.SoundButtonRepository
import org.role.samples_button.core.data.UserPreferencesRepository
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

@OptIn(ExperimentalCoroutinesApi::class)
class SoundBoardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(
        groups: FakeGroupRepository = FakeGroupRepository(),
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository()
    ) = SoundBoardViewModel(groups, prefs)

    @Test
    fun `initial groups state is empty list`() = runTest {
        assertEquals(emptyList<Group>(), vm().groups.value)
    }

    @Test
    fun `createGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        vm(groups = repo).createGroup("Percusión")
        assertEquals(listOf("Percusión"), repo.createdGroups)
    }

    @Test
    fun `deleteGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        vm(groups = repo).deleteGroup(42L)
        assertEquals(listOf(42L), repo.deletedIds)
    }

    @Test
    fun `renameGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        vm(groups = repo).renameGroup(5L, "Renamed")
        assertEquals(listOf(5L to "Renamed"), repo.renamedGroups)
    }

    @Test
    fun `reorderGroups delegates reordered list with updated positions to repository`() = runTest {
        val repo = FakeGroupRepository()
        val groupA = Group(id = 1L, name = "A", position = 0, buttons = emptyList())
        val groupB = Group(id = 2L, name = "B", position = 1, buttons = emptyList())
        val groupC = Group(id = 3L, name = "C", position = 2, buttons = emptyList())
        repo.seedGroups(listOf(groupA, groupB, groupC))

        val viewModel = vm(groups = repo)
        val job = launch { viewModel.groups.collect { } }
        advanceUntilIdle()
        job.cancel()

        viewModel.reorderGroups(from = 0, to = 2)
        advanceUntilIdle()

        val reordered = repo.reorderedLists.last()
        assertEquals(3, reordered.size)
        assertEquals(2L, reordered[0].id)
        assertEquals(0, reordered[0].position)
        assertEquals(3L, reordered[1].id)
        assertEquals(1, reordered[1].position)
        assertEquals(1L, reordered[2].id)
        assertEquals(2, reordered[2].position)
    }

    @Test
    fun `hasSeenOnboarding initial value is null`() = runTest {
        assertNull(vm().hasSeenOnboarding.value)
    }

    @Test
    fun `hasSeenOnboarding emits false when prefs returns false`() = runTest {
        val prefs = FakeUserPreferencesRepository(initial = false)
        val viewModel = vm(prefs = prefs)
        val job = launch { viewModel.hasSeenOnboarding.collect { } }
        advanceUntilIdle()
        assertEquals(false, viewModel.hasSeenOnboarding.value)
        job.cancel()
    }

    @Test
    fun `hasSeenOnboarding emits true when prefs returns true`() = runTest {
        val prefs = FakeUserPreferencesRepository(initial = true)
        val viewModel = vm(prefs = prefs)
        val job = launch { viewModel.hasSeenOnboarding.collect { } }
        advanceUntilIdle()
        assertEquals(true, viewModel.hasSeenOnboarding.value)
        job.cancel()
    }

    @Test
    fun `markOnboardingSeen delegates to repository`() = runTest {
        val prefs = FakeUserPreferencesRepository(initial = false)
        val viewModel = vm(prefs = prefs)
        viewModel.markOnboardingSeen()
        advanceUntilIdle()
        assertTrue(prefs.markSeenCalled)
    }
}

// ── Fakes ──────────────────────────────────────────────────────────────────

class FakeGroupRepository : GroupRepository {
    val createdGroups = mutableListOf<String>()
    val deletedIds = mutableListOf<Long>()
    val renamedGroups = mutableListOf<Pair<Long, String>>()
    val reorderedLists = mutableListOf<List<Group>>()
    val _groups = MutableStateFlow<List<Group>>(emptyList())

    override fun getGroupsWithButtons(): Flow<List<Group>> = _groups
    override suspend fun createGroup(name: String) {
        createdGroups.add(name)
        _groups.value = _groups.value + Group(createdGroups.size.toLong(), name, createdGroups.size - 1, emptyList())
    }
    override suspend fun deleteGroup(id: Long) { deletedIds.add(id); _groups.value = _groups.value.filter { it.id != id } }
    override suspend fun renameGroup(id: Long, newName: String) { renamedGroups.add(id to newName); _groups.value = _groups.value.map { if (it.id == id) it.copy(name = newName) else it } }
    override suspend fun reorderGroups(groups: List<Group>) { reorderedLists.add(groups); _groups.value = groups }
    fun seedGroups(groups: List<Group>) { _groups.value = groups }
}

class FakeUserPreferencesRepository(initial: Boolean = false) : UserPreferencesRepository {
    var markSeenCalled = false
    private val _flow = MutableStateFlow(initial)
    override val hasSeenOnboarding: Flow<Boolean> get() = _flow
    override suspend fun markSeen() { markSeenCalled = true; _flow.value = true }
}

class FakeSoundButtonRepository : SoundButtonRepository {
    val deletedIds = mutableListOf<Long>()
    val renamedButtons = mutableListOf<Pair<Long, String>>()
    val reorderedLists = mutableListOf<List<SoundButton>>()
    override suspend fun addButton(label: String, filePath: String, groupId: Long) = Unit
    override suspend fun deleteButton(id: Long) { deletedIds.add(id) }
    override suspend fun renameButton(id: Long, newLabel: String) { renamedButtons.add(id to newLabel) }
    override suspend fun reorderButtons(buttons: List<SoundButton>) { reorderedLists.add(buttons) }
}

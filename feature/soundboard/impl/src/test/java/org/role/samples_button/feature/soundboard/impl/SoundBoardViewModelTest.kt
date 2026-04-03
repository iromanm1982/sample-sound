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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

@OptIn(ExperimentalCoroutinesApi::class)
class SoundBoardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial groups state is empty list`() = runTest {
        val viewModel = SoundBoardViewModel(FakeGroupRepository(), FakeSoundPoolPlayer())
        assertEquals(emptyList<Group>(), viewModel.groups.value)
    }

    @Test
    fun `createGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        val viewModel = SoundBoardViewModel(repo, FakeSoundPoolPlayer())
        viewModel.createGroup("Percusión")
        assertEquals(listOf("Percusión"), repo.createdGroups)
    }

    @Test
    fun `deleteGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        val viewModel = SoundBoardViewModel(repo, FakeSoundPoolPlayer())
        viewModel.deleteGroup(42L)
        assertEquals(listOf(42L), repo.deletedIds)
    }

    @Test
    fun `playSound delegates filePath to player`() = runTest {
        val player = FakeSoundPoolPlayer()
        val viewModel = SoundBoardViewModel(FakeGroupRepository(), player)
        viewModel.playSound("/storage/emulated/0/Music/kick.mp3")
        assertEquals(listOf("/storage/emulated/0/Music/kick.mp3"), player.playedPaths)
    }

    @Test
    fun `playSound multiple times plays all paths`() = runTest {
        val player = FakeSoundPoolPlayer()
        val viewModel = SoundBoardViewModel(FakeGroupRepository(), player)
        viewModel.playSound("/storage/a.mp3")
        viewModel.playSound("/storage/b.mp3")
        viewModel.playSound("/storage/a.mp3")
        assertEquals(
            listOf("/storage/a.mp3", "/storage/b.mp3", "/storage/a.mp3"),
            player.playedPaths
        )
    }

    @Test
    fun `onCleared releases player`() = runTest {
        val player = FakeSoundPoolPlayer()
        val viewModel = SoundBoardViewModel(FakeGroupRepository(), player)
        viewModel.onCleared()
        assertTrue(player.released)
    }
}

class FakeGroupRepository : GroupRepository {
    val createdGroups = mutableListOf<String>()
    val deletedIds = mutableListOf<Long>()
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

    override suspend fun reorderButtons(groupId: Long, buttons: List<SoundButton>) = Unit
}

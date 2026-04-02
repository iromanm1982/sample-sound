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

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

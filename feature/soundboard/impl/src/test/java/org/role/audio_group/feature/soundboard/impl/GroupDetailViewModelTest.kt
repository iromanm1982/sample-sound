package org.role.audio_group.feature.soundboard.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

class FakeDurationReader(private val data: Map<String, Long> = emptyMap()) :
    org.role.audio_group.core.audio.DurationReader {
    override suspend fun getDurationMs(filePath: String): Long = data[filePath] ?: 0L
}

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
        player: FakeSoundPoolPlayer = FakeSoundPoolPlayer(),
        durationReader: FakeDurationReader = FakeDurationReader()
    ): GroupDetailViewModel {
        val handle = SavedStateHandle(mapOf("groupId" to groupId))
        return GroupDetailViewModel(handle, repo, FakeSoundButtonRepository(), player, durationReader)
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
        val vm = GroupDetailViewModel(handle, FakeGroupRepository(), soundButtonRepo, FakeSoundPoolPlayer(), FakeDurationReader())
        vm.deleteButton(99L)
        assertEquals(listOf(99L), soundButtonRepo.deletedIds)
    }

    @Test
    fun `renameButton delegates to repository`() = runTest {
        val soundButtonRepo = FakeSoundButtonRepository()
        val handle = SavedStateHandle(mapOf("groupId" to 1L))
        val vm = GroupDetailViewModel(handle, FakeGroupRepository(), soundButtonRepo, FakeSoundPoolPlayer(), FakeDurationReader())
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
                    player,
                    FakeDurationReader()
                ) as T
        }
        ViewModelProvider(store, factory)[GroupDetailViewModel::class.java]
        store.clear()
        assertTrue(player.released)
    }

    @Test
    fun `reorderButtons moves item and delegates to soundButtonRepository`() = runTest {
        val buttons = listOf(
            org.role.audio_group.core.model.SoundButton(
                id = 1L, label = "A", filePath = "/a.mp3", groupId = 1L, position = 0
            ),
            org.role.audio_group.core.model.SoundButton(
                id = 2L, label = "B", filePath = "/b.mp3", groupId = 1L, position = 1
            ),
            org.role.audio_group.core.model.SoundButton(
                id = 3L, label = "C", filePath = "/c.mp3", groupId = 1L, position = 2
            ),
        )
        val groupRepo = FakeGroupRepository()
        groupRepo.seedGroups(
            listOf(
                org.role.audio_group.core.model.Group(
                    id = 1L, name = "Test", position = 0, buttons = buttons
                )
            )
        )
        val soundButtonRepo = FakeSoundButtonRepository()
        val handle = SavedStateHandle(mapOf("groupId" to 1L))
        val vm = GroupDetailViewModel(handle, groupRepo, soundButtonRepo, FakeSoundPoolPlayer(), FakeDurationReader())

        vm.reorderButtons(from = 0, to = 2)

        val reordered = soundButtonRepo.reorderedLists.last()
        assertEquals(listOf(2L, 3L, 1L), reordered.map { it.id })
        assertEquals(listOf(0, 1, 2), reordered.map { it.position })
    }

    @Test
    fun `toggleLoop adds filePath to loopingPaths when not looping`() = runTest {
        val vm = makeVm(1L)
        vm.toggleLoop("/storage/kick.mp3")
        assertTrue(vm.loopingPaths.value.contains("/storage/kick.mp3"))
    }

    @Test
    fun `toggleLoop removes filePath from loopingPaths when already looping`() = runTest {
        val vm = makeVm(1L)
        vm.toggleLoop("/storage/kick.mp3")
        vm.toggleLoop("/storage/kick.mp3")
        assertFalse(vm.loopingPaths.value.contains("/storage/kick.mp3"))
    }

    @Test
    fun `toggleLoop delegates setLooping true to player`() = runTest {
        val player = FakeSoundPoolPlayer()
        val vm = makeVm(groupId = 1L, player = player)
        vm.toggleLoop("/storage/kick.mp3")
        assertEquals(true, player.loopingStates["/storage/kick.mp3"])
    }

    @Test
    fun `toggleLoop delegates setLooping false when already looping`() = runTest {
        val player = FakeSoundPoolPlayer()
        val vm = makeVm(groupId = 1L, player = player)
        vm.toggleLoop("/storage/kick.mp3")
        vm.toggleLoop("/storage/kick.mp3")
        assertEquals(false, player.loopingStates["/storage/kick.mp3"])
    }

    @Test
    fun `restartSound delegates restart to player`() = runTest {
        val player = FakeSoundPoolPlayer()
        val vm = makeVm(groupId = 1L, player = player)
        vm.restartSound("/storage/kick.mp3")
        assertEquals(listOf("/storage/kick.mp3"), player.restartedPaths)
    }

    @Test
    fun `durations are populated from group buttons via DurationReader`() = runTest {
        val buttons = listOf(
            org.role.audio_group.core.model.SoundButton(
                id = 1L, label = "Kick", filePath = "/kick.mp3", groupId = 1L, position = 0
            ),
            org.role.audio_group.core.model.SoundButton(
                id = 2L, label = "Snare", filePath = "/snare.mp3", groupId = 1L, position = 1
            )
        )
        val repo = FakeGroupRepository()
        repo.seedGroups(
            listOf(org.role.audio_group.core.model.Group(id = 1L, name = "Test", position = 0, buttons = buttons))
        )
        val reader = FakeDurationReader(mapOf("/kick.mp3" to 45_000L, "/snare.mp3" to 22_000L))
        val vm = makeVm(groupId = 1L, repo = repo, durationReader = reader)

        assertEquals(45_000L, vm.durations.value["/kick.mp3"])
        assertEquals(22_000L, vm.durations.value["/snare.mp3"])
    }

    @Test
    fun `seekSound delegates seekTo to player with correct position`() = runTest {
        val buttons = listOf(
            org.role.audio_group.core.model.SoundButton(
                id = 1L, label = "Kick", filePath = "/kick.mp3", groupId = 1L, position = 0
            )
        )
        val repo = FakeGroupRepository()
        repo.seedGroups(listOf(org.role.audio_group.core.model.Group(id = 1L, name = "Test", position = 0, buttons = buttons)))
        val player = FakeSoundPoolPlayer()
        val reader = FakeDurationReader(mapOf("/kick.mp3" to 4000L))
        val vm = makeVm(groupId = 1L, repo = repo, player = player, durationReader = reader)

        vm.seekSound("/kick.mp3", 0.5f)

        assertEquals(listOf("/kick.mp3" to 2000L), player.seekedTo)
    }

    @Test
    fun `seekSound updates progress state immediately`() = runTest {
        val buttons = listOf(
            org.role.audio_group.core.model.SoundButton(
                id = 1L, label = "Kick", filePath = "/kick.mp3", groupId = 1L, position = 0
            )
        )
        val repo = FakeGroupRepository()
        repo.seedGroups(listOf(org.role.audio_group.core.model.Group(id = 1L, name = "Test", position = 0, buttons = buttons)))
        val reader = FakeDurationReader(mapOf("/kick.mp3" to 4000L))
        val vm = makeVm(groupId = 1L, repo = repo, durationReader = reader)

        vm.seekSound("/kick.mp3", 0.75f)

        assertEquals(0.75f, vm.progress.value["/kick.mp3"])
    }

    @Test
    fun `seekSound does nothing when filePath has no duration`() = runTest {
        val player = FakeSoundPoolPlayer()
        val vm = makeVm(groupId = 1L, player = player)

        vm.seekSound("/missing.mp3", 0.5f)

        assertTrue(player.seekedTo.isEmpty())
    }

    @Test
    fun `pauseSound preserves last progress value`() = runTest {
        val buttons = listOf(
            org.role.audio_group.core.model.SoundButton(
                id = 1L, label = "Kick", filePath = "/kick.mp3", groupId = 1L, position = 0
            )
        )
        val repo = FakeGroupRepository()
        repo.seedGroups(listOf(org.role.audio_group.core.model.Group(id = 1L, name = "Test", position = 0, buttons = buttons)))
        val reader = FakeDurationReader(mapOf("/kick.mp3" to 4000L))
        val vm = makeVm(groupId = 1L, repo = repo, durationReader = reader)

        vm.seekSound("/kick.mp3", 0.4f)
        vm.pauseSound("/kick.mp3")

        assertEquals(0.4f, vm.progress.value["/kick.mp3"])
    }

    @Test
    fun `pauseAll preserves progress values`() = runTest {
        val buttons = listOf(
            org.role.audio_group.core.model.SoundButton(
                id = 1L, label = "Kick", filePath = "/kick.mp3", groupId = 1L, position = 0
            )
        )
        val repo = FakeGroupRepository()
        repo.seedGroups(listOf(org.role.audio_group.core.model.Group(id = 1L, name = "Test", position = 0, buttons = buttons)))
        val reader = FakeDurationReader(mapOf("/kick.mp3" to 4000L))
        val vm = makeVm(groupId = 1L, repo = repo, durationReader = reader)

        vm.seekSound("/kick.mp3", 0.6f)
        vm.pauseAll()

        assertEquals(0.6f, vm.progress.value["/kick.mp3"])
    }

    @Test
    fun `progress is updated by polling during playback`() = runTest(testDispatcher) {
        val buttons = listOf(
            org.role.audio_group.core.model.SoundButton(
                id = 1L, label = "Kick", filePath = "/kick.mp3", groupId = 1L, position = 0
            )
        )
        val repo = FakeGroupRepository()
        repo.seedGroups(listOf(org.role.audio_group.core.model.Group(id = 1L, name = "Test", position = 0, buttons = buttons)))
        val player = FakeSoundPoolPlayer(positions = mapOf("/kick.mp3" to 1000L))
        val reader = FakeDurationReader(mapOf("/kick.mp3" to 4000L))
        val vm = makeVm(groupId = 1L, repo = repo, player = player, durationReader = reader)

        vm.playSound("/kick.mp3")
        advanceTimeBy(150)

        assertEquals(0.25f, vm.progress.value["/kick.mp3"])

        vm.pauseSound("/kick.mp3") // stop the infinite polling loop
    }
}

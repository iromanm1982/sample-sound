package org.role.samples_button.feature.browser.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.role.samples_button.core.data.AudioFileRepository
import org.role.samples_button.core.data.SoundButtonRepository
import org.role.samples_button.core.model.AudioFile

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelTest {

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
    fun `initial audioFiles state is empty list`() = runTest {
        val viewModel = FileBrowserViewModel(FakeAudioFileRepository(), FakeSoundButtonRepository())
        assertEquals(emptyList<AudioFile>(), viewModel.audioFiles.value)
    }

    @Test
    fun `addButtonToGroup delegates to repository with correct params`() = runTest {
        val repo = FakeSoundButtonRepository()
        val viewModel = FileBrowserViewModel(FakeAudioFileRepository(), repo)
        viewModel.addButtonToGroup("Kick", "/sdcard/kick.mp3", 1L)
        assertEquals(1, repo.addedButtons.size)
        assertEquals("Kick", repo.addedButtons[0].first)
        assertEquals("/sdcard/kick.mp3", repo.addedButtons[0].second)
        assertEquals(1L, repo.addedButtons[0].third)
    }
}

class FakeAudioFileRepository : AudioFileRepository {
    override fun getAudioFiles(): Flow<List<AudioFile>> = flowOf(emptyList())
}

class FakeSoundButtonRepository : SoundButtonRepository {
    val addedButtons = mutableListOf<Triple<String, String, Long>>()
    val deletedIds = mutableListOf<Long>()

    override suspend fun addButton(label: String, filePath: String, groupId: Long) {
        addedButtons.add(Triple(label, filePath, groupId))
    }

    override suspend fun deleteButton(id: Long) {
        deletedIds.add(id)
    }

    override suspend fun renameButton(id: Long, newLabel: String) = Unit

    override suspend fun reorderButtons(buttons: List<org.role.samples_button.core.model.SoundButton>) = Unit
}

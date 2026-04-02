package org.role.samples_button.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoundButtonRepositoryImplTest {

    @Test
    fun `addButton inserts button with correct data`() = runTest {
        val dao = FakeSoundButtonDao()
        val repo = SoundButtonRepositoryImpl(dao)
        repo.addButton("Kick", "/sdcard/kick.mp3", 1L)
        val buttons = dao.getByGroupId(1L).first()
        assertEquals(1, buttons.size)
        assertEquals("Kick", buttons[0].label)
        assertEquals("/sdcard/kick.mp3", buttons[0].filePath)
        assertEquals(1L, buttons[0].groupId)
        assertEquals(0, buttons[0].position)
    }

    @Test
    fun `addButton assigns sequential positions within same group`() = runTest {
        val dao = FakeSoundButtonDao()
        val repo = SoundButtonRepositoryImpl(dao)
        repo.addButton("First", "/sdcard/a.mp3", 1L)
        repo.addButton("Second", "/sdcard/b.mp3", 1L)
        val buttons = dao.getByGroupId(1L).first()
        assertEquals(0, buttons[0].position)
        assertEquals(1, buttons[1].position)
    }

    @Test
    fun `deleteButton removes button from dao`() = runTest {
        val dao = FakeSoundButtonDao()
        val repo = SoundButtonRepositoryImpl(dao)
        repo.addButton("Kick", "/sdcard/kick.mp3", 1L)
        val id = dao.getByGroupId(1L).first()[0].id
        repo.deleteButton(id)
        assertTrue(dao.getByGroupId(1L).first().isEmpty())
    }
}

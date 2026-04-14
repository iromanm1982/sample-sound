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

    @Test
    fun `renameButton updates label in dao`() = runTest {
        val dao = FakeSoundButtonDao()
        val repo = SoundButtonRepositoryImpl(dao)
        repo.addButton("Kick", "/sdcard/kick.mp3", 1L)
        val id = dao.getByGroupId(1L).first()[0].id
        repo.renameButton(id, "  Bass Drum  ")
        val buttons = dao.getByGroupId(1L).first()
        assertEquals("Bass Drum", buttons[0].label)
    }

    @Test
    fun `reorderButtons updates position for each button in dao`() = runTest {
        val dao = FakeSoundButtonDao()
        val repo = SoundButtonRepositoryImpl(dao)
        repo.addButton("A", "/a.mp3", 1L)
        repo.addButton("B", "/b.mp3", 1L)
        repo.addButton("C", "/c.mp3", 1L)
        val original = dao.getByGroupId(1L).first()
        // Simulate moving first item to last: [A,B,C] -> [B,C,A]
        val reordered = listOf(
            org.role.samples_button.core.model.SoundButton(
                id = original[1].id, label = "B", filePath = "/b.mp3", groupId = 1L, position = 0
            ),
            org.role.samples_button.core.model.SoundButton(
                id = original[2].id, label = "C", filePath = "/c.mp3", groupId = 1L, position = 1
            ),
            org.role.samples_button.core.model.SoundButton(
                id = original[0].id, label = "A", filePath = "/a.mp3", groupId = 1L, position = 2
            ),
        )

        repo.reorderButtons(reordered)

        val updated = dao.getByGroupId(1L).first().sortedBy { it.position }
        assertEquals(original[1].id, updated[0].id)
        assertEquals(0, updated[0].position)
        assertEquals(original[2].id, updated[1].id)
        assertEquals(1, updated[1].position)
        assertEquals(original[0].id, updated[2].id)
        assertEquals(2, updated[2].position)
    }
}

package org.role.samples_button.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.role.samples_button.core.database.SoundButtonEntity

@OptIn(ExperimentalCoroutinesApi::class)
class GroupRepositoryImplTest {

    @Test
    fun `createGroup inserts group with correct name`() = runTest {
        val repo = GroupRepositoryImpl(FakeGroupDao(), FakeSoundButtonDao())
        repo.createGroup("Drums")
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(1, groups.size)
        assertEquals("Drums", groups[0].name)
    }

    @Test
    fun `deleteGroup removes group from list`() = runTest {
        val dao = FakeGroupDao()
        val repo = GroupRepositoryImpl(dao, FakeSoundButtonDao())
        repo.createGroup("Drums")
        val id = repo.getGroupsWithButtons().first()[0].id
        repo.deleteGroup(id)
        assertTrue(repo.getGroupsWithButtons().first().isEmpty())
    }

    @Test
    fun `createGroup assigns sequential positions`() = runTest {
        val repo = GroupRepositoryImpl(FakeGroupDao(), FakeSoundButtonDao())
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
        val repo = GroupRepositoryImpl(groupDao, buttonDao)
        repo.createGroup("Drums")
        val groupId = repo.getGroupsWithButtons().first()[0].id
        buttonDao.insert(
            SoundButtonEntity(label = "Kick", filePath = "/kick.mp3", groupId = groupId, position = 0)
        )
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(1, groups[0].buttons.size)
        assertEquals("Kick", groups[0].buttons[0].label)
    }
}

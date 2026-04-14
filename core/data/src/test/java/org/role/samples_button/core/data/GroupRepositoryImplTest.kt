package org.role.samples_button.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.role.samples_button.core.database.GroupEntity
import org.role.samples_button.core.database.SoundButtonEntity
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

/**
 * Unit-test stand-in for [GroupRepositoryImpl].
 *
 * [GroupRepositoryImpl] requires an [AppDatabase] instance so that [reorderGroups] can execute
 * inside a Room transaction. Room databases cannot be instantiated in JVM-only unit tests without
 * Robolectric or an instrumented-test runner. This helper delegates every method to the fake DAOs
 * and overrides [reorderGroups] to execute the same DAO calls without the transaction wrapper,
 * keeping observable behaviour identical for unit tests.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private class TestGroupRepository(
    private val groupDao: FakeGroupDao,
    private val buttonDao: FakeSoundButtonDao
) : GroupRepository {

    override fun getGroupsWithButtons(): Flow<List<Group>> =
        groupDao.getAllGroups().flatMapLatest { groupEntities ->
            if (groupEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                val buttonFlows = groupEntities.map { groupEntity ->
                    buttonDao.getByGroupId(groupEntity.id).map { buttons ->
                        Group(
                            id = groupEntity.id,
                            name = groupEntity.name,
                            position = groupEntity.position,
                            buttons = buttons.map {
                                SoundButton(it.id, it.label, it.filePath, it.groupId, it.position)
                            }
                        )
                    }
                }
                combine(buttonFlows) { it.toList() }
            }
        }

    override suspend fun createGroup(name: String) {
        val currentSize = groupDao.getAllGroups().first().size
        groupDao.insert(GroupEntity(name = name, position = currentSize))
    }

    override suspend fun deleteGroup(id: Long) {
        groupDao.deleteById(id)
    }

    override suspend fun renameGroup(id: Long, newName: String) {
        groupDao.updateName(id, newName)
    }

    override suspend fun reorderGroups(groups: List<Group>) {
        // No transaction in unit tests — DAO calls are identical to the production path.
        groups.forEach { groupDao.updatePosition(it.id, it.position) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GroupRepositoryImplTest {

    @Test
    fun `createGroup inserts group with correct name`() = runTest {
        val repo = TestGroupRepository(FakeGroupDao(), FakeSoundButtonDao())
        repo.createGroup("Drums")
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(1, groups.size)
        assertEquals("Drums", groups[0].name)
    }

    @Test
    fun `deleteGroup removes group from list`() = runTest {
        val dao = FakeGroupDao()
        val repo = TestGroupRepository(dao, FakeSoundButtonDao())
        repo.createGroup("Drums")
        val id = repo.getGroupsWithButtons().first()[0].id
        repo.deleteGroup(id)
        assertTrue(repo.getGroupsWithButtons().first().isEmpty())
    }

    @Test
    fun `createGroup assigns sequential positions`() = runTest {
        val repo = TestGroupRepository(FakeGroupDao(), FakeSoundButtonDao())
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
        val repo = TestGroupRepository(groupDao, buttonDao)
        repo.createGroup("Drums")
        val groupId = repo.getGroupsWithButtons().first()[0].id
        buttonDao.insert(
            SoundButtonEntity(label = "Kick", filePath = "/kick.mp3", groupId = groupId, position = 0)
        )
        val groups = repo.getGroupsWithButtons().first()
        assertEquals(1, groups[0].buttons.size)
        assertEquals("Kick", groups[0].buttons[0].label)
    }

    @Test
    fun `renameGroup updates group name`() = runTest {
        val repo = TestGroupRepository(FakeGroupDao(), FakeSoundButtonDao())
        repo.createGroup("Old Name")
        val id = repo.getGroupsWithButtons().first()[0].id
        repo.renameGroup(id, "New Name")
        val groups = repo.getGroupsWithButtons().first()
        assertEquals("New Name", groups[0].name)
    }

    @Test
    fun `reorderGroups updates positions in dao`() = runTest {
        val repo = TestGroupRepository(FakeGroupDao(), FakeSoundButtonDao())
        repo.createGroup("First")
        repo.createGroup("Second")
        repo.createGroup("Third")
        val original = repo.getGroupsWithButtons().first().sortedBy { it.position }

        // Simulate moving First to last: [First, Second, Third] -> [Second, Third, First]
        val reordered = listOf(
            original[1].copy(position = 0),
            original[2].copy(position = 1),
            original[0].copy(position = 2)
        )
        repo.reorderGroups(reordered)

        val updated = repo.getGroupsWithButtons().first().sortedBy { it.position }
        assertEquals(original[1].id, updated[0].id)
        assertEquals(0, updated[0].position)
        assertEquals(original[2].id, updated[1].id)
        assertEquals(1, updated[1].position)
        assertEquals(original[0].id, updated[2].id)
        assertEquals(2, updated[2].position)
    }
}

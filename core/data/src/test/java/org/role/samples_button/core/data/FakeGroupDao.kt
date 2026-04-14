package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.role.samples_button.core.database.GroupDao
import org.role.samples_button.core.database.GroupEntity

class FakeGroupDao : GroupDao {
    private val groups = mutableListOf<GroupEntity>()
    private val flow = MutableStateFlow<List<GroupEntity>>(emptyList())
    private var nextId = 1L

    override fun getAllGroups(): Flow<List<GroupEntity>> = flow

    override suspend fun insert(group: GroupEntity): Long {
        val entity = group.copy(id = nextId++)
        groups.add(entity)
        flow.value = groups.toList()
        return entity.id
    }

    override suspend fun deleteById(id: Long) {
        groups.removeAll { it.id == id }
        flow.value = groups.toList()
    }

    override suspend fun updateName(id: Long, name: String) {
        val index = groups.indexOfFirst { it.id == id }
        if (index >= 0) {
            groups[index] = groups[index].copy(name = name)
            flow.value = groups.toList()
        }
    }

    override suspend fun updatePosition(id: Long, position: Int) {
        val index = groups.indexOfFirst { it.id == id }
        if (index >= 0) {
            groups[index] = groups[index].copy(position = position)
            flow.value = groups.sortedBy { it.position }
        }
    }
}

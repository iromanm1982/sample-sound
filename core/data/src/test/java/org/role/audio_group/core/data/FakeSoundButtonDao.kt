package org.role.audio_group.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.role.audio_group.core.database.SoundButtonDao
import org.role.audio_group.core.database.SoundButtonEntity

class FakeSoundButtonDao : SoundButtonDao {
    private val map = mutableMapOf<Long, MutableList<SoundButtonEntity>>()
    private val flows = mutableMapOf<Long, MutableStateFlow<List<SoundButtonEntity>>>()
    private var nextId = 1L

    private fun flowFor(groupId: Long): MutableStateFlow<List<SoundButtonEntity>> =
        flows.getOrPut(groupId) { MutableStateFlow(emptyList()) }

    override fun getByGroupId(groupId: Long): Flow<List<SoundButtonEntity>> = flowFor(groupId)

    override suspend fun insert(button: SoundButtonEntity): Long {
        val entity = button.copy(id = nextId++)
        map.getOrPut(entity.groupId) { mutableListOf() }.add(entity)
        flowFor(entity.groupId).value = map[entity.groupId]!!.toList()
        return entity.id
    }

    override suspend fun deleteById(id: Long) {
        map.forEach { (groupId, list) ->
            if (list.removeAll { it.id == id }) {
                flowFor(groupId).value = list.toList()
            }
        }
    }

    override suspend fun updateLabel(id: Long, label: String) {
        map.forEach { (groupId, list) ->
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = list[index].copy(label = label)
                flowFor(groupId).value = list.toList()
            }
        }
    }

    override suspend fun updatePosition(id: Long, position: Int) {
        map.forEach { (groupId, list) ->
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = list[index].copy(position = position)
                flowFor(groupId).value = list.toList()
            }
        }
    }
}

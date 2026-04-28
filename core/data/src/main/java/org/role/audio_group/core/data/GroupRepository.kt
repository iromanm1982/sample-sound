package org.role.audio_group.core.data

import kotlinx.coroutines.flow.Flow
import org.role.audio_group.core.model.Group

interface GroupRepository {
    fun getGroupsWithButtons(): Flow<List<Group>>
    suspend fun createGroup(name: String)
    suspend fun deleteGroup(id: Long)
    suspend fun renameGroup(id: Long, newName: String)
    suspend fun reorderGroups(groups: List<Group>)
}

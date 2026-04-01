package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

interface GroupRepository {
    fun getGroupsWithButtons(): Flow<List<Group>>
    suspend fun createGroup(name: String)
    suspend fun deleteGroup(id: Long)
    suspend fun reorderButtons(groupId: Long, buttons: List<SoundButton>)
}

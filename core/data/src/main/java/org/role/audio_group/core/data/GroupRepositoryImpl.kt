package org.role.audio_group.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.role.audio_group.core.database.AppDatabase
import org.role.audio_group.core.database.GroupDao
import org.role.audio_group.core.database.GroupEntity
import org.role.audio_group.core.database.SoundButtonDao
import org.role.audio_group.core.model.Group
import org.role.audio_group.core.model.SoundButton
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val soundButtonDao: SoundButtonDao,
    private val database: AppDatabase
) : GroupRepository {

    override fun getGroupsWithButtons(): Flow<List<Group>> =
        groupDao.getAllGroups().flatMapLatest { groupEntities ->
            if (groupEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                val buttonFlows = groupEntities.map { groupEntity ->
                    soundButtonDao.getByGroupId(groupEntity.id).map { buttons ->
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
        database.withTransaction {
            groups.forEach { groupDao.updatePosition(it.id, it.position) }
        }
    }
}

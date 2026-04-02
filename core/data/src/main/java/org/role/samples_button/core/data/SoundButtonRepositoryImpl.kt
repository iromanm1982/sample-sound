package org.role.samples_button.core.data

import kotlinx.coroutines.flow.first
import org.role.samples_button.core.database.SoundButtonDao
import org.role.samples_button.core.database.SoundButtonEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundButtonRepositoryImpl @Inject constructor(
    private val soundButtonDao: SoundButtonDao
) : SoundButtonRepository {

    override suspend fun addButton(label: String, filePath: String, groupId: Long) {
        val position = soundButtonDao.getByGroupId(groupId).first().size
        soundButtonDao.insert(
            SoundButtonEntity(label = label, filePath = filePath, groupId = groupId, position = position)
        )
    }

    override suspend fun deleteButton(id: Long) {
        soundButtonDao.deleteById(id)
    }
}

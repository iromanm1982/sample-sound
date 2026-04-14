package org.role.samples_button.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import org.role.samples_button.core.database.AppDatabase
import org.role.samples_button.core.database.SoundButtonDao
import org.role.samples_button.core.database.SoundButtonEntity
import org.role.samples_button.core.model.SoundButton
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundButtonRepositoryImpl @Inject constructor(
    private val soundButtonDao: SoundButtonDao,
    private val database: AppDatabase
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

    override suspend fun renameButton(id: Long, newLabel: String) {
        soundButtonDao.updateLabel(id, newLabel.trim())
    }

    override suspend fun reorderButtons(buttons: List<SoundButton>) {
        database.withTransaction {
            buttons.forEach { soundButtonDao.updatePosition(it.id, it.position) }
        }
    }
}

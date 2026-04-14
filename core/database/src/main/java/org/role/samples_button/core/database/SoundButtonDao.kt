package org.role.samples_button.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundButtonDao {
    @Query("SELECT * FROM sound_buttons WHERE groupId = :groupId ORDER BY position ASC")
    fun getByGroupId(groupId: Long): Flow<List<SoundButtonEntity>>

    @Insert
    suspend fun insert(button: SoundButtonEntity): Long

    @Query("DELETE FROM sound_buttons WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sound_buttons SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String)

    @Query("UPDATE sound_buttons SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
}

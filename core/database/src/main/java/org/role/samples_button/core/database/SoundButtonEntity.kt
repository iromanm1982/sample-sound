package org.role.samples_button.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sound_buttons")
data class SoundButtonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val filePath: String,
    val groupId: Long,
    val position: Int
)

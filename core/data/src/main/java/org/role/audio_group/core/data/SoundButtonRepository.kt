package org.role.audio_group.core.data

import org.role.audio_group.core.model.SoundButton

interface SoundButtonRepository {
    suspend fun addButton(label: String, filePath: String, groupId: Long)
    suspend fun deleteButton(id: Long)
    suspend fun renameButton(id: Long, newLabel: String)
    suspend fun reorderButtons(buttons: List<SoundButton>)
}

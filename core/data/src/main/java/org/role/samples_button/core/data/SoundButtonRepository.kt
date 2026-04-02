package org.role.samples_button.core.data

interface SoundButtonRepository {
    suspend fun addButton(label: String, filePath: String, groupId: Long)
    suspend fun deleteButton(id: Long)
}

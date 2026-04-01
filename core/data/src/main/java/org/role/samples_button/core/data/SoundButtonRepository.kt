package org.role.samples_button.core.data

import org.role.samples_button.core.model.SoundButton

interface SoundButtonRepository {
    suspend fun addButton(button: SoundButton)
    suspend fun deleteButton(id: Long)
}

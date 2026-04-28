package org.role.audio_group.core.model

data class SoundButton(
    val id: Long,
    val label: String,
    val filePath: String,
    val groupId: Long,
    val position: Int
)

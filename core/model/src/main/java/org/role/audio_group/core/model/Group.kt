package org.role.audio_group.core.model

data class Group(
    val id: Long,
    val name: String,
    val position: Int,
    val buttons: List<SoundButton>
)

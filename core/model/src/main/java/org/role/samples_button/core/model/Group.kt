package org.role.samples_button.core.model

data class Group(
    val id: Long,
    val name: String,
    val position: Int,
    val buttons: List<SoundButton>
)

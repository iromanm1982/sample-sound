package org.role.samples_button.core.audio

interface SoundPoolPlayer {
    fun play(filePath: String)
    fun release()
}

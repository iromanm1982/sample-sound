package org.role.samples_button.core.audio

interface SoundPoolPlayer {
    fun play(filePath: String)   // new-play or resume, manager decides
    fun pause(filePath: String)
    fun pauseAll()
    fun release()
    fun restart(filePath: String)
    fun setLooping(filePath: String, loop: Boolean)
}

package org.role.samples_button.core.audio

interface SoundPoolPlayer {
    fun play(filePath: String)
    fun pause(filePath: String)
    fun pauseAll()
    fun release()
    fun restart(filePath: String)
    fun setLooping(filePath: String, loop: Boolean)
    fun getCurrentPositionMs(filePath: String): Long
    fun seekTo(filePath: String, positionMs: Long)
}

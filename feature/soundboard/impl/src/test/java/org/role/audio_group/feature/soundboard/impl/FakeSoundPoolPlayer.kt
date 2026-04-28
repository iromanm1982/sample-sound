package org.role.audio_group.feature.soundboard.impl

import org.role.audio_group.core.audio.SoundPoolPlayer

class FakeSoundPoolPlayer(
    var positions: Map<String, Long> = emptyMap()
) : SoundPoolPlayer {
    val playedPaths = mutableListOf<String>()
    val pausedPaths = mutableListOf<String>()
    val restartedPaths = mutableListOf<String>()
    val loopingStates = mutableMapOf<String, Boolean>()
    val seekedTo = mutableListOf<Pair<String, Long>>()
    var pauseAllCalled = false
    var released = false

    override fun play(filePath: String) { playedPaths += filePath }
    override fun pause(filePath: String) { pausedPaths += filePath }
    override fun pauseAll() { pauseAllCalled = true }
    override fun release() { released = true }
    override fun restart(filePath: String) { restartedPaths += filePath }
    override fun setLooping(filePath: String, loop: Boolean) { loopingStates[filePath] = loop }
    override fun getCurrentPositionMs(filePath: String): Long = positions[filePath] ?: 0L
    override fun seekTo(filePath: String, positionMs: Long) { seekedTo += filePath to positionMs }
}

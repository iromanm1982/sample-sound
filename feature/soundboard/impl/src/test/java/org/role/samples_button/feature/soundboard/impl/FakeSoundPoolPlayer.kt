package org.role.samples_button.feature.soundboard.impl

import org.role.samples_button.core.audio.SoundPoolPlayer

class FakeSoundPoolPlayer : SoundPoolPlayer {
    val playedPaths = mutableListOf<String>()
    val pausedPaths = mutableListOf<String>()
    val restartedPaths = mutableListOf<String>()
    val loopingStates = mutableMapOf<String, Boolean>()
    var pauseAllCalled = false
    var released = false

    override fun play(filePath: String) { playedPaths += filePath }
    override fun pause(filePath: String) { pausedPaths += filePath }
    override fun pauseAll() { pauseAllCalled = true }
    override fun release() { released = true }
    override fun restart(filePath: String) { restartedPaths += filePath }
    override fun setLooping(filePath: String, loop: Boolean) { loopingStates[filePath] = loop }
}

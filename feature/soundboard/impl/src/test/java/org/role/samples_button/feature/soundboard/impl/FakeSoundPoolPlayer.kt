package org.role.samples_button.feature.soundboard.impl

import org.role.samples_button.core.audio.SoundPoolPlayer

class FakeSoundPoolPlayer : SoundPoolPlayer {
    val playedPaths = mutableListOf<String>()
    var released = false

    override fun play(filePath: String) {
        playedPaths += filePath
    }

    override fun release() {
        released = true
    }
}

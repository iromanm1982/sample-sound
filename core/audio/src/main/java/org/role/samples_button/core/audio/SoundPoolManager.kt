package org.role.samples_button.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundPoolManager @Inject constructor(
    @ApplicationContext context: Context
) : SoundPoolPlayer {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        ).build()

    private val soundIds = mutableMapOf<String, Int>()
    private val pendingPlay = mutableSetOf<String>()

    init {
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            if (status == 0) {
                val path = soundIds.entries.find { it.value == soundId }?.key
                if (path != null && pendingPlay.remove(path)) {
                    soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
                }
            }
        }
    }

    override fun play(filePath: String) {
        val existing = soundIds[filePath]
        if (existing != null) {
            soundPool.play(existing, 1f, 1f, 1, 0, 1f)
        } else {
            pendingPlay.add(filePath)
            soundIds[filePath] = soundPool.load(filePath, 1)
        }
    }

    override fun release() {
        soundPool.release()
        soundIds.clear()
        pendingPlay.clear()
    }
}

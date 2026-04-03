package org.role.samples_button.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundPoolManager @Inject constructor(
    @Suppress("UnusedParameter") @ApplicationContext context: Context
) : SoundPoolPlayer {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        ).build()

    private val lock = Any()
    private val soundIds = mutableMapOf<String, Int>()       // filePath → soundId
    private val soundIdToPath = mutableMapOf<Int, String>()  // soundId → filePath (reverse map)
    private val pendingPlay = mutableSetOf<String>()         // waiting for OnLoadComplete

    init {
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            if (status == 0) {
                val shouldPlay = synchronized(lock) {
                    val path = soundIdToPath[soundId]
                    if (path != null) pendingPlay.remove(path) else false
                }
                if (shouldPlay) {
                    soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
                }
            }
        }
    }

    override fun play(filePath: String) {
        synchronized(lock) {
            val existing = soundIds[filePath]
            if (existing != null) {
                soundPool.play(existing, 1f, 1f, 1, 0, 1f)
            } else {
                val soundId = soundPool.load(filePath, 1)
                if (soundId > 0) {
                    pendingPlay.add(filePath)
                    soundIds[filePath] = soundId
                    soundIdToPath[soundId] = filePath
                }
                // if soundId <= 0: load() failed — silently skip (file may be missing/corrupt)
            }
        }
    }

    override fun release() {
        synchronized(lock) {
            soundIds.clear()
            soundIdToPath.clear()
            pendingPlay.clear()
        }
        soundPool.release()
    }
}

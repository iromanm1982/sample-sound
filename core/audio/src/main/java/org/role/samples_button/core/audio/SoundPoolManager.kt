package org.role.samples_button.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
class SoundPoolManager @Inject constructor(
    @Suppress("UnusedParameter") @ApplicationContext context: Context
) : SoundPoolPlayer {

    private val maxStreams = 8
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val lock = Any()
    // LinkedHashMap preserves insertion order — oldest entry is evicted when pool is full
    private val activePlayers = LinkedHashMap<String, MediaPlayer>()

    override fun play(filePath: String) {
        synchronized(lock) {
            val existing = activePlayers[filePath]
            if (existing != null) {
                // Resume if paused, no-op if already playing
                if (!safeIsPlaying(existing)) {
                    try { existing.start() } catch (_: Exception) {}
                }
                return
            }
            val player = acquirePlayer(filePath)
            try {
                player.reset()
                player.setAudioAttributes(audioAttributes)
                player.setDataSource(filePath)
                player.setOnPreparedListener { it.start() }
                player.setOnErrorListener { _, _, _ -> true }
                player.prepareAsync()
            } catch (e: Exception) {
                try { player.reset() } catch (_: Exception) {}
                activePlayers.remove(filePath)
            }
        }
    }

    override fun pause(filePath: String) {
        synchronized(lock) {
            val player = activePlayers[filePath] ?: return
            if (safeIsPlaying(player)) {
                try { player.pause() } catch (_: Exception) {}
            }
        }
    }

    override fun pauseAll() {
        synchronized(lock) {
            activePlayers.values.forEach { player ->
                if (safeIsPlaying(player)) {
                    try { player.pause() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun acquirePlayer(forPath: String): MediaPlayer {
        return if (activePlayers.size < maxStreams) {
            MediaPlayer().also { activePlayers[forPath] = it }
        } else {
            // Evict oldest entry and reuse its player
            val oldestPath = activePlayers.keys.first()
            val player = activePlayers.remove(oldestPath)!!
            try { player.stop() } catch (_: Exception) {}
            activePlayers[forPath] = player
            player
        }
    }

    private fun safeIsPlaying(player: MediaPlayer): Boolean =
        try { player.isPlaying } catch (_: Exception) { false }

    override fun release() {
        synchronized(lock) {
            activePlayers.values.forEach { try { it.release() } catch (_: Exception) {} }
            activePlayers.clear()
        }
    }
}

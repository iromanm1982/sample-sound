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
    // Paths that should not start playing when prepareAsync completes
    private val pendingPause = mutableSetOf<String>()

    override fun play(filePath: String) {
        synchronized(lock) {
            val existing = activePlayers[filePath]
            if (existing != null) {
                // Cancel any pending pause request for this path
                pendingPause.remove(filePath)
                // Refresh LRU position so a just-resumed player isn't evicted immediately
                activePlayers.remove(filePath)
                activePlayers[filePath] = existing
                if (!safeIsPlaying(existing)) {
                    try { existing.start() } catch (_: Exception) {}
                }
                return
            }
            val player = acquirePlayer(filePath)
            try {
                player.reset() // reset() clears any previously set listeners
                player.setAudioAttributes(audioAttributes)
                player.setDataSource(filePath)
                player.setOnPreparedListener { mp ->
                    // Check pendingPause under lock — pause() may have been called during preparation
                    val shouldStart = synchronized(lock) { !pendingPause.remove(filePath) }
                    if (shouldStart) {
                        try { mp.start() } catch (_: Exception) {}
                    }
                }
                player.setOnErrorListener { _, _, _ -> true }
                player.prepareAsync()
            } catch (e: Exception) {
                try { player.reset() } catch (_: Exception) {}
                activePlayers.remove(filePath)
                pendingPause.remove(filePath)
            }
        }
    }

    override fun pause(filePath: String) {
        synchronized(lock) {
            val player = activePlayers[filePath] ?: return
            // Mark as pending so OnPreparedListener won't start if still preparing
            pendingPause.add(filePath)
            if (safeIsPlaying(player)) {
                try { player.pause() } catch (_: Exception) {}
            }
        }
    }

    override fun pauseAll() {
        synchronized(lock) {
            activePlayers.forEach { (path, player) ->
                pendingPause.add(path)
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
            // Evict oldest entry and reuse its player; reset() called next clears its listeners
            val oldestPath = activePlayers.keys.first()
            val player = activePlayers.remove(oldestPath)!!
            pendingPause.remove(oldestPath)
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
            pendingPause.clear()
        }
    }

    override fun restart(filePath: String) {
        // TODO: implement in Task 2
    }

    override fun setLooping(filePath: String, loop: Boolean) {
        // TODO: implement in Task 2
    }
}

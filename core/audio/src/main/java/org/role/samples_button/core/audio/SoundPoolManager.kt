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
    private val players = mutableListOf<MediaPlayer>()

    override fun play(filePath: String) {
        val player = synchronized(lock) { acquirePlayer() }
        try {
            player.reset()
            player.setAudioAttributes(audioAttributes)
            player.setDataSource(filePath)
            player.setOnPreparedListener { it.start() }
            player.setOnErrorListener { _, _, _ -> true }
            player.prepareAsync()
        } catch (e: Exception) {
            try { player.reset() } catch (_: Exception) {}
        }
    }

    private fun acquirePlayer(): MediaPlayer {
        // Prefer an idle player
        players.firstOrNull { !safeIsPlaying(it) }?.let { return it }
        // Create new if under the limit
        if (players.size < maxStreams) {
            return MediaPlayer().also { players.add(it) }
        }
        // Reuse the oldest player (stop it first)
        val oldest = players.first()
        try { oldest.stop() } catch (_: Exception) {}
        return oldest
    }

    private fun safeIsPlaying(player: MediaPlayer): Boolean =
        try { player.isPlaying } catch (_: Exception) { false }

    override fun release() {
        synchronized(lock) {
            players.forEach { try { it.release() } catch (_: Exception) {} }
            players.clear()
        }
    }
}

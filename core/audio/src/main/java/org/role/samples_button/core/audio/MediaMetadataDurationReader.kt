package org.role.samples_button.core.audio

import android.media.MediaMetadataRetriever
import javax.inject.Inject

class MediaMetadataDurationReader @Inject constructor() : DurationReader {
    override fun getDurationMs(filePath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }
}

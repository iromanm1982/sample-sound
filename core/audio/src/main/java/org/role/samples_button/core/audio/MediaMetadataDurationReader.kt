package org.role.samples_button.core.audio

import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaMetadataDurationReader @Inject constructor() : DurationReader {
    override suspend fun getDurationMs(filePath: String): Long =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
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

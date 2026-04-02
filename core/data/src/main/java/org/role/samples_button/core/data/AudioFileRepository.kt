package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow
import org.role.samples_button.core.model.AudioFile

interface AudioFileRepository {
    fun getAudioFiles(): Flow<List<AudioFile>>
}

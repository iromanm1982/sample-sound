package org.role.audio_group.core.data

import kotlinx.coroutines.flow.Flow
import org.role.audio_group.core.model.AudioFile

interface AudioFileRepository {
    fun getAudioFiles(): Flow<List<AudioFile>>
}

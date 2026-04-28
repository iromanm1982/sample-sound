package org.role.audio_group.core.data

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.role.audio_group.core.model.AudioFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioFileRepository {

    override fun getAudioFiles(): Flow<List<AudioFile>> = flow {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA
        )
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )
        val files = mutableListOf<AudioFile>()
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (it.moveToNext()) {
                val displayName = it.getString(nameCol).substringBeforeLast(".")
                files += AudioFile(it.getLong(idCol), displayName, it.getString(dataCol))
            }
        }
        emit(files)
    }
}

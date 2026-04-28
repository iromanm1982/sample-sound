package org.role.audio_group.core.audio

interface DurationReader {
    /** Devuelve la duración en milisegundos, o 0L si no se puede leer. */
    suspend fun getDurationMs(filePath: String): Long
}

package org.role.samples_button.core.audio

interface DurationReader {
    /** Devuelve la duración en milisegundos, o 0L si no se puede leer. */
    suspend fun getDurationMs(filePath: String): Long
}

package org.role.audio_group.core.data

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val hasSeenOnboarding: Flow<Boolean>
    suspend fun markSeen()
}

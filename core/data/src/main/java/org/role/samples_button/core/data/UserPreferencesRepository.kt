package org.role.samples_button.core.data

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val hasSeenOnboarding: Flow<Boolean>
    suspend fun markSeen()
}

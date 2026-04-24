package org.role.samples_button.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    private val hasSeenKey = booleanPreferencesKey("has_seen_onboarding")

    override val hasSeenOnboarding: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[hasSeenKey] ?: false }

    override suspend fun markSeen() {
        dataStore.edit { prefs -> prefs[hasSeenKey] = true }
    }
}

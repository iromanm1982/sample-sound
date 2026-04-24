package org.role.samples_button.feature.onboarding.impl

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.role.samples_button.core.data.UserPreferencesRepository
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    suspend fun markSeen() {
        userPreferencesRepository.markSeen()
    }
}

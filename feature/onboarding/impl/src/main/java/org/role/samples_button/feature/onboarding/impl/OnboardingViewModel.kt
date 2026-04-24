package org.role.samples_button.feature.onboarding.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.role.samples_button.core.data.UserPreferencesRepository
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    fun markSeen() {
        viewModelScope.launch { userPreferencesRepository.markSeen() }
    }
}

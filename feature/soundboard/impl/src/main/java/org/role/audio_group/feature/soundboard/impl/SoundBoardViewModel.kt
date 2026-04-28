package org.role.audio_group.feature.soundboard.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.role.audio_group.core.data.GroupRepository
import org.role.audio_group.core.data.UserPreferencesRepository
import org.role.audio_group.core.model.Group
import javax.inject.Inject

@HiltViewModel
class SoundBoardViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val groups: StateFlow<List<Group>> = groupRepository
        .getGroupsWithButtons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasSeenOnboarding: StateFlow<Boolean?> = userPreferencesRepository
        .hasSeenOnboarding
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markOnboardingSeen() {
        viewModelScope.launch { userPreferencesRepository.markSeen() }
    }

    fun createGroup(name: String) {
        viewModelScope.launch { groupRepository.createGroup(name) }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch { groupRepository.deleteGroup(id) }
    }

    fun renameGroup(id: Long, newName: String) {
        viewModelScope.launch { groupRepository.renameGroup(id, newName) }
    }

    fun reorderGroups(from: Int, to: Int) {
        val current = groups.value
        val reordered = current.toMutableList()
            .apply { add(to, removeAt(from)) }
            .mapIndexed { index, grp -> grp.copy(position = index) }
        viewModelScope.launch { groupRepository.reorderGroups(reordered) }
    }
}

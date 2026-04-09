package org.role.samples_button.feature.soundboard.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.role.samples_button.core.audio.SoundPoolPlayer
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.data.SoundButtonRepository
import org.role.samples_button.core.model.Group
import javax.inject.Inject

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val soundButtonRepository: SoundButtonRepository,
    private val soundPoolPlayer: SoundPoolPlayer
) : ViewModel() {

    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    val group: StateFlow<Group?> = groupRepository
        .getGroupsWithButtons()
        .map { groups -> groups.find { it.id == groupId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _playingPaths = MutableStateFlow<Set<String>>(emptySet())
    val playingPaths: StateFlow<Set<String>> = _playingPaths.asStateFlow()

    fun playSound(filePath: String) {
        soundPoolPlayer.play(filePath)
        _playingPaths.update { it + filePath }
    }

    fun pauseSound(filePath: String) {
        soundPoolPlayer.pause(filePath)
        _playingPaths.update { it - filePath }
    }

    fun pauseAll() {
        soundPoolPlayer.pauseAll()
        _playingPaths.value = emptySet()
    }

    fun deleteButton(id: Long) {
        viewModelScope.launch { soundButtonRepository.deleteButton(id) }
    }

    fun renameButton(id: Long, newLabel: String) {
        viewModelScope.launch { soundButtonRepository.renameButton(id, newLabel) }
    }

    override fun onCleared() {
        soundPoolPlayer.release()
    }
}

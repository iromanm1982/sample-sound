package org.role.samples_button.feature.soundboard.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.role.samples_button.core.audio.SoundPoolPlayer
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.model.Group
import javax.inject.Inject

@HiltViewModel
class SoundBoardViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val soundPoolPlayer: SoundPoolPlayer
) : ViewModel() {

    val groups: StateFlow<List<Group>> = groupRepository
        .getGroupsWithButtons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _playingPaths = MutableStateFlow<Set<String>>(emptySet())
    val playingPaths: StateFlow<Set<String>> = _playingPaths.asStateFlow()

    fun createGroup(name: String) {
        viewModelScope.launch { groupRepository.createGroup(name) }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch { groupRepository.deleteGroup(id) }
    }

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

    override fun onCleared() {
        soundPoolPlayer.release()
    }
}

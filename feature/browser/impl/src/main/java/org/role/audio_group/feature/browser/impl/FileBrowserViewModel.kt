package org.role.audio_group.feature.browser.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.role.audio_group.core.data.AudioFileRepository
import org.role.audio_group.core.data.SoundButtonRepository
import org.role.audio_group.core.model.AudioFile
import javax.inject.Inject

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val audioFileRepository: AudioFileRepository,
    private val soundButtonRepository: SoundButtonRepository
) : ViewModel() {

    private val allAudioFiles: StateFlow<List<AudioFile>> = audioFileRepository
        .getAudioFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchQuery = MutableStateFlow("")

    val audioFiles: StateFlow<List<AudioFile>> = combine(allAudioFiles, searchQuery) { files, query ->
        if (query.isBlank()) files
        else files.filter { it.displayName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addButtonToGroup(label: String, filePath: String, groupId: Long) {
        viewModelScope.launch {
            soundButtonRepository.addButton(label.trim(), filePath, groupId)
        }
    }
}

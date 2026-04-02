package org.role.samples_button.feature.browser.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.role.samples_button.core.data.AudioFileRepository
import org.role.samples_button.core.data.SoundButtonRepository
import org.role.samples_button.core.model.AudioFile
import javax.inject.Inject

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val audioFileRepository: AudioFileRepository,
    private val soundButtonRepository: SoundButtonRepository
) : ViewModel() {

    val audioFiles: StateFlow<List<AudioFile>> = audioFileRepository
        .getAudioFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addButtonToGroup(label: String, filePath: String, groupId: Long) {
        viewModelScope.launch {
            soundButtonRepository.addButton(label.trim(), filePath, groupId)
        }
    }
}

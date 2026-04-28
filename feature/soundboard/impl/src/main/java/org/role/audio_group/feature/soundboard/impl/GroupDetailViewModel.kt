package org.role.audio_group.feature.soundboard.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.role.audio_group.core.audio.DurationReader
import org.role.audio_group.core.audio.SoundPoolPlayer
import org.role.audio_group.core.data.GroupRepository
import org.role.audio_group.core.data.SoundButtonRepository
import org.role.audio_group.core.model.Group
import javax.inject.Inject

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val soundButtonRepository: SoundButtonRepository,
    private val soundPoolPlayer: SoundPoolPlayer,
    private val durationReader: DurationReader
) : ViewModel() {

    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    val group: StateFlow<Group?> = groupRepository
        .getGroupsWithButtons()
        .map { groups -> groups.find { it.id == groupId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _playingPaths = MutableStateFlow<Set<String>>(emptySet())
    val playingPaths: StateFlow<Set<String>> = _playingPaths.asStateFlow()

    private val _loopingPaths = MutableStateFlow<Set<String>>(emptySet())
    val loopingPaths: StateFlow<Set<String>> = _loopingPaths.asStateFlow()

    private val _durations = MutableStateFlow<Map<String, Long>>(emptyMap())
    val durations: StateFlow<Map<String, Long>> = _durations.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val progressJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            group.filterNotNull().collect { g ->
                val map = g.buttons.associate { btn ->
                    btn.filePath to durationReader.getDurationMs(btn.filePath)
                }
                _durations.value = map
            }
        }
    }

    fun playSound(filePath: String) {
        soundPoolPlayer.play(filePath)
        _playingPaths.update { it + filePath }
        startProgressPolling(filePath)
    }

    private fun startProgressPolling(filePath: String) {
        progressJobs[filePath]?.cancel()
        progressJobs[filePath] = viewModelScope.launch {
            while (true) {
                delay(100)
                val durationMs = _durations.value[filePath] ?: 0L
                val posMs = soundPoolPlayer.getCurrentPositionMs(filePath)
                val fraction = if (durationMs > 0L) (posMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                _progress.update { it + (filePath to fraction) }
            }
        }
    }

    fun pauseSound(filePath: String) {
        soundPoolPlayer.pause(filePath)
        _playingPaths.update { it - filePath }
        progressJobs.remove(filePath)?.cancel()
    }

    fun pauseAll() {
        soundPoolPlayer.pauseAll()
        _playingPaths.value = emptySet()
        progressJobs.values.forEach { it.cancel() }
        progressJobs.clear()
    }

    fun seekSound(filePath: String, fraction: Float) {
        val durationMs = _durations.value[filePath] ?: return
        val clamped = fraction.coerceIn(0f, 1f)
        val posMs = (clamped * durationMs).toLong()
        soundPoolPlayer.seekTo(filePath, posMs)
        _progress.update { it + (filePath to clamped) }
    }

    fun restartSound(filePath: String) {
        soundPoolPlayer.restart(filePath)
    }

    fun toggleLoop(filePath: String) {
        val isNowLooping = filePath !in _loopingPaths.value
        _loopingPaths.update { if (isNowLooping) it + filePath else it - filePath }
        soundPoolPlayer.setLooping(filePath, isNowLooping)
    }

    fun deleteButton(id: Long) {
        viewModelScope.launch { soundButtonRepository.deleteButton(id) }
    }

    fun renameButton(id: Long, newLabel: String) {
        viewModelScope.launch { soundButtonRepository.renameButton(id, newLabel) }
    }

    fun reorderButtons(from: Int, to: Int) {
        val current = group.value?.buttons ?: return
        val reordered = current.toMutableList()
            .apply { add(to, removeAt(from)) }
            .mapIndexed { index, btn -> btn.copy(position = index) }
        viewModelScope.launch { soundButtonRepository.reorderButtons(reordered) }
    }

    override fun onCleared() {
        progressJobs.values.forEach { it.cancel() }
        progressJobs.clear()
        soundPoolPlayer.release()
    }
}

# Playback Progress Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir una barra de progreso interactiva de 4 dp bajo los controles de cada sample, que muestra la posición de reproducción en tiempo real y permite hacer seek tocando cualquier punto de la barra.

**Architecture:** El ViewModel es dueño de todo el estado. Al reproducir un audio se lanza una corrutina que consulta `getCurrentPositionMs` cada 100 ms y actualiza un `StateFlow<Map<String, Float>>`. La UI observa ese flow y muestra un `LinearProgressIndicator` dentro de un `Box` con `pointerInput` para calcular la fracción del tap y llamar a `seekSound`. Cuando el audio se pausa, la corrutina se cancela pero el valor en `_progress` se mantiene (el usuario ve dónde pausó).

**Tech Stack:** Kotlin Coroutines, Jetpack Compose (`LinearProgressIndicator`, `BoxWithConstraints`, `pointerInput`, `detectTapGestures`), MediaPlayer.

---

## Archivos que se modifican

| Archivo | Acción |
|---|---|
| `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt` | Modificar — añadir 2 métodos |
| `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt` | Modificar — implementar 2 métodos |
| `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModel.kt` | Modificar — añadir estado de progreso + seekSound |
| `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailScreen.kt` | Modificar — añadir ProgressBar, actualizar SoundButtonRow y ButtonList |
| `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt` | Modificar — añadir métodos del fake |
| `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModelTest.kt` | Modificar — añadir tests de progreso y seek |

---

## Task 1: Extender SoundPoolPlayer e implementar el fake

**Files:**
- Modify: `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt`

- [ ] **Step 1: Añadir los dos métodos a la interfaz SoundPoolPlayer**

Reemplazar el contenido de `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt`:

```kotlin
package org.role.samples_button.core.audio

interface SoundPoolPlayer {
    fun play(filePath: String)
    fun pause(filePath: String)
    fun pauseAll()
    fun release()
    fun restart(filePath: String)
    fun setLooping(filePath: String, loop: Boolean)
    fun getCurrentPositionMs(filePath: String): Long
    fun seekTo(filePath: String, positionMs: Long)
}
```

- [ ] **Step 2: Actualizar FakeSoundPoolPlayer con los nuevos métodos**

Reemplazar el contenido de `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

import org.role.samples_button.core.audio.SoundPoolPlayer

class FakeSoundPoolPlayer(
    var positions: Map<String, Long> = emptyMap()
) : SoundPoolPlayer {
    val playedPaths = mutableListOf<String>()
    val pausedPaths = mutableListOf<String>()
    val restartedPaths = mutableListOf<String>()
    val loopingStates = mutableMapOf<String, Boolean>()
    val seekedTo = mutableListOf<Pair<String, Long>>()
    var pauseAllCalled = false
    var released = false

    override fun play(filePath: String) { playedPaths += filePath }
    override fun pause(filePath: String) { pausedPaths += filePath }
    override fun pauseAll() { pauseAllCalled = true }
    override fun release() { released = true }
    override fun restart(filePath: String) { restartedPaths += filePath }
    override fun setLooping(filePath: String, loop: Boolean) { loopingStates[filePath] = loop }
    override fun getCurrentPositionMs(filePath: String): Long = positions[filePath] ?: 0L
    override fun seekTo(filePath: String, positionMs: Long) { seekedTo += filePath to positionMs }
}
```

- [ ] **Step 3: Verificar que compila sin errores**

```bash
./gradlew :feature:soundboard:impl:compileDebugUnitTestKotlin
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt
git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt
git commit -m "feat: add getCurrentPositionMs and seekTo to SoundPoolPlayer interface"
```

---

## Task 2: Implementar getCurrentPositionMs y seekTo en SoundPoolManager

**Files:**
- Modify: `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt`

- [ ] **Step 1: Añadir los dos métodos al final de SoundPoolManager**

Localizar el bloque `override fun setLooping(...)` (línea ~125) y añadir después:

```kotlin
    override fun getCurrentPositionMs(filePath: String): Long {
        synchronized(lock) {
            val player = activePlayers[filePath] ?: return 0L
            return try { player.currentPosition.toLong() } catch (_: Exception) { 0L }
        }
    }

    override fun seekTo(filePath: String, positionMs: Long) {
        synchronized(lock) {
            val player = activePlayers[filePath] ?: return
            try { player.seekTo(positionMs.toInt()) } catch (_: Exception) {}
        }
    }
```

El archivo completo `SoundPoolManager.kt` debe quedar así (sin cambiar nada más):

```kotlin
package org.role.samples_button.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
class SoundPoolManager @Inject constructor(
    @Suppress("UnusedParameter") @ApplicationContext context: Context
) : SoundPoolPlayer {

    private val maxStreams = 8
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val lock = Any()
    private val activePlayers = LinkedHashMap<String, MediaPlayer>()
    private val pendingPause = mutableSetOf<String>()
    private val pendingLoops = mutableMapOf<String, Boolean>()

    override fun play(filePath: String) {
        synchronized(lock) {
            val existing = activePlayers[filePath]
            if (existing != null) {
                pendingPause.remove(filePath)
                activePlayers.remove(filePath)
                activePlayers[filePath] = existing
                if (!safeIsPlaying(existing)) {
                    try { existing.start() } catch (_: Exception) {}
                }
                return
            }
            val player = acquirePlayer(filePath)
            try {
                player.reset()
                player.setAudioAttributes(audioAttributes)
                player.setDataSource(filePath)
                player.setOnPreparedListener { mp ->
                    synchronized(lock) {
                        pendingLoops.remove(filePath)?.let { mp.isLooping = it }
                        val shouldStart = !pendingPause.remove(filePath)
                        if (shouldStart) {
                            try { mp.start() } catch (_: Exception) {}
                        }
                    }
                }
                player.setOnErrorListener { _, _, _ -> true }
                player.prepareAsync()
            } catch (e: Exception) {
                try { player.reset() } catch (_: Exception) {}
                activePlayers.remove(filePath)
                pendingPause.remove(filePath)
            }
        }
    }

    override fun pause(filePath: String) {
        synchronized(lock) {
            val player = activePlayers[filePath] ?: return
            pendingPause.add(filePath)
            if (safeIsPlaying(player)) {
                try { player.pause() } catch (_: Exception) {}
            }
        }
    }

    override fun pauseAll() {
        synchronized(lock) {
            activePlayers.forEach { (path, player) ->
                pendingPause.add(path)
                if (safeIsPlaying(player)) {
                    try { player.pause() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun acquirePlayer(forPath: String): MediaPlayer {
        return if (activePlayers.size < maxStreams) {
            MediaPlayer().also { activePlayers[forPath] = it }
        } else {
            val oldestPath = activePlayers.keys.first()
            val player = activePlayers.remove(oldestPath)!!
            pendingPause.remove(oldestPath)
            pendingLoops.remove(oldestPath)
            try { player.stop() } catch (_: Exception) {}
            activePlayers[forPath] = player
            player
        }
    }

    private fun safeIsPlaying(player: MediaPlayer): Boolean =
        try { player.isPlaying } catch (_: Exception) { false }

    override fun release() {
        synchronized(lock) {
            activePlayers.values.forEach { try { it.release() } catch (_: Exception) {} }
            activePlayers.clear()
            pendingPause.clear()
            pendingLoops.clear()
        }
    }

    override fun restart(filePath: String) {
        synchronized(lock) {
            val player = activePlayers[filePath] ?: return
            try { player.seekTo(0) } catch (_: Exception) {}
        }
    }

    override fun setLooping(filePath: String, loop: Boolean) {
        synchronized(lock) {
            val player = activePlayers[filePath]
            if (player != null) {
                try { player.isLooping = loop } catch (_: Exception) {}
            } else {
                pendingLoops[filePath] = loop
            }
        }
    }

    override fun getCurrentPositionMs(filePath: String): Long {
        synchronized(lock) {
            val player = activePlayers[filePath] ?: return 0L
            return try { player.currentPosition.toLong() } catch (_: Exception) { 0L }
        }
    }

    override fun seekTo(filePath: String, positionMs: Long) {
        synchronized(lock) {
            val player = activePlayers[filePath] ?: return
            try { player.seekTo(positionMs.toInt()) } catch (_: Exception) {}
        }
    }
}
```

- [ ] **Step 2: Compilar**

```bash
./gradlew :core:audio:compileDebugKotlin
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt
git commit -m "feat: implement getCurrentPositionMs and seekTo in SoundPoolManager"
```

---

## Task 3: Añadir progreso y seekSound al ViewModel

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModel.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModelTest.kt`

- [ ] **Step 1: Escribir los tests que deben fallar**

Añadir al final de `GroupDetailViewModelTest.kt`, antes del cierre de clase `}`:

```kotlin
    @Test
    fun `seekSound delegates seekTo to player with correct position`() = runTest {
        val buttons = listOf(
            org.role.samples_button.core.model.SoundButton(
                id = 1L, label = "Kick", filePath = "/kick.mp3", groupId = 1L, position = 0
            )
        )
        val repo = FakeGroupRepository()
        repo.seedGroups(listOf(org.role.samples_button.core.model.Group(id = 1L, name = "Test", position = 0, buttons = buttons)))
        val player = FakeSoundPoolPlayer()
        val reader = FakeDurationReader(mapOf("/kick.mp3" to 4000L))
        val vm = makeVm(groupId = 1L, repo = repo, player = player, durationReader = reader)

        vm.seekSound("/kick.mp3", 0.5f)

        assertEquals(listOf("/kick.mp3" to 2000L), player.seekedTo)
    }

    @Test
    fun `seekSound updates progress state immediately`() = runTest {
        val buttons = listOf(
            org.role.samples_button.core.model.SoundButton(
                id = 1L, label = "Kick", filePath = "/kick.mp3", groupId = 1L, position = 0
            )
        )
        val repo = FakeGroupRepository()
        repo.seedGroups(listOf(org.role.samples_button.core.model.Group(id = 1L, name = "Test", position = 0, buttons = buttons)))
        val reader = FakeDurationReader(mapOf("/kick.mp3" to 4000L))
        val vm = makeVm(groupId = 1L, repo = repo, durationReader = reader)

        vm.seekSound("/kick.mp3", 0.75f)

        assertEquals(0.75f, vm.progress.value["/kick.mp3"])
    }

    @Test
    fun `seekSound does nothing when filePath has no duration`() = runTest {
        val player = FakeSoundPoolPlayer()
        val vm = makeVm(groupId = 1L, player = player)

        vm.seekSound("/missing.mp3", 0.5f)

        assertTrue(player.seekedTo.isEmpty())
    }

    @Test
    fun `pauseSound preserves last progress value`() = runTest {
        val buttons = listOf(
            org.role.samples_button.core.model.SoundButton(
                id = 1L, label = "Kick", filePath = "/kick.mp3", groupId = 1L, position = 0
            )
        )
        val repo = FakeGroupRepository()
        repo.seedGroups(listOf(org.role.samples_button.core.model.Group(id = 1L, name = "Test", position = 0, buttons = buttons)))
        val reader = FakeDurationReader(mapOf("/kick.mp3" to 4000L))
        val vm = makeVm(groupId = 1L, repo = repo, durationReader = reader)

        vm.seekSound("/kick.mp3", 0.4f)
        vm.pauseSound("/kick.mp3")

        assertEquals(0.4f, vm.progress.value["/kick.mp3"])
    }

    @Test
    fun `pauseAll preserves progress values`() = runTest {
        val buttons = listOf(
            org.role.samples_button.core.model.SoundButton(
                id = 1L, label = "Kick", filePath = "/kick.mp3", groupId = 1L, position = 0
            )
        )
        val repo = FakeGroupRepository()
        repo.seedGroups(listOf(org.role.samples_button.core.model.Group(id = 1L, name = "Test", position = 0, buttons = buttons)))
        val reader = FakeDurationReader(mapOf("/kick.mp3" to 4000L))
        val vm = makeVm(groupId = 1L, repo = repo, durationReader = reader)

        vm.seekSound("/kick.mp3", 0.6f)
        vm.pauseAll()

        assertEquals(0.6f, vm.progress.value["/kick.mp3"])
    }
```

- [ ] **Step 2: Ejecutar tests para verificar que fallan**

```bash
./gradlew :feature:soundboard:impl:testDebugUnitTest --tests "*.GroupDetailViewModelTest.seekSound*" --tests "*.GroupDetailViewModelTest.pauseSound preserves*" --tests "*.GroupDetailViewModelTest.pauseAll preserves*"
```

Esperado: FAIL — `Unresolved reference: seekSound` o `Unresolved reference: progress`

- [ ] **Step 3: Implementar el nuevo estado en GroupDetailViewModel**

Reemplazar el contenido completo de `GroupDetailViewModel.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

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
import org.role.samples_button.core.audio.DurationReader
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
        val posMs = (fraction * durationMs).toLong()
        soundPoolPlayer.seekTo(filePath, posMs)
        _progress.update { it + (filePath to fraction.coerceIn(0f, 1f)) }
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
```

- [ ] **Step 4: Ejecutar los tests nuevos**

```bash
./gradlew :feature:soundboard:impl:testDebugUnitTest --tests "*.GroupDetailViewModelTest"
```

Esperado: todos los tests PASS incluidos los 5 nuevos.

- [ ] **Step 5: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModel.kt
git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModelTest.kt
git commit -m "feat: add progress tracking and seekSound to GroupDetailViewModel"
```

---

## Task 4: Añadir ProgressBar e integrar en GroupDetailScreen

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailScreen.kt`

- [ ] **Step 1: Reemplazar el contenido completo de GroupDetailScreen.kt**

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.role.samples_button.core.model.SoundButton
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    viewModel: GroupDetailViewModel,
    onNavigateToFileBrowser: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val playingPaths by viewModel.playingPaths.collectAsStateWithLifecycle()
    val loopingPaths by viewModel.loopingPaths.collectAsStateWithLifecycle()
    val durations by viewModel.durations.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (playingPaths.isNotEmpty()) {
                        IconButton(onClick = { viewModel.pauseAll() }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pausar todo")
                        }
                    }
                }
            )
        }
    ) { padding ->
        group?.let { g ->
            ButtonList(
                buttons = g.buttons,
                playingPaths = playingPaths,
                loopingPaths = loopingPaths,
                durations = durations,
                progress = progress,
                onAddSound = { onNavigateToFileBrowser(g.id) },
                onSoundButtonClick = { filePath ->
                    if (filePath in playingPaths) viewModel.pauseSound(filePath)
                    else viewModel.playSound(filePath)
                },
                onRestartButton = { viewModel.restartSound(it) },
                onToggleLoop = { viewModel.toggleLoop(it) },
                onSeek = { filePath, fraction -> viewModel.seekSound(filePath, fraction) },
                onDeleteButton = { viewModel.deleteButton(it) },
                onRenameButton = { id, newLabel -> viewModel.renameButton(id, newLabel) },
                onReorder = { from, to -> viewModel.reorderButtons(from, to) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ButtonList(
    buttons: List<SoundButton>,
    playingPaths: Set<String>,
    loopingPaths: Set<String>,
    durations: Map<String, Long>,
    progress: Map<String, Float>,
    onAddSound: () -> Unit,
    onSoundButtonClick: (String) -> Unit,
    onRestartButton: (String) -> Unit,
    onToggleLoop: (String) -> Unit,
    onSeek: (filePath: String, fraction: Float) -> Unit,
    onDeleteButton: (Long) -> Unit,
    onRenameButton: (Long, String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(buttons, key = { it.id }) { button ->
            ReorderableItem(reorderState, key = button.id) { isDragging ->
                SoundButtonRow(
                    button = button,
                    isPlaying = button.filePath in playingPaths,
                    isLooping = button.filePath in loopingPaths,
                    durationMs = durations[button.filePath] ?: 0L,
                    progress = progress[button.filePath] ?: 0f,
                    isDragging = isDragging,
                    dragModifier = Modifier.longPressDraggableHandle(),
                    onClick = { onSoundButtonClick(button.filePath) },
                    onRestart = { onRestartButton(button.filePath) },
                    onToggleLoop = { onToggleLoop(button.filePath) },
                    onSeek = { fraction -> onSeek(button.filePath, fraction) },
                    onDelete = { onDeleteButton(button.id) },
                    onRename = onRenameButton
                )
            }
        }
        item {
            AddSoundButton(onClick = onAddSound)
        }
    }
}

@Composable
private fun SoundButtonRow(
    button: SoundButton,
    isPlaying: Boolean,
    isLooping: Boolean,
    durationMs: Long,
    progress: Float,
    isDragging: Boolean,
    dragModifier: Modifier,
    onClick: () -> Unit,
    onRestart: () -> Unit,
    onToggleLoop: () -> Unit,
    onSeek: (Float) -> Unit,
    onDelete: () -> Unit,
    onRename: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val elevation = if (isDragging) 8.dp else 1.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(dragModifier),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Fila 1: label + badge "Sonando" + menú
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isPlaying) {
                    Text(
                        text = "● Sonando",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showMenu = false; showRenameDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { showMenu = false; showConfirmDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fila 2: play/pause + restart + loop + duración
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir"
                    )
                }
                IconButton(onClick = onRestart, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Volver al principio"
                    )
                }
                IconButton(
                    onClick = onToggleLoop,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isLooping) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = if (isLooping) "Desactivar bucle" else "Activar bucle",
                        tint = if (isLooping)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fila 3: barra de progreso interactiva
            PlaybackProgressBar(
                progress = progress,
                isPlaying = isPlaying,
                onSeek = onSeek
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Eliminar sample") },
            text = { Text("¿Eliminar \"${button.label}\"?") },
            confirmButton = {
                TextButton(onClick = { showConfirmDialog = false; onDelete() }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showRenameDialog) {
        RenameDialog(
            currentLabel = button.label,
            onConfirm = { newLabel -> showRenameDialog = false; onRename(button.id, newLabel) },
            onDismiss = { showRenameDialog = false }
        )
    }
}

@Composable
private fun PlaybackProgressBar(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .pointerInput(onSeek) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.Center),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
        if (isPlaying) {
            val thumbOffset = (maxWidth * progress - 6.dp).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .offset(x = thumbOffset)
                    .background(color, CircleShape)
                    .align(Alignment.CenterStart)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
private fun RenameDialog(
    currentLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(currentLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar sample") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (label.isNotBlank()) onConfirm(label.trim()) },
                enabled = label.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AddSoundButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
                text = "Añadir sample",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Compilar el módulo**

```bash
./gradlew :feature:soundboard:impl:compileDebugKotlin
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 3: Ejecutar todos los tests unitarios**

```bash
./gradlew :feature:soundboard:impl:testDebugUnitTest
```

Esperado: todos los tests PASS

- [ ] **Step 4: Probar en dispositivo físico**

Instalar y verificar manualmente:
1. Abrir un grupo con samples
2. Reproducir un sample → la barra debe avanzar en tiempo real
3. Pausar → la barra se queda fija en la posición actual
4. Tocar un punto de la barra → el audio debe saltar a esa posición
5. Repetir con varios samples simultáneos → cada barra avanza de forma independiente

```bash
./gradlew assembleDebug
```

- [ ] **Step 5: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailScreen.kt
git commit -m "feat: add interactive playback progress bar to each sample row"
```

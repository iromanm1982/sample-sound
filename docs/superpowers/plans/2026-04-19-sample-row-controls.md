# Sample Row Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cambiar el grid de 3 columnas por una lista de 1 columna donde cada sample muestra play/pause, volver al principio (↩), loop (⟳) y duración total.

**Architecture:** Se extiende la interfaz `SoundPoolPlayer` con `restart()` y `setLooping()`. El ViewModel añade estado runtime para loop y duraciones (leídas con `MediaMetadataRetriever` vía una interfaz `DurationReader` inyectable). La UI reemplaza `LazyVerticalGrid` por `LazyColumn` con un nuevo composable `SoundButtonRow`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, MediaPlayer (ya en uso), MediaMetadataRetriever, sh.calvin.reorderable

---

## File Map

| Acción | Archivo |
|---|---|
| Modify | `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt` |
| Modify | `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt` |
| Create | `core/audio/src/main/java/org/role/samples_button/core/audio/DurationReader.kt` |
| Create | `core/audio/src/main/java/org/role/samples_button/core/audio/MediaMetadataDurationReader.kt` |
| Modify | `core/audio/src/main/java/org/role/samples_button/core/audio/di/AudioModule.kt` |
| Modify | `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt` |
| Modify | `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModel.kt` |
| Modify | `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModelTest.kt` |
| Modify | `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailScreen.kt` |

---

## Task 1: Extend SoundPoolPlayer + FakeSoundPoolPlayer

**Files:**
- Modify: `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt`

- [ ] **Step 1: Añadir los dos métodos nuevos a la interfaz**

Reemplazar el contenido completo de `SoundPoolPlayer.kt`:

```kotlin
package org.role.samples_button.core.audio

interface SoundPoolPlayer {
    fun play(filePath: String)
    fun pause(filePath: String)
    fun pauseAll()
    fun release()
    fun restart(filePath: String)
    fun setLooping(filePath: String, loop: Boolean)
}
```

- [ ] **Step 2: Actualizar FakeSoundPoolPlayer para implementar los nuevos métodos**

Reemplazar el contenido completo de `FakeSoundPoolPlayer.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

import org.role.samples_button.core.audio.SoundPoolPlayer

class FakeSoundPoolPlayer : SoundPoolPlayer {
    val playedPaths = mutableListOf<String>()
    val pausedPaths = mutableListOf<String>()
    val restartedPaths = mutableListOf<String>()
    val loopingStates = mutableMapOf<String, Boolean>()
    var pauseAllCalled = false
    var released = false

    override fun play(filePath: String) { playedPaths += filePath }
    override fun pause(filePath: String) { pausedPaths += filePath }
    override fun pauseAll() { pauseAllCalled = true }
    override fun release() { released = true }
    override fun restart(filePath: String) { restartedPaths += filePath }
    override fun setLooping(filePath: String, loop: Boolean) { loopingStates[filePath] = loop }
}
```

- [ ] **Step 3: Verificar que el proyecto compila**

```bash
./gradlew :feature:soundboard:impl:compileDebugKotlin
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt \
        feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt
git commit -m "feat: add restart() and setLooping() to SoundPoolPlayer interface"
```

---

## Task 2: Implementar restart + setLooping en SoundPoolManager

**Files:**
- Modify: `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt`

- [ ] **Step 1: Añadir pendingLoops y actualizar play() para aplicar loop pendiente**

En `SoundPoolManager.kt`, añadir el campo `pendingLoops` junto a los existentes y actualizar `play()` para aplicarlo:

```kotlin
// Añadir junto a pendingPause (línea ~25):
private val pendingLoops = mutableMapOf<String, Boolean>()
```

En el método `play()`, dentro del bloque `player.setOnPreparedListener { mp -> ... }`, añadir la aplicación de loop antes de `mp.start()`:

```kotlin
player.setOnPreparedListener { mp ->
    synchronized(lock) {
        pendingLoops[filePath]?.let { mp.isLooping = it }
        val shouldStart = !pendingPause.remove(filePath)
        if (shouldStart) {
            try { mp.start() } catch (_: Exception) {}
        }
    }
}
```

- [ ] **Step 2: Implementar restart()**

Añadir después del método `pauseAll()`:

```kotlin
override fun restart(filePath: String) {
    synchronized(lock) {
        val player = activePlayers[filePath] ?: return
        try { player.seekTo(0) } catch (_: Exception) {}
    }
}
```

- [ ] **Step 3: Implementar setLooping()**

Añadir después de `restart()`:

```kotlin
override fun setLooping(filePath: String, loop: Boolean) {
    synchronized(lock) {
        val player = activePlayers[filePath]
        if (player != null) {
            try { player.isLooping = loop } catch (_: Exception) {}
        } else {
            // El player aún no existe; se aplicará en onPreparedListener cuando se cree
            pendingLoops[filePath] = loop
        }
    }
}
```

- [ ] **Step 4: Limpiar pendingLoops en acquirePlayer() al reutilizar un player**

En el método `acquirePlayer()`, en el bloque `else` (eviction), añadir:

```kotlin
pendingLoops.remove(oldestPath)
```

Y también en el método `release()`, añadir:

```kotlin
pendingLoops.clear()
```

- [ ] **Step 5: Verificar que el proyecto compila**

```bash
./gradlew :core:audio:compileDebugKotlin
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt
git commit -m "feat: implement restart() and setLooping() in SoundPoolManager"
```

---

## Task 3: Crear DurationReader + binding DI

**Files:**
- Create: `core/audio/src/main/java/org/role/samples_button/core/audio/DurationReader.kt`
- Create: `core/audio/src/main/java/org/role/samples_button/core/audio/MediaMetadataDurationReader.kt`
- Modify: `core/audio/src/main/java/org/role/samples_button/core/audio/di/AudioModule.kt`

- [ ] **Step 1: Crear la interfaz DurationReader**

```kotlin
// DurationReader.kt
package org.role.samples_button.core.audio

interface DurationReader {
    /** Devuelve la duración en milisegundos, o 0L si no se puede leer. */
    fun getDurationMs(filePath: String): Long
}
```

- [ ] **Step 2: Crear la implementación con MediaMetadataRetriever**

```kotlin
// MediaMetadataDurationReader.kt
package org.role.samples_button.core.audio

import android.media.MediaMetadataRetriever
import javax.inject.Inject

class MediaMetadataDurationReader @Inject constructor() : DurationReader {
    override fun getDurationMs(filePath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }
}
```

- [ ] **Step 3: Añadir el binding en AudioModule**

Reemplazar el contenido completo de `AudioModule.kt`:

```kotlin
package org.role.samples_button.core.audio.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.role.samples_button.core.audio.DurationReader
import org.role.samples_button.core.audio.MediaMetadataDurationReader
import org.role.samples_button.core.audio.SoundPoolManager
import org.role.samples_button.core.audio.SoundPoolPlayer

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class AudioModule {

    @Binds
    @ActivityRetainedScoped
    abstract fun bindSoundPoolPlayer(impl: SoundPoolManager): SoundPoolPlayer

    @Binds
    @ActivityRetainedScoped
    abstract fun bindDurationReader(impl: MediaMetadataDurationReader): DurationReader
}
```

- [ ] **Step 4: Verificar que el proyecto compila**

```bash
./gradlew :core:audio:compileDebugKotlin
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core/audio/src/main/java/org/role/samples_button/core/audio/DurationReader.kt \
        core/audio/src/main/java/org/role/samples_button/core/audio/MediaMetadataDurationReader.kt \
        core/audio/src/main/java/org/role/samples_button/core/audio/di/AudioModule.kt
git commit -m "feat: add DurationReader interface and MediaMetadataDurationReader"
```

---

## Task 4: Actualizar GroupDetailViewModel + tests

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModel.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModelTest.kt`

- [ ] **Step 1: Escribir los tests que fallarán**

Añadir al final de la clase `GroupDetailViewModelTest`, antes del `}` de cierre, los tests nuevos. También actualizar la función `makeVm()` para aceptar un `FakeDurationReader`:

```kotlin
// Al inicio del archivo, añadir la clase FakeDurationReader:
class FakeDurationReader(private val data: Map<String, Long> = emptyMap()) :
    org.role.samples_button.core.audio.DurationReader {
    override fun getDurationMs(filePath: String): Long = data[filePath] ?: 0L
}
```

Actualizar la función `makeVm()` en `GroupDetailViewModelTest`:

```kotlin
private fun makeVm(
    groupId: Long,
    repo: FakeGroupRepository = FakeGroupRepository(),
    player: FakeSoundPoolPlayer = FakeSoundPoolPlayer(),
    durationReader: FakeDurationReader = FakeDurationReader()
): GroupDetailViewModel {
    val handle = SavedStateHandle(mapOf("groupId" to groupId))
    return GroupDetailViewModel(handle, repo, FakeSoundButtonRepository(), player, durationReader)
}
```

Añadir los tests nuevos:

```kotlin
@Test
fun `toggleLoop adds filePath to loopingPaths when not looping`() = runTest {
    val vm = makeVm(1L)
    vm.toggleLoop("/storage/kick.mp3")
    assertTrue(vm.loopingPaths.value.contains("/storage/kick.mp3"))
}

@Test
fun `toggleLoop removes filePath from loopingPaths when already looping`() = runTest {
    val vm = makeVm(1L)
    vm.toggleLoop("/storage/kick.mp3")
    vm.toggleLoop("/storage/kick.mp3")
    assertFalse(vm.loopingPaths.value.contains("/storage/kick.mp3"))
}

@Test
fun `toggleLoop delegates setLooping true to player`() = runTest {
    val player = FakeSoundPoolPlayer()
    val vm = makeVm(groupId = 1L, player = player)
    vm.toggleLoop("/storage/kick.mp3")
    assertEquals(true, player.loopingStates["/storage/kick.mp3"])
}

@Test
fun `toggleLoop delegates setLooping false when already looping`() = runTest {
    val player = FakeSoundPoolPlayer()
    val vm = makeVm(groupId = 1L, player = player)
    vm.toggleLoop("/storage/kick.mp3")
    vm.toggleLoop("/storage/kick.mp3")
    assertEquals(false, player.loopingStates["/storage/kick.mp3"])
}

@Test
fun `restartSound delegates restart to player`() = runTest {
    val player = FakeSoundPoolPlayer()
    val vm = makeVm(groupId = 1L, player = player)
    vm.restartSound("/storage/kick.mp3")
    assertEquals(listOf("/storage/kick.mp3"), player.restartedPaths)
}

@Test
fun `durations are populated from group buttons via DurationReader`() = runTest {
    val buttons = listOf(
        org.role.samples_button.core.model.SoundButton(
            id = 1L, label = "Kick", filePath = "/kick.mp3", groupId = 1L, position = 0
        ),
        org.role.samples_button.core.model.SoundButton(
            id = 2L, label = "Snare", filePath = "/snare.mp3", groupId = 1L, position = 1
        )
    )
    val repo = FakeGroupRepository()
    repo.seedGroups(
        listOf(org.role.samples_button.core.model.Group(id = 1L, name = "Test", position = 0, buttons = buttons))
    )
    val reader = FakeDurationReader(mapOf("/kick.mp3" to 45_000L, "/snare.mp3" to 22_000L))
    val vm = makeVm(groupId = 1L, repo = repo, durationReader = reader)

    assertEquals(45_000L, vm.durations.value["/kick.mp3"])
    assertEquals(22_000L, vm.durations.value["/snare.mp3"])
}
```

- [ ] **Step 2: Ejecutar los tests para confirmar que fallan**

```bash
./gradlew :feature:soundboard:impl:testDebugUnitTest --tests "*.GroupDetailViewModelTest" 2>&1 | tail -20
```

Esperado: fallos por `Unresolved reference: loopingPaths`, `Unresolved reference: durations`, etc.

- [ ] **Step 3: Implementar los cambios en GroupDetailViewModel**

Reemplazar el contenido completo de `GroupDetailViewModel.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

    init {
        viewModelScope.launch(Dispatchers.IO) {
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
    }

    fun pauseSound(filePath: String) {
        soundPoolPlayer.pause(filePath)
        _playingPaths.update { it - filePath }
    }

    fun pauseAll() {
        soundPoolPlayer.pauseAll()
        _playingPaths.value = emptySet()
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
        soundPoolPlayer.release()
    }
}
```

- [ ] **Step 4: Ejecutar los tests**

```bash
./gradlew :feature:soundboard:impl:testDebugUnitTest --tests "*.GroupDetailViewModelTest" 2>&1 | tail -20
```

Esperado: `BUILD SUCCESSFUL`, todos los tests en `PASSED`

- [ ] **Step 5: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModel.kt \
        feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/GroupDetailViewModelTest.kt
git commit -m "feat: add loop, restart and duration support to GroupDetailViewModel"
```

---

## Task 5: Reescribir GroupDetailScreen — lista de 1 columna + SoundButtonRow

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailScreen.kt`

- [ ] **Step 1: Reemplazar el contenido completo de GroupDetailScreen.kt**

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
                onAddSound = { onNavigateToFileBrowser(g.id) },
                onSoundButtonClick = { filePath ->
                    if (filePath in playingPaths) viewModel.pauseSound(filePath)
                    else viewModel.playSound(filePath)
                },
                onRestartButton = { viewModel.restartSound(it) },
                onToggleLoop = { viewModel.toggleLoop(it) },
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
    onAddSound: () -> Unit,
    onSoundButtonClick: (String) -> Unit,
    onRestartButton: (String) -> Unit,
    onToggleLoop: (String) -> Unit,
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
                    isDragging = isDragging,
                    dragModifier = Modifier.longPressDraggableHandle(),
                    onClick = { onSoundButtonClick(button.filePath) },
                    onRestart = { onRestartButton(button.filePath) },
                    onToggleLoop = { onToggleLoop(button.filePath) },
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
    isDragging: Boolean,
    dragModifier: Modifier,
    onClick: () -> Unit,
    onRestart: () -> Unit,
    onToggleLoop: () -> Unit,
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
                IconButton(onClick = onToggleLoop, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
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

- [ ] **Step 2: Verificar que el proyecto compila**

```bash
./gradlew :feature:soundboard:impl:compileDebugKotlin
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 3: Ejecutar todos los tests**

```bash
./gradlew test 2>&1 | tail -20
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/GroupDetailScreen.kt
git commit -m "feat: replace sample grid with single-column list with loop, restart and duration controls"
```

---

## Self-Review

### Spec coverage
- [x] Grid 3 columnas → 1 columna: Task 5
- [x] Volver al principio (funciona en pausa): Task 2 (`seekTo(0)` sin condicionar a `isPlaying`) + Task 4 + Task 5
- [x] Bucle runtime: Task 1, 2, 4, 5
- [x] Duración visible: Task 3, 4, 5
- [x] Badge "Sonando": Task 5 (`● Sonando`)
- [x] Drag & drop mantenido: Task 5 (`rememberReorderableLazyListState` + `longPressDraggableHandle`)
- [x] `AddSoundButton` a ancho completo: Task 5

### Type consistency
- `restart(filePath)` definido en Task 1, implementado en Task 2, llamado en Task 4 ViewModel, delegado en Task 5 UI — consistente
- `setLooping(filePath, Boolean)` — mismo patrón — consistente
- `DurationReader` definido en Task 3, inyectado en ViewModel en Task 4 — consistente
- `loopingPaths`, `durations`, `toggleLoop`, `restartSound` definidos en Task 4 ViewModel, consumidos en Task 5 UI — consistente

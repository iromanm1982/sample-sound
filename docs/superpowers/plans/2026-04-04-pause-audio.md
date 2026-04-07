# Pause Audio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-button pause/resume and global pause-all to the SoundBoard, with visual feedback on each button.

**Architecture:** `SoundPoolPlayer` interface gains `pause(filePath)` and `pauseAll()`. `SoundPoolManager` replaces the anonymous `MutableList<MediaPlayer>` with a `LinkedHashMap<String, MediaPlayer>` keyed by filePath so pause/resume can target the correct player. `SoundBoardViewModel` exposes `playingPaths: StateFlow<Set<String>>` so the UI can render playing state per button.

**Tech Stack:** Kotlin, MediaPlayer, Hilt, Jetpack Compose, Kotlin Coroutines StateFlow, JUnit

---

### Task 1: Expand `SoundPoolPlayer` interface and update `FakeSoundPoolPlayer`

**Files:**
- Modify: `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt`

- [ ] **Step 1: Add `pause` and `pauseAll` to the interface**

Replace the full contents of `SoundPoolPlayer.kt`:

```kotlin
package org.role.samples_button.core.audio

interface SoundPoolPlayer {
    fun play(filePath: String)   // new-play or resume, manager decides
    fun pause(filePath: String)
    fun pauseAll()
    fun release()
}
```

- [ ] **Step 2: Update `FakeSoundPoolPlayer` to implement the new methods**

Replace the full contents of `FakeSoundPoolPlayer.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

import org.role.samples_button.core.audio.SoundPoolPlayer

class FakeSoundPoolPlayer : SoundPoolPlayer {
    val playedPaths = mutableListOf<String>()
    val pausedPaths = mutableListOf<String>()
    var pauseAllCalled = false
    var released = false

    override fun play(filePath: String) {
        playedPaths += filePath
    }

    override fun pause(filePath: String) {
        pausedPaths += filePath
    }

    override fun pauseAll() {
        pauseAllCalled = true
    }

    override fun release() {
        released = true
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
powershell.exe -Command "Set-Location 'C:\Users\ivan.muerte\AndroidStudioProjects\samplesbutton'; .\gradlew.bat :core:audio:compileDebugKotlin :feature:soundboard:impl:compileDebugKotlin 2>&1 | Select-String 'error:|BUILD'"
```

Expected: `BUILD SUCCESSFUL` — `SoundPoolManager` will show an error because it doesn't implement the new methods yet; that's expected and handled in Task 2.

- [ ] **Step 4: Commit**

```bash
powershell.exe -Command "Set-Location 'C:\Users\ivan.muerte\AndroidStudioProjects\samplesbutton'; git add core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt; git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt; git commit -m 'feat: expand SoundPoolPlayer interface with pause/pauseAll'"
```

---

### Task 2: Rewrite `SoundPoolManager` with `LinkedHashMap` and pause support

**Files:**
- Modify: `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt`

- [ ] **Step 1: Replace `SoundPoolManager` implementation**

Replace the full contents of `SoundPoolManager.kt`:

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
    // LinkedHashMap preserves insertion order — oldest entry is evicted when pool is full
    private val activePlayers = LinkedHashMap<String, MediaPlayer>()

    override fun play(filePath: String) {
        synchronized(lock) {
            val existing = activePlayers[filePath]
            if (existing != null) {
                // Resume if paused, no-op if already playing
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
                player.setOnPreparedListener { it.start() }
                player.setOnErrorListener { _, _, _ -> true }
                player.prepareAsync()
            } catch (e: Exception) {
                try { player.reset() } catch (_: Exception) {}
                activePlayers.remove(filePath)
            }
        }
    }

    override fun pause(filePath: String) {
        synchronized(lock) {
            val player = activePlayers[filePath] ?: return
            if (safeIsPlaying(player)) {
                try { player.pause() } catch (_: Exception) {}
            }
        }
    }

    override fun pauseAll() {
        synchronized(lock) {
            activePlayers.values.forEach { player ->
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
            // Evict oldest entry and reuse its player
            val oldestPath = activePlayers.keys.first()
            val player = activePlayers.remove(oldestPath)!!
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
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
powershell.exe -Command "Set-Location 'C:\Users\ivan.muerte\AndroidStudioProjects\samplesbutton'; .\gradlew.bat :core:audio:compileDebugKotlin 2>&1 | Select-String 'error:|BUILD'"
```

Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 3: Commit**

```bash
powershell.exe -Command "Set-Location 'C:\Users\ivan.muerte\AndroidStudioProjects\samplesbutton'; git add core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt; git commit -m 'feat: replace MediaPlayer list with LinkedHashMap, add pause/pauseAll'"
```

---

### Task 3: Update `SoundBoardViewModel` and tests

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`

- [ ] **Step 1: Write the failing tests first**

Add the following tests at the end of the `SoundBoardViewModelTest` class (before the closing `}`), after the existing `onCleared releases player` test:

```kotlin
@Test
fun `playSound adds filePath to playingPaths`() = runTest {
    val viewModel = SoundBoardViewModel(FakeGroupRepository(), FakeSoundPoolPlayer())
    viewModel.playSound("/storage/kick.mp3")
    assertTrue(viewModel.playingPaths.value.contains("/storage/kick.mp3"))
}

@Test
fun `pauseSound removes filePath from playingPaths`() = runTest {
    val viewModel = SoundBoardViewModel(FakeGroupRepository(), FakeSoundPoolPlayer())
    viewModel.playSound("/storage/kick.mp3")
    viewModel.pauseSound("/storage/kick.mp3")
    assertFalse(viewModel.playingPaths.value.contains("/storage/kick.mp3"))
}

@Test
fun `pauseAll clears playingPaths`() = runTest {
    val viewModel = SoundBoardViewModel(FakeGroupRepository(), FakeSoundPoolPlayer())
    viewModel.playSound("/storage/kick.mp3")
    viewModel.playSound("/storage/snare.mp3")
    viewModel.pauseAll()
    assertTrue(viewModel.playingPaths.value.isEmpty())
}

@Test
fun `pauseSound delegates to player`() = runTest {
    val player = FakeSoundPoolPlayer()
    val viewModel = SoundBoardViewModel(FakeGroupRepository(), player)
    viewModel.pauseSound("/storage/kick.mp3")
    assertEquals(listOf("/storage/kick.mp3"), player.pausedPaths)
}

@Test
fun `pauseAll delegates to player`() = runTest {
    val player = FakeSoundPoolPlayer()
    val viewModel = SoundBoardViewModel(FakeGroupRepository(), player)
    viewModel.pauseAll()
    assertTrue(player.pauseAllCalled)
}
```

Also add `assertFalse` to the imports at the top of the file:
```kotlin
import org.junit.Assert.assertFalse
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
powershell.exe -Command "Set-Location 'C:\Users\ivan.muerte\AndroidStudioProjects\samplesbutton'; .\gradlew.bat :feature:soundboard:impl:test 2>&1 | Select-String 'FAILED|error:|BUILD'"
```

Expected: compilation error — `pauseSound`, `pauseAll`, `playingPaths` not found on ViewModel.

- [ ] **Step 3: Update `SoundBoardViewModel` to make the tests pass**

Replace the full contents of `SoundBoardViewModel.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
        _playingPaths.value = _playingPaths.value + filePath
    }

    fun pauseSound(filePath: String) {
        soundPoolPlayer.pause(filePath)
        _playingPaths.value = _playingPaths.value - filePath
    }

    fun pauseAll() {
        soundPoolPlayer.pauseAll()
        _playingPaths.value = emptySet()
    }

    override fun onCleared() {
        soundPoolPlayer.release()
    }
}
```

- [ ] **Step 4: Run tests and confirm all pass**

```bash
powershell.exe -Command "Set-Location 'C:\Users\ivan.muerte\AndroidStudioProjects\samplesbutton'; .\gradlew.bat :feature:soundboard:impl:test 2>&1 | Select-String 'tests|FAILED|BUILD'"
```

Expected: `BUILD SUCCESSFUL`, all 11 tests pass (6 existing + 5 new).

- [ ] **Step 5: Commit**

```bash
powershell.exe -Command "Set-Location 'C:\Users\ivan.muerte\AndroidStudioProjects\samplesbutton'; git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt; git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt; git commit -m 'feat: add playingPaths StateFlow, pauseSound and pauseAll to ViewModel'"
```

---

### Task 4: Update `SoundBoardScreen` UI

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`

- [ ] **Step 1: Replace the full contents of `SoundBoardScreen.kt`**

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundBoardScreen(
    viewModel: SoundBoardViewModel,
    onNavigateToFileBrowser: (Long) -> Unit = {}
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val playingPaths by viewModel.playingPaths.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SoundBoard") },
                actions = {
                    if (playingPaths.isNotEmpty()) {
                        IconButton(onClick = { viewModel.pauseAll() }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pausar todo")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Crear grupo")
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            GroupList(
                groups = groups,
                playingPaths = playingPaths,
                onDelete = { viewModel.deleteGroup(it) },
                onAddSound = { groupId -> onNavigateToFileBrowser(groupId) },
                onSoundButtonClick = { filePath ->
                    if (filePath in playingPaths) viewModel.pauseSound(filePath)
                    else viewModel.playSound(filePath)
                },
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onConfirm = { name ->
                viewModel.createGroup(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Sin grupos todavía")
            Text("Toca + para crear uno")
        }
    }
}

@Composable
private fun GroupList(
    groups: List<Group>,
    playingPaths: Set<String>,
    onDelete: (Long) -> Unit,
    onAddSound: (Long) -> Unit,
    onSoundButtonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(groups, key = { it.id }) { group ->
            GroupCard(
                group = group,
                playingPaths = playingPaths,
                onDelete = { onDelete(group.id) },
                onAddSound = { onAddSound(group.id) },
                onSoundButtonClick = onSoundButtonClick
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    playingPaths: Set<String>,
    onDelete: () -> Unit,
    onAddSound: () -> Unit,
    onSoundButtonClick: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = group.name, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar ${group.name}")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ButtonGrid(
                buttons = group.buttons,
                playingPaths = playingPaths,
                onAddSound = onAddSound,
                onSoundButtonClick = onSoundButtonClick
            )
        }
    }
}

@Composable
private fun ButtonGrid(
    buttons: List<SoundButton>,
    playingPaths: Set<String>,
    onAddSound: () -> Unit,
    onSoundButtonClick: (String) -> Unit
) {
    val allItems: List<SoundButton?> = buttons + listOf(null)
    allItems.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { button ->
                if (button != null) {
                    val filePath = button.filePath
                    SoundButtonItem(
                        button = button,
                        isPlaying = filePath in playingPaths,
                        onClick = { onSoundButtonClick(filePath) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    AddSoundButton(onClick = onAddSound, modifier = Modifier.weight(1f))
                }
            }
            repeat(3 - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SoundButtonItem(
    button: SoundButton,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = button.label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(4.dp)
            )
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun AddSoundButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(64.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = "Agregar sonido")
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo grupo") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del grupo") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

- [ ] **Step 2: Build debug to verify compilation**

```bash
powershell.exe -Command "Set-Location 'C:\Users\ivan.muerte\AndroidStudioProjects\samplesbutton'; .\gradlew.bat assembleDebug 2>&1 | Select-String 'error:|BUILD'"
```

Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 3: Commit**

```bash
powershell.exe -Command "Set-Location 'C:\Users\ivan.muerte\AndroidStudioProjects\samplesbutton'; git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt; git commit -m 'feat: show playing state on buttons, add pause-all to TopAppBar'"
```

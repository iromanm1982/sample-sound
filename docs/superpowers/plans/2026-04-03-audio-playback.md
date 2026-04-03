# Audio Playback — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reproducción polyfónica de SoundButtons con SoundPool — varios sonidos simultáneos, carga on-demand, primer tap encola y reproduce al terminar de cargar.

**Architecture:** `SoundPoolPlayer` (interfaz en `:core:audio`) desacopla la lógica de audio del ViewModel. `SoundPoolManager` implementa la interfaz con SoundPool real. `SoundBoardViewModel` recibe `SoundPoolPlayer` por inyección y expone `playSound(filePath)`. `SoundBoardScreen` propaga `onPlaySound` hasta `SoundButtonItem`.

**Tech Stack:** Kotlin 2.1.20, AGP 9.1.0, SoundPool (Android framework), Hilt 2.56.1 (sin Gradle plugin — usar solo `ksp(libs.hilt.compiler)` + `implementation(libs.hilt.android)`), JUnit 4

**IMPORTANTE — quirk AGP 9.1.0:** NO usar `alias(libs.plugins.hilt.android)` en ningún módulo. Solo `ksp(libs.hilt.compiler)` + `implementation(libs.hilt.android)`.

---

## File Map

**Creados:**
- `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt` — interfaz con `play(filePath)` y `release()`
- `core/audio/src/main/java/org/role/samples_button/core/audio/di/AudioModule.kt` — binding Hilt `SoundPoolPlayer → SoundPoolManager`
- `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt` — fake para tests

**Modificados:**
- `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt` — implementar SoundPool real (reemplazar stub)
- `feature/soundboard/impl/build.gradle.kts` — añadir `implementation(project(":core:audio"))`
- `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt` — inyectar `SoundPoolPlayer`, añadir `playSound()` y `onCleared()`
- `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt` — actualizar constructores + añadir tests de audio
- `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt` — propagar `onPlaySound`, hacer `SoundButtonItem` clickable

---

## Task 1: SoundPoolPlayer interface + SoundPoolManager + Hilt binding

**Files:**
- Create: `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt`
- Modify: `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt`
- Create: `core/audio/src/main/java/org/role/samples_button/core/audio/di/AudioModule.kt`

- [ ] **Step 1: Crear la interfaz `SoundPoolPlayer`**

Crear `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt`:

```kotlin
package org.role.samples_button.core.audio

interface SoundPoolPlayer {
    fun play(filePath: String)
    fun release()
}
```

- [ ] **Step 2: Reemplazar `SoundPoolManager` con implementación real**

Reemplazar el contenido completo de `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt`:

```kotlin
package org.role.samples_button.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundPoolManager @Inject constructor(
    @ApplicationContext context: Context
) : SoundPoolPlayer {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        ).build()

    private val soundIds = mutableMapOf<String, Int>()
    private val pendingPlay = mutableSetOf<String>()

    init {
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            if (status == 0) {
                val path = soundIds.entries.find { it.value == soundId }?.key
                if (path != null && pendingPlay.remove(path)) {
                    soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
                }
            }
        }
    }

    override fun play(filePath: String) {
        val existing = soundIds[filePath]
        if (existing != null) {
            soundPool.play(existing, 1f, 1f, 1, 0, 1f)
        } else {
            pendingPlay.add(filePath)
            soundIds[filePath] = soundPool.load(filePath, 1)
        }
    }

    override fun release() {
        soundPool.release()
        soundIds.clear()
        pendingPlay.clear()
    }
}
```

Nota: `@ApplicationContext context: Context` es requerido por Hilt para inyectar el contexto de aplicación. `context` no se usa directamente aquí pero garantiza que Hilt puede construir la clase correctamente en el componente Singleton.

- [ ] **Step 3: Crear `AudioModule` con el binding Hilt**

Crear `core/audio/src/main/java/org/role/samples_button/core/audio/di/AudioModule.kt`:

```kotlin
package org.role.samples_button.core.audio.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.role.samples_button.core.audio.SoundPoolManager
import org.role.samples_button.core.audio.SoundPoolPlayer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    @Singleton
    abstract fun bindSoundPoolPlayer(impl: SoundPoolManager): SoundPoolPlayer
}
```

- [ ] **Step 4: Verificar que `:core:audio` compila**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :core:audio:assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core/audio/
git commit -m "feat: implement SoundPoolPlayer interface and SoundPoolManager with on-demand loading"
```

---

## Task 2: SoundBoardViewModel con audio (TDD)

**Files:**
- Modify: `feature/soundboard/impl/build.gradle.kts`
- Create: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`

- [ ] **Step 1: Añadir dependencia `:core:audio` al módulo soundboard**

En `feature/soundboard/impl/build.gradle.kts`, añadir dentro del bloque `dependencies { ... }` después de `implementation(project(":core:model"))`:

```kotlin
    implementation(project(":core:audio"))
```

El bloque `dependencies` completo queda:

```kotlin
dependencies {
    implementation(project(":feature:soundboard:api"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:audio"))
    implementation(project(":core:designsystem"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: Crear `FakeSoundPoolPlayer`**

Crear `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

import org.role.samples_button.core.audio.SoundPoolPlayer

class FakeSoundPoolPlayer : SoundPoolPlayer {
    val playedPaths = mutableListOf<String>()
    var released = false

    override fun play(filePath: String) {
        playedPaths += filePath
    }

    override fun release() {
        released = true
    }
}
```

- [ ] **Step 3: Escribir los tests nuevos y actualizar los existentes**

Reemplazar el contenido completo de `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.model.Group
import org.role.samples_button.core.model.SoundButton

@OptIn(ExperimentalCoroutinesApi::class)
class SoundBoardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial groups state is empty list`() = runTest {
        val viewModel = SoundBoardViewModel(FakeGroupRepository(), FakeSoundPoolPlayer())
        assertEquals(emptyList<Group>(), viewModel.groups.value)
    }

    @Test
    fun `createGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        val viewModel = SoundBoardViewModel(repo, FakeSoundPoolPlayer())
        viewModel.createGroup("Percusión")
        assertEquals(listOf("Percusión"), repo.createdGroups)
    }

    @Test
    fun `deleteGroup delegates to repository`() = runTest {
        val repo = FakeGroupRepository()
        val viewModel = SoundBoardViewModel(repo, FakeSoundPoolPlayer())
        viewModel.deleteGroup(42L)
        assertEquals(listOf(42L), repo.deletedIds)
    }

    @Test
    fun `playSound delegates filePath to player`() = runTest {
        val player = FakeSoundPoolPlayer()
        val viewModel = SoundBoardViewModel(FakeGroupRepository(), player)
        viewModel.playSound("/storage/emulated/0/Music/kick.mp3")
        assertEquals(listOf("/storage/emulated/0/Music/kick.mp3"), player.playedPaths)
    }

    @Test
    fun `playSound multiple times plays all paths`() = runTest {
        val player = FakeSoundPoolPlayer()
        val viewModel = SoundBoardViewModel(FakeGroupRepository(), player)
        viewModel.playSound("/storage/a.mp3")
        viewModel.playSound("/storage/b.mp3")
        viewModel.playSound("/storage/a.mp3")
        assertEquals(
            listOf("/storage/a.mp3", "/storage/b.mp3", "/storage/a.mp3"),
            player.playedPaths
        )
    }

    @Test
    fun `onCleared releases player`() = runTest {
        val player = FakeSoundPoolPlayer()
        val viewModel = SoundBoardViewModel(FakeGroupRepository(), player)
        viewModel.onCleared()
        assertTrue(player.released)
    }
}

class FakeGroupRepository : GroupRepository {
    val createdGroups = mutableListOf<String>()
    val deletedIds = mutableListOf<Long>()
    private val _groups = MutableStateFlow<List<Group>>(emptyList())

    override fun getGroupsWithButtons(): Flow<List<Group>> = _groups

    override suspend fun createGroup(name: String) {
        createdGroups.add(name)
        _groups.value = _groups.value + Group(
            id = createdGroups.size.toLong(),
            name = name,
            position = createdGroups.size - 1,
            buttons = emptyList()
        )
    }

    override suspend fun deleteGroup(id: Long) {
        deletedIds.add(id)
        _groups.value = _groups.value.filter { it.id != id }
    }

    override suspend fun reorderButtons(groupId: Long, buttons: List<SoundButton>) = Unit
}
```

- [ ] **Step 4: Verificar que los tests nuevos fallan (ViewModel aún no tiene `playSound`)**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :feature:soundboard:impl:testDebugUnitTest 2>&1 | tail -15
```

Expected: FAILED — `Unresolved reference: playSound` o error de compilación porque `SoundBoardViewModel` sigue teniendo un solo parámetro.

- [ ] **Step 5: Actualizar `SoundBoardViewModel`**

Reemplazar el contenido completo de `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`:

```kotlin
package org.role.samples_button.feature.soundboard.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun createGroup(name: String) {
        viewModelScope.launch { groupRepository.createGroup(name) }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch { groupRepository.deleteGroup(id) }
    }

    fun playSound(filePath: String) {
        soundPoolPlayer.play(filePath)
    }

    override fun onCleared() {
        soundPoolPlayer.release()
    }
}
```

- [ ] **Step 6: Ejecutar todos los tests de soundboard**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew :feature:soundboard:impl:testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` — 6 tests passing.

- [ ] **Step 7: Commit**

```bash
git add feature/soundboard/impl/build.gradle.kts \
        feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt \
        feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt \
        feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt
git commit -m "feat: inject SoundPoolPlayer into SoundBoardViewModel and expose playSound()"
```

---

## Task 3: SoundBoardScreen — SoundButtonItem clickable

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`

- [ ] **Step 1: Propagar `onPlaySound` y hacer `SoundButtonItem` clickable**

Reemplazar el contenido completo de `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`:

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SoundBoard") }) },
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
                onDelete = { viewModel.deleteGroup(it) },
                onAddSound = { groupId -> onNavigateToFileBrowser(groupId) },
                onPlaySound = { filePath -> viewModel.playSound(filePath) },
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
    onDelete: (Long) -> Unit,
    onAddSound: (Long) -> Unit,
    onPlaySound: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(groups, key = { it.id }) { group ->
            GroupCard(
                group = group,
                onDelete = { onDelete(group.id) },
                onAddSound = { onAddSound(group.id) },
                onPlaySound = onPlaySound
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    onDelete: () -> Unit,
    onAddSound: () -> Unit,
    onPlaySound: (String) -> Unit
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
                onAddSound = onAddSound,
                onPlaySound = onPlaySound
            )
        }
    }
}

@Composable
private fun ButtonGrid(
    buttons: List<SoundButton>,
    onAddSound: () -> Unit,
    onPlaySound: (String) -> Unit
) {
    val allItems: List<SoundButton?> = buttons + listOf(null)
    allItems.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { button ->
                if (button != null) {
                    SoundButtonItem(
                        button = button,
                        onClick = { onPlaySound(button.filePath) },
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = button.label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(4.dp)
            )
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

- [ ] **Step 2: Compilar debug completo**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew assembleDebug 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Ejecutar todos los tests del proyecto**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt
git commit -m "feat: wire onPlaySound through SoundBoardScreen to SoundButtonItem"
```

---

## Task 4: Instalar y verificar en emulador

- [ ] **Step 1: Verificar emulador conectado**

```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe devices
```

Expected: `emulator-XXXX   device`

- [ ] **Step 2: Instalar**

```bash
cd "C:/Users/ivan.muerte/AndroidStudioProjects/samplesbutton" && ./gradlew installDebug 2>&1 | tail -5
```

Expected: `Installed on 1 device.` + `BUILD SUCCESSFUL`

- [ ] **Step 3: Lanzar app**

```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am start -n "org.role.samples_button/.MainActivity"
```

Expected: `Starting: Intent { cmp=org.role.samples_button/.MainActivity }`

- [ ] **Step 4: Verificar proceso vivo sin crashes**

```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe shell pidof org.role.samples_button
```

Expected: número de PID (no vacío)

Si está vacío (crash), revisar logcat:

```bash
/c/Users/ivan.muerte/AppData/Local/Android/Sdk/platform-tools/adb.exe logcat -d -s AndroidRuntime:E 2>&1 | tail -30
```

- [ ] **Step 5: Verificar flujo en el emulador**

En el emulador:
1. Crear un grupo con el FAB "+"
2. Pulsar "+" dentro del GroupCard → navega a FileBrowserScreen
3. Conceder permiso si lo pide
4. Seleccionar un archivo de audio → confirmar label → volver
5. El botón aparece en el grid del grupo
6. Pulsar el botón → debe reproducirse el audio
7. Pulsar dos botones distintos rápidamente → deben sonar simultáneamente

# Delete Sample Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir al usuario eliminar un SoundButton de un grupo mediante long press → DropdownMenu → AlertDialog de confirmación.

**Architecture:** Se inyecta `SoundButtonRepository` en el ViewModel (ya está bound en Hilt) y se añade `deleteButton(id)`. En la UI, `SoundButtonItem` gana long press con `combinedClickable`, un `DropdownMenu` anclado y un `AlertDialog` de confirmación. El callback `onDelete` sube por la cadena: `SoundButtonItem → ButtonGrid → GroupCard → GroupList → SoundBoardScreen → ViewModel`.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Hilt, Room, Coroutines

---

## File Map

| Acción  | Archivo |
|---------|---------|
| Modify  | `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt` |
| Modify  | `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt` |
| Modify  | `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt` |

---

### Task 1: ViewModel — inyectar SoundButtonRepository y exponer deleteButton

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`
- Modify: `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`

- [ ] **Step 1: Añadir FakeSoundButtonRepository y el test fallido**

Al final de `SoundBoardViewModelTest.kt`, después de la clase `FakeGroupRepository`, añadir:

```kotlin
class FakeSoundButtonRepository : SoundButtonRepository {
    val deletedIds = mutableListOf<Long>()
    override suspend fun addButton(label: String, filePath: String, groupId: Long) = Unit
    override suspend fun deleteButton(id: Long) { deletedIds.add(id) }
}
```

Añadir también el import necesario al bloque de imports del test:

```kotlin
import org.role.samples_button.core.data.SoundButtonRepository
```

Y añadir este test dentro de `SoundBoardViewModelTest`:

```kotlin
@Test
fun `deleteButton delegates to repository`() = runTest {
    val soundButtonRepo = FakeSoundButtonRepository()
    val viewModel = SoundBoardViewModel(FakeGroupRepository(), soundButtonRepo, FakeSoundPoolPlayer())
    viewModel.deleteButton(99L)
    assertEquals(listOf(99L), soundButtonRepo.deletedIds)
}
```

- [ ] **Step 2: Ejecutar el test para confirmar que falla**

```bash
./gradlew :feature:soundboard:impl:test --tests "*.SoundBoardViewModelTest.deleteButton delegates to repository"
```

Resultado esperado: **FAILED** — `SoundBoardViewModel` constructor no acepta 3 argumentos todavía.

- [ ] **Step 3: Actualizar SoundBoardViewModel**

Reemplazar el contenido completo de `SoundBoardViewModel.kt`:

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.role.samples_button.core.audio.SoundPoolPlayer
import org.role.samples_button.core.data.GroupRepository
import org.role.samples_button.core.data.SoundButtonRepository
import org.role.samples_button.core.model.Group
import javax.inject.Inject

@HiltViewModel
class SoundBoardViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val soundButtonRepository: SoundButtonRepository,
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

    fun deleteButton(id: Long) {
        viewModelScope.launch { soundButtonRepository.deleteButton(id) }
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
```

- [ ] **Step 4: Actualizar todos los constructores en SoundBoardViewModelTest**

Cada `SoundBoardViewModel(repo, player)` o `SoundBoardViewModel(FakeGroupRepository(), player)` ahora necesita el segundo argumento `FakeSoundButtonRepository()`. Reemplazar todas las instanciaciones existentes:

| Línea original | Reemplazar por |
|---|---|
| `SoundBoardViewModel(FakeGroupRepository(), FakeSoundPoolPlayer())` | `SoundBoardViewModel(FakeGroupRepository(), FakeSoundButtonRepository(), FakeSoundPoolPlayer())` |
| `SoundBoardViewModel(repo, FakeSoundPoolPlayer())` | `SoundBoardViewModel(repo, FakeSoundButtonRepository(), FakeSoundPoolPlayer())` |
| `SoundBoardViewModel(FakeGroupRepository(), player)` | `SoundBoardViewModel(FakeGroupRepository(), FakeSoundButtonRepository(), player)` |
| `SoundBoardViewModel(repo, FakeSoundPoolPlayer())` (línea 55) | `SoundBoardViewModel(repo, FakeSoundButtonRepository(), FakeSoundPoolPlayer())` |

Hay 11 instanciaciones en total. Hacer un reemplazo global con estos dos patrones:
- `SoundBoardViewModel(FakeGroupRepository(), FakeSoundPoolPlayer())` → `SoundBoardViewModel(FakeGroupRepository(), FakeSoundButtonRepository(), FakeSoundPoolPlayer())`
- `SoundBoardViewModel(repo, FakeSoundPoolPlayer())` → `SoundBoardViewModel(repo, FakeSoundButtonRepository(), FakeSoundPoolPlayer())`
- `SoundBoardViewModel(FakeGroupRepository(), player)` → `SoundBoardViewModel(FakeGroupRepository(), FakeSoundButtonRepository(), player)`

- [ ] **Step 5: Ejecutar todos los tests del módulo**

```bash
./gradlew :feature:soundboard:impl:test
```

Resultado esperado: **BUILD SUCCESSFUL**, todos los tests en verde incluido `deleteButton delegates to repository`.

- [ ] **Step 6: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt
git add feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt
git commit -m "feat: inject SoundButtonRepository and expose deleteButton in ViewModel"
```

---

### Task 2: UI — long press, DropdownMenu y AlertDialog en SoundButtonItem

**Files:**
- Modify: `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`

- [ ] **Step 1: Actualizar imports en SoundBoardScreen.kt**

Añadir estos imports al bloque existente:

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
```

- [ ] **Step 2: Actualizar SoundBoardScreen — conectar onDeleteButton al ViewModel**

En la llamada a `GroupList` dentro de `SoundBoardScreen`, añadir el parámetro `onDeleteButton`:

```kotlin
GroupList(
    groups = groups,
    playingPaths = playingPaths,
    onDelete = { viewModel.deleteGroup(it) },
    onAddSound = { groupId -> onNavigateToFileBrowser(groupId) },
    onSoundButtonClick = { filePath ->
        if (filePath in playingPaths) viewModel.pauseSound(filePath)
        else viewModel.playSound(filePath)
    },
    onDeleteButton = { viewModel.deleteButton(it) },
    modifier = Modifier.padding(padding)
)
```

- [ ] **Step 3: Actualizar GroupList — propagar onDeleteButton**

Reemplazar la firma y el cuerpo de `GroupList`:

```kotlin
@Composable
private fun GroupList(
    groups: List<Group>,
    playingPaths: Set<String>,
    onDelete: (Long) -> Unit,
    onAddSound: (Long) -> Unit,
    onSoundButtonClick: (String) -> Unit,
    onDeleteButton: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(groups, key = { it.id }) { group ->
            GroupCard(
                group = group,
                playingPaths = playingPaths,
                onDelete = { onDelete(group.id) },
                onAddSound = { onAddSound(group.id) },
                onSoundButtonClick = onSoundButtonClick,
                onDeleteButton = onDeleteButton
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 4: Actualizar GroupCard — propagar onDeleteButton**

Reemplazar la firma y el cuerpo de `GroupCard`:

```kotlin
@Composable
private fun GroupCard(
    group: Group,
    playingPaths: Set<String>,
    onDelete: () -> Unit,
    onAddSound: () -> Unit,
    onSoundButtonClick: (String) -> Unit,
    onDeleteButton: (Long) -> Unit
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
                onSoundButtonClick = onSoundButtonClick,
                onDeleteButton = onDeleteButton
            )
        }
    }
}
```

- [ ] **Step 5: Actualizar ButtonGrid — propagar onDeleteButton**

Reemplazar la firma y el cuerpo de `ButtonGrid`:

```kotlin
@Composable
private fun ButtonGrid(
    buttons: List<SoundButton>,
    playingPaths: Set<String>,
    onAddSound: () -> Unit,
    onSoundButtonClick: (String) -> Unit,
    onDeleteButton: (Long) -> Unit
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
                        onDelete = { onDeleteButton(button.id) },
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
```

- [ ] **Step 6: Actualizar SoundButtonItem — long press, DropdownMenu y AlertDialog**

Reemplazar el composable `SoundButtonItem` completo:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundButtonItem(
    button: SoundButton,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.height(64.dp)) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
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

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Eliminar") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    showMenu = false
                    showConfirmDialog = true
                }
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Eliminar sample") },
            text = { Text("¿Eliminar \"${button.label}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onDelete()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
```

- [ ] **Step 7: Build para verificar que compila**

```bash
./gradlew :feature:soundboard:impl:assembleDebug
```

Resultado esperado: **BUILD SUCCESSFUL**

- [ ] **Step 8: Commit**

```bash
git add feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt
git commit -m "feat: add long press delete with DropdownMenu and confirmation dialog on SoundButtonItem"
```

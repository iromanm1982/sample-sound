# Audio Playback — Design Spec

**Date:** 2026-04-03
**Goal:** Reproducción polyfónica de SoundButtons. Al pulsar un botón suena el archivo de audio correspondiente; pueden sonar múltiples archivos a la vez (del mismo grupo o de distintos grupos). El primer tap encola la reproducción y la ejecuta automáticamente al terminar de cargar.

---

## Stack

- `SoundPool` (Android framework) — polyphony, maxStreams=8
- Hilt — inyección de `SoundPoolManager` en el ViewModel
- JUnit + fake — tests del ViewModel sin tocar Android framework

---

## Módulos afectados

| Módulo | Cambio |
|--------|--------|
| `:core:audio` | Implementar `SoundPoolPlayer` (interfaz) + `SoundPoolManager` (impl real) |
| `:feature:soundboard:impl` | Inyectar `SoundPoolPlayer` en ViewModel; propagar `onPlaySound` en UI |
| `:core:audio` `build.gradle.kts` | Sin cambios (SoundPool es Android framework) |
| `:feature:soundboard:impl` `build.gradle.kts` | Añadir `implementation(project(":core:audio"))` |

---

## Interfaz `SoundPoolPlayer` (core/audio)

```kotlin
interface SoundPoolPlayer {
    fun play(filePath: String)
    fun release()
}
```

Extraer interfaz permite sustituirla con un fake en tests de ViewModel sin depender del Android framework.

---

## `SoundPoolManager` (core/audio)

```kotlin
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

    private val soundIds = mutableMapOf<String, Int>()   // filePath → soundId
    private val pendingPlay = mutableSetOf<String>()     // esperando OnLoadComplete

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

**Comportamiento:**
- **Primera vez** que se toca un botón: `load()` registra el soundId en `soundIds` y añade el path a `pendingPlay`. `OnLoadCompleteListener` detecta el soundId, busca el path, lo elimina de `pendingPlay` y llama `play()`.
- **Taps siguientes**: `soundIds` ya contiene el soundId — `play()` es inmediato.
- **Polyphony**: hasta 8 streams simultáneos. Si se supera el límite, SoundPool descarta el stream de menor prioridad automáticamente.
- **Carga on-demand**: no se pre-cargan sonidos al arrancar. Solo al primer tap de cada botón.
- **Limpieza**: `release()` se llama en `onCleared()` del ViewModel.

### Binding Hilt

```kotlin
// core/audio/di/AudioModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds @Singleton
    abstract fun bindSoundPoolPlayer(impl: SoundPoolManager): SoundPoolPlayer
}
```

---

## `SoundBoardViewModel` (feature/soundboard/impl)

```kotlin
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

---

## `SoundBoardScreen` — propagación de `onPlaySound`

`SoundButtonItem` pasa de `Card` no clickable a `Card(onClick = onClick)`. El lambda se propaga hacia arriba sin estado adicional:

```
SoundBoardScreen
  └─ GroupList(onPlaySound: (String) -> Unit)
       └─ GroupCard(onPlaySound: (String) -> Unit)
            └─ ButtonGrid(onPlaySound: (String) -> Unit)
                 └─ SoundButtonItem(
                        button = button,
                        onClick = { onPlaySound(button.filePath) }
                    )
```

`SoundBoardScreen` conecta ViewModel con UI:
```kotlin
GroupList(
    onPlaySound = { filePath -> viewModel.playSound(filePath) },
    ...
)
```

---

## Testing

### `FakeSoundPoolPlayer`

```kotlin
class FakeSoundPoolPlayer : SoundPoolPlayer {
    val playedPaths = mutableListOf<String>()
    var released = false

    override fun play(filePath: String) { playedPaths += filePath }
    override fun release() { released = true }
}
```

### `SoundBoardViewModelTest`

```kotlin
class SoundBoardViewModelTest {
    @Test fun `playSound delegates to player`() {
        val player = FakeSoundPoolPlayer()
        val vm = SoundBoardViewModel(FakeGroupRepository(), player)
        vm.playSound("/storage/foo.mp3")
        assertEquals(listOf("/storage/foo.mp3"), player.playedPaths)
    }

    @Test fun `onCleared releases player`() {
        val player = FakeSoundPoolPlayer()
        val vm = SoundBoardViewModel(FakeGroupRepository(), player)
        vm.onCleared()
        assertTrue(player.released)
    }
}
```

`SoundPoolManager` en sí no se testa con JUnit (SoundPool requiere Android runtime). La cobertura de la lógica crítica (encolamiento, dispatch al cargar) queda en el ViewModel vía fake.

---

## Archivos modificados / creados

**Creados:**
- `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolPlayer.kt`
- `core/audio/src/main/java/org/role/samples_button/core/audio/di/AudioModule.kt`
- `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/FakeSoundPoolPlayer.kt`
- `feature/soundboard/impl/src/test/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModelTest.kt`

**Modificados:**
- `core/audio/src/main/java/org/role/samples_button/core/audio/SoundPoolManager.kt`
- `feature/soundboard/impl/build.gradle.kts` — añadir `implementation(project(":core:audio"))`
- `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardViewModel.kt`
- `feature/soundboard/impl/src/main/java/org/role/samples_button/feature/soundboard/impl/SoundBoardScreen.kt`

# Feature: File Browser — Design Spec

**Date:** 2026-04-02
**Feature:** Subir archivos de audio a grupos como SoundButtons
**Status:** Aprobado

---

## Objetivo

Permitir al usuario explorar los archivos de audio del dispositivo y agregarlos a un grupo como `SoundButton`. El botón queda visible en un grid de 3 columnas dentro del `GroupCard`. Los botones no reproducen audio todavía (eso viene en una feature posterior).

---

## Arquitectura general

```
feature/browser/api      →  FileBrowserNavigator (contrato público)
feature/browser/impl     →  FileBrowserScreen + FileBrowserViewModel
core/data                →  AudioFileRepository + SoundButtonRepositoryImpl
                             + actualizar GroupRepositoryImpl (buttons reales)
core/database            →  SoundButtonDao (completar esqueleto vacío)
app                      →  NavHost: soundboard / file_browser/{groupId}
feature/soundboard/impl  →  GroupCard con grid 3 columnas + botón "+" por grupo
```

**Flujo de datos completo:**
```
MediaStore → AudioFileRepositoryImpl → FileBrowserViewModel → FileBrowserScreen
                                                ↓ (al confirmar label)
                                       SoundButtonRepository.addButton(...)
                                                ↓
                                       Room (SoundButtonEntity)
                                                ↓
                                       GroupRepository.getGroupsWithButtons() [Flow reactivo]
                                                ↓
                                       SoundBoardViewModel.groups → GroupCard grid
```

---

## Modelo de dominio nuevo

En `:core:model`:

```kotlin
data class AudioFile(
    val id: Long,
    val displayName: String,  // nombre sin extensión, usado como label por defecto
    val filePath: String
)
```

---

## Capa de datos (`:core:database` + `:core:data`)

### SoundButtonDao (completar esqueleto vacío)

```kotlin
@Dao
interface SoundButtonDao {
    @Query("SELECT * FROM sound_buttons WHERE groupId = :groupId ORDER BY position ASC")
    fun getByGroupId(groupId: Long): Flow<List<SoundButtonEntity>>

    @Insert
    suspend fun insert(button: SoundButtonEntity): Long

    @Query("DELETE FROM sound_buttons WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

### GroupRepositoryImpl — actualizar getGroupsWithButtons()

Actualmente retorna `emptyList()` para `Group.buttons`. Hay que inyectar `SoundButtonDao` y hacer el join:

```kotlin
override fun getGroupsWithButtons(): Flow<List<Group>> =
    groupDao.getAllGroups().flatMapLatest { groupEntities ->
        if (groupEntities.isEmpty()) {
            flowOf(emptyList())
        } else {
            val buttonFlows = groupEntities.map { groupEntity ->
                soundButtonDao.getByGroupId(groupEntity.id).map { buttons ->
                    Group(
                        id = groupEntity.id,
                        name = groupEntity.name,
                        position = groupEntity.position,
                        buttons = buttons.map {
                            SoundButton(it.id, it.label, it.filePath, it.groupId, it.position)
                        }
                    )
                }
            }
            combine(buttonFlows) { it.toList() }
        }
    }
```

### SoundButtonRepositoryImpl

Implementa la interfaz `SoundButtonRepository` ya existente en `:core:data`. Firma actualizada de `addButton`:

```kotlin
interface SoundButtonRepository {
    suspend fun addButton(label: String, filePath: String, groupId: Long, position: Int)
    suspend fun deleteButton(id: Long)
}

@Singleton
class SoundButtonRepositoryImpl @Inject constructor(
    private val soundButtonDao: SoundButtonDao
) : SoundButtonRepository {
    override suspend fun addButton(label: String, filePath: String, groupId: Long, position: Int) {
        soundButtonDao.insert(SoundButtonEntity(label = label, filePath = filePath,
            groupId = groupId, position = position))
    }
    override suspend fun deleteButton(id: Long) {
        soundButtonDao.deleteById(id)
    }
}
```

### AudioFileRepository

Nueva interfaz + implementación en `:core:data`:

```kotlin
interface AudioFileRepository {
    fun getAudioFiles(): Flow<List<AudioFile>>
}

class AudioFileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioFileRepository {
    override fun getAudioFiles(): Flow<List<AudioFile>> = flow {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA
        )
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )
        val files = mutableListOf<AudioFile>()
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (it.moveToNext()) {
                val displayName = it.getString(nameCol).substringBeforeLast(".")
                files += AudioFile(it.getLong(idCol), displayName, it.getString(dataCol))
            }
        }
        emit(files)
    }
}
```

### DI — cambios en DatabaseModule y RepositoryModule

`DatabaseModule` añade:
```kotlin
@Provides
fun provideSoundButtonDao(db: AppDatabase): SoundButtonDao = db.soundButtonDao()
```

`RepositoryModule` añade:
```kotlin
@Binds @Singleton
abstract fun bindSoundButtonRepository(impl: SoundButtonRepositoryImpl): SoundButtonRepository

@Binds @Singleton
abstract fun bindAudioFileRepository(impl: AudioFileRepositoryImpl): AudioFileRepository
```

---

## Feature browser (`:feature:browser:api` + `:feature:browser:impl`)

### :feature:browser:api

Módulo Kotlin JVM puro (sin Android):

```kotlin
package org.role.samples_button.feature.browser.api

interface FileBrowserNavigator {
    fun navigateToFileBrowser(groupId: Long)
    fun navigateBack()
}
```

### :feature:browser:impl — FileBrowserViewModel

```kotlin
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
            // position = número de botones actuales (simplificado; se recalcula en repo)
            soundButtonRepository.addButton(
                label = label.trim(),
                filePath = filePath,
                groupId = groupId,
                position = 0  // el repositorio calcula la posición real
            )
        }
    }
}
```

**Nota:** `addButton` en `SoundButtonRepositoryImpl` calculará la posición real consultando el count actual de botones en el grupo, similar a cómo `createGroup` calcula la posición.

### :feature:browser:impl — FileBrowserScreen

```kotlin
@Composable
fun FileBrowserScreen(
    groupId: Long,
    onNavigateBack: () -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel()
)
```

**Estados de la pantalla:**

1. **Sin permiso** — muestra texto explicativo + botón "Conceder permiso" que usa `rememberPermissionState(READ_MEDIA_AUDIO)`. Si el usuario deniega permanentemente, muestra "Ve a Ajustes para activar el permiso".

2. **Con permiso, lista cargada** — `LazyColumn` de `AudioFileItem` (nombre del archivo). Al tocar un item se muestra `ConfirmLabelDialog`.

3. **`ConfirmLabelDialog`** — `AlertDialog` con `OutlinedTextField` pre-llenado con `audioFile.displayName`. Botones: Cancelar / Agregar. Al confirmar: `viewModel.addButtonToGroup(label, filePath, groupId)` + `onNavigateBack()`.

**Estructura Compose:**
```
FileBrowserScreen
├── Scaffold
│   ├── TopAppBar("Seleccionar audio", navigationIcon = BackButton → onNavigateBack)
│   └── content:
│       ├── PermissionRequired (si no hay permiso)
│       └── AudioList → AudioFileItem (si hay permiso)
└── ConfirmLabelDialog (si hay archivo seleccionado)
```

**Permiso en AndroidManifest.xml de `:app`:**
```xml
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

---

## Actualización de SoundBoardScreen (`:feature:soundboard:impl`)

### GroupCard — grid de botones

```
┌─────────────────────────────────┐
│ Percusión              [delete] │
│ ┌──────┐ ┌──────┐ ┌──────┐    │
│ │ Kick │ │Snare │ │  Hi  │    │
│ └──────┘ └──────┘ └──────┘    │
│ ┌──────┐                       │
│ │  +   │  ← navega a browser  │
│ └──────┘                       │
└─────────────────────────────────┘
```

Usa `LazyVerticalGrid(columns = Fixed(3))`. El botón `+` siempre es el último item del grid. Los `SoundButtonCard` solo muestran el label (sin reproducción de audio todavía).

`GroupCard` recibe nuevo parámetro: `onAddSound: () -> Unit`.

`SoundBoardScreen` recibe nuevo parámetro: `onNavigateToFileBrowser: (Long) -> Unit`, lo pasa a cada `GroupCard`.

### SoundBoardViewModel

No se modifica. Los grupos con sus botones ya llegan reactivamente via `getGroupsWithButtons()` — no se necesita ninguna función extra ni nueva inyección.

---

## Navegación (`:app`)

### Dependencia nueva

En `libs.versions.toml`:
```toml
navigationCompose = "2.9.0"
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
```

### NavHost en MainActivity

```kotlin
@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamplesButtonTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "soundboard") {
                    composable("soundboard") {
                        val viewModel: SoundBoardViewModel = hiltViewModel()
                        SoundBoardScreen(
                            viewModel = viewModel,
                            onNavigateToFileBrowser = { groupId ->
                                navController.navigate("file_browser/$groupId")
                            }
                        )
                    }
                    composable(
                        "file_browser/{groupId}",
                        arguments = listOf(navArgument("groupId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val groupId = backStackEntry.arguments!!.getLong("groupId")
                        FileBrowserScreen(
                            groupId = groupId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
```

`MainActivity` ya no usa `by viewModels()` — el ViewModel lo provee `hiltViewModel()` dentro del composable, que es el patrón correcto con Compose Navigation.

---

## Módulos nuevos — estructura de archivos

```
feature/browser/
├── api/
│   ├── build.gradle.kts          (kotlin.jvm)
│   └── src/main/java/.../FileBrowserNavigator.kt
└── impl/
    ├── build.gradle.kts          (android.library + ksp + compose.compiler)
    ├── src/main/AndroidManifest.xml
    └── src/main/java/.../
        ├── FileBrowserViewModel.kt
        └── FileBrowserScreen.kt
```

---

## Permisos en runtime — Accompanist

Librería: `com.google.accompanist:accompanist-permissions`

```toml
accompanist = "0.37.3"
accompanist-permissions = { group = "com.google.accompanist", name = "accompanist-permissions", version.ref = "accompanist" }
```

Se usa `rememberPermissionState` en `FileBrowserScreen` para solicitar `READ_MEDIA_AUDIO`.

---

## Testing

**`FakeSoundButtonDao`** — implementación fake para tests de `SoundButtonRepositoryImpl`:
- Lista en memoria + `MutableStateFlow` por groupId

**`FakeAudioFileRepository`** — retorna lista fija de `AudioFile` para tests del `FileBrowserViewModel`

**`FileBrowserViewModelTest`**:
- `addButtonToGroup` llama al repositorio con los parámetros correctos
- Estado inicial de `audioFiles` es vacío

**`SoundButtonRepositoryImplTest`**:
- `addButton` inserta correctamente en el DAO
- `deleteButton` elimina correctamente

---

## Módulos registrados en settings.gradle.kts

```kotlin
include(":feature:browser:api")
include(":feature:browser:impl")
```

---

## Restricciones y notas

- Los `SoundButton` **no reproducen audio** en esta feature — eso viene en la siguiente iteración con `SoundPoolManager`
- `filePath` de `SoundButtonEntity` debe validarse antes de reproducir (el archivo puede haberse movido); validación diferida a la feature de audio
- La posición de los botones se asigna secuencialmente (count actual del grupo); reordenamiento con drag & drop viene después
- `hiltViewModel()` requiere `navigation-compose` + que el composable esté dentro de un `NavBackStackEntry` — esto funciona correctamente con el NavHost en MainActivity

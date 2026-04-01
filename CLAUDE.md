# SoundBoard App — Claude Code Instructions

## Descripción del proyecto
Aplicación soundboard/sampler que permite:
- Explorar el filesystem del dispositivo para seleccionar archivos de audio
- Crear botones de reproducción a partir de esos archivos
- Agrupar botones en grupos personalizados (ej: "Percusión", "Melodías")
- Reordenar botones dentro de cada grupo mediante drag & drop
- Persistir grupos y botones entre sesiones

## Stack tecnológico
- Language: Kotlin
- UI: Jetpack Compose
- Architecture: MVVM + Clean Architecture (UI / Domain / Data)
- Audio: SoundPool (polyphony — varios sonidos simultáneos)
- DI: Hilt
- DB: Room (persistencia de grupos y botones)
- Async: Coroutines + Kotlin Flow
- Drag & Drop: ReorderablelazyColumn (biblioteca: sh.calvin.reorderable)
- Testing: JUnit, Turbine, Compose Testing
- Build: Gradle con Version Catalog (libs.versions.toml)

## Permisos necesarios (AndroidManifest.xml)
- READ_MEDIA_AUDIO (Android 13+)
- READ_EXTERNAL_STORAGE (Android < 13, maxSdkVersion 32)
- Solicitar permisos en runtime con rememberPermissionState (Accompanist)

## Estructura de módulos
app/
feature/
├── browser/           # Explorador de archivos de audio del filesystem
│   ├── api/           # Contrato de navegación público
│   └── impl/          # FileBrowserScreen + ViewModel
├── soundboard/        # Pantalla principal con grupos y botones
│   ├── api/
│   └── impl/          # SoundBoardScreen + ViewModel
└── groupmanager/      # Crear / renombrar / eliminar grupos
├── api/
└── impl/
core/
├── data/              # Repositorios: GroupRepository, SoundButtonRepository
├── database/          # Room: GroupEntity, SoundButtonEntity, DAOs
├── audio/             # SoundPoolManager — wrapper de SoundPool
├── model/             # Domain models: Group, SoundButton
├── ui/                # Componentes reutilizables (SoundButton, GroupCard)
└── designsystem/      # Tema, colores, tipografía

## Modelos de dominio clave
data class Group(
val id: Long,
val name: String,
val position: Int,           // para reordenar grupos
val buttons: List<SoundButton>
)

data class SoundButton(
val id: Long,
val label: String,
val filePath: String,        // ruta absoluta en el filesystem
val groupId: Long,
val position: Int            // para reordenar dentro del grupo
)

## Patrones estándar

### SoundPoolManager (core/audio)
// Usar SoundPool con maxStreams = 8 para polyphony
// Cargar sonidos on-demand con load(filePath)
// Liberar con release() en onCleared del ViewModel
// Manejar el callback OnLoadCompleteListener antes de play()

### ViewModel patrón
@HiltViewModel
class SoundBoardViewModel @Inject constructor(
private val groupRepository: GroupRepository,
private val soundPoolManager: SoundPoolManager
) : ViewModel() {
val groups: StateFlow<List<Group>> = groupRepository
.getGroupsWithButtons()
.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playSound(filePath: String) {
        soundPoolManager.play(filePath)
    }

    fun reorderButton(groupId: Long, fromIndex: Int, toIndex: Int) { ... }

    override fun onCleared() { soundPoolManager.release() }
}

### Repository (offline-first con Room)
interface GroupRepository {
fun getGroupsWithButtons(): Flow<List<Group>>
suspend fun createGroup(name: String)
suspend fun deleteGroup(id: Long)
suspend fun reorderButtons(groupId: Long, buttons: List<SoundButton>)
}

### FileBrowser
// Usar MediaStore.Audio.Media para listar audios del dispositivo
// Columnas: _ID, DISPLAY_NAME, DATA (ruta), DURATION, SIZE
// Filtrar por MIME type audio/*
// Exponer como Flow<List<AudioFile>> desde AudioFileRepository

## Flujo principal de usuario
1. SoundBoardScreen muestra LazyColumn de GroupCards
2. Cada GroupCard contiene LazyRow de SoundButtons (reordenables)
3. Botón "+" en cada grupo abre FileBrowserScreen (bottom sheet)
4. FileBrowserScreen lista audios vía MediaStore → usuario selecciona
5. Se crea SoundButton, se persiste en Room, se recarga el grupo
6. Al pulsar SoundButton → SoundPoolManager.play(filePath)
7. Drag & drop en los botones actualiza position en Room

## Drag & Drop
// Usar la biblioteca: sh.calvin.reorderable
// implementation("sh.calvin.reorderable:reorderable:2.x.x")
// ReorderableItem + detectReorderAfterLongPress en cada botón
// Al soltar, llamar a viewModel.reorderButton(...)

## Manejo de permisos
// Comprobar READ_MEDIA_AUDIO en el arranque
// Si no hay permiso, mostrar pantalla de onboarding con explicación
// Usar ActivityResultContracts.RequestPermission()

## Comandos útiles
- Build debug:   ./gradlew assembleDebug
- Tests:         ./gradlew test
- Lint:          ./gradlew lint
- Room schema:   activar exportSchema = true en @Database

## Notas importantes
- SoundPool tiene un límite de streams simultáneos: configurar maxStreams=8
- Los filePath deben validarse antes de cargar (el archivo puede haberse movido)
- Al eliminar un SoundButton, liberar su soundId en SoundPool
- Evitar cargar todos los sonidos al arrancar; cargar on-demand
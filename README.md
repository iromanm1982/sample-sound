# SamplesButton — SoundBoard Android App

Aplicación soundboard/sampler para Android que permite explorar archivos de audio del dispositivo, crear botones de reproducción y organizarlos en grupos personalizados con soporte de polifonía.

## Características

- **Explorador de archivos de audio** — Lista los audios del dispositivo via MediaStore con solicitud de permisos en runtime
- **Grupos personalizados** — Crea, renombra y elimina grupos (ej: "Percusión", "Melodías")
- **Reordenación de grupos** — Long press sobre un grupo para arrastrarlo a la posición deseada
- **Grid de botones por grupo** — Cada grupo muestra sus botones en una cuadrícula de 3 columnas
- **Reproducción polofónica** — Hasta 8 streams simultáneos via SoundPool; pulsa varios botones a la vez
- **Reordenación de botones** — Long press sobre un botón dentro del grupo para reordenarlo via drag & drop
- **Gestión de botones** — Renombra o elimina botones individuales desde su menú contextual
- **Indicador visual de reproducción** — Los botones activos se resaltan con el color primario y un icono de pausa
- **Pausa global** — Botón en el toolbar del grupo para detener todos los sonidos de golpe
- **Carga on-demand** — Los sonidos se cargan al primer tap y se cachean para reproducción instantánea posterior
- **Persistencia** — Grupos y botones (con su orden) sobreviven reinicios de la app via Room Database
- **UI reactiva** — Compose + StateFlow; la pantalla se actualiza automáticamente ante cualquier cambio

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Kotlin 2.1.20 |
| UI | Jetpack Compose (BOM 2025.04.00) |
| Arquitectura | MVVM + Clean Architecture |
| Audio | SoundPool (maxStreams = 8) |
| Inyección de dependencias | Hilt 2.56.1 |
| Base de datos | Room 2.7.0 |
| Async | Coroutines 1.10.2 + Kotlin Flow |
| Navegación | Navigation Compose 2.9.0 |
| Permisos | Accompanist Permissions 0.37.3 |
| Drag & Drop | sh.calvin.reorderable 2.4.3 |
| Build | Gradle con Version Catalog |

**SDK:** minSdk 30 · targetSdk 36 · compileSdk 36

## Estructura de módulos

```
samplesbutton/
├── app/                        # Entry point, NavHost, Hilt Application
│
├── core/
│   ├── model/                  # Modelos de dominio: Group, SoundButton, AudioFile
│   ├── database/               # Room: entidades, DAOs, AppDatabase
│   ├── data/                   # Repositorios: Group, SoundButton, AudioFile
│   ├── audio/                  # SoundPoolPlayer (interfaz) + SoundPoolManager (impl)
│   ├── ui/                     # Componentes Compose reutilizables
│   └── designsystem/           # SamplesButtonTheme (Material3)
│
└── feature/
    ├── soundboard/
    │   ├── api/                # Contrato de navegación (SoundBoardNavigator)
    │   └── impl/               # SoundBoardScreen + SoundBoardViewModel
    │                           # GroupDetailScreen + GroupDetailViewModel
    └── browser/
        ├── api/                # Contrato de navegación (FileBrowserNavigator)
        └── impl/               # FileBrowserScreen + FileBrowserViewModel
```

## Modelos de dominio

```kotlin
data class Group(
    val id: Long,
    val name: String,
    val position: Int,
    val buttons: List<SoundButton>
)

data class SoundButton(
    val id: Long,
    val label: String,
    val filePath: String,   // ruta absoluta en el filesystem
    val groupId: Long,
    val position: Int
)

data class AudioFile(
    val id: Long,
    val displayName: String,
    val filePath: String
)
```

## Flujo de usuario

```
SoundBoardScreen
  └── FAB "+"           →  CreateGroupDialog  →  Nuevo grupo en lista
  └── Long press grupo  →  Drag & drop para reordenar  →  posición persiste en Room
  └── GroupCard (tap)   →  GroupDetailScreen
        └── FAB "+"  →  FileBrowserScreen
              └── Solicitar permiso READ_MEDIA_AUDIO
              └── Lista de audios del dispositivo
              └── Tap en archivo  →  ConfirmLabelDialog (editar nombre del botón)
                    └── Confirmar  →  SoundButton guardado en Room  →  aparece en el grid
        └── Tap en SoundButton          →  SoundPoolManager.play(filePath)
        └── Long press en SoundButton   →  Drag & drop para reordenar
        └── Menú "⋮" en SoundButton     →  Renombrar / Eliminar
        └── Icono pausa en toolbar      →  Detener todos los sonidos
  └── Menú "⋮" en GroupCard  →  Renombrar / Eliminar grupo
```

## Permisos

Declarados en `AndroidManifest.xml` y solicitados en runtime desde `FileBrowserScreen`:

```xml
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

## Compilar y ejecutar

```bash
# Compilar debug
./gradlew assembleDebug

# Instalar en dispositivo/emulador conectado
./gradlew installDebug

# Ejecutar tests unitarios
./gradlew test

# Lint
./gradlew lint
```

Requiere Android Studio Meerkat (2025.1) o superior con JDK 11+.

## Arquitectura de audio

`SoundPoolManager` es `@ActivityRetainedScoped` — su ciclo de vida coincide con el del ViewModel.

```
Primer tap           → soundPool.load(filePath)  → pendingPlay.add(filePath)
OnLoadComplete cb    → soundPool.play(soundId)   → audio reproducido
Taps siguientes      → soundIds[filePath] hit     → soundPool.play inmediato
ViewModel.onCleared  → soundPoolManager.release() → recursos liberados
```

Acceso concurrente entre el hilo principal y el callback de SoundPool protegido con `synchronized(lock)`.

## Tests

```bash
# Todos los tests unitarios
./gradlew test

# Por módulo
./gradlew :feature:soundboard:impl:test
./gradlew :feature:browser:impl:test
./gradlew :core:data:test
```

Los tests usan fakes en lugar de mocks para evitar dependencias del framework Android:
- `FakeSoundPoolPlayer` — verifica que `playSound()` delega correctamente y `onCleared()` libera recursos
- `FakeGroupRepository` / `FakeSoundButtonRepository` — repositorios en memoria con tracking de llamadas
- `FakeGroupDao` / `FakeSoundButtonDao` — DAOs en memoria con `MutableStateFlow` que simulan Room
- `TestGroupRepository` / `TestSoundButtonRepository` — variantes sin `withTransaction` para tests puros de JVM

# SamplesButton — SoundBoard Android App

Aplicación soundboard/sampler para Android que permite explorar archivos de audio del dispositivo, crear botones de reproducción y organizarlos en grupos personalizados con soporte de polifonía.

## Características

- **Explorador de archivos de audio** — Lista los audios del dispositivo via MediaStore con solicitud de permisos en runtime
- **Grupos personalizados** — Crea, renombra y elimina grupos (ej: "Percusión", "Melodías")
- **Reproducción polofónica** — Hasta 8 streams simultáneos via SoundPool; pulsa varios botones a la vez
- **Carga on-demand** — Los sonidos se cargan al primer tap y se cachean para reproducción instantánea posterior
- **Persistencia** — Grupos y botones sobreviven reinicios de la app via Room Database
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
  └── FAB "+"  →  CreateGroupDialog  →  Nuevo grupo en lista
  └── GroupCard "+"  →  FileBrowserScreen
        └── Solicitar permiso READ_MEDIA_AUDIO
        └── Lista de audios del dispositivo
        └── Tap en archivo  →  ConfirmLabelDialog (editar nombre del botón)
              └── Confirmar  →  SoundButton guardado en Room  →  aparece en el grid
  └── Tap en SoundButton  →  SoundPoolManager.play(filePath)  →  audio reproducido
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
# Tests del ViewModel de SoundBoard (incluye FakeSoundPoolPlayer)
./gradlew :feature:soundboard:impl:test

# Tests del ViewModel del FileBrowser
./gradlew :feature:browser:impl:test
```

Los tests de ViewModel usan fakes en lugar de mocks para evitar dependencias del framework Android:
- `FakeSoundPoolPlayer` — verifica que `playSound()` delega correctamente y `onCleared()` libera recursos
- `FakeGroupRepository`, `FakeSoundButtonRepository` — repositorios en memoria para tests del browser

## Próximas funcionalidades

- [ ] Drag & drop para reordenar botones dentro de un grupo (`sh.calvin.reorderable`)
- [ ] Eliminar botones individuales
- [ ] Reordenar grupos
- [ ] Indicador visual de "sonando" en el botón activo
- [ ] Soporte para renombrar grupos existentes

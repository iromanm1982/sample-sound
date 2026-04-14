# Diseño: Navegación a detalle de grupo

**Fecha:** 2026-04-09  
**Estado:** Aprobado

## Resumen

Rediseño de la pantalla principal para mostrar únicamente la lista de grupos. Los botones de cada grupo se mueven a una pantalla de detalle dedicada (`GroupDetailScreen`). Desde la lista se puede añadir un sample a cualquier grupo sin entrar en el detalle.

---

## Navegación

### Rutas (NavHost en `MainActivity`)

| Ruta | Pantalla |
|------|----------|
| `soundboard` | `SoundBoardScreen` — lista de grupos |
| `group_detail/{groupId}` | `GroupDetailScreen` — botones del grupo |
| `file_browser/{groupId}` | `FileBrowserScreen` — explorador de archivos (existente) |

### Flujo

```
SoundBoardScreen
  ├─ tap cuerpo tarjeta  → navigate("group_detail/{groupId}")
  ├─ tap "+"             → navigate("file_browser/{groupId}")  ← sin pasar por detalle
  ├─ tap "⋮"            → DropdownMenu con Renombrar / Eliminar grupo
  └─ FAB "+"             → CreateGroupDialog (sin cambios)

GroupDetailScreen
  ├─ tap botón           → play / pause
  ├─ long press botón    → DropdownMenu con Renombrar / Eliminar botón
  ├─ tap "+" al final    → navigate("file_browser/{groupId}")
  └─ back button         → popBackStack()
```

---

## UI — SoundBoardScreen (refactorizada)

Cada `GroupCard` muestra:
- **Nombre del grupo** (izquierda, clickable para navegar al detalle)
- **Badge** con número de samples (centro-derecha)
- **Botón "+"** (icono) → navega directo al `FileBrowserScreen`
- **Botón "⋮"** → `DropdownMenu` con opciones:
  - Renombrar grupo → `RenameGroupDialog`
  - Eliminar grupo → `DeleteGroupDialog`

La `GroupCard` **no** muestra botones de audio.

---

## UI — GroupDetailScreen (nueva)

- `TopAppBar` con back button y nombre del grupo como título
- Icono de pausa global (igual que ahora) si hay sonidos reproduciéndose
- Grid de 3 columnas con los `SoundButtonItem` del grupo
- Último elemento: `AddSoundButton` ("+") que navega al `FileBrowserScreen`
- Long press en `SoundButtonItem` → `DropdownMenu` con Renombrar / Eliminar

---

## Arquitectura — Módulos y archivos

### Archivos modificados

**`feature/soundboard/impl/SoundBoardScreen.kt`**
- Refactorizar `GroupCard`: eliminar `ButtonGrid`, añadir badge + "+" + "⋮"
- Añadir `RenameGroupDialog` (nuevo, similar al `RenameDialog` existente)
- Eliminar composables `ButtonGrid`, `SoundButtonItem`, `AddSoundButton` (se mueven al detalle)

**`feature/soundboard/impl/SoundBoardViewModel.kt`**
- Eliminar lógica de audio (play/pause/SoundPoolManager)
- Mantener: `groups: StateFlow<List<Group>>`, `createGroup`, `deleteGroup`, `renameGroup`

**`app/src/main/java/.../MainActivity.kt`**
- Añadir ruta `group_detail/{groupId}` con argumento `Long`

### Archivos nuevos

**`feature/soundboard/impl/GroupDetailScreen.kt`**
- Recibe `groupId: Long` y `onNavigateToFileBrowser: (Long) -> Unit`
- Contiene: `GroupDetailContent`, `ButtonGrid`, `SoundButtonItem`, `AddSoundButton` (movidos desde `SoundBoardScreen.kt`)

**`feature/soundboard/impl/GroupDetailViewModel.kt`**
- Anotado con `@HiltViewModel`
- Recibe `groupId` vía `SavedStateHandle`
- `group: StateFlow<Group?>` — grupo completo con botones
- `playingPaths: StateFlow<Set<String>>`
- Métodos: `playSound`, `pauseSound`, `pauseAll`, `deleteButton`, `renameButton`
- Gestiona `SoundPoolManager` (lógica movida desde `SoundBoardViewModel`)

---

## Estado y datos

```
SoundBoardViewModel
  groups: StateFlow<List<Group>>   ← solo necesita id, name, buttons.size
  createGroup(name)
  deleteGroup(id)
  renameGroup(id, newName)         ← nuevo

GroupDetailViewModel
  group: StateFlow<Group?>         ← grupo completo con botones
  playingPaths: StateFlow<Set<String>>
  playSound(filePath)
  pauseSound(filePath)
  pauseAll()
  deleteButton(id)
  renameButton(id, newLabel)
  onCleared() → soundPoolManager.release()
```

---

## Lo que NO cambia

- `FileBrowserScreen` y su ViewModel — sin modificaciones
- `GroupRepository`, `SoundButtonRepository`, Room entities y DAOs — sin modificaciones
- `SoundPoolManager` — sin modificaciones, se inyecta en `GroupDetailViewModel`
- Lógica de `CreateGroupDialog` — sin cambios
- Diálogos de renombrar / eliminar botones — se mueven sin cambios a `GroupDetailScreen.kt`

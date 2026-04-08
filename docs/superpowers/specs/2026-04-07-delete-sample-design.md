# Spec: Borrar sample de un grupo

**Fecha:** 2026-04-07
**Feature branch:** feature/stop-audio (en curso)

---

## Objetivo

Permitir al usuario eliminar un sample (SoundButton) de un grupo mediante long press → menú contextual → confirmación.

---

## Flujo de usuario

1. El usuario hace **long press** sobre un `SoundButtonItem`.
2. Aparece un **`DropdownMenu`** anclado al botón con la opción "Eliminar".
3. El usuario toca "Eliminar" → el menú se cierra y aparece un **`AlertDialog`** de confirmación con el nombre del sample.
4. El usuario confirma → el botón se elimina de la base de datos y desaparece de la UI.
5. El usuario cancela → vuelve al estado normal sin cambios.

---

## Cambios por capa

### ViewModel — `SoundBoardViewModel`

- Inyectar `SoundButtonRepository` (ya existe, solo falta añadirlo al constructor).
- Añadir método:

```kotlin
fun deleteButton(id: Long) {
    viewModelScope.launch { soundButtonRepository.deleteButton(id) }
}
```

El `groups` StateFlow se actualiza automáticamente vía Room al eliminar.

### UI — `SoundBoardScreen`

**`SoundButtonItem`** recibe un nuevo parámetro `onDelete: () -> Unit` y añade:

- Estado local `var showMenu by remember { mutableStateOf(false) }`.
- Estado local `var showConfirmDialog by remember { mutableStateOf(false) }`.
- El `Card` añade `onLongClick = { showMenu = true }` (usando `combinedClickable`).
- `DropdownMenu` anclado al botón con item "Eliminar".
- `AlertDialog` de confirmación al activar `showConfirmDialog`.

**`ButtonGrid`** pasa `onDeleteButton: (Long) -> Unit` a `SoundButtonItem`.

**`GroupCard`** pasa `onDeleteButton` recibido desde `GroupList`.

**`GroupList`** recibe `onDeleteButton: (Long) -> Unit` desde `SoundBoardScreen`.

**`SoundBoardScreen`** conecta: `onDeleteButton = { viewModel.deleteButton(it) }`.

---

## Capas no modificadas

- `SoundButtonRepository` — `deleteButton(id)` ya implementado.
- `SoundButtonRepositoryImpl` — ya implementado.
- `SoundButtonDao` — `deleteById(id)` ya implementado.
- Room schema — sin cambios.

---

## Casos límite

- Si el sample está sonando cuando se borra, no se pausa explícitamente. SoundPool libera el stream al completarse naturalmente. En una iteración futura se puede añadir `pauseSound(filePath)` antes de borrar.
- El `filePath` puede apuntar a un archivo ya inexistente; el borrado solo elimina la entidad de Room, lo cual es correcto.

---

## Futuras extensiones contempladas

El `DropdownMenu` permite añadir fácilmente:
- "Renombrar" — editar el label del SoundButton.
- Otras acciones de gestión del sample.

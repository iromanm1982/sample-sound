# Sample Row Controls — Design Spec

**Date:** 2026-04-19  
**Scope:** `GroupDetailScreen` + `GroupDetailViewModel` + `SoundPoolPlayer` / `SoundPoolManager`

---

## Resumen

Cambiar el grid de 3 columnas de la pantalla de detalle de grupo por una lista de 1 columna. Con el espacio ganado, cada sample muestra:

- Botón play/pause
- Botón volver al principio (↩) — funciona cuando está sonando y también en pausa
- Botón loop (⟳) — activa/desactiva reproducción en bucle (estado runtime, no persiste en BD)
- Duración total del sample formateada como `m:ss`
- Badge "● Sonando" cuando está en reproducción

El drag & drop para reordenar se mantiene.

---

## 1. `SoundPoolPlayer` (interfaz)

Añadir dos métodos nuevos:

```kotlin
interface SoundPoolPlayer {
    fun play(filePath: String)
    fun pause(filePath: String)
    fun pauseAll()
    fun release()
    fun restart(filePath: String)                    // nuevo
    fun setLooping(filePath: String, loop: Boolean)  // nuevo
}
```

### `restart(filePath)`
- Si el `MediaPlayer` está sonando: `seekTo(0)` y el audio continúa desde el principio.
- Si está en pausa: `seekTo(0)` y permanece en pausa en posición 0.
- Si no existe player para ese path (nunca se ha reproducido): no hace nada.

### `setLooping(filePath, loop)`
- Llama a `MediaPlayer.isLooping = loop` sobre el player activo para ese path.
- Si no existe player para ese path: almacena el estado en un `pendingLoop: Map<String, Boolean>` para aplicarlo cuando se cree el player en `play()`.

---

## 2. `GroupDetailViewModel`

### 2.1 Loop (runtime)

```kotlin
private val _loopingPaths = MutableStateFlow<Set<String>>(emptySet())
val loopingPaths: StateFlow<Set<String>> = _loopingPaths.asStateFlow()

fun toggleLoop(filePath: String) {
    val isNowLooping = filePath !in _loopingPaths.value
    _loopingPaths.update { if (isNowLooping) it + filePath else it - filePath }
    soundPoolPlayer.setLooping(filePath, isNowLooping)
}
```

El estado de loop no se persiste en Room. Se reinicia al salir de la pantalla.

### 2.2 Restart

```kotlin
fun restartSound(filePath: String) {
    soundPoolPlayer.restart(filePath)
}
```

### 2.3 Duraciones

```kotlin
private val _durations = MutableStateFlow<Map<String, Long>>(emptyMap())
val durations: StateFlow<Map<String, Long>> = _durations.asStateFlow()
```

En `init`, observar el `group` flow. Cada vez que llegan botones, calcular duraciones en `Dispatchers.IO`:

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    group.filterNotNull().collect { g ->
        val map = g.buttons.associate { btn ->
            val retriever = MediaMetadataRetriever()
            val durationMs = try {
                retriever.setDataSource(btn.filePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                0L
            } finally {
                retriever.release()
            }
            btn.filePath to durationMs
        }
        _durations.value = map
    }
}
```

Duración 0L se muestra como `—` en la UI. Duraciones válidas se formatean: `String.format("%d:%02d", ms/60000, (ms/1000)%60)`.

---

## 3. UI — `GroupDetailScreen`

### 3.1 `ButtonGrid` → `ButtonList`

Reemplazar `LazyVerticalGrid(GridCells.Fixed(3))` por `LazyColumn` con `rememberReorderableLazyListState`.

```kotlin
@Composable
private fun ButtonList(
    buttons: List<SoundButton>,
    playingPaths: Set<String>,
    loopingPaths: Set<String>,
    durations: Map<String, Long>,
    onAddSound: () -> Unit,
    onSoundButtonClick: (String) -> Unit,
    onRestartButton: (String) -> Unit,
    onToggleLoop: (String) -> Unit,
    onDeleteButton: (Long) -> Unit,
    onRenameButton: (Long, String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier
)
```

### 3.2 `SoundButtonRow` — layout de la tarjeta

```
┌─────────────────────────────────────────────┐
│ [label]                  [● Sonando]  [⋮]   │  fila 1
│ [▶/⏸]  [↩]  [⟳]                    m:ss   │  fila 2
└─────────────────────────────────────────────┘
```

- **Fila 1:** `Text(label, weight=1f)` + badge "● Sonando" (visible solo si `isPlaying`) + `IconButton(⋮)` para menú
- **Fila 2:** `IconButton(▶/⏸)` + `IconButton(↩)` + `IconButton(⟳, tint=primary si looping)` + `Text(duración, Arrangement.End)`
- La tarjeta entera actúa como drag handle via `longPressDraggableHandle()` (igual que antes)
- Color de fondo: `primaryContainer` si `isPlaying`, `secondaryContainer` si no

### 3.3 `AddSoundButton`

Pasa de cuadrado con `aspectRatio(1f)` a `OutlinedCard` de ancho completo con una fila `Icon + Text("Añadir sample")`.

---

## Archivos afectados

| Archivo | Cambio |
|---|---|
| `core/audio/.../SoundPoolPlayer.kt` | Añadir `restart()` y `setLooping()` |
| `core/audio/.../SoundPoolManager.kt` | Implementar `restart()` y `setLooping()` |
| `feature/soundboard/impl/.../GroupDetailViewModel.kt` | Añadir `loopingPaths`, `durations`, `toggleLoop()`, `restartSound()` |
| `feature/soundboard/impl/.../GroupDetailScreen.kt` | Reemplazar grid por lista, nuevo `SoundButtonRow` |

---

## Lo que NO cambia

- `SoundBoardScreen` (pantalla de grupos) — sin cambios
- `Room` / entidades de BD — sin cambios
- `FileBrowserScreen` — sin cambios
- Lógica de pausa/play existente — sin cambios

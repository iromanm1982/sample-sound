# Barra de progreso de reproducción por sample

**Fecha:** 2026-04-22

## Resumen

Añadir una barra de progreso interactiva a cada `SoundButtonRow` en `GroupDetailScreen`. La barra muestra el avance de reproducción en tiempo real y permite al usuario tocar cualquier punto para saltar a esa posición (seek).

## Comportamiento

- **Posición:** barra fina (4 dp) debajo de la fila de controles (play/pause, restart, loop, duración).
- **Interactiva:** tap en cualquier punto de la barra hace seek a esa fracción del audio.
- **Cuando suena:** barra de color `primary`, con thumb circular visible en el extremo derecho del progreso.
- **Cuando está pausado o no ha empezado:** barra de color `onSurfaceVariant`, sin thumb. La barra se queda fija en la última posición alcanzada (no vuelve a cero).
- **Frecuencia de actualización:** 100 ms mientras el audio está en reproducción.
- **Valores:** fracción 0.0–1.0 calculada como `currentPositionMs / durationMs`. Si `durationMs == 0`, fracción es 0.

## Cambios por capa

### 1. `SoundPoolPlayer` (interfaz) — `core/audio`

Añadir dos métodos:

```kotlin
fun getCurrentPositionMs(filePath: String): Long
fun seekTo(filePath: String, positionMs: Long)
```

### 2. `SoundPoolManager` — `core/audio`

Implementar los dos métodos nuevos:

- `getCurrentPositionMs`: devuelve `activePlayers[filePath]?.currentPosition?.toLong() ?: 0L`. Envuelto en try/catch silencioso.
- `seekTo`: llama `activePlayers[filePath]?.seekTo(positionMs.toInt())`. Envuelto en try/catch silencioso. No-op si el path no está en `activePlayers`.

### 3. `GroupDetailViewModel` — `feature/soundboard/impl`

**Estado nuevo:**

```kotlin
private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

private val progressJobs = mutableMapOf<String, Job>()
```

**`playSound(filePath)`** — al llamarlo, lanzar (o re-lanzar) la corrutina de polling:

```kotlin
fun playSound(filePath: String) {
    soundPoolPlayer.play(filePath)
    _playingPaths.update { it + filePath }
    startProgressPolling(filePath)
}

private fun startProgressPolling(filePath: String) {
    progressJobs[filePath]?.cancel()
    progressJobs[filePath] = viewModelScope.launch {
        while (true) {
            delay(100)
            val durationMs = _durations.value[filePath] ?: 0L
            val posMs = soundPoolPlayer.getCurrentPositionMs(filePath)
            val fraction = if (durationMs > 0) (posMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            _progress.update { it + (filePath to fraction) }
        }
    }
}
```

**`pauseSound(filePath)`** — cancelar corrutina pero conservar el valor en `_progress`:

```kotlin
fun pauseSound(filePath: String) {
    soundPoolPlayer.pause(filePath)
    _playingPaths.update { it - filePath }
    progressJobs.remove(filePath)?.cancel()
}
```

**`pauseAll()`** — cancelar todas las corrutinas, conservar valores:

```kotlin
fun pauseAll() {
    soundPoolPlayer.pauseAll()
    _playingPaths.value = emptySet()
    progressJobs.values.forEach { it.cancel() }
    progressJobs.clear()
}
```

**`seekSound(filePath, fraction)`** — nuevo método público:

```kotlin
fun seekSound(filePath: String, fraction: Float) {
    val durationMs = _durations.value[filePath] ?: return
    val posMs = (fraction * durationMs).toLong()
    soundPoolPlayer.seekTo(filePath, posMs)
    _progress.update { it + (filePath to fraction.coerceIn(0f, 1f)) }
}
```

**`onCleared()`** — cancelar todas las corrutinas antes de liberar:

```kotlin
override fun onCleared() {
    progressJobs.values.forEach { it.cancel() }
    progressJobs.clear()
    soundPoolPlayer.release()
}
```

### 4. `GroupDetailScreen` — `feature/soundboard/impl`

**`ButtonList`:**
- Recibe `progress: Map<String, Float>` desde el ViewModel.
- Pasa `progress = progress[button.filePath] ?: 0f` y `onSeek = { fraction -> viewModel.seekSound(button.filePath, fraction) }` a cada `SoundButtonRow`.

**`SoundButtonRow`:**
- Añadir parámetros: `progress: Float`, `onSeek: (Float) -> Unit`.
- Debajo de la `Row` de controles, añadir:

```kotlin
Spacer(modifier = Modifier.height(4.dp))
ProgressBar(
    progress = progress,
    isPlaying = isPlaying,
    onSeek = onSeek
)
```

**`ProgressBar`** — composable privado nuevo:

```kotlin
@Composable
private fun ProgressBar(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)  // área táctil + espacio para el thumb
            .pointerInput(onSeek) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.Center),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
        if (isPlaying) {
            val thumbOffset = (maxWidth * progress - 6.dp).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .offset(x = thumbOffset)
                    .background(color, CircleShape)
                    .align(Alignment.CenterStart)
            )
        }
    }
}
```

## Limitaciones conocidas

- **Fin natural de reproducción:** cuando un audio termina sin loop, `_playingPaths` no se actualiza automáticamente (limitación preexistente). La barra quedará fija al final hasta que el usuario toque pause o play. Esto se resolverá en una tarea separada añadiendo `setOnCompletionListener` a `SoundPoolManager`.

## Tests

- `SoundPoolManager`: tests unitarios de `getCurrentPositionMs` y `seekTo` con un `MediaPlayer` mockeado o usando la lógica de `activePlayers`.
- `GroupDetailViewModel`: añadir `getCurrentPositionMs` y `seekTo` a `FakeSoundPoolPlayer`; tests para `seekSound` y para que `pauseSound` conserva el valor en `_progress`.

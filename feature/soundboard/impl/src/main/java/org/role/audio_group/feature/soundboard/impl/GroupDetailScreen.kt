package org.role.audio_group.feature.soundboard.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.role.audio_group.core.model.SoundButton
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    viewModel: GroupDetailViewModel,
    onNavigateToFileBrowser: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val playingPaths by viewModel.playingPaths.collectAsStateWithLifecycle()
    val loopingPaths by viewModel.loopingPaths.collectAsStateWithLifecycle()
    val durations by viewModel.durations.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (playingPaths.isNotEmpty()) {
                        IconButton(onClick = { viewModel.pauseAll() }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pausar todo")
                        }
                    }
                }
            )
        }
    ) { padding ->
        group?.let { g ->
            ButtonList(
                buttons = g.buttons,
                playingPaths = playingPaths,
                loopingPaths = loopingPaths,
                durations = durations,
                progress = progress,
                onAddSound = { onNavigateToFileBrowser(g.id) },
                onSoundButtonClick = { filePath ->
                    if (filePath in playingPaths) viewModel.pauseSound(filePath)
                    else viewModel.playSound(filePath)
                },
                onRestartButton = { viewModel.restartSound(it) },
                onToggleLoop = { viewModel.toggleLoop(it) },
                onSeek = { filePath, fraction -> viewModel.seekSound(filePath, fraction) },
                onDeleteButton = { viewModel.deleteButton(it) },
                onRenameButton = { id, newLabel -> viewModel.renameButton(id, newLabel) },
                onReorder = { from, to -> viewModel.reorderButtons(from, to) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ButtonList(
    buttons: List<SoundButton>,
    playingPaths: Set<String>,
    loopingPaths: Set<String>,
    durations: Map<String, Long>,
    progress: Map<String, Float>,
    onAddSound: () -> Unit,
    onSoundButtonClick: (String) -> Unit,
    onRestartButton: (String) -> Unit,
    onToggleLoop: (String) -> Unit,
    onSeek: (filePath: String, fraction: Float) -> Unit,
    onDeleteButton: (Long) -> Unit,
    onRenameButton: (Long, String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(buttons, key = { it.id }) { button ->
            ReorderableItem(reorderState, key = button.id) { isDragging ->
                SoundButtonRow(
                    button = button,
                    isPlaying = button.filePath in playingPaths,
                    isLooping = button.filePath in loopingPaths,
                    durationMs = durations[button.filePath] ?: 0L,
                    progress = progress[button.filePath] ?: 0f,
                    isDragging = isDragging,
                    dragModifier = Modifier.longPressDraggableHandle(),
                    onClick = { onSoundButtonClick(button.filePath) },
                    onRestart = { onRestartButton(button.filePath) },
                    onToggleLoop = { onToggleLoop(button.filePath) },
                    onSeek = { fraction -> onSeek(button.filePath, fraction) },
                    onDelete = { onDeleteButton(button.id) },
                    onRename = onRenameButton
                )
            }
        }
        item {
            AddSoundButton(onClick = onAddSound)
        }
    }
}

@Composable
private fun SoundButtonRow(
    button: SoundButton,
    isPlaying: Boolean,
    isLooping: Boolean,
    durationMs: Long,
    progress: Float,
    isDragging: Boolean,
    dragModifier: Modifier,
    onClick: () -> Unit,
    onRestart: () -> Unit,
    onToggleLoop: () -> Unit,
    onSeek: (Float) -> Unit,
    onDelete: () -> Unit,
    onRename: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val elevation = if (isDragging) 8.dp else 1.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(dragModifier),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Fila 1: label + badge "Sonando" + menú
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isPlaying) {
                    Text(
                        text = "● Sonando",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showMenu = false; showRenameDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { showMenu = false; showConfirmDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fila 2: play/pause + restart + loop + duración
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir"
                    )
                }
                IconButton(onClick = onRestart, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Volver al principio"
                    )
                }
                IconButton(
                    onClick = onToggleLoop,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isLooping) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = if (isLooping) "Desactivar bucle" else "Activar bucle",
                        tint = if (isLooping)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fila 3: barra de progreso interactiva
            PlaybackProgressBar(
                progress = progress,
                isPlaying = isPlaying,
                onSeek = onSeek
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Eliminar sample") },
            text = { Text("¿Eliminar \"${button.label}\"?") },
            confirmButton = {
                TextButton(onClick = { showConfirmDialog = false; onDelete() }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showRenameDialog) {
        RenameDialog(
            currentLabel = button.label,
            onConfirm = { newLabel -> showRenameDialog = false; onRename(button.id, newLabel) },
            onDismiss = { showRenameDialog = false }
        )
    }
}

@Composable
private fun PlaybackProgressBar(
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
            .height(16.dp)
            .pointerInput(onSeek) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
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

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
private fun RenameDialog(
    currentLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(currentLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar sample") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (label.isNotBlank()) onConfirm(label.trim()) },
                enabled = label.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AddSoundButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
                text = "Añadir sample",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

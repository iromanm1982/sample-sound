package org.role.samples_button.feature.soundboard.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.role.samples_button.core.model.Group
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundBoardScreen(
    viewModel: SoundBoardViewModel,
    onNavigateToGroup: (Long) -> Unit = {},
    onNavigateToFileBrowser: (Long) -> Unit = {},
    onNavigateToOnboarding: () -> Unit = {}
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    LaunchedEffect(hasSeenOnboarding) {
        if (hasSeenOnboarding == false) {
            onNavigateToOnboarding()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SoundBoard") },
                actions = {
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ver tutorial") },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                },
                                onClick = {
                                    showTopMenu = false
                                    onNavigateToOnboarding()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Crear grupo")
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            GroupList(
                groups = groups,
                onNavigateToGroup = onNavigateToGroup,
                onAddSound = onNavigateToFileBrowser,
                onRenameGroup = { id, newName -> viewModel.renameGroup(id, newName) },
                onDeleteGroup = { viewModel.deleteGroup(it) },
                onReorder = { from, to -> viewModel.reorderGroups(from, to) },
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onConfirm = { name ->
                viewModel.createGroup(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Sin grupos todavía")
            Text("Toca + para crear uno")
        }
    }
}

@Composable
private fun GroupList(
    groups: List<Group>,
    onNavigateToGroup: (Long) -> Unit,
    onAddSound: (Long) -> Unit,
    onRenameGroup: (Long, String) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onReorder: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(groups, key = { it.id }) { group ->
            ReorderableItem(reorderState, key = group.id) { isDragging ->
                GroupCard(
                    group = group,
                    isDragging = isDragging,
                    dragModifier = Modifier.longPressDraggableHandle(),
                    onNavigateToGroup = { onNavigateToGroup(group.id) },
                    onAddSound = { onAddSound(group.id) },
                    onRename = { newName -> onRenameGroup(group.id, newName) },
                    onDelete = { onDeleteGroup(group.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    isDragging: Boolean,
    dragModifier: Modifier,
    onNavigateToGroup: () -> Unit,
    onAddSound: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val elevation = if (isDragging) 8.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToGroup() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = group.buttons.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            IconButton(onClick = onAddSound) {
                Icon(Icons.Default.Add, contentDescription = "Añadir sample a ${group.name}")
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${group.name}")
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
                        onClick = { showMenu = false; showDeleteDialog = true }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameGroupDialog(
            currentName = group.name,
            onConfirm = { newName -> showRenameDialog = false; onRename(newName) },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar grupo") },
            text = { Text("¿Eliminar \"${group.name}\" y todos sus samples?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun RenameGroupDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar grupo") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun CreateGroupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo grupo") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del grupo") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

package org.role.samples_button.feature.browser.impl

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import org.role.samples_button.core.model.AudioFile

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FileBrowserScreen(
    groupId: Long,
    onNavigateBack: () -> Unit,
    viewModel: FileBrowserViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val audioFiles by viewModel.audioFiles.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var selectedFile by remember { mutableStateOf<AudioFile?>(null) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seleccionar audio") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when {
            permissionState.status.isGranted -> {
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        placeholder = { Text("Buscar por nombre…") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    if (audioFiles.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No se encontraron archivos de audio")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(audioFiles, key = { it.id }) { audioFile ->
                                AudioFileItem(
                                    audioFile = audioFile,
                                    onClick = { selectedFile = audioFile }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
            permissionState.status.shouldShowRationale -> {
                PermissionRequired(
                    message = "Se necesita acceso a tus archivos de audio para agregar sonidos.",
                    buttonText = "Conceder permiso",
                    onRequest = { permissionState.launchPermissionRequest() },
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                PermissionRequired(
                    message = "Se necesita acceso a tus archivos de audio para agregar sonidos.",
                    buttonText = "Conceder permiso",
                    onRequest = { permissionState.launchPermissionRequest() },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    selectedFile?.let { file ->
        ConfirmLabelDialog(
            defaultLabel = file.displayName,
            onConfirm = { label ->
                viewModel.addButtonToGroup(label, file.filePath, groupId)
                selectedFile = null
                onNavigateBack()
            },
            onDismiss = { selectedFile = null }
        )
    }
}

@Composable
private fun AudioFileItem(audioFile: AudioFile, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(audioFile.displayName) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun PermissionRequired(
    message: String,
    buttonText: String,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(message)
            TextButton(onClick = onRequest) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun ConfirmLabelDialog(
    defaultLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(defaultLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre del botón") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (label.isNotBlank()) onConfirm(label.trim()) },
                enabled = label.isNotBlank()
            ) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

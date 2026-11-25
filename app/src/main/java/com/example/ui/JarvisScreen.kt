package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.assistant.DefaultAssistantHelper
import com.example.model.JarvisCoreState
import com.example.model.PermissionActionType
import com.example.online.OllamaClient
import com.example.ui.components.ArcReactorCore
import com.example.ui.components.HudBracketPanel
import com.example.ui.components.JarvisHudBackdrop
import com.example.ui.components.PermissionsDialog
import com.example.ui.components.TelemetryHeader
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceBorder

@Composable
fun JarvisScreen(
    assistTriggerCount: Int = 0,
    viewModel: JarvisViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val window = (view.context as? Activity)?.window

    var showManualInputDialog by rememberSaveable { mutableStateOf(false) }
    var showOllamaDialog by rememberSaveable { mutableStateOf(false) }
    var manualText by rememberSaveable { mutableStateOf("") }
    var pendingHotwordMic by rememberSaveable { mutableStateOf(false) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            if (pendingHotwordMic) viewModel.toggleHotwordService() else viewModel.toggleVoiceListening(window)
            pendingHotwordMic = false
        } else {
            pendingHotwordMic = false
            viewModel.reportMicPermissionDenied()
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshTelemetry()
    }

    val defaultAssistantLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshTelemetry()
    }

    LaunchedEffect(assistTriggerCount) {
        if (assistTriggerCount > 0) {
            viewModel.handleAssistTrigger(window)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.refreshTelemetry()
    }

    val triggerVoiceCapture: () -> Unit = {
        if (hasMicPermission) {
            viewModel.toggleVoiceListening(window)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val requestDefaultAssistant: () -> Unit = {
        val intent = DefaultAssistantHelper.createDefaultAssistantIntent(context)
        runCatching { defaultAssistantLauncher.launch(intent) }
    }

    val latestEntry = uiState.logEntries.firstOrNull()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = JarvisObsidian,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            JarvisHudBackdrop()

            val isCompact = maxWidth < 600.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TelemetryHeader(
                    coreState = uiState.coreState,
                    isOnline = uiState.telemetry.isNetworkConnected,
                    ttsEnabled = uiState.ttsEnabled,
                    isHotwordActive = uiState.isHotwordActive,
                    onToggleTts = viewModel::toggleTts,
                    onToggleHotword = {
                        if (hasMicPermission || uiState.isHotwordActive) viewModel.toggleHotwordService()
                        else { pendingHotwordMic = true; micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    },
                    onOpenPermissions = { viewModel.setPermissionsDialogVisible(true) },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Pure Centered J.A.R.V.I.S. Hologram (no microphone icon) + Ephemeral HUD Readout
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 560.dp)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ArcReactorCore(
                        coreState = uiState.coreState,
                        rmsLevel = uiState.rmsLevel,
                        partialTranscript = uiState.partialTranscript,
                        onCoreClick = triggerVoiceCapture,
                        compact = isCompact
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!hasMicPermission) {
                        OutlinedButton(
                            onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            shape = CutCornerShape(8.dp),
                            border = BorderStroke(1.dp, JarvisCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyanGlow),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("grant_mic_button")
                        ) {
                            Text(
                                text = "ACTIVAR ACCESO DE VOZ",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    AnimatedVisibility(visible = !uiState.activeErrorMessage.isNullOrBlank()) {
                        HudBracketPanel(
                            accentColor = JarvisCrimson,
                            backgroundColor = JarvisCrimson.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = JarvisCrimson,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.activeErrorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = viewModel::dismissError,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("dismiss_error_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Cerrar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = latestEntry != null && uiState.coreState != JarvisCoreState.LISTENING
                    ) {
                        if (latestEntry != null) {
                            val badgeColor = when {
                                latestEntry.isError -> JarvisCrimson
                                latestEntry.actionSuccess -> JarvisEmerald
                                else -> JarvisAmber
                            }
                            HudBracketPanel(
                                accentColor = if (latestEntry.isError) JarvisCrimson else JarvisCyan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!latestEntry.actionBadge.isNullOrBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (latestEntry.actionSuccess && !latestEntry.isError) {
                                                    Icons.Rounded.CheckCircle
                                                } else {
                                                    Icons.Rounded.ErrorOutline
                                                },
                                                contentDescription = null,
                                                tint = badgeColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = latestEntry.actionBadge,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = badgeColor
                                            )
                                        }
                                    }

                                    Text(
                                        text = latestEntry.jarvisResponse,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        textAlign = TextAlign.Center,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (latestEntry.permissionActionType != null) {
                                        OutlinedButton(
                                            onClick = {
                                                if (latestEntry.permissionActionType == PermissionActionType.READ_CONTACTS) {
                                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                                } else {
                                                    viewModel.handleSpecialPermissionAction(latestEntry.permissionActionType)
                                                }
                                            },
                                            shape = CutCornerShape(6.dp),
                                            border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.7f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisAmber),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Security,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = when (latestEntry.permissionActionType) {
                                                    PermissionActionType.READ_CONTACTS -> "Permitir acceso a Contactos"
                                                    PermissionActionType.WRITE_SETTINGS -> "Permitir brillo del sistema"
                                                    PermissionActionType.DND_POLICY -> "Permitir modo silencio"
                                                    PermissionActionType.RECORD_AUDIO -> "Activar acceso de voz"
                                                },
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom bar: Default Assistant quick setup pill (until set) + subtle prompt + keyboard fallback
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!uiState.isDefaultAssistant) {
                        TextButton(
                            onClick = requestDefaultAssistant,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("set_default_assistant_button")
                        ) {
                            Text(
                                text = "FIJAR COMO ASISTENTE PREDETERMINADO",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisAmber
                            )
                        }
                    }

                    TextButton(onClick = { showOllamaDialog = true }, modifier = Modifier.testTag("ollama_settings_button")) {
                        Text(if (uiState.ollamaEndpoint.isBlank()) "CONECTAR OLLAMA" else "OLLAMA // CONFIGURAR", color = JarvisCyan, style = MaterialTheme.typography.labelSmall)
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = when (uiState.coreState) {
                                JarvisCoreState.LISTENING -> "DICTA TU INSTRUCCIÓN AHORA…"
                                JarvisCoreState.PROCESSING -> "EJECUTANDO DIRECTIVA…"
                                else -> "TOCA EL NÚCLEO PARA DICTAR CUALQUIER INSTRUCCIÓN"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )

                        IconButton(
                            onClick = { showManualInputDialog = true },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .minimumInteractiveComponentSize()
                                .testTag("manual_keyboard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Keyboard,
                                contentDescription = stringResource(R.string.cd_keyboard_input),
                                tint = JarvisCyan.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOllamaDialog) {
        OllamaSettingsDialog(
            endpoint = uiState.ollamaEndpoint,
            model = uiState.ollamaModel,
            onSave = { endpoint, model -> viewModel.saveOllamaSettings(endpoint, model); showOllamaDialog = false },
            onDismiss = { showOllamaDialog = false }
        )
    }

    if (showManualInputDialog) {
        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            containerColor = JarvisSurface,
            title = {
                Text(
                    text = "Instrucción a J.A.R.V.I.S.",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisCyanGlow
                )
            },
            text = {
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.command_input_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val txt = manualText.trim()
                            if (txt.isNotEmpty()) {
                                manualText = ""
                                showManualInputDialog = false
                                viewModel.submitCommand(txt, window)
                            }
                        }
                    ),
                    shape = CutCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisSurfaceBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("command_text_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val txt = manualText.trim()
                        if (txt.isNotEmpty()) {
                            manualText = ""
                            showManualInputDialog = false
                            viewModel.submitCommand(txt, window)
                        }
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("EJECUTAR", color = JarvisCyan)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualInputDialog = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (uiState.showPermissionsDialog) {
        PermissionsDialog(
            telemetry = uiState.telemetry,
            hasMicPermission = hasMicPermission,
            isDefaultAssistant = uiState.isDefaultAssistant,
            hapticsEnabled = uiState.hapticsEnabled,
            onToggleHaptics = viewModel::toggleHaptics,
            onRequestDefaultAssistant = requestDefaultAssistant,
            onRequestMicPermission = {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onRequestContactsPermission = {
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onOpenSpecialPermission = viewModel::handleSpecialPermissionAction,
            onDismiss = { viewModel.setPermissionsDialogVisible(false) }
        )
    }
}


@Composable
private fun OllamaSettingsDialog(endpoint: String, model: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var address by rememberSaveable { mutableStateOf(endpoint) }
    var modelName by rememberSaveable { mutableStateOf(model) }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    val disconnected = address.isBlank() && modelName.isBlank()
    val valid = disconnected || (OllamaClient.validEndpoint(address) && modelName.isNotBlank() && confirmed)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisSurface,
        title = { Text("Conectar Ollama", color = JarvisCyanGlow) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Configura un servidor Ollama accesible desde este teléfono. Las preguntas conversacionales se enviarán a ese servidor. Para consultar Wikipedia di «investiga en internet» seguido del tema; Ollama no navega por sí solo.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = address, onValueChange = { address = it; confirmed = false }, label = { Text("URL del servidor") }, placeholder = { Text("http://IP_DEL_SERVIDOR:11434") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = modelName, onValueChange = { modelName = it }, label = { Text("Modelo instalado") }, placeholder = { Text("llama3.2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("HTTP no cifra tus preguntas. Úsalo solo en una red de confianza; para acceso remoto usa HTTPS protegido. No incluyas claves en la URL. Deja ambos campos vacíos para desconectar.", style = MaterialTheme.typography.bodySmall, color = JarvisAmber)
                if (!disconnected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text("Acepto enviar mis preguntas a este servidor", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(address.trim(), modelName.trim()) }, enabled = valid) { Text("GUARDAR") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

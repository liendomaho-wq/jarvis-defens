package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.DeviceTelemetry
import com.example.model.PermissionActionType
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceBorder
import com.example.ui.theme.JarvisSurfaceElevated

@Composable
fun PermissionsDialog(
    telemetry: DeviceTelemetry,
    hasMicPermission: Boolean,
    isDefaultAssistant: Boolean,
    hapticsEnabled: Boolean,
    onToggleHaptics: () -> Unit,
    onRequestDefaultAssistant: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onOpenSpecialPermission: (PermissionActionType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisSurface,
        title = {
            Text(
                text = "Accesos y Asistente",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PermissionStatusRow(
                    icon = Icons.Rounded.Assistant,
                    title = "Asistente Predeterminado",
                    subtitle = "Fija a Jarvis como asistente digital del teléfono.",
                    granted = isDefaultAssistant,
                    actionLabel = "Configurar",
                    onGrantClick = onRequestDefaultAssistant
                )
                PermissionStatusRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Acceso de Voz",
                    subtitle = "Necesario para dictar instrucciones a Jarvis.",
                    granted = hasMicPermission,
                    actionLabel = "Activar",
                    onGrantClick = onRequestMicPermission
                )
                PermissionStatusRow(
                    icon = Icons.Rounded.Contacts,
                    title = "Contactos",
                    subtitle = "Busca destinatarios para WhatsApp, Telegram y llamadas.",
                    granted = telemetry.hasContactsPermission,
                    actionLabel = "Activar",
                    onGrantClick = onRequestContactsPermission
                )
                PermissionStatusRow(
                    icon = Icons.Rounded.Brightness6,
                    title = "Brillo del Sistema",
                    subtitle = "Permite cambiar el brillo global del dispositivo.",
                    granted = telemetry.hasWriteSettingsPermission,
                    actionLabel = "Activar",
                    onGrantClick = { onOpenSpecialPermission(PermissionActionType.WRITE_SETTINGS) }
                )
                PermissionStatusRow(
                    icon = Icons.Rounded.DoNotDisturbOn,
                    title = "Acceso No Molestar",
                    subtitle = "Permite activar modo silencio del teléfono.",
                    granted = telemetry.hasDndPolicyPermission,
                    actionLabel = "Activar",
                    onGrantClick = { onOpenSpecialPermission(PermissionActionType.DND_POLICY) }
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Vibration,
                            contentDescription = null,
                            tint = JarvisCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Respuesta háptica",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Vibración táctica al dictar comandos",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hapticsEnabled,
                            onCheckedChange = { onToggleHaptics() },
                            colors = SwitchDefaults.colors(checkedTrackColor = JarvisCyan),
                            modifier = Modifier.testTag("haptics_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("close_permissions_dialog")
            ) {
                Text("Cerrar", color = JarvisCyan)
            }
        }
    )
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onGrantClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = JarvisSurfaceElevated,
        border = BorderStroke(1.dp, JarvisSurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) JarvisEmerald else JarvisAmber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (granted) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Concedido",
                    tint = JarvisEmerald,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                OutlinedButton(
                    onClick = onGrantClick,
                    border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisAmber),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

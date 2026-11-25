package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.JarvisCoreState
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisPlasmaBlue

@Composable
fun TelemetryHeader(
    coreState: JarvisCoreState,
    isOnline: Boolean,
    ttsEnabled: Boolean,
    isHotwordActive: Boolean,
    onToggleTts: () -> Unit,
    onToggleHotword: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusLabel, statusColor) = when (coreState) {
        JarvisCoreState.IDLE -> if (isOnline) "EN LÍNEA" to JarvisCyan else "LOCAL" to JarvisAmber
        JarvisCoreState.LISTENING -> stringResource(R.string.status_listening) to JarvisEmerald
        JarvisCoreState.PROCESSING -> stringResource(R.string.status_processing) to JarvisAmber
        JarvisCoreState.SPEAKING -> stringResource(R.string.status_speaking) to JarvisPlasmaBlue
        JarvisCoreState.ERROR -> "ALERTA" to JarvisCrimson
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.jarvis_brand_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val badgeShape = CutCornerShape(4.dp)
                    Box(
                        modifier = Modifier
                            .clip(badgeShape)
                            .background(statusColor.copy(alpha = 0.14f))
                            .border(1.dp, statusColor.copy(alpha = 0.55f), badgeShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
                Text(
                    text = if (isHotwordActive) "HOTWORD ACTIVO // JARVIS ESTAS DESPIERTO?" else "IA NEURAL LOCAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleHotword,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_hotword_button")
            ) {
                Icon(
                    imageVector = if (isHotwordActive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                    contentDescription = "Hotword: JARVIS ESTAS DESPIERTO?",
                    tint = if (isHotwordActive) JarvisEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onToggleTts,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_tts_button")
            ) {
                Icon(
                    imageVector = if (ttsEnabled) {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    },
                    contentDescription = stringResource(R.string.cd_toggle_tts),
                    tint = if (ttsEnabled) JarvisCyan else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onOpenPermissions,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("permissions_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = stringResource(R.string.cd_system_permissions),
                    tint = JarvisAmber
                )
            }
        }
    }
}

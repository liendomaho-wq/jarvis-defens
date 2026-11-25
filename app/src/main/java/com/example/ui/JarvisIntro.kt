package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.JarvisCoreState
import com.example.ui.components.ArcReactorCore
import com.example.ui.components.JarvisHudBackdrop
import com.example.ui.theme.JarvisCyan

@Composable
fun JarvisIntro(onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        JarvisHudBackdrop()
        Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("J.A.R.V.I.S.", style = MaterialTheme.typography.headlineLarge, color = JarvisCyan)
            Text("JUST A RATHER VERY INTELLIGENT SYSTEM", style = MaterialTheme.typography.labelSmall, color = JarvisCyan, textAlign = TextAlign.Center)
            ArcReactorCore(JarvisCoreState.IDLE, 0f, "", onContinue, compact = true)
            Text("INICIALIZANDO SISTEMAS", style = MaterialTheme.typography.labelMedium, color = JarvisCyan)
            TextButton(onClick = onContinue) { Text("ENTRAR", color = JarvisCyan) }
        }
    }
}

package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.assistant.JarvisVoiceInteractionSession
import com.example.ui.JarvisScreen
import com.example.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {

    private var assistTriggerCounter by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isAssistIntent(intent)) {
            assistTriggerCounter++
        }

        // STEP 2: Enable edge-to-edge display and render Jarvis HUD
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                JarvisScreen(assistTriggerCount = assistTriggerCounter)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isAssistIntent(intent)) {
            assistTriggerCounter++
        }
    }

    private fun isAssistIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return intent.action == Intent.ACTION_ASSIST ||
            intent.action == Intent.ACTION_VOICE_COMMAND ||
            intent.getBooleanExtra(JarvisVoiceInteractionSession.EXTRA_ASSIST_AUTO_LISTEN, false)
    }
}

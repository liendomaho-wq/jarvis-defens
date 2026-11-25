package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jarvis_preferences"
)

data class JarvisSettings(
    val ttsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val ollamaEndpoint: String = "",
    val ollamaModel: String = ""
)

class JarvisPreferencesRepository(private val context: Context) {

    private object Keys {
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val OLLAMA_ENDPOINT = stringPreferencesKey("ollama_endpoint")
        val OLLAMA_MODEL = stringPreferencesKey("ollama_model")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        JarvisSettings(
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            ollamaEndpoint = prefs[Keys.OLLAMA_ENDPOINT].orEmpty(),
            ollamaModel = prefs[Keys.OLLAMA_MODEL].orEmpty()
        )
    }

    suspend fun setOllama(endpoint: String, model: String) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.OLLAMA_ENDPOINT] = endpoint.trim().trimEnd('/')
            prefs[Keys.OLLAMA_MODEL] = model.trim()
        }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.TTS_ENABLED] = enabled
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.HAPTICS_ENABLED] = enabled
        }
    }
}

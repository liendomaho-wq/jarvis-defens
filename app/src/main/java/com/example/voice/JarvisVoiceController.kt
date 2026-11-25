package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "JarvisVoiceController"

class JarvisVoiceController(
    private val context: Context,
    private val onFinalTranscript: (String) -> Unit,
    private val onSpeechError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var forceDefaultLocaleNextTry = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _isRecognitionAvailable = MutableStateFlow(checkSpeechAvailable())
    val isRecognitionAvailable: StateFlow<Boolean> = _isRecognitionAvailable.asStateFlow()

    init {
        try {
            textToSpeech = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.w(TAG, "TextToSpeech constructor unavailable", e)
        }
    }

    private fun checkSpeechAvailable(): Boolean {
        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        val serviceAvailable =
            runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        return onDevice || serviceAvailable
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val spanishLocale = Locale("es", "ES")
            val result = textToSpeech?.setLanguage(spanishLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech?.setLanguage(Locale.getDefault())
            }
            textToSpeech?.setSpeechRate(0.90f)
            textToSpeech?.setPitch(0.70f)
            try {
                val voices = textToSpeech?.voices
                val spanishVoice = voices?.firstOrNull { it.locale.language == "es" && (it.name.contains("male", true) || it.name.contains("premium", true) || it.name.contains("neural", true)) }
                    ?: voices?.firstOrNull { it.locale.language == "es" }
                if (spanishVoice != null) {
                    textToSpeech?.voice = spanishVoice
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not set butler voice", e)
            }
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                }
            })
            isTtsReady = true
        } else {
            Log.w(TAG, "TextToSpeech init failed with status $status")
            isTtsReady = false
        }
    }

    fun startListening() {
        val available = checkSpeechAvailable()
        _isRecognitionAvailable.value = available
        if (!available) {
            onSpeechError("El servicio de reconocimiento de voz no está instalado en este dispositivo. Usa el cuadro de texto o los botones rápidos.")
            return
        }

        stopSpeaking()
        if (_isListening.value) {
            stopListening()
            return
        }

        if (!ensureRecognizer()) {
            onSpeechError("No se pudo inicializar el servicio de voz del dispositivo. Puedes escribir tu comando abajo.")
            return
        }

        _partialTranscript.value = ""
        _rmsLevel.value = 0f

        val languageTag = if (forceDefaultLocaleNextTry) {
            Locale.getDefault().toLanguageTag()
        } else {
            "es-ES"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            _isListening.value = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer", e)
            _isListening.value = false
            onSpeechError("No se pudo iniciar el micrófono. Escribe tu comando abajo o revisa los servicios de voz del sistema.")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer", e)
        } finally {
            _isListening.value = false
            _rmsLevel.value = 0f
        }
    }

    fun speak(text: String): Boolean {
        if (!isTtsReady || text.isBlank()) return false
        return try {
            val utteranceId = UUID.randomUUID().toString()
            val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            result == TextToSpeech.SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "Error speaking TTS", e)
            false
        }
    }

    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
        _isSpeaking.value = false
    }

    private fun ensureRecognizer(): Boolean {
        if (speechRecognizer != null) return true
        return try {
            val recognizer = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    null
                }
            }.getOrNull() ?: SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _rmsLevel.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                    ) {
                        forceDefaultLocaleNextTry = true
                    }
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Error al capturar audio del micrófono."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono requerido."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Sin conexión para dictado en la nube. Escribe tu comando abajo o usa los controles rápidos."
                        SpeechRecognizer.ERROR_SERVER,
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                            "El servicio de voz del sistema no responde. Puedes escribir tu comando abajo."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "Idioma de voz no descargado; toca de nuevo para usar el idioma del sistema o escribe abajo."
                        SpeechRecognizer.ERROR_NO_MATCH -> "No logré entender el comando. Intenta hablar de nuevo."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz. Toca el núcleo para intentarlo de nuevo."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocimiento de voz está ocupado."
                        else -> "No se pudo iniciar el dictado por voz (código $error). Puedes escribir tu comando abajo."
                    }
                    if (error != SpeechRecognizer.ERROR_CLIENT) {
                        onSpeechError(message)
                    }
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull()?.trim().orEmpty()
                    _partialTranscript.value = ""
                    if (transcript.isNotEmpty()) {
                        onFinalTranscript(transcript)
                    } else {
                        onSpeechError("No se detectó texto en el comando de voz.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial =
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                    if (partial.isNotBlank()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer = recognizer
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to create SpeechRecognizer", e)
            speechRecognizer = null
            false
        }
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying SpeechRecognizer", e)
        }
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
    }
}

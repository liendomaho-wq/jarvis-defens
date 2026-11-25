package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.JarvisLocalBrain
import com.example.assistant.DefaultAssistantHelper
import com.example.data.JarvisPreferencesRepository
import com.example.model.DeviceTelemetry
import com.example.model.JarvisAiDecision
import com.example.model.JarvisCoreState
import com.example.model.JarvisIntentType
import com.example.model.JarvisLogEntry
import com.example.model.PermissionActionType
import com.example.system.SystemControlManager
import com.example.voice.JarvisHotwordService
import com.example.voice.JarvisVoiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class JarvisUiState(
    val telemetry: DeviceTelemetry = DeviceTelemetry(),
    val coreState: JarvisCoreState = JarvisCoreState.IDLE,
    val rmsLevel: Float = 0f,
    val partialTranscript: String = "",
    val logEntries: List<JarvisLogEntry> = emptyList(),
    val ttsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val isSpeechAvailable: Boolean = true,
    val isDefaultAssistant: Boolean = false,
    val isHotwordActive: Boolean = JarvisHotwordService.isRunning,
    val activeErrorMessage: String? = null,
    val showPermissionsDialog: Boolean = false
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val systemManager = SystemControlManager(application)
    private val localBrain = JarvisLocalBrain(application)
    private val prefsRepository = JarvisPreferencesRepository(application)

    private var currentWindowRef: Window? = null

    private val voiceController: JarvisVoiceController = JarvisVoiceController(
        context = application,
        onFinalTranscript = { transcript ->
            submitCommand(transcript, currentWindowRef)
        },
        onSpeechError = { errorMsg ->
            _uiState.update {
                it.copy(
                    coreState = JarvisCoreState.ERROR,
                    activeErrorMessage = errorMsg
                )
            }
        }
    )

    private val _uiState = MutableStateFlow(
        JarvisUiState(
            telemetry = systemManager.getTelemetry(),
            isSpeechAvailable = voiceController.isRecognitionAvailable.value,
            isDefaultAssistant = DefaultAssistantHelper.isJarvisDefaultAssistant(application)
        )
    )
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    private var isProcessingCommand = false

    init {
        viewModelScope.launch(Dispatchers.Default) {
            localBrain.ensureInitialized()
        }
        observeVoiceAndSettings()
        startTelemetryPolling()
    }

    private fun observeVoiceAndSettings() {
        viewModelScope.launch {
            combine(
                voiceController.isListening,
                voiceController.isSpeaking,
                voiceController.rmsLevel,
                voiceController.partialTranscript,
                prefsRepository.settingsFlow
            ) { listening, speaking, rms, partial, settings ->
                VoiceSnapshot(listening, speaking, rms, partial, settings.ttsEnabled, settings.hapticsEnabled)
            }.collect { snap ->
                _uiState.update { current ->
                    val nextCoreState = when {
                        isProcessingCommand -> JarvisCoreState.PROCESSING
                        snap.listening -> JarvisCoreState.LISTENING
                        snap.speaking -> JarvisCoreState.SPEAKING
                        current.coreState == JarvisCoreState.ERROR && current.activeErrorMessage != null ->
                            JarvisCoreState.ERROR
                        else -> JarvisCoreState.IDLE
                    }
                    current.copy(
                        coreState = nextCoreState,
                        rmsLevel = snap.rms,
                        partialTranscript = snap.partial,
                        ttsEnabled = snap.ttsEnabled,
                        hapticsEnabled = snap.hapticsEnabled,
                        isSpeechAvailable = voiceController.isRecognitionAvailable.value
                    )
                }
            }
        }

        viewModelScope.launch {
            systemManager.isTorchOn.collect { torchOn ->
                _uiState.update { state ->
                    state.copy(telemetry = state.telemetry.copy(isFlashlightOn = torchOn))
                }
            }
        }
    }

    private fun startTelemetryPolling() {
        viewModelScope.launch {
            while (isActive) {
                refreshTelemetryAndEvaluateAutomations()
                delay(3_500L)
            }
        }
    }

    fun refreshTelemetry() {
        val latest = systemManager.getTelemetry()
        val isDefault = DefaultAssistantHelper.isJarvisDefaultAssistant(getApplication())
        _uiState.update {
            it.copy(
                telemetry = latest,
                isDefaultAssistant = isDefault
            )
        }
    }

    private suspend fun refreshTelemetryAndEvaluateAutomations() {
        val latest = systemManager.getTelemetry()
        val isDefault = DefaultAssistantHelper.isJarvisDefaultAssistant(getApplication())
        _uiState.update {
            it.copy(
                telemetry = latest,
                isDefaultAssistant = isDefault
            )
        }

        val triggeredRules = withContext(Dispatchers.Default) {
            localBrain.automationEngine.evaluateTelemetryTriggers(latest)
        }
        for (rule in triggeredRules) {
            val steps = withContext(Dispatchers.Default) {
                localBrain.expandActionChain(rule.actions, latest)
            }
            val decision = JarvisAiDecision(
                intent = steps.firstOrNull()?.intent ?: JarvisIntentType.RUN_ROUTINE,
                spokenResponse = "Automatización activada: ${rule.name}.",
                actionSummary = "AUTO // ${rule.name.uppercase()}",
                steps = steps
            )
            applyDecisionResult(
                commandText = "[AUTO] ${rule.name}",
                decision = decision,
                window = currentWindowRef
            )
        }
    }

    fun handleAssistTrigger(window: Window?) {
        currentWindowRef = window
        val hasMic = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic && !voiceController.isListening.value && !isProcessingCommand) {
            voiceController.startListening()
        }
    }

    fun toggleVoiceListening(window: Window?) {
        currentWindowRef = window
        dismissError()
        if (_uiState.value.hapticsEnabled) {
            systemManager.triggerHapticPulse()
        }
        if (voiceController.isListening.value) {
            voiceController.stopListening()
        } else {
            voiceController.startListening()
        }
    }

    fun submitCommand(rawText: String, window: Window?) {
        val commandText = rawText.trim()
        if (commandText.isEmpty() || isProcessingCommand) return

        currentWindowRef = window
        dismissError()
        voiceController.stopListening()
        voiceController.stopSpeaking()

        if (_uiState.value.hapticsEnabled) {
            systemManager.triggerHapticPulse()
        }

        viewModelScope.launch {
            isProcessingCommand = true
            _uiState.update {
                it.copy(
                    coreState = JarvisCoreState.PROCESSING,
                    activeErrorMessage = null
                )
            }

            val telemetry = systemManager.getTelemetry()
            val decision = withContext(Dispatchers.Default) {
                localBrain.interpretCommand(commandText, telemetry)
            }

            isProcessingCommand = false

            if (decision.intent == JarvisIntentType.SCHEDULE_COMMAND &&
                decision.durationSeconds != null &&
                !decision.scheduledActionText.isNullOrBlank()
            ) {
                scheduleAutonomousTask(
                    delaySeconds = decision.durationSeconds,
                    actionText = decision.scheduledActionText
                )
            }

            applyDecisionResult(commandText, decision, window)
        }
    }

    private suspend fun applyDecisionResult(
        commandText: String,
        decision: JarvisAiDecision,
        window: Window?
    ) {
        val allSteps = if (decision.steps.isNotEmpty()) decision.steps else listOf(decision)
        allSteps.firstOrNull { it.intent == JarvisIntentType.SET_TTS_VOICE }?.let { ttsStep ->
            prefsRepository.setTtsEnabled(ttsStep.enabled ?: true)
        }

        val execResult = withContext(Dispatchers.IO) {
            systemManager.executeDecisionPlan(decision, window)
        }
        val updatedTelemetry = systemManager.getTelemetry()

        val combinedResponse = buildString {
            append(decision.spokenResponse)
            if (!execResult.detailText.isNullOrBlank() &&
                decision.intent != JarvisIntentType.QUERY_DEVICE_STATUS
            ) {
                append("\n")
                append(execResult.detailText)
            }
        }

        val entry = JarvisLogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = currentTimestamp(),
            userText = commandText,
            jarvisResponse = combinedResponse,
            actionBadge = execResult.actionBadge,
            actionSuccess = execResult.success,
            isError = false,
            permissionActionType = execResult.permissionActionType
        )

        val ttsAllowed = _uiState.value.ttsEnabled &&
            allSteps.none { it.intent == JarvisIntentType.SET_TTS_VOICE && it.enabled == false }

        val speechText = if (!execResult.detailText.isNullOrBlank() &&
            (decision.intent == JarvisIntentType.WEATHER_REPORT ||
             decision.intent == JarvisIntentType.QUERY_DEVICE_STATUS ||
             decision.intent == JarvisIntentType.READ_NOTES ||
             decision.intent == JarvisIntentType.LIST_AUTOMATIONS)
        ) {
            execResult.detailText
        } else {
            decision.spokenResponse
        }

        val startedSpeaking = if (ttsAllowed) {
            voiceController.speak(speechText)
        } else {
            false
        }

        _uiState.update { state ->
            state.copy(
                telemetry = updatedTelemetry,
                coreState = if (startedSpeaking) JarvisCoreState.SPEAKING else JarvisCoreState.IDLE,
                logEntries = listOf(entry) + state.logEntries
            )
        }
    }

    fun handleHotwordDetected() {
        voiceController.stopSpeaking()
        val greeting = "A sus órdenes, señor. Estoy despierto y escuchándole."
        _uiState.update {
            it.copy(
                coreState = JarvisCoreState.SPEAKING,
                activeErrorMessage = null
            )
        }
        if (_uiState.value.ttsEnabled) {
            voiceController.speak(greeting)
        }
        viewModelScope.launch {
            delay(2000L)
            voiceController.startListening()
        }
    }

    private fun scheduleAutonomousTask(delaySeconds: Int, actionText: String) {
        viewModelScope.launch {
            delay(delaySeconds.toLong() * 1_000L)
            val telemetry = systemManager.getTelemetry()
            val decision = withContext(Dispatchers.Default) {
                localBrain.interpretCommand(actionText, telemetry)
            }
            applyDecisionResult(
                commandText = "[PROGRAMADO] $actionText",
                decision = decision.copy(
                    actionSummary = "AUTO // ${decision.actionSummary}"
                ),
                window = currentWindowRef
            )
        }
    }

    fun toggleTts() {
        val next = !_uiState.value.ttsEnabled
        viewModelScope.launch {
            prefsRepository.setTtsEnabled(next)
            if (!next) {
                voiceController.stopSpeaking()
            }
        }
    }

    fun toggleHaptics() {
        val next = !_uiState.value.hapticsEnabled
        viewModelScope.launch {
            prefsRepository.setHapticsEnabled(next)
        }
    }

    fun dismissError() {
        _uiState.update { state ->
            state.copy(
                activeErrorMessage = null,
                coreState = if (state.coreState == JarvisCoreState.ERROR) JarvisCoreState.IDLE else state.coreState
            )
        }
    }

    fun setPermissionsDialogVisible(visible: Boolean) {
        if (visible) refreshTelemetry()
        _uiState.update { it.copy(showPermissionsDialog = visible) }
    }

    fun handleSpecialPermissionAction(type: PermissionActionType) {
        when (type) {
            PermissionActionType.WRITE_SETTINGS -> systemManager.openWriteSettingsPermission()
            PermissionActionType.DND_POLICY -> systemManager.openDndPolicyPermission()
            PermissionActionType.RECORD_AUDIO,
            PermissionActionType.READ_CONTACTS -> setPermissionsDialogVisible(true)
        }
    }

    fun reportMicPermissionDenied() {
        _uiState.update {
            it.copy(
                coreState = JarvisCoreState.ERROR,
                activeErrorMessage = "Permiso de micrófono denegado. Actívalo para dictar instrucciones por voz."
            )
        }
    }

    fun toggleHotwordService() {
        val app = getApplication<Application>()
        val intent = Intent(app, JarvisHotwordService::class.java)
        if (JarvisHotwordService.isRunning) {
            app.stopService(intent)
            _uiState.update { it.copy(isHotwordActive = false) }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
            _uiState.update { it.copy(isHotwordActive = true) }
        }
    }

    private fun currentTimestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
        systemManager.release()
    }

    private data class VoiceSnapshot(
        val listening: Boolean,
        val speaking: Boolean,
        val rms: Float,
        val partial: String,
        val ttsEnabled: Boolean,
        val hapticsEnabled: Boolean
    )
}

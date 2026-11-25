package com.example.model

enum class JarvisCoreState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

enum class JarvisIntentType {
    TOGGLE_FLASHLIGHT,
    SET_VOLUME,
    SET_RINGER_MODE,
    SET_BRIGHTNESS,
    SET_ALARM,
    SET_TIMER,
    OPEN_SYSTEM_PANEL,
    OPEN_APP_ACTION,
    QUERY_DEVICE_STATUS,
    SET_TTS_VOICE,
    SEND_MESSAGE,
    MAKE_CALL,
    WEB_BROWSE,
    NAVIGATE_MAPS,
    SEND_EMAIL,
    MEDIA_CONTROL,
    OPEN_APP_BY_NAME,
    RUN_ROUTINE,
    CREATE_AUTOMATION,
    LIST_AUTOMATIONS,
    DELETE_AUTOMATION,
    SCHEDULE_COMMAND,
    SAVE_NOTE,
    READ_NOTES,
    TEACH_NEURAL_NET,
    OPEN_ASSISTANT_SETTINGS,
    WEATHER_REPORT,
    CONVERSATION
}

enum class PermissionActionType {
    WRITE_SETTINGS,
    DND_POLICY,
    RECORD_AUDIO,
    READ_CONTACTS
}

data class DeviceTelemetry(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempC: Float = 0f,
    val mediaVolumePercent: Int = 50,
    val isMediaMuted: Boolean = false,
    val ringerModeLabel: String = "Normal",
    val brightnessPercent: Int = 50,
    val isAutoBrightness: Boolean = false,
    val hasWriteSettingsPermission: Boolean = false,
    val hasDndPolicyPermission: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val isFlashlightOn: Boolean = false,
    val hasFlashlight: Boolean = false,
    val isNetworkConnected: Boolean = false,
    val networkType: String = "Sin red",
    val availableStorageGb: Float = 0f,
    val totalStorageGb: Float = 0f,
    val availableRamMb: Long = 0L,
    val totalRamMb: Long = 0L
)

data class JarvisAiDecision(
    val intent: JarvisIntentType,
    val spokenResponse: String,
    val actionSummary: String,
    val enabled: Boolean? = null,
    val levelPercent: Int? = null,
    val deltaPercent: Int? = null,
    val stream: String? = null,
    val ringerMode: String? = null,
    val autoMode: Boolean? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val durationSeconds: Int? = null,
    val label: String? = null,
    val panel: String? = null,
    val appAction: String? = null,
    val query: String? = null,
    val metric: String? = null,
    val platform: String? = null,
    val recipient: String? = null,
    val messageBody: String? = null,
    val url: String? = null,
    val subject: String? = null,
    val mediaCommand: String? = null,
    val appName: String? = null,
    val routineName: String? = null,
    val conditionSpec: String? = null,
    val scheduledActionText: String? = null,
    val neuralConfidence: Float = 1f,
    val steps: List<JarvisAiDecision> = emptyList()
)

data class CommandExecutionResult(
    val success: Boolean,
    val actionBadge: String,
    val detailText: String? = null,
    val permissionActionType: PermissionActionType? = null
)

data class JarvisLogEntry(
    val id: String,
    val timestamp: String,
    val userText: String,
    val jarvisResponse: String,
    val actionBadge: String? = null,
    val actionSuccess: Boolean = true,
    val isError: Boolean = false,
    val permissionActionType: PermissionActionType? = null
)

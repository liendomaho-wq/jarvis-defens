package com.example.system

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import com.example.model.CommandExecutionResult
import com.example.model.DeviceTelemetry
import com.example.model.JarvisAiDecision
import com.example.model.JarvisIntentType
import com.example.model.PermissionActionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import kotlin.math.roundToInt

private const val TAG = "SystemControlManager"

class SystemControlManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val contactsHelper = ContactsHelper(context)

    private var torchCameraId: String? = null
    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                _isTorchOn.value = enabled
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) {
                _isTorchOn.value = false
            }
        }
    }

    init {
        torchCameraId = findFlashCameraId()
        try {
            cameraManager.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register TorchCallback", e)
        }
    }

    fun release() {
        try {
            cameraManager.unregisterTorchCallback(torchCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to unregister TorchCallback", e)
        }
    }

    private fun findFlashCameraId(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val isBack =
                    chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                hasFlash && isBack
            } ?: cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying camera flash IDs", e)
            null
        }
    }

    fun getTelemetry(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt() else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTempC = tempTenths / 10f

        val maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curMedia = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mediaPercent = ((curMedia.toFloat() / maxMedia) * 100f).roundToInt()
        val isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC) || curMedia == 0

        val ringerLabel = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silencio"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibración"
            else -> "Normal"
        }

        val rawBrightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) {
            128
        }
        val brightnessPercent = ((rawBrightness / 255f) * 100f).roundToInt().coerceIn(0, 100)
        val brightnessMode = try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE
            )
        } catch (_: Exception) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }

        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val isConnected = caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val networkType = when {
            caps == null -> "Sin red"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos Móviles"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Conectado"
        }

        val statFs = StatFs(Environment.getDataDirectory().path)
        val availableGb = statFs.availableBytes / (1024f * 1024f * 1024f)
        val totalGb = statFs.totalBytes / (1024f * 1024f * 1024f)

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        return DeviceTelemetry(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            batteryTempC = batteryTempC,
            mediaVolumePercent = mediaPercent,
            isMediaMuted = isMuted,
            ringerModeLabel = ringerLabel,
            brightnessPercent = brightnessPercent,
            isAutoBrightness = brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            hasWriteSettingsPermission = Settings.System.canWrite(context),
            hasDndPolicyPermission = notificationManager.isNotificationPolicyAccessGranted,
            hasContactsPermission = contactsHelper.hasContactsPermission(),
            isFlashlightOn = _isTorchOn.value,
            hasFlashlight = torchCameraId != null,
            isNetworkConnected = isConnected,
            networkType = networkType,
            availableStorageGb = availableGb,
            totalStorageGb = totalGb,
            availableRamMb = memInfo.availMem / (1024L * 1024L),
            totalRamMb = memInfo.totalMem / (1024L * 1024L)
        )
    }

    fun executeDecisionPlan(
        decision: JarvisAiDecision,
        window: Window?
    ): CommandExecutionResult {
        if (decision.steps.isNotEmpty()) {
            val badges = mutableListOf<String>()
            val details = mutableListOf<String>()
            var anySuccess = false
            var firstPermissionAction: PermissionActionType? = null

            for (step in decision.steps) {
                val res = executeSingleDecision(step, window)
                if (res.success) anySuccess = true
                badges += res.actionBadge
                if (!res.detailText.isNullOrBlank()) {
                    details += res.detailText
                }
                if (firstPermissionAction == null && res.permissionActionType != null) {
                    firstPermissionAction = res.permissionActionType
                }
            }

            return CommandExecutionResult(
                success = anySuccess,
                actionBadge = decision.actionSummary.ifBlank { badges.joinToString(" • ").take(32) },
                detailText = details.distinct().joinToString(" | ").ifBlank {
                    badges.joinToString(" → ")
                },
                permissionActionType = firstPermissionAction
            )
        }
        return executeSingleDecision(decision, window)
    }

    fun executeAiDecision(
        decision: JarvisAiDecision,
        window: Window?
    ): CommandExecutionResult = executeDecisionPlan(decision, window)

    private fun executeSingleDecision(
        decision: JarvisAiDecision,
        window: Window?
    ): CommandExecutionResult {
        return when (decision.intent) {
            JarvisIntentType.TOGGLE_FLASHLIGHT -> {
                val target = decision.enabled ?: !_isTorchOn.value
                setFlashlight(target)
            }
            JarvisIntentType.SET_VOLUME -> {
                setVolume(
                    levelPercent = decision.levelPercent,
                    deltaPercent = decision.deltaPercent,
                    stream = decision.stream ?: "media",
                    mute = decision.enabled
                )
            }
            JarvisIntentType.SET_RINGER_MODE -> {
                setRingerMode(decision.ringerMode ?: "normal")
            }
            JarvisIntentType.SET_BRIGHTNESS -> {
                setBrightness(
                    levelPercent = decision.levelPercent,
                    autoMode = decision.autoMode,
                    window = window
                )
            }
            JarvisIntentType.SET_ALARM -> {
                setAlarm(
                    hour = decision.hour ?: 7,
                    minute = decision.minute ?: 0,
                    label = decision.label ?: "Alarma Jarvis"
                )
            }
            JarvisIntentType.SET_TIMER -> {
                setTimer(
                    durationSeconds = (decision.durationSeconds ?: 60).coerceAtLeast(1),
                    label = decision.label ?: "Temporizador Jarvis"
                )
            }
            JarvisIntentType.OPEN_SYSTEM_PANEL -> {
                openSystemPanel(decision.panel ?: "settings")
            }
            JarvisIntentType.OPEN_APP_ACTION -> {
                openAppAction(decision.appAction ?: "settings", decision.query.orEmpty())
            }
            JarvisIntentType.QUERY_DEVICE_STATUS -> {
                val t = getTelemetry()
                val summary = buildTelemetryReport(t, decision.metric ?: "all")
                CommandExecutionResult(
                    success = true,
                    actionBadge = decision.actionSummary.ifBlank { "TELEMETRÍA OK" },
                    detailText = summary
                )
            }
            JarvisIntentType.SET_TTS_VOICE -> {
                val enabled = decision.enabled ?: true
                CommandExecutionResult(
                    success = true,
                    actionBadge = if (enabled) "VOZ ACTIVADA" else "VOZ SILENCIADA"
                )
            }
            JarvisIntentType.SEND_MESSAGE -> {
                sendMessage(
                    platform = decision.platform ?: "whatsapp",
                    recipient = decision.recipient.orEmpty(),
                    messageBody = decision.messageBody.orEmpty()
                )
            }
            JarvisIntentType.MAKE_CALL -> {
                makeCall(decision.recipient ?: decision.query.orEmpty())
            }
            JarvisIntentType.WEB_BROWSE -> {
                webBrowse(
                    url = decision.url,
                    query = decision.query
                )
            }
            JarvisIntentType.NAVIGATE_MAPS -> {
                navigateMaps(decision.query ?: decision.label.orEmpty())
            }
            JarvisIntentType.SEND_EMAIL -> {
                sendEmail(
                    recipient = decision.recipient.orEmpty(),
                    subject = decision.subject.orEmpty(),
                    body = decision.messageBody.orEmpty()
                )
            }
            JarvisIntentType.MEDIA_CONTROL -> {
                mediaControl(decision.mediaCommand ?: "play_pause")
            }
            JarvisIntentType.OPEN_APP_BY_NAME -> {
                openAppByName(decision.appName ?: decision.appAction.orEmpty())
            }
            JarvisIntentType.OPEN_ASSISTANT_SETTINGS -> {
                val intent = com.example.assistant.DefaultAssistantHelper.createDefaultAssistantIntent(context)
                launchIntent(intent, "ASISTENTE OS", "AJUSTES ASISTENTE")
            }
            JarvisIntentType.RUN_ROUTINE,
            JarvisIntentType.CREATE_AUTOMATION,
            JarvisIntentType.LIST_AUTOMATIONS,
            JarvisIntentType.DELETE_AUTOMATION,
            JarvisIntentType.SCHEDULE_COMMAND,
            JarvisIntentType.SAVE_NOTE,
            JarvisIntentType.READ_NOTES,
            JarvisIntentType.TEACH_NEURAL_NET,
            JarvisIntentType.CONVERSATION -> {
                CommandExecutionResult(
                    success = true,
                    actionBadge = decision.actionSummary.ifBlank { "IA NEURAL" }
                )
            }
        }
    }

    fun sendMessage(
        platform: String,
        recipient: String,
        messageBody: String
    ): CommandExecutionResult {
        val cleanPlatform = platform.lowercase().trim()
        val trimmedRecipient = recipient.trim()
        val encodedText = URLEncoder.encode(messageBody, "UTF-8")

        // Resolve contact phone if recipient name is provided
        val directDigits = contactsHelper.extractDirectPhoneDigits(trimmedRecipient)
        val contactMatch = if (directDigits == null && trimmedRecipient.isNotBlank()) {
            contactsHelper.findPhoneByName(trimmedRecipient)
        } else {
            null
        }
        val resolvedPhone = directDigits ?: contactMatch?.phoneNumber

        return when {
            cleanPlatform.contains("telegram") -> {
                val tgPackages = listOf(
                    "org.telegram.messenger",
                    "org.telegram.messenger.web",
                    "org.thunderdog.challegram"
                )
                val installedTg = tgPackages.firstOrNull { isPackageInstalled(it) }
                if (trimmedRecipient.startsWith("@")) {
                    val username = trimmedRecipient.removePrefix("@").trim()
                    val uri = Uri.parse("https://t.me/$username")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        if (installedTg != null) setPackage(installedTg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    return launchIntent(intent, "TELEGRAM → @$username", "TELEGRAM NO INSTALADO")
                }

                if (installedTg == null) {
                    return CommandExecutionResult(
                        success = false,
                        actionBadge = "TELEGRAM NO INSTALADO",
                        detailText = "No se encontró Telegram instalado en este dispositivo."
                    )
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageBody)
                    setPackage(installedTg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val badge = if (trimmedRecipient.isNotBlank()) {
                    "TELEGRAM → ${trimmedRecipient.uppercase()}"
                } else {
                    "TELEGRAM LISTO"
                }
                val permPrompt = if (trimmedRecipient.isNotBlank() &&
                    resolvedPhone == null &&
                    !contactsHelper.hasContactsPermission()
                ) {
                    PermissionActionType.READ_CONTACTS
                } else null

                val res = launchIntent(shareIntent, badge, "ERROR TELEGRAM")
                if (res.success && permPrompt != null) {
                    res.copy(
                        detailText = "Telegram abierto con tu mensaje. Activa permiso de Contactos para buscar destinatarios automáticamente.",
                        permissionActionType = permPrompt
                    )
                } else res
            }

            cleanPlatform.contains("sms") -> {
                val smsUri = if (!resolvedPhone.isNullOrBlank()) {
                    Uri.parse("smsto:$resolvedPhone")
                } else {
                    Uri.parse("smsto:")
                }
                val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                    putExtra("sms_body", messageBody)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val targetName = contactMatch?.displayName ?: trimmedRecipient.ifBlank { "SMS" }
                launchIntent(smsIntent, "SMS → ${targetName.uppercase()}", "SIN APP SMS")
            }

            else -> {
                // WhatsApp (default)
                val waPackage = listOf("com.whatsapp", "com.whatsapp.w4b")
                    .firstOrNull { isPackageInstalled(it) }

                if (!resolvedPhone.isNullOrBlank()) {
                    val digitsOnly = resolvedPhone.replace("+", "")
                    val uri = Uri.parse("https://wa.me/$digitsOnly?text=$encodedText")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        if (waPackage != null) setPackage(waPackage)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val targetLabel = (contactMatch?.displayName ?: trimmedRecipient).uppercase()
                    return launchIntent(intent, "WHATSAPP → $targetLabel", "WHATSAPP NO INSTALADO")
                }

                if (waPackage != null) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, messageBody)
                        setPackage(waPackage)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val permHint = if (trimmedRecipient.isNotBlank() && !contactsHelper.hasContactsPermission()) {
                        PermissionActionType.READ_CONTACTS
                    } else null
                    val res = launchIntent(
                        shareIntent,
                        "WHATSAPP LISTO",
                        "ERROR WHATSAPP"
                    )
                    if (res.success && permHint != null) {
                        res.copy(
                            detailText = "WhatsApp abierto con el mensaje redactado. Concede Contactos para enviar directo al destinatario.",
                            permissionActionType = permHint
                        )
                    } else res
                } else {
                    // Fallback to generic messaging share sheet if WhatsApp isn't installed
                    val genericShare = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, messageBody)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    launchIntent(
                        Intent.createChooser(genericShare, "Enviar mensaje con…").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        "MENSAJE LISTO",
                        "SIN APP DE MENSAJES"
                    )
                }
            }
        }
    }

    fun makeCall(target: String): CommandExecutionResult {
        val trimmed = target.trim()
        val directDigits = contactsHelper.extractDirectPhoneDigits(trimmed)
        val match = if (directDigits == null && trimmed.isNotBlank()) {
            contactsHelper.findPhoneByName(trimmed)
        } else {
            null
        }
        val phone = directDigits ?: match?.phoneNumber
        val dialUri = if (!phone.isNullOrBlank()) Uri.parse("tel:$phone") else Uri.parse("tel:")
        val intent = Intent(Intent.ACTION_DIAL, dialUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val res = launchIntent(
            intent,
            if (!phone.isNullOrBlank()) "LLAMADA → ${(match?.displayName ?: phone).uppercase()}" else "MARCADOR",
            "SIN TELÉFONO"
        )
        return if (res.success && phone == null && trimmed.isNotBlank() && !contactsHelper.hasContactsPermission()) {
            res.copy(
                detailText = "Concede permiso de Contactos para buscar el número de $trimmed automáticamente.",
                permissionActionType = PermissionActionType.READ_CONTACTS
            )
        } else res
    }

    fun webBrowse(url: String?, query: String?): CommandExecutionResult {
        val rawUrl = url?.trim().orEmpty()
        val rawQuery = query?.trim().orEmpty()

        val targetUri = when {
            rawUrl.isNotBlank() -> {
                val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                    rawUrl
                } else {
                    "https://$rawUrl"
                }
                Uri.parse(normalized)
            }
            rawQuery.isNotBlank() -> {
                val encoded = URLEncoder.encode(rawQuery, "UTF-8")
                Uri.parse("https://www.google.com/search?q=$encoded")
            }
            else -> Uri.parse("https://www.google.com")
        }

        val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val badge = if (rawUrl.isNotBlank()) {
            "WEB // ${rawUrl.take(18).uppercase()}"
        } else if (rawQuery.isNotBlank()) {
            "BUSCAR // ${rawQuery.take(16).uppercase()}"
        } else {
            "NAVEGADOR"
        }
        return launchIntent(intent, badge, "SIN NAVEGADOR")
    }

    fun navigateMaps(destination: String): CommandExecutionResult {
        val encoded = URLEncoder.encode(destination.trim(), "UTF-8")
        val uri = Uri.parse("geo:0,0?q=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchIntent(
            intent,
            "MAPAS // ${destination.take(16).uppercase()}",
            "SIN APP DE MAPAS"
        )
    }

    fun sendEmail(recipient: String, subject: String, body: String): CommandExecutionResult {
        val emailMatch = if (!recipient.contains("@") && recipient.isNotBlank()) {
            contactsHelper.findEmailByName(recipient)
        } else {
            null
        }
        val resolvedEmail = when {
            recipient.contains("@") -> recipient.trim()
            emailMatch?.email != null -> emailMatch.email
            else -> ""
        }

        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$resolvedEmail")).apply {
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchIntent(intent, "CORREO LISTO", "SIN APP DE CORREO")
    }

    fun mediaControl(command: String): CommandExecutionResult {
        val keyCode = when (command.lowercase()) {
            "play", "reproducir" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause", "pausa", "pausar", "detener" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next", "siguiente", "adelantar" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev", "anterior", "atras" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        return try {
            val eventTime = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
            CommandExecutionResult(
                success = true,
                actionBadge = "MEDIA // ${command.uppercase()}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching media key", e)
            CommandExecutionResult(
                success = false,
                actionBadge = "ERROR MEDIA",
                detailText = e.localizedMessage
            )
        }
    }

    fun openAppByName(rawName: String): CommandExecutionResult {
        val key = rawName.lowercase().trim()
        val knownPackages = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"),
            "youtube" to listOf("com.google.android.youtube"),
            "spotify" to listOf("com.spotify.music"),
            "maps" to listOf("com.google.android.apps.maps"),
            "mapas" to listOf("com.google.android.apps.maps"),
            "chrome" to listOf("com.android.chrome"),
            "navegador" to listOf("com.android.chrome"),
            "gmail" to listOf("com.google.android.gm"),
            "correo" to listOf("com.google.android.gm"),
            "instagram" to listOf("com.instagram.android"),
            "facebook" to listOf("com.facebook.katana"),
            "tiktok" to listOf("com.zhiliaoapp.musically")
        )

        val matchedPackages = knownPackages.entries.firstOrNull { key.contains(it.key) }?.value
        if (matchedPackages != null) {
            for (pkg in matchedPackages) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return launchIntent(launchIntent, "APP // ${key.uppercase()}", "ERROR AL ABRIR")
                }
            }
            return CommandExecutionResult(
                success = false,
                actionBadge = "${key.uppercase()} NO INSTALADA",
                detailText = "No encontré $rawName instalada en este dispositivo."
            )
        }

        // Standard Android category intents
        return when {
            key.contains("camara") || key.contains("cámara") -> openAppAction("camera", "")
            key.contains("alarma") || key.contains("reloj") -> openAppAction("alarms", "")
            key.contains("calendario") -> openAppAction("calendar", "")
            key.contains("calculadora") -> openAppAction("calculator", "")
            key.contains("telefono") || key.contains("teléfono") -> openAppAction("phone", "")
            key.contains("musica") || key.contains("música") -> {
                val intent = Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_MUSIC
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                launchIntent(intent, "APP MÚSICA", "SIN APP DE MÚSICA")
            }
            key.contains("contacto") -> {
                val intent = Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_CONTACTS
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                launchIntent(intent, "CONTACTOS", "SIN APP CONTACTOS")
            }
            key.contains("galeria") || key.contains("galería") || key.contains("fotos") -> {
                val intent = Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_GALLERY
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                launchIntent(intent, "GALERÍA", "SIN GALERÍA")
            }
            else -> webBrowse(url = null, query = rawName)
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun setFlashlight(enabled: Boolean): CommandExecutionResult {
        val camId = torchCameraId ?: findFlashCameraId().also { torchCameraId = it }
        if (camId == null) {
            return CommandExecutionResult(
                success = false,
                actionBadge = "SIN FLASH",
                detailText = "Este dispositivo no reporta un flash de cámara disponible."
            )
        }
        return try {
            cameraManager.setTorchMode(camId, enabled)
            _isTorchOn.value = enabled
            CommandExecutionResult(
                success = true,
                actionBadge = if (enabled) "LINTERNA ON" else "LINTERNA OFF"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set torch mode", e)
            CommandExecutionResult(
                success = false,
                actionBadge = "ERROR LINTERNA",
                detailText = e.localizedMessage ?: "No se pudo controlar la linterna."
            )
        }
    }

    fun setVolume(
        levelPercent: Int?,
        deltaPercent: Int?,
        stream: String = "media",
        mute: Boolean? = null
    ): CommandExecutionResult {
        val streamType = when (stream.lowercase()) {
            "ring", "timbre", "llamada" -> AudioManager.STREAM_RING
            "alarm", "alarma" -> AudioManager.STREAM_ALARM
            "notification", "notificaciones" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        val streamLabel = when (streamType) {
            AudioManager.STREAM_RING -> "TIMBRE"
            AudioManager.STREAM_ALARM -> "ALARMA"
            AudioManager.STREAM_NOTIFICATION -> "NOTIF"
            else -> "MEDIA"
        }

        return try {
            val max = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
            val current = audioManager.getStreamVolume(streamType)
            val currentPct = ((current.toFloat() / max) * 100f).roundToInt()

            if (mute == true || levelPercent == 0) {
                audioManager.setStreamVolume(streamType, 0, AudioManager.FLAG_SHOW_UI)
                return CommandExecutionResult(
                    success = true,
                    actionBadge = "VOL $streamLabel 0%"
                )
            }

            val targetPct = when {
                levelPercent != null -> levelPercent.coerceIn(0, 100)
                deltaPercent != null -> (currentPct + deltaPercent).coerceIn(0, 100)
                mute == false && current == 0 -> 50
                else -> currentPct
            }

            val targetIndex = ((targetPct / 100f) * max).roundToInt().coerceIn(0, max)
            audioManager.setStreamVolume(streamType, targetIndex, AudioManager.FLAG_SHOW_UI)
            val actualPct = ((targetIndex.toFloat() / max) * 100f).roundToInt()

            CommandExecutionResult(
                success = true,
                actionBadge = "VOL $streamLabel $actualPct%"
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException setting volume (DND policy)", e)
            CommandExecutionResult(
                success = false,
                actionBadge = "PERMISO NO MOLESTAR",
                detailText = "Se requiere acceso de No Molestar para cambiar el volumen del timbre.",
                permissionActionType = PermissionActionType.DND_POLICY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to adjust volume", e)
            CommandExecutionResult(
                success = false,
                actionBadge = "ERROR VOLUMEN",
                detailText = e.localizedMessage
            )
        }
    }

    fun setRingerMode(mode: String): CommandExecutionResult {
        val targetMode = when (mode.lowercase()) {
            "silent", "silencio", "mute" -> AudioManager.RINGER_MODE_SILENT
            "vibrate", "vibrar", "vibración", "vibracion" -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        if (targetMode == AudioManager.RINGER_MODE_SILENT &&
            !notificationManager.isNotificationPolicyAccessGranted
        ) {
            return CommandExecutionResult(
                success = false,
                actionBadge = "REQUIERE PERMISO DND",
                detailText = "Concede acceso a No Molestar para activar el modo silencio del sistema.",
                permissionActionType = PermissionActionType.DND_POLICY
            )
        }
        return try {
            audioManager.ringerMode = targetMode
            val badge = when (targetMode) {
                AudioManager.RINGER_MODE_SILENT -> "MODO SILENCIO"
                AudioManager.RINGER_MODE_VIBRATE -> "MODO VIBRACIÓN"
                else -> "MODO NORMAL"
            }
            CommandExecutionResult(success = true, actionBadge = badge)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting ringer mode", e)
            CommandExecutionResult(
                success = false,
                actionBadge = "ERROR PERFIL AUDIO",
                detailText = e.localizedMessage
            )
        }
    }

    fun setBrightness(
        levelPercent: Int?,
        autoMode: Boolean?,
        window: Window?
    ): CommandExecutionResult {
        val pct = (levelPercent ?: 60).coerceIn(5, 100)
        window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = if (autoMode == true) -1f else (pct / 100f).coerceIn(0.05f, 1f)
            w.attributes = lp
        }

        if (!Settings.System.canWrite(context)) {
            return CommandExecutionResult(
                success = true,
                actionBadge = "BRILLO APP $pct%",
                detailText = "Brillo de ventana ajustado. Activa 'Modificar ajustes del sistema' para cambiar el brillo global.",
                permissionActionType = PermissionActionType.WRITE_SETTINGS
            )
        }

        return try {
            if (autoMode == true) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                )
                CommandExecutionResult(success = true, actionBadge = "BRILLO AUTO")
            } else {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                val value255 = ((pct / 100f) * 255f).roundToInt().coerceIn(10, 255)
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    value255
                )
                CommandExecutionResult(success = true, actionBadge = "BRILLO $pct%")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing system brightness", e)
            CommandExecutionResult(
                success = false,
                actionBadge = "ERROR BRILLO",
                detailText = e.localizedMessage
            )
        }
    }

    fun setAlarm(hour: Int, minute: Int, label: String): CommandExecutionResult {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, safeHour)
            putExtra(AlarmClock.EXTRA_MINUTES, safeMinute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchIntent(
            intent = intent,
            successBadge = String.format("ALARMA %02d:%02d", safeHour, safeMinute),
            errorBadge = "SIN APP RELOJ"
        )
    }

    fun setTimer(durationSeconds: Int, label: String): CommandExecutionResult {
        val safeSec = durationSeconds.coerceAtLeast(1)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, safeSec)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val formatted = if (safeSec >= 60) "${safeSec / 60}m ${safeSec % 60}s" else "${safeSec}s"
        return launchIntent(
            intent = intent,
            successBadge = "TIMER $formatted",
            errorBadge = "SIN TEMPORIZADOR"
        )
    }

    fun openSystemPanel(panel: String): CommandExecutionResult {
        val (intent, badge) = when (panel.lowercase()) {
            "wifi", "wi-fi", "internet" -> {
                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_WIFI
                } else {
                    Settings.ACTION_WIFI_SETTINGS
                }
                Intent(action) to "PANEL WI-FI"
            }
            "bluetooth", "bt" -> {
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS) to "AJUSTES BLUETOOTH"
            }
            "volume", "sound", "sonido", "audio" -> {
                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_VOLUME
                } else {
                    Settings.ACTION_SOUND_SETTINGS
                }
                Intent(action) to "PANEL AUDIO"
            }
            "display", "brightness", "pantalla", "brillo" -> {
                Intent(Settings.ACTION_DISPLAY_SETTINGS) to "AJUSTES PANTALLA"
            }
            "battery", "bateria", "batería", "energia" -> {
                Intent(Intent.ACTION_POWER_USAGE_SUMMARY) to "USO DE BATERÍA"
            }
            "location", "ubicacion", "ubicación", "gps" -> {
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) to "UBICACIÓN GPS"
            }
            else -> {
                Intent(Settings.ACTION_SETTINGS) to "AJUSTES SISTEMA"
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchIntent(intent, badge, "PANEL NO DISPONIBLE")
    }

    fun openAppAction(appAction: String, query: String): CommandExecutionResult {
        return when (appAction.lowercase()) {
            "camera", "camara", "cámara" -> {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                launchIntent(intent, "CÁMARA ABIERTA", "SIN APP CÁMARA")
            }
            "alarms", "alarmas", "reloj" -> {
                val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                launchIntent(intent, "ALARMAS ABIERTAS", "SIN APP RELOJ")
            }
            "calendar", "calendario" -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALENDAR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                launchIntent(intent, "CALENDARIO", "SIN CALENDARIO")
            }
            "calculator", "calculadora" -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                launchIntent(intent, "CALCULADORA", "SIN CALCULADORA")
            }
            "phone", "dialer", "telefono", "teléfono" -> makeCall(query)
            "browser", "search", "buscar", "web" -> webBrowse(url = null, query = query)
            else -> openSystemPanel("settings")
        }
    }

    private fun launchIntent(
        intent: Intent,
        successBadge: String,
        errorBadge: String
    ): CommandExecutionResult {
        return try {
            context.startActivity(intent)
            CommandExecutionResult(success = true, actionBadge = successBadge)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity found for intent ${intent.action}", e)
            CommandExecutionResult(
                success = false,
                actionBadge = errorBadge,
                detailText = "No se encontró una aplicación compatible en este dispositivo."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching intent", e)
            CommandExecutionResult(
                success = false,
                actionBadge = errorBadge,
                detailText = e.localizedMessage
            )
        }
    }

    fun openWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open WRITE_SETTINGS screen", e)
        }
    }

    fun openDndPolicyPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open DND policy screen", e)
        }
    }

    fun triggerHapticPulse() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(24L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(24L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic pulse unavailable", e)
        }
    }

    private fun buildTelemetryReport(t: DeviceTelemetry, metric: String): String {
        val chargeStr = if (t.isCharging) "cargando" else "descargando"
        return when (metric.lowercase()) {
            "battery", "bateria", "batería" ->
                "Batería: ${t.batteryPercent}% ($chargeStr, ${String.format("%.1f", t.batteryTempC)}°C)"
            "storage", "almacenamiento" ->
                "Almacenamiento libre: ${String.format("%.1f", t.availableStorageGb)} GB de ${String.format("%.1f", t.totalStorageGb)} GB"
            "memory", "ram" ->
                "RAM disponible: ${t.availableRamMb} MB de ${t.totalRamMb} MB"
            "network", "red", "wifi" ->
                "Red: ${t.networkType} (${if (t.isNetworkConnected) "Conectado" else "Sin conexión"})"
            else ->
                "Batería ${t.batteryPercent}% ($chargeStr) • Vol ${t.mediaVolumePercent}% • Red ${t.networkType} • Libre ${String.format("%.1f", t.availableStorageGb)} GB"
        }
    }
}

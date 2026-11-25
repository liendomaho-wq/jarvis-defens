package com.example.system

import com.example.model.DeviceTelemetry
import com.example.model.JarvisAiDecision
import com.example.model.JarvisIntentType
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fast, robust on-device parser for explicit Android system and assistant commands.
 * Accurately extracts phone numbers, contacts, messaging bodies, web/YouTube searches,
 * and camera/photo capture intents.
 */
object LocalSystemCommandParser {

    fun tryParseSystemCommand(rawText: String, telemetry: DeviceTelemetry): JarvisAiDecision? {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) return null

        // 1. WhatsApp / Telegram / SMS messaging
        parseMessagingCommand(rawText, normalized)?.let { return it }

        // 2. Camera & Photo Capture
        if (containsAny(normalized, "toma una foto", "toma foto", "saca una foto", "haz una foto", "captura la foto", "abre la camara", "abre camara", "camara")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.OPEN_APP_ACTION,
                spokenResponse = "Abriendo cámara para capturar la foto.",
                actionSummary = "CÁMARA // FOTO",
                appAction = "camera"
            )
        }

        // 3. Web search / URLs / YouTube
        parseWebCommand(rawText, normalized)?.let { return it }

        // 4. Maps & Navigation
        if (containsAny(normalized, "como llegar a ", "llevame a ", "ruta a ", "navega a ", "navegar a ")) {
            val dest = extractAfterKeywords(rawText, listOf("como llegar a ", "llevame a ", "ruta a ", "navega a ", "navegar a "))
            if (dest.isNotBlank()) {
                return JarvisAiDecision(
                    intent = JarvisIntentType.NAVIGATE_MAPS,
                    spokenResponse = "Iniciando ruta hacia $dest.",
                    actionSummary = "MAPAS // ${dest.take(14).uppercase()}",
                    query = dest
                )
            }
        }

        // 5. Phone Calls ("llama a X", "marca a X", "llamar a X")
        if (containsAny(normalized, "llama a ", "llamar a ", "marca a ", "marcar a ", "comunicame con ", "hablar con ")) {
            val target = extractAfterKeywords(
                rawText,
                listOf("llama a ", "llamar a ", "marca a ", "marcar a ", "comunicame con ", "hablar con ")
            )
            if (target.isNotBlank()) {
                return JarvisAiDecision(
                    intent = JarvisIntentType.MAKE_CALL,
                    spokenResponse = "Marcando a $target.",
                    actionSummary = "LLAMADA // ${target.take(12).uppercase()}",
                    recipient = target
                )
            }
        }

        // 6. Media playback controls
        if (containsAny(normalized, "pausa la musica", "pausar musica", "deten la musica", "pausa el audio")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.MEDIA_CONTROL,
                spokenResponse = "Reproducción en pausa.",
                actionSummary = "MEDIA // PAUSA",
                mediaCommand = "pause"
            )
        }
        if (containsAny(normalized, "reanuda la musica", "pon la musica", "play musica", "reproduce musica")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.MEDIA_CONTROL,
                spokenResponse = "Reanudando reproducción.",
                actionSummary = "MEDIA // PLAY",
                mediaCommand = "play"
            )
        }
        if (containsAny(normalized, "siguiente cancion", "cambia de cancion", "pasa la cancion")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.MEDIA_CONTROL,
                spokenResponse = "Saltando a la siguiente pista.",
                actionSummary = "MEDIA // SIGUIENTE",
                mediaCommand = "next"
            )
        }
        if (containsAny(normalized, "cancion anterior", "pista anterior")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.MEDIA_CONTROL,
                spokenResponse = "Volviendo a la pista anterior.",
                actionSummary = "MEDIA // ANTERIOR",
                mediaCommand = "previous"
            )
        }

        // 7. Flashlight / Torch
        if (containsAny(normalized, "linterna", "flash", "antorcha", "luz trasera")) {
            val turnOff = containsAny(normalized, "apaga", "apagar", "desactiva", "desactivar", "off", "quita")
            val turnOn = containsAny(normalized, "enciende", "encender", "prende", "prender", "activa", "activar", "on", "pon")
            val target = when {
                turnOff -> false
                turnOn -> true
                else -> !telemetry.isFlashlightOn
            }
            return JarvisAiDecision(
                intent = JarvisIntentType.TOGGLE_FLASHLIGHT,
                spokenResponse = if (target) "Linterna encendida, señor." else "Linterna apagada.",
                actionSummary = if (target) "LINTERNA ON" else "LINTERNA OFF",
                enabled = target
            )
        }

        // 8. Jarvis TTS Voice Mute / Unmute
        if (containsAny(normalized, "silencia tu voz", "callate", "no hables", "silenciar jarvis", "desactiva tu voz")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.SET_TTS_VOICE,
                spokenResponse = "Voz de Jarvis silenciada.",
                actionSummary = "VOZ SILENCIADA",
                enabled = false
            )
        }
        if (containsAny(normalized, "activa tu voz", "habla jarvis", "reactiva tu voz")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.SET_TTS_VOICE,
                spokenResponse = "Voz de Jarvis reactivada, señor.",
                actionSummary = "VOZ ACTIVADA",
                enabled = true
            )
        }

        // 9. Volume Control
        if (containsAny(normalized, "volumen", "sonido", "audio")) {
            val stream = when {
                containsAny(normalized, "timbre", "llamada") -> "ring"
                containsAny(normalized, "alarma") -> "alarm"
                containsAny(normalized, "notificacion") -> "notification"
                else -> "media"
            }
            val pct = extractPercentage(normalized)
            return when {
                containsAny(normalized, "silencia", "silenciar", "mute", "cero") -> JarvisAiDecision(
                    intent = JarvisIntentType.SET_VOLUME,
                    spokenResponse = "Volumen silenciado.",
                    actionSummary = "VOLUMEN 0%",
                    enabled = true,
                    stream = stream
                )
                containsAny(normalized, "maximo", "al tope", "completo") -> JarvisAiDecision(
                    intent = JarvisIntentType.SET_VOLUME,
                    spokenResponse = "Volumen ajustado al máximo.",
                    actionSummary = "VOLUMEN 100%",
                    levelPercent = 100,
                    stream = stream
                )
                pct != null -> JarvisAiDecision(
                    intent = JarvisIntentType.SET_VOLUME,
                    spokenResponse = "Volumen ajustado al $pct por ciento.",
                    actionSummary = "VOLUMEN $pct%",
                    levelPercent = pct,
                    stream = stream
                )
                containsAny(normalized, "sube", "subir", "aumenta", "mas alto") -> JarvisAiDecision(
                    intent = JarvisIntentType.SET_VOLUME,
                    spokenResponse = "Subiendo el volumen.",
                    actionSummary = "VOLUMEN +20%",
                    deltaPercent = 20,
                    stream = stream
                )
                containsAny(normalized, "baja", "bajar", "disminuye", "reduce", "menos") -> JarvisAiDecision(
                    intent = JarvisIntentType.SET_VOLUME,
                    spokenResponse = "Bajando el volumen.",
                    actionSummary = "VOLUMEN -20%",
                    deltaPercent = -20,
                    stream = stream
                )
                else -> null
            }
        }

        // 10. Ringer Mode
        if (containsAny(normalized, "modo vibracion", "en vibracion", "pon vibrar", "vibracion")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.SET_RINGER_MODE,
                spokenResponse = "Teléfono configurado en modo vibración.",
                actionSummary = "MODO VIBRACIÓN",
                ringerMode = "vibrate"
            )
        }
        if (containsAny(normalized, "modo silencio", "en silencio", "no molestar", "silencia el telefono")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.SET_RINGER_MODE,
                spokenResponse = "Activando modo silencio del teléfono.",
                actionSummary = "MODO SILENCIO",
                ringerMode = "silent"
            )
        }
        if (containsAny(normalized, "modo normal", "sonido normal", "activa el timbre")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.SET_RINGER_MODE,
                spokenResponse = "Perfil de sonido restaurado a modo normal.",
                actionSummary = "MODO NORMAL",
                ringerMode = "normal"
            )
        }

        // 11. Screen Brightness
        if (containsAny(normalized, "brillo", "iluminacion de pantalla")) {
            val pct = extractPercentage(normalized)
            return when {
                containsAny(normalized, "automatico", "auto") -> JarvisAiDecision(
                    intent = JarvisIntentType.SET_BRIGHTNESS,
                    spokenResponse = "Activando brillo automático.",
                    actionSummary = "BRILLO AUTO",
                    autoMode = true
                )
                pct != null -> JarvisAiDecision(
                    intent = JarvisIntentType.SET_BRIGHTNESS,
                    spokenResponse = "Brillo ajustado al $pct por ciento.",
                    actionSummary = "BRILLO $pct%",
                    levelPercent = pct
                )
                containsAny(normalized, "maximo", "al tope") -> JarvisAiDecision(
                    intent = JarvisIntentType.SET_BRIGHTNESS,
                    spokenResponse = "Brillo de pantalla al máximo.",
                    actionSummary = "BRILLO 100%",
                    levelPercent = 100
                )
                containsAny(normalized, "minimo", "muy bajo") -> JarvisAiDecision(
                    intent = JarvisIntentType.SET_BRIGHTNESS,
                    spokenResponse = "Brillo reducido al mínimo.",
                    actionSummary = "BRILLO 15%",
                    levelPercent = 15
                )
                containsAny(normalized, "sube", "subir", "aumenta", "mas") -> JarvisAiDecision(
                    intent = JarvisIntentType.SET_BRIGHTNESS,
                    spokenResponse = "Aumentando el brillo de pantalla.",
                    actionSummary = "BRILLO +20%",
                    levelPercent = (telemetry.brightnessPercent + 20).coerceIn(10, 100)
                )
                containsAny(normalized, "baja", "bajar", "reduce", "menos") -> JarvisAiDecision(
                    intent = JarvisIntentType.SET_BRIGHTNESS,
                    spokenResponse = "Reduciendo el brillo de pantalla.",
                    actionSummary = "BRILLO -20%",
                    levelPercent = (telemetry.brightnessPercent - 20).coerceIn(10, 100)
                )
                else -> null
            }
        }

        // 12. Timer
        if (containsAny(normalized, "temporizador", "timer", "cuenta regresiva")) {
            val number = extractFirstInt(normalized)
            if (number != null && number > 0) {
                val seconds = when {
                    containsAny(normalized, "hora", "horas") -> number * 3600
                    containsAny(normalized, "segundo", "segundos", "seg") -> number
                    else -> number * 60
                }
                val unitText = when {
                    containsAny(normalized, "hora", "horas") -> "$number h"
                    containsAny(normalized, "segundo", "segundos", "seg") -> "$number s"
                    else -> "$number min"
                }
                return JarvisAiDecision(
                    intent = JarvisIntentType.SET_TIMER,
                    spokenResponse = "Iniciando temporizador de $unitText.",
                    actionSummary = "TIMER $unitText",
                    durationSeconds = seconds,
                    label = "Jarvis $unitText"
                )
            }
        }

        // 13. Alarm
        if (containsAny(normalized, "alarma", "despiertame", "despertador")) {
            val timeMatch = Regex("""\b(\d{1,2})(?::(\d{2}))?\b""").find(normalized)
            if (timeMatch != null) {
                var hour = timeMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 7
                val minute = timeMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
                if (containsAny(normalized, "tarde", "noche", "pm") && hour in 1..11) {
                    hour += 12
                }
                val formatted = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                return JarvisAiDecision(
                    intent = JarvisIntentType.SET_ALARM,
                    spokenResponse = "Configurando alarma para las $formatted.",
                    actionSummary = "ALARMA $formatted",
                    hour = hour,
                    minute = minute,
                    label = "Alarma Jarvis"
                )
            }
        }

        // 14. System Panels & App Launchers
        if (containsAny(normalized, "abre ", "abrir ", "muestra ", "ir a ", "panel ")) {
            return when {
                containsAny(normalized, "wifi", "wi-fi", "red") -> JarvisAiDecision(
                    intent = JarvisIntentType.OPEN_SYSTEM_PANEL,
                    spokenResponse = "Abriendo el panel de Wi-Fi.",
                    actionSummary = "PANEL WI-FI",
                    panel = "wifi"
                )
                containsAny(normalized, "bluetooth", "bt") -> JarvisAiDecision(
                    intent = JarvisIntentType.OPEN_SYSTEM_PANEL,
                    spokenResponse = "Abriendo ajustes de Bluetooth.",
                    actionSummary = "PANEL BLUETOOTH",
                    panel = "bluetooth"
                )
                containsAny(normalized, "ubicacion", "gps") -> JarvisAiDecision(
                    intent = JarvisIntentType.OPEN_SYSTEM_PANEL,
                    spokenResponse = "Abriendo ajustes de ubicación.",
                    actionSummary = "PANEL GPS",
                    panel = "location"
                )
                containsAny(normalized, "pantalla", "brillo") -> JarvisAiDecision(
                    intent = JarvisIntentType.OPEN_SYSTEM_PANEL,
                    spokenResponse = "Abriendo ajustes de pantalla.",
                    actionSummary = "PANEL PANTALLA",
                    panel = "display"
                )
                containsAny(normalized, "bateria") -> JarvisAiDecision(
                    intent = JarvisIntentType.OPEN_SYSTEM_PANEL,
                    spokenResponse = "Abriendo detalles de batería.",
                    actionSummary = "PANEL BATERÍA",
                    panel = "battery"
                )
                containsAny(normalized, "ajustes", "configuracion") -> JarvisAiDecision(
                    intent = JarvisIntentType.OPEN_SYSTEM_PANEL,
                    spokenResponse = "Abriendo ajustes del sistema.",
                    actionSummary = "AJUSTES OS",
                    panel = "settings"
                )
                else -> {
                    val appTarget = extractAfterKeywords(
                        rawText,
                        listOf("abre ", "abrir ", "muestra ", "ir a ")
                    )
                    if (appTarget.isNotBlank()) {
                        JarvisAiDecision(
                            intent = JarvisIntentType.OPEN_APP_BY_NAME,
                            spokenResponse = "Abriendo $appTarget.",
                            actionSummary = "APP // ${appTarget.take(14).uppercase()}",
                            appName = appTarget
                        )
                    } else null
                }
            }
        }

        // 15. Device Status / Diagnostics
        if (containsAny(normalized, "diagnostico", "estado del sistema", "estado del dispositivo", "telemetria")) {
            val charge = if (telemetry.isCharging) "cargando" else "en uso"
            return JarvisAiDecision(
                intent = JarvisIntentType.QUERY_DEVICE_STATUS,
                spokenResponse = "Diagnóstico completado, señor. Batería al ${telemetry.batteryPercent}% ($charge), red ${telemetry.networkType}, volumen al ${telemetry.mediaVolumePercent}% y ${telemetry.availableRamMb} megabytes de RAM disponibles.",
                actionSummary = "DIAGNÓSTICO OK",
                metric = "all"
            )
        }
        if (containsAny(normalized, "bateria", "carga", "energia")) {
            val charge = if (telemetry.isCharging) "cargando actualmente" else "en descarga"
            return JarvisAiDecision(
                intent = JarvisIntentType.QUERY_DEVICE_STATUS,
                spokenResponse = "La batería está al ${telemetry.batteryPercent}%, $charge.",
                actionSummary = "BATERÍA ${telemetry.batteryPercent}%",
                metric = "battery"
            )
        }
        if (containsAny(normalized, "almacenamiento", "espacio libre", "memoria interna")) {
            val free = String.format(Locale.getDefault(), "%.1f", telemetry.availableStorageGb)
            val total = String.format(Locale.getDefault(), "%.1f", telemetry.totalStorageGb)
            return JarvisAiDecision(
                intent = JarvisIntentType.QUERY_DEVICE_STATUS,
                spokenResponse = "Tienes $free gigabytes libres de un total de $total gigabytes.",
                actionSummary = "ESPACIO ${free}GB",
                metric = "storage"
            )
        }
        if (containsAny(normalized, "ram", "memoria disponible")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.QUERY_DEVICE_STATUS,
                spokenResponse = "Hay ${telemetry.availableRamMb} megabytes de memoria RAM disponibles de ${telemetry.totalRamMb} megabytes.",
                actionSummary = "RAM ${telemetry.availableRamMb}M",
                metric = "memory"
            )
        }
        if (containsAny(normalized, "que hora es", "dime la hora", "hora actual")) {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "Son las $timeStr, señor.",
                actionSummary = "HORA $timeStr"
            )
        }

        if (containsAny(normalized, "clima", "tiempo", "pronostico", "temperatura", "va a llover", "mañana")) {
            val city = extractAfterKeywords(rawText, listOf("clima en ", "tiempo en ", "pronostico en ", "temperatura en ", "clima para ", "tiempo para ", "clima de "))
            return JarvisAiDecision(
                intent = JarvisIntentType.WEATHER_REPORT,
                spokenResponse = "Permítame consultar el reporte meteorológico, señor.",
                actionSummary = "CLIMA // ${if (city.isNotBlank()) city.uppercase() else "HOY"}",
                query = city
            )
        }

        return null
    }

    private fun parseMessagingCommand(rawText: String, normalized: String): JarvisAiDecision? {
        val platform = when {
            containsAny(normalized, "whatsapp", "whasaap", "wasap", "watsap") -> "whatsapp"
            containsAny(normalized, "telegram") -> "telegram"
            containsAny(normalized, "sms", "mensaje de texto") -> "sms"
            else -> return null
        }

        val isSendIntent = containsAny(
            normalized,
            "envia", "enviar", "manda", "mandar", "escribe", "escribir", "mensaje"
        )
        if (!isSendIntent) return null

        // Improved recipient and message body extraction for WhatsApp/Telegram/SMS
        // Examples: "envía un WhatsApp a Carlos diciendo hola" or "manda mensaje a María que nos vemos"
        val regex = Regex(
            """\b(?:a|para)\s+(.+?)\s+(?:(?:por|en)\s+(?:whatsapp|whasaap|wasap|telegram|sms)\s+)?(?:diciendo|que diga|que sea|con el texto|con el mensaje|el mensaje|dice|dicen|que|:)\s+(.+)""",
            RegexOption.IGNORE_CASE
        )
        val normMatch = regex.find(normalized)
        if (normMatch != null) {
            val recipientStart = normMatch.groups[1]?.range?.first ?: 0
            val recipientEnd = normMatch.groups[1]?.range?.last ?: 0
            val msgStart = normMatch.groups[2]?.range?.first ?: 0
            val safeRec = if (recipientEnd < rawText.length && recipientStart <= recipientEnd) {
                rawText.substring(recipientStart, recipientEnd + 1).trim()
            } else normMatch.groupValues[1].trim()
            val safeMsg = if (msgStart < rawText.length) {
                rawText.substring(msgStart).trim()
            } else normMatch.groupValues[2].trim()

            return JarvisAiDecision(
                intent = JarvisIntentType.SEND_MESSAGE,
                spokenResponse = "Enviando mensaje por $platform a $safeRec.",
                actionSummary = "${platform.uppercase()} → ${safeRec.take(12).uppercase()}",
                platform = platform,
                recipient = safeRec,
                messageBody = safeMsg
            )
        }

        // Fallback: extract recipient after "a" or "para" and message after saying keywords
        val msgAfterSaying = extractAfterKeywords(
            rawText,
            listOf("diciendo ", "que diga ", "que sea ", "con el mensaje ", ": ")
        )
        val recipientOnly = extractAfterKeywords(rawText, listOf(" para ", " a "))
            .takeIf { it.isNotBlank() && msgAfterSaying.isBlank() }

        return JarvisAiDecision(
            intent = JarvisIntentType.SEND_MESSAGE,
            spokenResponse = "Abriendo $platform para enviar tu mensaje.",
            actionSummary = platform.uppercase(),
            platform = platform,
            recipient = recipientOnly.orEmpty(),
            messageBody = msgAfterSaying
        )
    }

    private fun parseWebCommand(rawText: String, normalized: String): JarvisAiDecision? {
        val urlMatch = Regex("""\b((?:https?://)?[a-z0-9\-]+\.[a-z]{2,}(?:/\S*)?)\b""", RegexOption.IGNORE_CASE)
            .find(rawText)
        if (urlMatch != null && containsAny(normalized, "abre", "abrir", "entra", "ve a", "navega", "visita")) {
            val url = urlMatch.groupValues[1]
            return JarvisAiDecision(
                intent = JarvisIntentType.WEB_BROWSE,
                spokenResponse = "Abriendo $url en el navegador.",
                actionSummary = "WEB // ${url.take(16).uppercase()}",
                url = url
            )
        }

        if (containsAny(normalized, "en youtube", "youtube ")) {
            val query = extractAfterKeywords(rawText, listOf("busca en youtube ", "pon en youtube ", "reproduce en youtube ", "youtube "))
            if (query.isNotBlank()) {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                return JarvisAiDecision(
                    intent = JarvisIntentType.WEB_BROWSE,
                    spokenResponse = "Buscando $query en YouTube.",
                    actionSummary = "YOUTUBE",
                    url = "https://www.youtube.com/results?search_query=$encoded"
                )
            }
        }

        if (containsAny(normalized, "busca en internet ", "busca en google ", "investiga sobre ", "buscar en internet ", "navega por internet ")) {
            val query = extractAfterKeywords(
                rawText,
                listOf("busca en internet ", "busca en google ", "investiga sobre ", "buscar en internet ", "navega por internet ")
            )
            if (query.isNotBlank()) {
                return JarvisAiDecision(
                    intent = JarvisIntentType.WEB_BROWSE,
                    spokenResponse = "Buscando $query en internet.",
                    actionSummary = "WEB SEARCH",
                    query = query
                )
            }
        }
        return null
    }

    fun tryOfflineAssistantFallback(rawText: String, telemetry: DeviceTelemetry): JarvisAiDecision? {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) return null

        if (containsAny(normalized, "que dia es", "fecha de hoy", "que fecha es", "en que dia estamos")) {
            val dateStr = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "ES")).format(Date())
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "Hoy es $dateStr, señor.",
                actionSummary = "FECHA LOCAL"
            )
        }

        if (containsAny(normalized, "hola", "buenos dias", "buenas tardes", "buenas noches", "hey jarvis", "despierta")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "A sus órdenes, señor. Todos los sistemas locales están operativos.",
                actionSummary = "MODO LOCAL OK"
            )
        }

        if (containsAny(normalized, "gracias", "muy bien", "perfecto", "excelente")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "Siempre a su servicio, señor.",
                actionSummary = "ASISTENTE LISTO"
            )
        }

        if (containsAny(normalized, "que puedes hacer", "quien eres", "ayuda", "funciones", "comandos")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "Puedo controlar linterna, volumen, brillo, alarmas, temporizadores, enviar mensajes por WhatsApp o Telegram, realizar llamadas, abrir rutas en mapas, navegar en internet y responder consultas tanto en línea como en modo local.",
                actionSummary = "SISTEMA HÍBRIDO"
            )
        }

        val mathResult = evaluateSimpleMath(normalized)
        if (mathResult != null) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "El resultado es $mathResult.",
                actionSummary = "CÁLCULO = $mathResult"
            )
        }

        if (containsAny(normalized, "como estas", "estado", "reporte")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.QUERY_DEVICE_STATUS,
                spokenResponse = "Operando en modo local, señor. Batería al ${telemetry.batteryPercent}% y ${telemetry.availableRamMb} megabytes de RAM disponibles.",
                actionSummary = "LOCAL // ${telemetry.batteryPercent}%",
                metric = "all"
            )
        }

        return null
    }

    private fun evaluateSimpleMath(normalized: String): String? {
        val cleaned = normalized
            .replace(" por ", " * ")
            .replace(" multiplicado por ", " * ")
            .replace(" x ", " * ")
            .replace(" entre ", " / ")
            .replace(" dividido por ", " / ")
            .replace(" dividido entre ", " / ")
            .replace(" mas ", " + ")
            .replace(" menos ", " - ")
        val match = Regex("""(-?\d+(?:\.\d+)?)\s*([+\-*/])\s*(-?\d+(?:\.\d+)?)""").find(cleaned)
            ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val b = match.groupValues[3].toDoubleOrNull() ?: return null
        val res = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b != 0.0) a / b else return null
            else -> return null
        }
        return if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.2f", res)
    }

    private fun extractAfterKeywords(rawText: String, keywords: List<String>): String {
        val norm = normalize(rawText)
        for (kw in keywords) {
            val idx = norm.indexOf(kw)
            if (idx >= 0) {
                val start = (idx + kw.length).coerceAtMost(rawText.length)
                val extracted = rawText.substring(start).trim()
                if (extracted.isNotBlank()) return extracted
            }
        }
        return ""
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun extractPercentage(text: String): Int? {
        val match = Regex("""\b(\d{1,3})\s*%?""").find(text) ?: return null
        return match.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
    }

    private fun extractFirstInt(text: String): Int? {
        val match = Regex("""\b(\d{1,4})\b""").find(text) ?: return null
        return match.groupValues[1].toIntOrNull()
    }
}

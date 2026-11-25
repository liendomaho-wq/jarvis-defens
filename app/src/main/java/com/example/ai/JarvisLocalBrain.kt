package com.example.ai

import android.content.Context
import com.example.automation.AutomationEngine
import com.example.automation.TriggerConditionType
import com.example.model.DeviceTelemetry
import com.example.model.JarvisAiDecision
import com.example.model.JarvisIntentType
import com.example.system.LocalSystemCommandParser
import java.io.File
import java.util.Locale

/**
 * 100% On-Device Autonomous AI Brain for J.A.R.V.I.S.
 * Combines:
 *  - From-scratch Multi-Layer Perceptron (LocalNeuralNetwork) with live incremental learning
 *  - Multi-step compound command planner
 *  - Autonomous routines, sensor triggers, delayed scheduling & memory notes (AutomationEngine)
 *  - Local arithmetic/unit solver & semantic vector knowledge engine
 */
class JarvisLocalBrain(context: Context) {

    val automationEngine = AutomationEngine(context)
    val neuralNetwork = LocalNeuralNetwork(File(context.filesDir, "jarvis_nn_weights_v2.bin"))

    @Synchronized
    fun ensureInitialized() {
        if (!neuralNetwork.isReady) {
            val learnedPairs = automationEngine.getAllLearnedAliases().mapNotNull { alias ->
                val targetDecision = LocalSystemCommandParser.tryParseSystemCommand(
                    alias.targetCommand,
                    DeviceTelemetry()
                )
                if (targetDecision != null) alias.phrase to targetDecision.intent else null
            }
            neuralNetwork.initializeOrTrain(learnedPairs)
        }
    }

    suspend fun interpretCommand(rawText: String, telemetry: DeviceTelemetry): JarvisAiDecision {
        ensureInitialized()
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "No recibí ninguna instrucción, señor.",
                actionSummary = "SIN ENTRADA"
            )
        }

        // 1. Check user-taught learned aliases first
        automationEngine.resolveLearnedAlias(trimmed)?.let { mappedCommand ->
            return interpretCommand(mappedCommand, telemetry).copy(
                actionSummary = "APRENDIDO // OK"
            )
        }

        val norm = TextVectorizer.normalize(trimmed)

        // 2. Check autonomous meta-commands (teach, create automation, schedule, notes, routines)
        parseMetaAutonomousCommand(trimmed, norm, telemetry)?.let { return it }

        // 3. Compound multi-clause decomposition (e.g. "Enciende la linterna y pon el brillo al 80%")
        val clauses = splitCompoundClauses(trimmed, norm)
        if (clauses.size > 1) {
            val subDecisions = mutableListOf<JarvisAiDecision>()
            for (clause in clauses) {
                subDecisions.add(interpretSingleClause(clause, telemetry))
            }
            val actionable = subDecisions.filter { it.intent != JarvisIntentType.CONVERSATION }
            if (actionable.size >= 2) {
                return JarvisAiDecision(
                    intent = actionable.first().intent,
                    spokenResponse = "Ejecutando ${actionable.size} acciones en secuencia, señor.",
                    actionSummary = "CADENA (${actionable.size} PASOS)",
                    steps = actionable
                )
            }
        }

        // 4. Single instruction interpretation (deterministic + neural network)
        return interpretSingleClause(trimmed, telemetry)
    }

    suspend fun expandActionChain(actions: List<String>, telemetry: DeviceTelemetry): List<JarvisAiDecision> {
        ensureInitialized()
        val list = mutableListOf<JarvisAiDecision>()
        for (act in actions) {
            val d = interpretCommand(act, telemetry)
            if (d.steps.isNotEmpty()) list.addAll(d.steps) else list.add(d)
        }
        return list
    }

    private suspend fun interpretSingleClause(clause: String, telemetry: DeviceTelemetry): JarvisAiDecision {
        val norm = TextVectorizer.normalize(clause)

        // Check routine match directly inside a clause too
        automationEngine.findRoutine(norm)?.let { routine ->
            val steps = expandActionChain(routine.actions, telemetry)
            return JarvisAiDecision(
                intent = JarvisIntentType.RUN_ROUTINE,
                spokenResponse = "Activando rutina ${routine.name}, señor.",
                actionSummary = "RUTINA // ${routine.name.uppercase()}",
                routineName = routine.name,
                steps = steps
            )
        }

        // Exact deterministic command parser
        LocalSystemCommandParser.tryParseSystemCommand(clause, telemetry)?.let { return it }

        // Exact math, unit conversions, date/time, and greetings
        LocalKnowledgeAndReasoning.tryExactLocalQuery(clause, telemetry)?.let { return it }

        // Neural Network Inference
        val prediction = neuralNetwork.predict(clause)
        if (prediction.confidence >= 0.42f && prediction.intent != JarvisIntentType.CONVERSATION) {
            buildDecisionFromNeuralPrediction(prediction, clause, norm, telemetry)?.let { return it }
        }

        // Factual knowledge, online lookup, math, conversions, diagnostics, or conversational reasoning
        return LocalKnowledgeAndReasoning.answerQuery(clause, telemetry)
    }

    private suspend fun parseMetaAutonomousCommand(
        rawText: String,
        norm: String,
        telemetry: DeviceTelemetry
    ): JarvisAiDecision? {
        // A. Teach neural network: "aprende que X significa Y" / "cuando diga X haz Y"
        val teachRegex1 = Regex(
            """(?:aprende|recuerda|ensena(?:\s+a\s+tu\s+red\s+neuronal)?)\s+que\s+(.+?)\s+(?:significa|es|equivale a|ejecuta|haz)\s+(.+)"""
        )
        val teachRegex2 = Regex(
            """cuando\s+(?:yo\s+)?diga\s+(.+?)\s+(?:haz|ejecuta|activa|pon|abre|di)\s+(.+)"""
        )
        val teachMatch = teachRegex1.find(norm) ?: teachRegex2.find(norm)
        if (teachMatch != null) {
            val phrase = extractSlice(rawText, teachMatch.groups[1]?.range).trim('"', ' ', '\'')
            val action = extractSlice(rawText, teachMatch.groups[2]?.range).trim('"', ' ', '\'')
            if (phrase.isNotBlank() && action.isNotBlank()) {
                automationEngine.addLearnedAlias(phrase, action)
                val targetDecision = interpretCommand(action, telemetry)
                neuralNetwork.fineTune(phrase, targetDecision.intent, epochs = 18)
                return JarvisAiDecision(
                    intent = JarvisIntentType.TEACH_NEURAL_NET,
                    spokenResponse = "Aprendido, señor. Mi red neuronal local ahora asocia '$phrase' con '$action'.",
                    actionSummary = "RED ENTRENADA"
                )
            }
        }

        // B. Create routine: "crea (una)? rutina (llamada)? <name> que <actions>"
        val routineRegex = Regex(
            """(?:crea|crear|nueva|guarda|configura)\s+(?:una\s+)?rutina\s+(?:llamada\s+)?(.+?)\s+que\s+(.+)"""
        )
        routineRegex.find(norm)?.let { m ->
            val name = extractSlice(rawText, m.groups[1]?.range).trim('"', ' ')
            val actionsRaw = extractSlice(rawText, m.groups[2]?.range)
            val actions = splitActionSteps(actionsRaw)
            if (name.isNotBlank() && actions.isNotEmpty()) {
                val saved = automationEngine.saveRoutine(name, actions)
                neuralNetwork.fineTune("activa ${saved.name}", JarvisIntentType.RUN_ROUTINE, epochs = 10)
                return JarvisAiDecision(
                    intent = JarvisIntentType.CREATE_AUTOMATION,
                    spokenResponse = "Rutina '${saved.name}' guardada con ${saved.actions.size} acciones. Puedes activarla diciendo 'activa ${saved.name}'.",
                    actionSummary = "RUTINA // ${saved.name.take(12).uppercase()}",
                    routineName = saved.name
                )
            }
        }

        // C. Sensor / telemetry trigger automation
        if (containsAny(norm, "automatiza", "cada vez que", "si la bateria", "cuando la bateria", "cuando conecte", "cuando me quede sin", "cuando se conecte")) {
            val triggerMatch = Regex(
                """(?:automatiza(?:\s+que)?\s+)?(?:si|cuando|cada vez que)\s+(.+?)\s+(?:entonces|haz|ejecuta|activa|pon|baja|sube|apaga|enciende|abre|di)\s+(.+)"""
            ).find(norm)

            val condAndActions = if (triggerMatch != null) {
                val condText = triggerMatch.groupValues[1]
                // Re-include verb after condition if it was matched as separator
                val sepMatch = Regex("""\b(entonces|haz|ejecuta)\b""").find(norm)
                val actionRaw = if (sepMatch != null && sepMatch.range.first > (triggerMatch.groups[1]?.range?.last ?: 0)) {
                    rawText.substring(sepMatch.range.last + 1).trim()
                } else {
                    val startIdx = triggerMatch.groups[2]?.range?.first ?: 0
                    val verbSearchStart = (triggerMatch.groups[1]?.range?.last ?: 0) + 1
                    rawText.substring(verbSearchStart.coerceAtMost(startIdx)).trim()
                        .removePrefix("entonces").removePrefix("haz").removePrefix("ejecuta").trim()
                }
                condText to actionRaw
            } else null

            if (condAndActions != null) {
                val (condNorm, actionsRaw) = condAndActions
                val actions = splitActionSteps(actionsRaw)
                val (condType, threshold, ruleName) = parseTriggerCondition(condNorm)
                if (actions.isNotEmpty()) {
                    val rule = automationEngine.saveTriggerRule(ruleName, condType, threshold, actions)
                    return JarvisAiDecision(
                        intent = JarvisIntentType.CREATE_AUTOMATION,
                        spokenResponse = "Automatización '${rule.name}' activada. Se ejecutará automáticamente cuando se cumpla la condición.",
                        actionSummary = "AUTO // ${rule.name.take(12).uppercase()}",
                        conditionSpec = rule.name
                    )
                }
            }
        }

        // D. Delayed / scheduled task: "en 5 minutos apaga la linterna" / "dentro de 30 segundos pon vibracion"
        val schedRegex = Regex(
            """^(?:programa\s+)?(?:en|dentro de)\s+(\d+)\s*(segundo|segundos|seg|minuto|minutos|min|hora|horas|h)\s+(?:para\s+)?(.+)"""
        )
        schedRegex.find(norm)?.let { m ->
            val qty = m.groupValues[1].toIntOrNull()?.coerceAtLeast(1)
            val unit = m.groupValues[2]
            val actionPart = extractSlice(rawText, m.groups[3]?.range)
            if (qty != null && actionPart.isNotBlank() && !containsAny(norm, "temporizador", "timer", "cuenta regresiva")) {
                val seconds = when {
                    unit.startsWith("h") -> qty * 3600
                    unit.startsWith("min") -> qty * 60
                    else -> qty
                }
                return JarvisAiDecision(
                    intent = JarvisIntentType.SCHEDULE_COMMAND,
                    spokenResponse = "Programado, señor. Ejecutaré '$actionPart' dentro de $qty $unit.",
                    actionSummary = "PROGRAMADO ${seconds}S",
                    durationSeconds = seconds,
                    scheduledActionText = actionPart
                )
            }
        }

        // E. List / Delete automations
        if (containsAny(norm, "lista mis rutinas", "listar rutinas", "mis rutinas", "que rutinas", "que automatizaciones", "mis automatizaciones", "ver rutinas")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.LIST_AUTOMATIONS,
                spokenResponse = automationEngine.listSummary(),
                actionSummary = "RUTINAS"
            )
        }
        if (containsAny(norm, "elimina la rutina", "borra la rutina", "elimina rutina", "borra rutina", "elimina la automatizacion", "desactiva la rutina")) {
            val target = norm
                .replace(Regex("""^(elimina|borra|desactiva)\s+(la\s+)?(rutina|automatizacion)\s*"""), "")
                .trim()
            val msg = automationEngine.deleteByName(target)
                ?: "No encontré una rutina personalizada o regla llamada '$target'."
            return JarvisAiDecision(
                intent = JarvisIntentType.DELETE_AUTOMATION,
                spokenResponse = msg,
                actionSummary = "ELIMINADO"
            )
        }

        // F. Run explicit routine: "activa modo noche" / "ejecuta rutina X"
        if (containsAny(norm, "activa ", "ejecuta ", "corre ", "inicia ", "pon ")) {
            val candidate = norm
                .replace(Regex("""^(activa|ejecuta|corre|inicia|pon)\s+(el\s+|la\s+)?"""), "")
                .trim()
            automationEngine.findRoutine(candidate)?.let { routine ->
                val steps = expandActionChain(routine.actions, telemetry)
                return JarvisAiDecision(
                    intent = JarvisIntentType.RUN_ROUTINE,
                    spokenResponse = "Ejecutando rutina ${routine.name}, señor.",
                    actionSummary = "RUTINA // ${routine.name.uppercase()}",
                    routineName = routine.name,
                    steps = steps
                )
            }
        }

        // G. Notes / Local Memory Vault
        if (containsAny(norm, "borra mis notas", "elimina mis notas", "limpia mis notas")) {
            val count = automationEngine.clearNotes()
            return JarvisAiDecision(
                intent = JarvisIntentType.READ_NOTES,
                spokenResponse = "Se eliminaron $count notas de la memoria local.",
                actionSummary = "NOTAS LIMPIAS"
            )
        }
        if (containsAny(norm, "lee mis notas", "leer notas", "muestra mis notas", "ver mis notas", "que recuerdas")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.READ_NOTES,
                spokenResponse = automationEngine.getNotesSummary(),
                actionSummary = "MEMORIA LOCAL"
            )
        }
        val noteRegex = Regex("""^(?:guarda(?:\s+una)?\s+nota|anota|apunta(?:\s+esto)?|recuerda\s+que|memoriza)\s*:?\s*(.+)""")
        noteRegex.find(norm)?.let { m ->
            val content = extractSlice(rawText, m.groups[1]?.range)
            if (content.isNotBlank()) {
                automationEngine.addNote(content)
                return JarvisAiDecision(
                    intent = JarvisIntentType.SAVE_NOTE,
                    spokenResponse = "Nota guardada en memoria local: $content.",
                    actionSummary = "NOTA GUARDADA",
                    messageBody = content
                )
            }
        }

        // H. Default Assistant Setup by Voice
        if (containsAny(norm, "asistente predeterminado", "asistente por defecto")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.OPEN_ASSISTANT_SETTINGS,
                spokenResponse = "Abriendo la configuración para fijar a Jarvis como asistente predeterminado.",
                actionSummary = "ASISTENTE OS"
            )
        }

        return null
    }

    private fun parseTriggerCondition(condNorm: String): Triple<TriggerConditionType, Int, String> {
        val pct = Regex("""(\d{1,3})""").find(condNorm)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        return when {
            containsAny(condNorm, "cargador", "conecte a cargar", "cargando") ->
                Triple(TriggerConditionType.CHARGING_STARTED, 0, "Al conectar cargador")
            containsAny(condNorm, "wifi", "wi-fi") ->
                Triple(TriggerConditionType.WIFI_CONNECTED, 0, "Al conectar Wi-Fi")
            containsAny(condNorm, "sin red", "sin internet", "desconecte") ->
                Triple(TriggerConditionType.NETWORK_LOST, 0, "Al perder conexión")
            containsAny(condNorm, "bateria") && containsAny(condNorm, "suba", "llegue", "supere", "mas de", "arriba") ->
                Triple(TriggerConditionType.BATTERY_ABOVE, pct, "Batería >= $pct%")
            else ->
                Triple(TriggerConditionType.BATTERY_BELOW, pct, "Batería <= $pct%")
        }
    }

    private fun buildDecisionFromNeuralPrediction(
        prediction: NeuralPrediction,
        rawText: String,
        norm: String,
        telemetry: DeviceTelemetry
    ): JarvisAiDecision? {
        val confPct = (prediction.confidence * 100).toInt()
        val intent = prediction.intent
        return when (intent) {
            JarvisIntentType.TOGGLE_FLASHLIGHT -> {
                val off = containsAny(norm, "apaga", "corta", "quita", "desactiva", "off")
                val on = containsAny(norm, "enciende", "prende", "luz", "ilumina", "oscuro", "on", "activa")
                val target = when {
                    off -> false
                    on -> true
                    else -> !telemetry.isFlashlightOn
                }
                JarvisAiDecision(
                    intent = JarvisIntentType.TOGGLE_FLASHLIGHT,
                    spokenResponse = if (target) "Linterna encendida, señor." else "Linterna apagada.",
                    actionSummary = "NN $confPct% // ${if (target) "LINTERNA ON" else "LINTERNA OFF"}",
                    enabled = target,
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.SET_VOLUME -> {
                val pct = Regex("""\b(\d{1,3})\b""").find(norm)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)
                val lower = containsAny(norm, "baja", "fuerte", "alto", "menos", "reduce", "silencia", "calla")
                JarvisAiDecision(
                    intent = JarvisIntentType.SET_VOLUME,
                    spokenResponse = if (pct != null) "Volumen ajustado al $pct por ciento." else if (lower) "Reduciendo el volumen." else "Aumentando el volumen.",
                    actionSummary = "NN $confPct% // VOLUMEN",
                    levelPercent = pct,
                    deltaPercent = if (pct == null) (if (lower) -20 else 20) else null,
                    stream = "media",
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.SET_BRIGHTNESS -> {
                val pct = Regex("""\b(\d{1,3})\b""").find(norm)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(5, 100)
                val brighter = containsAny(norm, "oscura", "mas luz", "sube", "aumenta")
                val nextPct = pct ?: (telemetry.brightnessPercent + if (brighter) 25 else -25).coerceIn(10, 100)
                JarvisAiDecision(
                    intent = JarvisIntentType.SET_BRIGHTNESS,
                    spokenResponse = "Ajustando el brillo al $nextPct por ciento.",
                    actionSummary = "NN $confPct% // BRILLO $nextPct%",
                    levelPercent = nextPct,
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.SET_RINGER_MODE -> {
                val mode = when {
                    containsAny(norm, "vibr") -> "vibrate"
                    containsAny(norm, "normal", "timbre") -> "normal"
                    else -> "silent"
                }
                JarvisAiDecision(
                    intent = JarvisIntentType.SET_RINGER_MODE,
                    spokenResponse = "Cambiando perfil de audio a $mode.",
                    actionSummary = "NN $confPct% // PERFIL",
                    ringerMode = mode,
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.QUERY_DEVICE_STATUS -> {
                JarvisAiDecision(
                    intent = JarvisIntentType.QUERY_DEVICE_STATUS,
                    spokenResponse = "Batería al ${telemetry.batteryPercent}%, RAM libre ${telemetry.availableRamMb} MB y red ${telemetry.networkType}.",
                    actionSummary = "NN $confPct% // TELEMETRÍA",
                    metric = "all",
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.MEDIA_CONTROL -> {
                val cmd = when {
                    containsAny(norm, "siguiente", "otra", "pasa") -> "next"
                    containsAny(norm, "anterior", "atras") -> "previous"
                    containsAny(norm, "play", "pon", "reanuda", "reproduce") -> "play"
                    else -> "pause"
                }
                JarvisAiDecision(
                    intent = JarvisIntentType.MEDIA_CONTROL,
                    spokenResponse = "Control multimedia ejecutado.",
                    actionSummary = "NN $confPct% // MEDIA",
                    mediaCommand = cmd,
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.OPEN_SYSTEM_PANEL -> {
                val panel = when {
                    containsAny(norm, "wifi", "red", "internet") -> "wifi"
                    containsAny(norm, "bluetooth") -> "bluetooth"
                    containsAny(norm, "gps", "ubicacion") -> "location"
                    containsAny(norm, "bateria") -> "battery"
                    else -> "settings"
                }
                JarvisAiDecision(
                    intent = JarvisIntentType.OPEN_SYSTEM_PANEL,
                    spokenResponse = "Abriendo panel de $panel.",
                    actionSummary = "NN $confPct% // PANEL",
                    panel = panel,
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.OPEN_APP_ACTION -> {
                val action = when {
                    containsAny(norm, "camara", "foto") -> "camera"
                    containsAny(norm, "calculadora", "calcula") -> "calculator"
                    containsAny(norm, "calendario", "agenda") -> "calendar"
                    else -> "alarms"
                }
                JarvisAiDecision(
                    intent = JarvisIntentType.OPEN_APP_ACTION,
                    spokenResponse = "Abriendo aplicación del sistema.",
                    actionSummary = "NN $confPct% // $action",
                    appAction = action,
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.OPEN_APP_BY_NAME -> {
                val apps = listOf("whatsapp", "telegram", "youtube", "spotify", "maps", "mapas", "chrome", "gmail", "instagram", "facebook", "tiktok", "camara", "calculadora", "calendario")
                val matched = apps.firstOrNull { norm.contains(it) } ?: rawText
                JarvisAiDecision(
                    intent = JarvisIntentType.OPEN_APP_BY_NAME,
                    spokenResponse = "Abriendo $matched.",
                    actionSummary = "NN $confPct% // APP",
                    appName = matched,
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.NAVIGATE_MAPS -> {
                val dest = rawText.replace(Regex("""^(?:quiero\s+ir\s+a|llevame\s+a|donde\s+queda|ruta\s+a|navega\s+a)\s*""", RegexOption.IGNORE_CASE), "").trim()
                JarvisAiDecision(
                    intent = JarvisIntentType.NAVIGATE_MAPS,
                    spokenResponse = "Abriendo ruta hacia $dest.",
                    actionSummary = "NN $confPct% // MAPAS",
                    query = dest.ifBlank { rawText },
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.WEB_BROWSE -> {
                JarvisAiDecision(
                    intent = JarvisIntentType.WEB_BROWSE,
                    spokenResponse = "Buscando en internet.",
                    actionSummary = "NN $confPct% // WEB",
                    query = rawText,
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.SEND_EMAIL -> {
                val emailMatch = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""").find(rawText)?.value.orEmpty()
                JarvisAiDecision(
                    intent = JarvisIntentType.SEND_EMAIL,
                    spokenResponse = "Abriendo redacción de correo.",
                    actionSummary = "NN $confPct% // CORREO",
                    recipient = emailMatch,
                    neuralConfidence = prediction.confidence
                )
            }
            JarvisIntentType.LIST_AUTOMATIONS -> JarvisAiDecision(
                intent = JarvisIntentType.LIST_AUTOMATIONS,
                spokenResponse = automationEngine.listSummary(),
                actionSummary = "NN $confPct% // RUTINAS",
                neuralConfidence = prediction.confidence
            )
            JarvisIntentType.READ_NOTES -> JarvisAiDecision(
                intent = JarvisIntentType.READ_NOTES,
                spokenResponse = automationEngine.getNotesSummary(),
                actionSummary = "NN $confPct% // NOTAS",
                neuralConfidence = prediction.confidence
            )
            JarvisIntentType.OPEN_ASSISTANT_SETTINGS -> JarvisAiDecision(
                intent = JarvisIntentType.OPEN_ASSISTANT_SETTINGS,
                spokenResponse = "Abriendo ajustes de asistente predeterminado.",
                actionSummary = "NN $confPct% // ASISTENTE",
                neuralConfidence = prediction.confidence
            )
            else -> null
        }
    }

    private fun splitCompoundClauses(rawText: String, norm: String): List<String> {
        // Never split inside message bodies or web searches
        if (containsAny(norm, "diciendo ", "que diga ", "con el mensaje ", "busca en ", "investiga sobre ")) {
            return listOf(rawText)
        }
        return splitActionSteps(rawText)
    }

    private fun splitActionSteps(rawText: String): List<String> {
        return rawText
            .split(Regex("""\s*(?:;|,\s*(?:y\s+)?|\s+y\s+luego\s+|\s+luego\s+|\s+después\s+|\s+despues\s+|\s+entonces\s+|\s+y\s+)\s*""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun extractSlice(rawText: String, range: IntRange?): String {
        if (range == null) return ""
        val s = range.first.coerceIn(0, rawText.length)
        val e = (range.last + 1).coerceIn(s, rawText.length)
        return rawText.substring(s, e).trim()
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }
}

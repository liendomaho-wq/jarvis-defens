package com.example.automation

import android.content.Context
import com.example.ai.TextVectorizer
import com.example.model.DeviceTelemetry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class TriggerConditionType {
    BATTERY_BELOW,
    BATTERY_ABOVE,
    CHARGING_STARTED,
    WIFI_CONNECTED,
    NETWORK_LOST
}

data class JarvisRoutine(
    val id: String,
    val name: String,
    val actions: List<String>,
    val isBuiltIn: Boolean = false
)

data class AutomationTriggerRule(
    val id: String,
    val name: String,
    val conditionType: TriggerConditionType,
    val threshold: Int = 20,
    val actions: List<String>,
    val enabled: Boolean = true,
    val lastTriggeredMs: Long = 0L
)

data class JarvisMemoryNote(
    val id: String,
    val timestamp: String,
    val content: String
)

data class LearnedCommandAlias(
    val phrase: String,
    val targetCommand: String
)

/**
 * Persistent local automation, routines, sensor-trigger watchdog, memory notes,
 * and self-learned alias store. Runs 100% locally in app internal storage.
 */
class AutomationEngine(context: Context) {

    private val storageFile = File(context.filesDir, "jarvis_automations_v1.json")

    private val routines = mutableListOf<JarvisRoutine>()
    private val triggerRules = mutableListOf<AutomationTriggerRule>()
    private val notes = mutableListOf<JarvisMemoryNote>()
    private val aliases = mutableListOf<LearnedCommandAlias>()

    private var previousTelemetry: DeviceTelemetry? = null

    init {
        loadState()
        if (routines.isEmpty()) {
            seedBuiltInRoutines()
            saveState()
        }
    }

    private fun seedBuiltInRoutines() {
        routines += listOf(
            JarvisRoutine(
                id = "preset_night",
                name = "modo noche",
                actions = listOf("apaga la linterna", "baja el brillo al 15%", "pon el telefono en vibracion"),
                isBuiltIn = true
            ),
            JarvisRoutine(
                id = "preset_cinema",
                name = "modo cine",
                actions = listOf("apaga la linterna", "baja el brillo al 20%", "pon el volumen al 15%"),
                isBuiltIn = true
            ),
            JarvisRoutine(
                id = "preset_powersave",
                name = "modo ahorro",
                actions = listOf("apaga la linterna", "baja el brillo al 20%", "pon el volumen al 25%"),
                isBuiltIn = true
            ),
            JarvisRoutine(
                id = "preset_work",
                name = "modo trabajo",
                actions = listOf("pon el brillo al 75%", "pon modo vibracion", "pon el volumen al 45%"),
                isBuiltIn = true
            ),
            JarvisRoutine(
                id = "preset_alert",
                name = "modo alerta",
                actions = listOf("enciende la linterna", "pon el volumen al 100%", "pon modo normal"),
                isBuiltIn = true
            )
        )
    }

    @Synchronized
    fun findRoutine(rawName: String): JarvisRoutine? {
        val norm = TextVectorizer.normalize(rawName)
            .removePrefix("la rutina ")
            .removePrefix("rutina ")
            .removePrefix("el ")
            .trim()
        return routines.firstOrNull { r ->
            val rNorm = TextVectorizer.normalize(r.name)
            rNorm == norm || norm.contains(rNorm) || rNorm.contains(norm)
        }
    }

    @Synchronized
    fun saveRoutine(name: String, actions: List<String>): JarvisRoutine {
        val cleanName = TextVectorizer.normalize(name)
        routines.removeAll { TextVectorizer.normalize(it.name) == cleanName }
        val routine = JarvisRoutine(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            actions = actions.map { it.trim() }.filter { it.isNotBlank() },
            isBuiltIn = false
        )
        routines.add(0, routine)
        saveState()
        return routine
    }

    @Synchronized
    fun saveTriggerRule(
        name: String,
        conditionType: TriggerConditionType,
        threshold: Int,
        actions: List<String>
    ): AutomationTriggerRule {
        val rule = AutomationTriggerRule(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            conditionType = conditionType,
            threshold = threshold,
            actions = actions.map { it.trim() }.filter { it.isNotBlank() },
            enabled = true
        )
        triggerRules.add(0, rule)
        saveState()
        return rule
    }

    @Synchronized
    fun deleteByName(rawQuery: String): String? {
        val norm = TextVectorizer.normalize(rawQuery)
        val routineMatch = routines.firstOrNull {
            !it.isBuiltIn && (TextVectorizer.normalize(it.name) == norm || norm.contains(TextVectorizer.normalize(it.name)))
        }
        if (routineMatch != null) {
            routines.remove(routineMatch)
            saveState()
            return "Rutina '${routineMatch.name}' eliminada."
        }
        val ruleMatch = triggerRules.firstOrNull {
            TextVectorizer.normalize(it.name).contains(norm) || norm.contains(TextVectorizer.normalize(it.name)) ||
                norm.contains("bateria") && (it.conditionType == TriggerConditionType.BATTERY_BELOW || it.conditionType == TriggerConditionType.BATTERY_ABOVE) ||
                norm.contains("wifi") && it.conditionType == TriggerConditionType.WIFI_CONNECTED ||
                norm.contains("cargador") && it.conditionType == TriggerConditionType.CHARGING_STARTED
        }
        if (ruleMatch != null) {
            triggerRules.remove(ruleMatch)
            saveState()
            return "Automatización '${ruleMatch.name}' eliminada."
        }
        return null
    }

    @Synchronized
    fun listSummary(): String {
        val rNames = routines.joinToString(", ") { it.name }
        val rulesPart = if (triggerRules.isEmpty()) {
            "Sin reglas de sensores activas."
        } else {
            "Reglas automáticas (${triggerRules.size}): " + triggerRules.joinToString("; ") { rule ->
                "${rule.name} -> ${rule.actions.joinToString(" y ")}"
            }
        }
        return "Rutinas disponibles: $rNames. $rulesPart"
    }

    @Synchronized
    fun addNote(content: String): JarvisMemoryNote {
        val ts = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        val note = JarvisMemoryNote(
            id = UUID.randomUUID().toString(),
            timestamp = ts,
            content = content.trim()
        )
        notes.add(0, note)
        saveState()
        return note
    }

    @Synchronized
    fun getNotesSummary(): String {
        if (notes.isEmpty()) return "No tienes notas guardadas en la memoria local."
        val top = notes.take(5).mapIndexed { idx, n -> "${idx + 1}. ${n.content} (${n.timestamp})" }
        return "Notas guardadas (${notes.size}): ${top.joinToString(" | ")}"
    }

    @Synchronized
    fun clearNotes(): Int {
        val count = notes.size
        notes.clear()
        saveState()
        return count
    }

    @Synchronized
    fun addLearnedAlias(phrase: String, targetCommand: String) {
        val cleanPhrase = TextVectorizer.normalize(phrase)
        aliases.removeAll { TextVectorizer.normalize(it.phrase) == cleanPhrase }
        aliases.add(0, LearnedCommandAlias(phrase = phrase.trim(), targetCommand = targetCommand.trim()))
        saveState()
    }

    @Synchronized
    fun resolveLearnedAlias(rawText: String): String? {
        val norm = TextVectorizer.normalize(rawText)
        return aliases.firstOrNull { TextVectorizer.normalize(it.phrase) == norm }?.targetCommand
    }

    @Synchronized
    fun getAllLearnedAliases(): List<LearnedCommandAlias> = aliases.toList()

    /**
     * Autonomous edge-triggered evaluator invoked periodically on telemetry updates.
     * Fires matching automation rules once per state transition (with a 2-minute cooldown).
     */
    @Synchronized
    fun evaluateTelemetryTriggers(current: DeviceTelemetry): List<AutomationTriggerRule> {
        val prev = previousTelemetry
        previousTelemetry = current
        if (prev == null || triggerRules.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val cooldownMs = 120_000L
        val triggered = mutableListOf<AutomationTriggerRule>()

        for (i in triggerRules.indices) {
            val rule = triggerRules[i]
            if (!rule.enabled) continue
            if (now - rule.lastTriggeredMs < cooldownMs) continue

            val shouldFire = when (rule.conditionType) {
                TriggerConditionType.BATTERY_BELOW ->
                    current.batteryPercent <= rule.threshold && prev.batteryPercent > rule.threshold
                TriggerConditionType.BATTERY_ABOVE ->
                    current.batteryPercent >= rule.threshold && prev.batteryPercent < rule.threshold
                TriggerConditionType.CHARGING_STARTED ->
                    current.isCharging && !prev.isCharging
                TriggerConditionType.WIFI_CONNECTED ->
                    current.networkType == "Wi-Fi" && prev.networkType != "Wi-Fi"
                TriggerConditionType.NETWORK_LOST ->
                    !current.isNetworkConnected && prev.isNetworkConnected
            }

            if (shouldFire) {
                val updated = rule.copy(lastTriggeredMs = now)
                triggerRules[i] = updated
                triggered += updated
            }
        }

        if (triggered.isNotEmpty()) {
            saveState()
        }
        return triggered
    }

    private fun saveState() {
        runCatching {
            val root = JSONObject()
            val rArr = JSONArray()
            for (r in routines) {
                rArr.put(JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("builtIn", r.isBuiltIn)
                    put("actions", JSONArray(r.actions))
                })
            }
            root.put("routines", rArr)

            val tArr = JSONArray()
            for (t in triggerRules) {
                tArr.put(JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("conditionType", t.conditionType.name)
                    put("threshold", t.threshold)
                    put("actions", JSONArray(t.actions))
                    put("enabled", t.enabled)
                    put("lastTriggeredMs", t.lastTriggeredMs)
                })
            }
            root.put("triggerRules", tArr)

            val nArr = JSONArray()
            for (n in notes) {
                nArr.put(JSONObject().apply {
                    put("id", n.id)
                    put("timestamp", n.timestamp)
                    put("content", n.content)
                })
            }
            root.put("notes", nArr)

            val aArr = JSONArray()
            for (a in aliases) {
                aArr.put(JSONObject().apply {
                    put("phrase", a.phrase)
                    put("target", a.targetCommand)
                })
            }
            root.put("aliases", aArr)

            storageFile.parentFile?.mkdirs()
            storageFile.writeText(root.toString())
        }
    }

    private fun loadState() {
        if (!storageFile.exists()) return
        runCatching {
            val root = JSONObject(storageFile.readText())

            routines.clear()
            val rArr = root.optJSONArray("routines") ?: JSONArray()
            for (i in 0 until rArr.length()) {
                val o = rArr.getJSONObject(i)
                val actArr = o.optJSONArray("actions") ?: JSONArray()
                val acts = (0 until actArr.length()).map { actArr.getString(it) }
                routines += JarvisRoutine(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    actions = acts,
                    isBuiltIn = o.optBoolean("builtIn", false)
                )
            }

            triggerRules.clear()
            val tArr = root.optJSONArray("triggerRules") ?: JSONArray()
            for (i in 0 until tArr.length()) {
                val o = tArr.getJSONObject(i)
                val actArr = o.optJSONArray("actions") ?: JSONArray()
                val acts = (0 until actArr.length()).map { actArr.getString(it) }
                val cond = runCatching {
                    TriggerConditionType.valueOf(o.optString("conditionType"))
                }.getOrDefault(TriggerConditionType.BATTERY_BELOW)
                triggerRules += AutomationTriggerRule(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    conditionType = cond,
                    threshold = o.optInt("threshold", 20),
                    actions = acts,
                    enabled = o.optBoolean("enabled", true),
                    lastTriggeredMs = o.optLong("lastTriggeredMs", 0L)
                )
            }

            notes.clear()
            val nArr = root.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until nArr.length()) {
                val o = nArr.getJSONObject(i)
                notes += JarvisMemoryNote(
                    id = o.optString("id"),
                    timestamp = o.optString("timestamp"),
                    content = o.optString("content")
                )
            }

            aliases.clear()
            val aArr = root.optJSONArray("aliases") ?: JSONArray()
            for (i in 0 until aArr.length()) {
                val o = aArr.getJSONObject(i)
                aliases += LearnedCommandAlias(
                    phrase = o.optString("phrase"),
                    targetCommand = o.optString("target")
                )
            }
        }
    }
}

package com.example.ai

import com.example.model.DeviceTelemetry
import com.example.model.JarvisAiDecision
import com.example.model.JarvisIntentType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Local autonomous reasoning, arithmetic/unit solver, and semantic vector knowledge base.
 * Operates 100% on-device with zero external cloud APIs.
 */
object LocalKnowledgeAndReasoning {

    private data class KnowledgeEntry(
        val topic: String,
        val keywords: String,
        val answer: String
    )

    private val knowledgeBase = listOf(
        KnowledgeEntry(
            topic = "IDENTIDAD",
            keywords = "quien eres jarvis inteligencia artificial red neuronal como funcionas creador",
            answer = "Soy J.A.R.V.I.S., tu asistente autónomo con red neuronal local integrada en el dispositivo. Analizo tus comandos y automatizaciones sin depender de servidores externos."
        ),
        KnowledgeEntry(
            topic = "CAPACIDADES",
            keywords = "que puedes hacer funciones comandos habilidades automatizar rutinas",
            answer = "Puedo ejecutar múltiples acciones en cadena, crear rutinas y reglas automáticas por batería o red, programar tareas con retraso, enviar WhatsApp, Telegram o SMS, llamar, navegar en web o mapas, guardar notas y aprender nuevos comandos por voz."
        ),
        KnowledgeEntry(
            topic = "AUTOMATIZACIÓN",
            keywords = "como automatizo crear rutina programar tarea cuando conecte bateria wifi",
            answer = "Puedes decir: 'Crea rutina modo noche que baje el brillo al 15% y ponga silencio', 'Automatiza si la batería baja del 20% activar modo ahorro', o 'En 5 minutos apaga la linterna'."
        ),
        KnowledgeEntry(
            topic = "APRENDIZAJE",
            keywords = "como te enseno aprender frase entrenar red neuronal nuevo comando",
            answer = "Solo dime: 'Cuando diga a dormir activa modo noche' o 'Aprende que luz significa enciende la linterna', y ajustaré mi red neuronal local de inmediato."
        ),
        KnowledgeEntry(
            topic = "GRAVEDAD",
            keywords = "que es la gravedad fisica newton einstein atraccion masa",
            answer = "La gravedad es la interacción fundamental que atrae entre sí a los objetos con masa o energía; en la relatividad general se describe como la curvatura del espacio-tiempo."
        ),
        KnowledgeEntry(
            topic = "IA",
            keywords = "que es una red neuronal inteligencia artificial backpropagation perceptron aprendizaje",
            answer = "Una red neuronal artificial procesa patrones mediante capas de neuronas ponderadas entrenadas por retropropagación. La mía funciona localmente en este Android para clasificar tus intenciones al instante."
        ),
        KnowledgeEntry(
            topic = "SISTEMA SOLAR",
            keywords = "planetas sistema solar sol tierra marte luna espacio universo",
            answer = "El sistema solar orbita alrededor del Sol con ocho planetas: Mercurio, Venus, Tierra, Marte, Júpiter, Saturno, Urano y Neptuno."
        ),
        KnowledgeEntry(
            topic = "VELOCIDAD LUZ",
            keywords = "velocidad de la luz cuanto mide km por segundo fisica",
            answer = "La velocidad de la luz en el vacío es exactamente 299,792,458 metros por segundo, aproximadamente 300,000 kilómetros por segundo."
        ),
        KnowledgeEntry(
            topic = "ANDROID",
            keywords = "que es android sistema operativo asistente predeterminado",
            answer = "Android es un sistema operativo móvil basado en Linux. Puedes configurarme como asistente predeterminado para activarme desde el botón o gesto de sistema."
        ),
        KnowledgeEntry(
            topic = "CHISTE",
            keywords = "cuentame un chiste dime algo gracioso humor broma",
            answer = "¿Qué le dice un bit a otro bit? Nos vemos en el bus de datos, señor."
        )
    )

    private val embeddedKnowledge: List<Pair<KnowledgeEntry, FloatArray>> by lazy {
        knowledgeBase.map { entry ->
            entry to TextVectorizer.vectorize("${entry.topic} ${entry.keywords}")
        }
    }

    fun tryExactLocalQuery(rawText: String, telemetry: DeviceTelemetry): JarvisAiDecision? {
        val norm = TextVectorizer.normalize(rawText)

        // 1. Time & Date
        if (containsAny(norm, "que hora es", "dime la hora", "hora actual")) {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "Son las $timeStr, señor.",
                actionSummary = "HORA $timeStr"
            )
        }
        if (containsAny(norm, "que dia es", "fecha de hoy", "que fecha es", "en que dia estamos")) {
            val dateStr = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "ES")).format(Date())
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "Hoy es $dateStr, señor.",
                actionSummary = "FECHA LOCAL"
            )
        }

        // 2. Greetings & Courtesy
        if (containsAny(norm, "hola", "buenos dias", "buenas tardes", "buenas noches", "hey jarvis", "despierta")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "A sus órdenes, señor. Red neuronal local en línea y lista para automatizar tareas.",
                actionSummary = "NEURAL ACTIVA"
            )
        }
        if (containsAny(norm, "gracias", "muy bien", "perfecto", "excelente", "genial")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "Siempre a su servicio, señor.",
                actionSummary = "ASISTENCIA OK"
            )
        }

        // 3. Unit conversion
        convertUnits(norm)?.let { (summary, answer) ->
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = answer,
                actionSummary = summary
            )
        }

        // 4. Math / Percentages / Roots
        evaluateMath(norm)?.let { resultStr ->
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "El resultado es $resultStr.",
                actionSummary = "CÁLCULO = $resultStr"
            )
        }

        // 5. Telemetry summary if general status mentioned
        if (containsAny(norm, "como estas", "estado", "reporte", "todo bien")) {
            val charge = if (telemetry.isCharging) "cargando" else "en descarga"
            return JarvisAiDecision(
                intent = JarvisIntentType.QUERY_DEVICE_STATUS,
                spokenResponse = "Sistemas autónomos estables, señor. Batería al ${telemetry.batteryPercent}% ($charge), ${telemetry.availableRamMb} MB de RAM libres y red ${telemetry.networkType}.",
                actionSummary = "SISTEMA ${telemetry.batteryPercent}%",
                metric = "all"
            )
        }

        return null
    }

    fun answerLocally(rawText: String, telemetry: DeviceTelemetry): JarvisAiDecision {
        tryExactLocalQuery(rawText, telemetry)?.let { return it }
        val norm = TextVectorizer.normalize(rawText)

        // 6. Semantic cosine-similarity search over local vector knowledge base
        val queryVec = TextVectorizer.vectorize(rawText)
        var bestEntry: KnowledgeEntry? = null
        var bestScore = 0f
        for ((entry, vec) in embeddedKnowledge) {
            val sim = TextVectorizer.cosineSimilarity(queryVec, vec)
            if (sim > bestScore) {
                bestScore = sim
                bestEntry = entry
            }
        }
        if (bestEntry != null && bestScore >= 0.24f) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = bestEntry.answer,
                actionSummary = "IA LOCAL // ${bestEntry.topic}",
                neuralConfidence = bestScore
            )
        }

        // 7. Autonomous web-search bridge if it looks like an open factual question and online
        if (telemetry.isNetworkConnected &&
            (rawText.contains("?") || containsAny(norm, "que es ", "quien es ", "quien fue ", "como funciona ", "cuando fue ", "donde esta "))
        ) {
            return JarvisAiDecision(
                intent = JarvisIntentType.WEB_BROWSE,
                spokenResponse = "No tengo ese dato en mi memoria local; abriendo búsqueda web sobre tu consulta.",
                actionSummary = "BÚSQUEDA AUTO",
                query = rawText.trim()
            )
        }

        return JarvisAiDecision(
            intent = JarvisIntentType.CONVERSATION,
            spokenResponse = "Procesado localmente. Puedes pedirme acciones del sistema, crear rutinas automáticas, enviar mensajes, calcular o enseñarme un nuevo comando diciendo 'cuando diga...'.",
            actionSummary = "IA LOCAL"
        )
    }

    private fun evaluateMath(normalized: String): String? {
        // Percentage: "15% de 800" or "15 por ciento de 800"
        val pctMatch = Regex("""(-?\d+(?:\.\d+)?)\s*(?:%|por ciento)\s+de\s+(-?\d+(?:\.\d+)?)""")
            .find(normalized)
        if (pctMatch != null) {
            val pct = pctMatch.groupValues[1].toDoubleOrNull() ?: return null
            val base = pctMatch.groupValues[2].toDoubleOrNull() ?: return null
            return formatNumber((pct / 100.0) * base)
        }

        // Square root: "raiz cuadrada de 144"
        val sqrtMatch = Regex("""raiz(?:\s+cuadrada)?\s+de\s+(\d+(?:\.\d+)?)""").find(normalized)
        if (sqrtMatch != null) {
            val n = sqrtMatch.groupValues[1].toDoubleOrNull() ?: return null
            if (n >= 0) return formatNumber(sqrt(n))
        }

        // Power: "2 elevado a 8" / "5 al cuadrado"
        val sqMatch = Regex("""(-?\d+(?:\.\d+)?)\s+al\s+cuadrado""").find(normalized)
        if (sqMatch != null) {
            val n = sqMatch.groupValues[1].toDoubleOrNull() ?: return null
            return formatNumber(n * n)
        }
        val powMatch = Regex("""(-?\d+(?:\.\d+)?)\s+(?:elevado\s+a|a\s+la|\^)\s+(-?\d+(?:\.\d+)?)""").find(normalized)
        if (powMatch != null) {
            val a = powMatch.groupValues[1].toDoubleOrNull() ?: return null
            val b = powMatch.groupValues[2].toDoubleOrNull() ?: return null
            return formatNumber(a.pow(b))
        }

        // Basic arithmetic
        val cleaned = normalized
            .replace(" multiplicado por ", " * ")
            .replace(" por ", " * ")
            .replace(" x ", " * ")
            .replace(" dividido entre ", " / ")
            .replace(" dividido por ", " / ")
            .replace(" entre ", " / ")
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
        return formatNumber(res)
    }

    private fun convertUnits(norm: String): Pair<String, String>? {
        val m = Regex("""(\d+(?:\.\d+)?)\s*(km|kilometros?|mi|millas?|m|metros?|cm|kg|kilogramos?|lb|libras?|c|celsius|grados?|f|fahrenheit|gb|gigabytes?|mb|megabytes?)\s+(?:a|en)\s+(km|kilometros?|mi|millas?|m|metros?|cm|kg|kilogramos?|lb|libras?|c|celsius|f|fahrenheit|gb|gigabytes?|mb|megabytes?)""")
            .find(norm) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        val from = canonicalUnit(m.groupValues[2])
        val to = canonicalUnit(m.groupValues[3])
        val converted: Double? = when (from to to) {
            "km" to "mi" -> value * 0.621371
            "mi" to "km" -> value * 1.60934
            "m" to "cm" -> value * 100.0
            "cm" to "m" -> value / 100.0
            "km" to "m" -> value * 1000.0
            "m" to "km" -> value / 1000.0
            "kg" to "lb" -> value * 2.20462
            "lb" to "kg" -> value * 0.453592
            "c" to "f" -> value * 9.0 / 5.0 + 32.0
            "f" to "c" -> (value - 32.0) * 5.0 / 9.0
            "gb" to "mb" -> value * 1024.0
            "mb" to "gb" -> value / 1024.0
            else -> null
        }
        if (converted == null) return null
        val formatted = formatNumber(converted)
        return "CONV // ${formatted} ${to.uppercase()}" to "$value $from equivalen a $formatted $to."
    }

    private fun canonicalUnit(u: String): String = when {
        u.startsWith("km") || u.startsWith("kilometr") -> "km"
        u.startsWith("mi") -> "mi"
        u == "m" || u.startsWith("metro") -> "m"
        u == "cm" -> "cm"
        u.startsWith("kg") || u.startsWith("kilogram") -> "kg"
        u.startsWith("lb") || u.startsWith("libra") -> "lb"
        u == "c" || u.startsWith("cels") || u.startsWith("grado") -> "c"
        u == "f" || u.startsWith("fahr") -> "f"
        u.startsWith("gb") || u.startsWith("giga") -> "gb"
        u.startsWith("mb") || u.startsWith("mega") -> "mb"
        else -> u
    }

    private fun formatNumber(v: Double): String {
        return if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v)
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }
}

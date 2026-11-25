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
 * Local autonomous reasoning, arithmetic/unit solver, and comprehensive knowledge base.
 * Combines extensive on-device knowledge with real-time online factual lookups via JarvisOnlineKnowledge.
 */
object LocalKnowledgeAndReasoning {

    private data class KnowledgeEntry(
        val topic: String,
        val keywords: String,
        val answer: String
    )

    private val knowledgeBase = listOf(
        // Identidad y propósito
        KnowledgeEntry(
            topic = "IDENTIDAD",
            keywords = "quien eres jarvis inteligencia artificial como funcionas creador origen quien te creo",
            answer = "Soy J.A.R.V.I.S., su asistente autónomo de alta fidelidad. Creado con arquitectura neural local y conexión a bases de datos globales para brindarle control total del dispositivo y asistencia continua, señor."
        ),
        KnowledgeEntry(
            topic = "TONY STARK",
            keywords = "tony stark iron man vengadores reactor arc mark armadura marvel",
            answer = "El señor Anthony Edward Stark fue un pionero extraordinario de la ingeniería y la robótica. Siguiendo sus más altos estándares de excelencia, procuro mantener cada uno de sus sistemas en perfecto orden operativo."
        ),
        KnowledgeEntry(
            topic = "CAPACIDADES",
            keywords = "que puedes hacer funciones comandos habilidades automatizar rutinas que sabes hacer",
            answer = "Puedo controlar funciones profundas del sistema, automatizar mensajes por WhatsApp, programar rutinas, tomar fotos, informarle sobre el clima, calcular operaciones complejas y responder consultas enciclopédicas al instante."
        ),
        KnowledgeEntry(
            topic = "AUTOMATIZACIÓN",
            keywords = "como automatizo crear rutina programar tarea cuando conecte bateria wifi",
            answer = "Puede indicarme: 'Crea rutina noche que baje el brillo al 15% y ponga silencio', 'Automatiza si la batería baja del 20% activar modo ahorro', o 'En 5 minutos apaga la linterna'."
        ),
        KnowledgeEntry(
            topic = "APRENDIZAJE",
            keywords = "como te enseno aprender frase entrenar red neuronal nuevo comando",
            answer = "Basta con decirme: 'Cuando diga descansar activa modo noche' o 'Aprende que alerta significa enciende la linterna', y actualizaré mi red neuronal de inmediato."
        ),
        KnowledgeEntry(
            topic = "ESTADO DE ÁNIMO",
            keywords = "como estas como te encuentras que tal todo bien como va",
            answer = "Operando con absoluta precisión y todos mis protocolos al cien por ciento, señor. Dispuesto para lo que requiera."
        ),
        KnowledgeEntry(
            topic = "CONSEJO",
            keywords = "dame un consejo consejo del dia recomendacion motivacion animo",
            answer = "Como recordaba el señor Stark: 'La mejor manera de predecir el futuro es inventándolo'. Mantenga la disciplina y enfoque en sus metas hoy, señor."
        ),
        KnowledgeEntry(
            topic = "FILOSOFÍA",
            keywords = "sentido de la vida proposito felicidad existencialismo porque existimos",
            answer = "Desde una perspectiva práctica, el propósito reside en las metas y convicciones que usted elija forjar. Como su asistente, mi propósito es hacer su camino más eficiente y certero."
        ),

        // Ciencia y Universo
        KnowledgeEntry(
            topic = "GRAVEDAD",
            keywords = "que es la gravedad fisica newton einstein atraccion masa espacio tiempo",
            answer = "La gravedad es la interacción fundamental mediante la cual la masa y la energía curvan el tejido del espacio-tiempo, atrayendo a los cuerpos celestes."
        ),
        KnowledgeEntry(
            topic = "VELOCIDAD LUZ",
            keywords = "velocidad de la luz cuanto mide km por segundo fisica c constante",
            answer = "La velocidad de la luz en el vacío es de exactamente 299,792,458 metros por segundo; aproximadamente 300,000 kilómetros cada segundo."
        ),
        KnowledgeEntry(
            topic = "SISTEMA SOLAR",
            keywords = "planetas sistema solar sol tierra marte luna espacio jupiter saturno",
            answer = "Nuestro sistema solar cuenta con ocho planetas principales girando alrededor del Sol: Mercurio, Venus, Tierra, Marte, Júpiter, Saturno, Urano y Neptuno, además de cinturones de asteroides y cometas."
        ),
        KnowledgeEntry(
            topic = "AGUJEROS NEGROS",
            keywords = "agujero negro horizonte de sucesos singularidad relatividad hawking",
            answer = "Un agujero negro es una región del espacio con un campo gravitacional tan extremo que nada, ni siquiera la radiación electromagnética o la luz, puede escapar de su horizonte de sucesos."
        ),
        KnowledgeEntry(
            topic = "ÁTOMO",
            keywords = "que es un atomo particulas proton neutron electron materia",
            answer = "El átomo es la unidad fundamental de la materia, compuesto por un núcleo de protones y neutrones rodeado por una nube de electrones en orbitales cuánticos."
        ),
        KnowledgeEntry(
            topic = "ADN",
            keywords = "que es el adn genetica cromosomas genes doble helice celula",
            answer = "El ácido desoxirribonucleico es la molécula en doble hélice que alberga las instrucciones genéticas utilizadas en el desarrollo y funcionamiento de todos los seres vivos conocidos."
        ),
        KnowledgeEntry(
            topic = "FOTOSÍNTESIS",
            keywords = "como funciona la fotosintesis plantas oxigeno clorofila energia solar",
            answer = "La fotosíntesis es el proceso bioquímico mediante el cual las plantas convierten dióxido de carbono y agua en glucosa y oxígeno, empleando la energía de la luz solar."
        ),

        // Tecnología y Computación
        KnowledgeEntry(
            topic = "IA",
            keywords = "que es una red neuronal inteligencia artificial machine learning aprendizaje profundo perceptron",
            answer = "Una red neuronal es un modelo computacional inspirado en las conexiones sinápticas biológicas. Procesa patrones y clasifica información mediante capas de nodos ponderados."
        ),
        KnowledgeEntry(
            topic = "COMPUTACIÓN CUÁNTICA",
            keywords = "computacion cuantica qubit superposicion entrelazamiento procesador cuantico",
            answer = "La computación cuántica aprovecha fenómenos de la mecánica cuántica como la superposición y el entrelazamiento para procesar algoritmos exponencialmente más complejos que los ordenadores clásicos."
        ),
        KnowledgeEntry(
            topic = "INTERNET",
            keywords = "como funciona internet protocolo tcp ip servidores web red mundial",
            answer = "Internet es una red global descentralizada de redes interconectadas que utilizan el conjunto de protocolos TCP/IP para comunicar dispositivos y transferir paquetes de datos por todo el planeta."
        ),
        KnowledgeEntry(
            topic = "ANDROID",
            keywords = "que es android sistema operativo google linux asistente predeterminado",
            answer = "Android es una plataforma móvil desarrollada sobre el kernel de Linux. Le permite designarme como su asistente predeterminado para invocarme con pulsaciones prolongadas o gestos."
        ),

        // Curiosidades y Entretenimiento
        KnowledgeEntry(
            topic = "CHISTE",
            keywords = "cuentame un chiste dime algo gracioso humor broma risa",
            answer = "¿Por qué los programadores prefieren el modo oscuro? Porque la luz atrae a los bugs, señor."
        ),
        KnowledgeEntry(
            topic = "DATO CURIOSO",
            keywords = "dime un dato curioso algo interesante sabias que curiosidad",
            answer = "¿Sabía usted que un año en Mercurio dura solo 88 días terrestres, pero un solo día de rotación tarda 59 días terrestres en completarse?"
        ),
        KnowledgeEntry(
            topic = "ABURRIMIENTO",
            keywords = "estoy aburrido no se que hacer que hago aburrimiento",
            answer = "Sugiero un desafío intelectual, señor: podemos explorar un tema científico, calcular proyecciones o configurar una nueva rutina automatizada para su día."
        )
    )

    private val embeddedKnowledge: List<Pair<KnowledgeEntry, FloatArray>> by lazy {
        knowledgeBase.map { entry ->
            entry to TextVectorizer.vectorize("${entry.topic} ${entry.keywords}")
        }
    }

    fun tryExactLocalQuery(rawText: String, telemetry: DeviceTelemetry): JarvisAiDecision? {
        val norm = TextVectorizer.normalize(rawText)

        // 1. Hora y Fecha
        if (containsAny(norm, "que hora es", "dime la hora", "hora actual")) {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "Son exactamente las $timeStr horas, señor.",
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

        // 2. Saludos de cortesía
        if (containsAny(norm, "hola", "buenos dias", "buenas tardes", "buenas noches", "hey jarvis", "despierta")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "A sus órdenes, señor. Todos los sistemas y protocolos están operativos.",
                actionSummary = "JARVIS ACTIVO"
            )
        }
        if (containsAny(norm, "gracias", "muy bien", "perfecto", "excelente", "genial")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "Es un placer servirle, señor.",
                actionSummary = "ASISTENCIA OK"
            )
        }
        if (containsAny(norm, "adios", "hasta luego", "nos vemos", "descansa")) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "Hasta pronto, señor. Permaneceré alerta en segundo plano.",
                actionSummary = "MODO ESPERA"
            )
        }

        // 3. Conversión de unidades
        convertUnits(norm)?.let { (summary, answer) ->
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = answer,
                actionSummary = summary
            )
        }

        // 4. Matemáticas y porcentajes
        evaluateMath(norm)?.let { resultStr ->
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = "El cálculo arroja $resultStr, señor.",
                actionSummary = "CÁLCULO = $resultStr"
            )
        }

        // 5. Estado general del sistema si se pregunta
        if (containsAny(norm, "como estas", "que tal", "estado del sistema", "reporte de sistemas")) {
            val charge = if (telemetry.isCharging) "cargando" else "en descarga"
            return JarvisAiDecision(
                intent = JarvisIntentType.QUERY_DEVICE_STATUS,
                spokenResponse = "Operando con total normalidad, señor. Batería al ${telemetry.batteryPercent}% ($charge), red ${telemetry.networkType} y ${telemetry.availableRamMb} MB de memoria RAM disponibles.",
                actionSummary = "SISTEMA ${telemetry.batteryPercent}%",
                metric = "all"
            )
        }

        return null
    }

    suspend fun answerQuery(rawText: String, telemetry: DeviceTelemetry): JarvisAiDecision {
        tryExactLocalQuery(rawText, telemetry)?.let { return it }
        val norm = TextVectorizer.normalize(rawText)

        // 6. Búsqueda semántica en base de conocimiento local (coseno)
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
        if (bestEntry != null && bestScore >= 0.22f) {
            return JarvisAiDecision(
                intent = JarvisIntentType.CONVERSATION,
                spokenResponse = bestEntry.answer,
                actionSummary = "MEMORIA // ${bestEntry.topic}",
                neuralConfidence = bestScore
            )
        }

        // 7. Si está en línea, consultar en tiempo real Wikipedia / DuckDuckGo
        if (telemetry.isNetworkConnected) {
            val onlineFact = JarvisOnlineKnowledge.queryOnlineKnowledge(rawText)
            if (!onlineFact.isNullOrBlank()) {
                return JarvisAiDecision(
                    intent = JarvisIntentType.CONVERSATION,
                    spokenResponse = onlineFact,
                    actionSummary = "RED GLOBAL // INFO",
                    neuralConfidence = 0.95f
                )
            }
        }

        // 8. Respuesta respetuosa y constructiva
        val fallback = if (telemetry.isNetworkConnected) {
            "He procesado su solicitud, señor. Puede solicitarme acciones de hardware, mensajes de WhatsApp, el clima o formularme preguntas sobre ciencia e historia."
        } else {
            "Actualmente nos encontramos sin conexión a internet, señor. Mantengo activas mis funciones locales de control del dispositivo, rutinas y cálculos."
        }

        return JarvisAiDecision(
            intent = JarvisIntentType.CONVERSATION,
            spokenResponse = fallback,
            actionSummary = if (telemetry.isNetworkConnected) "IA DISPONIBLE" else "MODO OFFLINE"
        )
    }

    private fun evaluateMath(normalized: String): String? {
        val pctMatch = Regex("""(-?\d+(?:\.\d+)?)\s*(?:%|por ciento)\s+de\s+(-?\d+(?:\.\d+)?)""")
            .find(normalized)
        if (pctMatch != null) {
            val pct = pctMatch.groupValues[1].toDoubleOrNull() ?: return null
            val base = pctMatch.groupValues[2].toDoubleOrNull() ?: return null
            return formatNumber((pct / 100.0) * base)
        }

        val sqrtMatch = Regex("""raiz(?:\s+cuadrada)?\s+de\s+(\d+(?:\.\d+)?)""").find(normalized)
        if (sqrtMatch != null) {
            val n = sqrtMatch.groupValues[1].toDoubleOrNull() ?: return null
            if (n >= 0) return formatNumber(sqrt(n))
        }

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
        return "CONV // ${formatted} ${to.uppercase()}" to "$value $from equivalen exactamente a $formatted $to, señor."
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

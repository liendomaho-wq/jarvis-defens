package com.example.ai

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Pure-Kotlin NLP tokenizer and feature vectorizer built from scratch.
 * Uses normalized unigrams, light Spanish/English stemming, bigrams, character 3-grams,
 * and structural cues hashed into an L2-normalized dense vector for the local neural network.
 */
object TextVectorizer {

    const val VECTOR_DIM = 256
    private const val STRUCTURAL_RESERVE = 16
    private const val HASH_BUCKET_SIZE = VECTOR_DIM - STRUCTURAL_RESERVE

    private val STOP_WORDS = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del",
        "por", "favor", "jarvis", "oye", "hola", "quiero", "puedes", "podrias",
        "hazme", "me", "te", "se", "lo", "le", "les", "mi", "tu", "su", "este",
        "esta", "ese", "esa", "al", "en", "con", "sin", "sobre", "the", "a", "an",
        "please", "to", "for", "of"
    )

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun tokenize(normalized: String): List<String> {
        return normalized
            .replace(Regex("[^a-z0-9@.:/+\\-*% ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    fun lightStem(word: String): String {
        if (word.length <= 4) return word
        val suffixes = listOf("amiento", "imiento", "iendo", "ando", "acion", "mente", "ar", "er", "ir", "es", "s")
        for (suffix in suffixes) {
            if (word.length - suffix.length >= 3 && word.endsWith(suffix)) {
                return word.removeSuffix(suffix)
            }
        }
        return word
    }

    fun vectorize(rawText: String): FloatArray {
        val vec = FloatArray(VECTOR_DIM)
        val norm = normalize(rawText)
        if (norm.isBlank()) return vec

        val allTokens = tokenize(norm)
        val contentTokens = allTokens.filter { it !in STOP_WORDS && it.length >= 2 }
        val activeTokens = if (contentTokens.isNotEmpty()) contentTokens else allTokens

        val counts = HashMap<Int, Float>()

        fun addFeature(key: String, weight: Float) {
            val hash = fnv1aHash(key)
            val bucket = ((hash ushr 1) % HASH_BUCKET_SIZE).toInt()
            val sign = if ((hash and 1L) == 0L) 1f else -1f
            counts[bucket] = (counts[bucket] ?: 0f) + sign * weight
        }

        // 1. Word unigrams & stems
        for (token in activeTokens) {
            addFeature("w:$token", 1.8f)
            val stem = lightStem(token)
            if (stem != token) {
                addFeature("s:$stem", 1.4f)
            }
        }

        // 2. Word bigrams
        for (i in 0 until activeTokens.size - 1) {
            addFeature("bg:${activeTokens[i]}_${activeTokens[i + 1]}", 2.0f)
        }

        // 3. Character 3-grams (resilient to typos and conjugations)
        for (token in activeTokens) {
            if (token.length < 3) continue
            val padded = "#$token#"
            for (i in 0..padded.length - 3) {
                addFeature("c3:${padded.substring(i, i + 3)}", 0.55f)
            }
        }

        // Sublinear TF scaling into hashed slice
        for ((idx, v) in counts) {
            val mag = kotlin.math.abs(v)
            val scaled = if (mag > 0f) (1f + ln(1f + mag)) * if (v >= 0f) 1f else -1f else 0f
            vec[idx] = scaled
        }

        // 4. Structural semantic cues in reserved tail [HASH_BUCKET_SIZE .. VECTOR_DIM - 1]
        val sBase = HASH_BUCKET_SIZE
        if (norm.contains("%") || norm.contains("por ciento") || norm.contains("porciento")) {
            vec[sBase + 0] = 1.6f
        }
        if (Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(norm)) {
            vec[sBase + 1] = 1.6f
        }
        if (Regex("""\b\d+\b""").containsMatchIn(norm)) {
            vec[sBase + 2] = 1.2f
        }
        if (Regex("""[+\-*/=]|\b(mas|menos|por|entre|dividido|raiz|convierte|a millas|a km)\b""").containsMatchIn(norm)) {
            vec[sBase + 3] = 1.5f
        }
        if (Regex("""\bhttps?://|\b[a-z0-9\-]+\.(com|org|net|es|io|mx|ar|co)\b""").containsMatchIn(norm)) {
            vec[sBase + 4] = 1.8f
        }
        if (norm.contains("@")) {
            vec[sBase + 5] = 1.5f
        }
        if (rawText.contains("?") || norm.startsWith("que ") || norm.startsWith("como ") ||
            norm.startsWith("quien ") || norm.startsWith("por que") || norm.startsWith("cual ") ||
            norm.startsWith("cuanto")
        ) {
            vec[sBase + 6] = 1.3f
        }
        if (norm.contains("whatsapp") || norm.contains("whasaap") || norm.contains("wasap") ||
            norm.contains("telegram") || norm.contains("sms")
        ) {
            vec[sBase + 7] = 2.0f
        }
        if (norm.contains("rutina") || norm.contains("automatiza") || norm.contains("cuando ") ||
            norm.contains("si la bateria") || norm.contains("cada vez que")
        ) {
            vec[sBase + 8] = 2.0f
        }
        if (Regex("""\ben \d+\s*(segundo|segundos|seg|minuto|minutos|min|hora|horas)\b""").containsMatchIn(norm)) {
            vec[sBase + 9] = 2.0f
        }
        if (norm.contains("aprende") || norm.contains("cuando diga") || norm.contains("enseña") || norm.contains("ensena")) {
            vec[sBase + 10] = 2.0f
        }
        if (norm.contains("nota") || norm.contains("recuerda que") || norm.contains("apunta")) {
            vec[sBase + 11] = 1.8f
        }
        if (norm.contains("asistente predeterminado") || norm.contains("asistente por defecto")) {
            vec[sBase + 12] = 2.0f
        }

        // L2 normalize
        var sumSq = 0f
        for (x in vec) sumSq += x * x
        val normLen = sqrt(sumSq)
        if (normLen > 1e-6f) {
            for (i in vec.indices) {
                vec[i] /= normLen
            }
        }
        return vec
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
        }
        return dot
    }

    private fun fnv1aHash(text: String): Long {
        var hash = -3750763034362895579L
        for (i in text.indices) {
            hash = hash xor text[i].code.toLong()
            hash *= 1099511628211L
        }
        return hash
    }
}

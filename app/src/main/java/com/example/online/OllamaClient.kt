package com.example.online

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** El texto del modelo es solo una respuesta: nunca se interpreta como una orden. */
object OllamaClient {
    fun validEndpoint(endpoint: String): Boolean = try {
        val url = URL(endpoint.trim())
        url.protocol in listOf("https", "http") && !url.host.isNullOrBlank() &&
            url.userInfo == null && url.query == null && url.ref == null
    } catch (_: Exception) { false }

    fun ask(endpoint: String, model: String, question: String): String {
        require(validEndpoint(endpoint) && model.isNotBlank()) { "Configuración de Ollama no válida." }
        val connection = URL(endpoint.trim().trimEnd('/') + "/api/chat").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 120_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = JSONObject().put("model", model.trim()).put("stream", false)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "Eres Jarvis, un asistente útil. Responde en español. No afirmes haber ejecutado acciones ni consultado internet cuando no sea cierto. Si no sabes algo, dilo."))
                    .put(JSONObject().put("role", "user").put("content", question.take(4000))))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw IllegalStateException("Ollama respondió HTTP ${connection.responseCode}")
            val response = connection.inputStream.bufferedReader().use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(2048)
                while (out.length < 65536) {
                    val n = reader.read(buffer, 0, minOf(buffer.size, 65536 - out.length))
                    if (n == -1) break
                    out.append(buffer, 0, n)
                }
                out.toString()
            }
            val text = JSONObject(response).getJSONObject("message").getString("content").trim()
            require(text.isNotBlank()) { "Ollama devolvió una respuesta vacía." }
            return text.take(4000)
        } finally { connection.disconnect() }
    }
}

package com.example.online

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Consulta explícita a Wikipedia, sin ejecutar instrucciones del contenido recuperado. */
object OnlineKnowledge {
    data class Article(val title: String, val extract: String, val url: String)

    fun researchQuery(text: String): String? {
        val match = Regex("""^\s*(?:jarvis[,\s]+)?(?:investiga en internet|busca en wikipedia|consulta en wikipedia)\s+(.+)""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.trim()?.take(200)?.takeIf { it.isNotEmpty() }
    }

    fun search(query: String, request: (String) -> JSONObject = ::getJson): Article? {
        val encoded = URLEncoder.encode(query.take(200), "UTF-8")
        val url = "https://es.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=1&prop=extracts&exintro=1&explaintext=1&formatversion=2&format=json"
        val page = request(url).optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0) ?: return null
        val id = page.optLong("pageid", -1)
        val title = page.optString("title", "")
        val extract = page.optString("extract", "").take(5000)
        if (id <= 0 || title.isBlank() || extract.isBlank()) return null
        return Article(title, extract, "https://es.wikipedia.org/?curid=$id")
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "JarvisAndroid/1.0 (Wikipedia research)")
            check(connection.responseCode in 200..299) { "Wikipedia no disponible" }
            val text = connection.inputStream.bufferedReader().use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(2048)
                while (output.length < 65536) {
                    val n = reader.read(buffer, 0, minOf(buffer.size, 65536 - output.length))
                    if (n == -1) break
                    output.append(buffer, 0, n)
                }
                output.toString()
            }
            return JSONObject(text)
        } finally { connection.disconnect() }
    }
}

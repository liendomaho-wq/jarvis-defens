package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Live internet factual knowledge engine for J.A.R.V.I.S.
 * Queries Wikipedia REST API and DuckDuckGo Instant Answer API in real-time on Dispatchers.IO.
 * Returns refined, dignified butler-style responses without kicking the user out of the app.
 */
object JarvisOnlineKnowledge {

    private const val TAG = "JarvisOnlineKnowledge"

    suspend fun queryOnlineKnowledge(rawQuery: String): String? = withContext(Dispatchers.IO) {
        val cleanQuery = cleanQueryText(rawQuery)
        if (cleanQuery.isBlank()) return@withContext null

        // 1. Try Wikipedia Summary API (Spanish)
        tryWikipediaSummary(cleanQuery)?.let { return@withContext it }

        // 2. Try DuckDuckGo Instant Answer API
        tryDuckDuckGoAnswer(cleanQuery)?.let { return@withContext it }

        // 3. Try Wikipedia Search as fallback
        tryWikipediaSearch(cleanQuery)?.let { return@withContext it }

        null
    }

    private fun cleanQueryText(text: String): String {
        return text
            .replace(Regex("""^(?:dime|explica|que es|quien es|quien fue|donde esta|como funciona|sabes que es|cuentame de|investiga sobre|busca sobre|que sabes de|sabes algo de)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[¿?.,;:!]"""), "")
            .trim()
    }

    private fun tryWikipediaSummary(title: String): String? {
        return try {
            val encoded = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
            val url = URL("https://es.wikipedia.org/api/rest_v1/page/summary/$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "JarvisAssistant/2.0 (Android; Extension)")
            }

            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                conn.disconnect()
                val root = JSONObject(json)
                val extract = root.optString("extract", "")
                if (extract.isNotBlank() && extract.length > 20) {
                    formatButlerAnswer(extract)
                } else null
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia summary failed for $title", e)
            null
        }
    }

    private fun tryWikipediaSearch(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=1&format=json&srlimit=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "JarvisAssistant/2.0 (Android; Extension)")
            }

            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                conn.disconnect()
                val root = JSONObject(json)
                val searchArr = root.optJSONObject("query")?.optJSONArray("search")
                if (searchArr != null && searchArr.length() > 0) {
                    val first = searchArr.getJSONObject(0)
                    val title = first.optString("title", "")
                    val snippet = first.optString("snippet", "")
                        .replace(Regex("""<[^>]*>"""), "") // strip HTML tags
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                    if (title.isNotBlank()) {
                        // Query the summary of this found title
                        val summary = tryWikipediaSummary(title)
                        if (summary != null) return summary
                    }
                    if (snippet.isNotBlank() && snippet.length > 30) {
                        return formatButlerAnswer(snippet)
                    }
                }
            } else {
                conn.disconnect()
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia search query failed for $query", e)
            null
        }
    }

    private fun tryDuckDuckGoAnswer(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "JarvisAssistant/2.0 (Android; Extension)")
            }

            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                conn.disconnect()
                val root = JSONObject(json)
                val abstractText = root.optString("AbstractText", "")
                if (abstractText.isNotBlank()) {
                    formatButlerAnswer(abstractText)
                } else null
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "DuckDuckGo answer query failed for $query", e)
            null
        }
    }

    private fun formatButlerAnswer(rawExtract: String): String {
        // Limit to 2-3 clean sentences
        val sentences = rawExtract.split(Regex("""(?<=[.!?])\s+"""))
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(" ")
            .trim()

        return "De acuerdo con los registros en línea, señor: $sentences"
    }
}

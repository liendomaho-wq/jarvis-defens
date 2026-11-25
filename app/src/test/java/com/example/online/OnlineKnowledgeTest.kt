package com.example.online

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlineKnowledgeTest {
    @Test fun `explicit research request extracts query`() {
        assertEquals("Caracas", OnlineKnowledge.researchQuery("Jarvis, investiga en internet Caracas"))
        assertNull(OnlineKnowledge.researchQuery("abre internet"))
    }
    @Test fun `returns source for actual page or no result`() {
        val article = OnlineKnowledge.search("Caracas") { JSONObject("""{"query":{"pages":[{"pageid":123,"title":"Caracas","extract":"Capital de Venezuela."}]}}""") }
        assertEquals("Caracas", article?.title)
        assertEquals("https://es.wikipedia.org/?curid=123", article?.url)
        assertNull(OnlineKnowledge.search("nada") { JSONObject("{}") })
    }
}

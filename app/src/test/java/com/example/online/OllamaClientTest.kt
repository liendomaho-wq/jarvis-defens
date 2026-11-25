package com.example.online

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OllamaClientTest {
    @Test fun `validates server address`() {
        assertTrue(OllamaClient.validEndpoint("http://192.168.1.5:11434"))
        assertTrue(OllamaClient.validEndpoint("https://ollama.example.org"))
        assertFalse(OllamaClient.validEndpoint("https://user:password@example.org"))
        assertFalse(OllamaClient.validEndpoint("file:///tmp/a"))
        assertFalse(OllamaClient.validEndpoint("https://example.org/?key=secret"))
    }

    @Test fun `sends question to chat and reads response`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var request = ""
        server.createContext("/api/chat") { exchange ->
            request = exchange.requestBody.bufferedReader().use { it.readText() }
            val bytes = """{"message":{"content":"Hola desde Ollama"}}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            assertEquals("Hola desde Ollama", OllamaClient.ask("http://127.0.0.1:${server.address.port}", "llama3.2", "hola"))
            assertTrue(request.contains("llama3.2"))
            assertTrue(request.contains("hola"))
        } finally { server.stop(0) }
    }
}

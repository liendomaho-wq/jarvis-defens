package com.example

import com.example.model.DeviceTelemetry
import com.example.model.JarvisIntentType
import com.example.system.LocalSystemCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalSystemCommandParserTest {

    private val defaultTelemetry = DeviceTelemetry(
        batteryPercent = 82,
        isCharging = false,
        mediaVolumePercent = 50,
        brightnessPercent = 60,
        isFlashlightOn = false,
        networkType = "Wi-Fi",
        isNetworkConnected = true,
        availableRamMb = 2048,
        totalRamMb = 4096
    )

    @Test
    fun `parses flashlight on and off commands`() {
        val on = LocalSystemCommandParser.tryParseSystemCommand("Enciende la linterna", defaultTelemetry)
        assertNotNull(on)
        assertEquals(JarvisIntentType.TOGGLE_FLASHLIGHT, on?.intent)
        assertEquals(true, on?.enabled)

        val off = LocalSystemCommandParser.tryParseSystemCommand("Apaga el flash", defaultTelemetry)
        assertNotNull(off)
        assertEquals(JarvisIntentType.TOGGLE_FLASHLIGHT, off?.intent)
        assertEquals(false, off?.enabled)
    }

    @Test
    fun `parses whatsapp and telegram messages`() {
        val wa = LocalSystemCommandParser.tryParseSystemCommand(
            "Envía un WhatsApp a Carlos diciendo llego en 10 minutos",
            defaultTelemetry
        )
        assertNotNull(wa)
        assertEquals(JarvisIntentType.SEND_MESSAGE, wa?.intent)
        assertEquals("whatsapp", wa?.platform)
        assertEquals("Carlos", wa?.recipient)
        assertEquals("llego en 10 minutos", wa?.messageBody)

        val tg = LocalSystemCommandParser.tryParseSystemCommand(
            "Manda un mensaje por Telegram a Ana diciendo hola",
            defaultTelemetry
        )
        assertNotNull(tg)
        assertEquals(JarvisIntentType.SEND_MESSAGE, tg?.intent)
        assertEquals("telegram", tg?.platform)
        assertEquals("Ana", tg?.recipient)
        assertEquals("hola", tg?.messageBody)
    }

    @Test
    fun `parses web search, maps, calls, and media controls`() {
        val web = LocalSystemCommandParser.tryParseSystemCommand(
            "Busca en internet noticias de tecnología",
            defaultTelemetry
        )
        assertNotNull(web)
        assertEquals(JarvisIntentType.WEB_BROWSE, web?.intent)

        val maps = LocalSystemCommandParser.tryParseSystemCommand(
            "Llévame a Aeropuerto Benito Juárez",
            defaultTelemetry
        )
        assertNotNull(maps)
        assertEquals(JarvisIntentType.NAVIGATE_MAPS, maps?.intent)

        val call = LocalSystemCommandParser.tryParseSystemCommand("Llama a Mamá", defaultTelemetry)
        assertNotNull(call)
        assertEquals(JarvisIntentType.MAKE_CALL, call?.intent)

        val media = LocalSystemCommandParser.tryParseSystemCommand("Pausa la música", defaultTelemetry)
        assertNotNull(media)
        assertEquals(JarvisIntentType.MEDIA_CONTROL, media?.intent)
    }

    @Test
    fun `parses volume percentage and timer commands`() {
        val vol = LocalSystemCommandParser.tryParseSystemCommand("Sube el volumen al 80%", defaultTelemetry)
        assertNotNull(vol)
        assertEquals(JarvisIntentType.SET_VOLUME, vol?.intent)
        assertEquals(80, vol?.levelPercent)

        val timer = LocalSystemCommandParser.tryParseSystemCommand("Temporizador de 5 minutos", defaultTelemetry)
        assertNotNull(timer)
        assertEquals(JarvisIntentType.SET_TIMER, timer?.intent)
        assertEquals(300, timer?.durationSeconds)
    }

    @Test
    fun `distinguishes opening camera from taking a photo`() {
        assertEquals("camera", LocalSystemCommandParser.tryParseSystemCommand("Abre la cámara", defaultTelemetry)?.appAction)
        assertEquals("take_photo", LocalSystemCommandParser.tryParseSystemCommand("Toma una foto", defaultTelemetry)?.appAction)
        assertEquals("take_photo", LocalSystemCommandParser.tryParseSystemCommand("Saca una foto", defaultTelemetry)?.appAction)
    }

    @Test
    fun `leaves open-ended questions for cloud AI`() {
        val chat = LocalSystemCommandParser.tryParseSystemCommand("¿Por qué el cielo es azul?", defaultTelemetry)
        assertNull(chat)
    }

    @Test
    fun `handles offline assistant fallback queries`() {
        val math = LocalSystemCommandParser.tryOfflineAssistantFallback("Cuánto es 15 por 4", defaultTelemetry)
        assertNotNull(math)
        assertEquals("CÁLCULO = 60", math?.actionSummary)

        val date = LocalSystemCommandParser.tryOfflineAssistantFallback("Qué día es hoy", defaultTelemetry)
        assertNotNull(date)
        assertEquals("FECHA LOCAL", date?.actionSummary)

        val greeting = LocalSystemCommandParser.tryOfflineAssistantFallback("Hola Jarvis", defaultTelemetry)
        assertNotNull(greeting)
        assertEquals("MODO LOCAL OK", greeting?.actionSummary)

        val help = LocalSystemCommandParser.tryOfflineAssistantFallback("Qué puedes hacer", defaultTelemetry)
        assertNotNull(help)
        assertEquals("SISTEMA HÍBRIDO", help?.actionSummary)
    }
}

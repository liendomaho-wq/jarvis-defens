package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.JarvisLocalBrain
import com.example.model.DeviceTelemetry
import com.example.model.JarvisIntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JarvisLocalBrainRobolectricTest {

    private lateinit var context: Context
    private lateinit var brain: JarvisLocalBrain

    private val baseTelemetry = DeviceTelemetry(
        batteryPercent = 50,
        isCharging = false,
        mediaVolumePercent = 40,
        brightnessPercent = 50,
        isFlashlightOn = false,
        networkType = "Wi-Fi",
        isNetworkConnected = true,
        availableRamMb = 2048,
        totalRamMb = 4096
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        brain = JarvisLocalBrain(context)
        brain.ensureInitialized()
    }

    @Test
    fun `executes compound multi-step commands autonomously`() {
        val decision = brain.interpretCommand(
            "Enciende la linterna y pon el volumen al 70%",
            baseTelemetry
        )
        assertEquals(2, decision.steps.size)
        assertEquals(JarvisIntentType.TOGGLE_FLASHLIGHT, decision.steps[0].intent)
        assertEquals(true, decision.steps[0].enabled)
        assertEquals(JarvisIntentType.SET_VOLUME, decision.steps[1].intent)
        assertEquals(70, decision.steps[1].levelPercent)
    }

    @Test
    fun `creates and runs custom routines and sensor automations`() {
        val createRoutine = brain.interpretCommand(
            "Crea una rutina llamada estudio que apague la linterna y baje el brillo al 35%",
            baseTelemetry
        )
        assertEquals(JarvisIntentType.CREATE_AUTOMATION, createRoutine.intent)

        val runRoutine = brain.interpretCommand("Activa estudio", baseTelemetry)
        assertEquals(JarvisIntentType.RUN_ROUTINE, runRoutine.intent)
        assertEquals(2, runRoutine.steps.size)

        val createTrigger = brain.interpretCommand(
            "Automatiza si la bateria baja del 20% entonces baja el brillo al 15%",
            baseTelemetry
        )
        assertEquals(JarvisIntentType.CREATE_AUTOMATION, createTrigger.intent)

        // First telemetry sample sets baseline; drop below 20% fires autonomously
        val initial = brain.automationEngine.evaluateTelemetryTriggers(baseTelemetry.copy(batteryPercent = 30))
        assertTrue(initial.isEmpty())

        val fired = brain.automationEngine.evaluateTelemetryTriggers(baseTelemetry.copy(batteryPercent = 18))
        assertEquals(1, fired.size)
        assertEquals(listOf("baja el brillo al 15%"), fired.first().actions)
    }

    @Test
    fun `learns custom commands, schedules tasks, saves notes, and solves math locally`() {
        val teach = brain.interpretCommand(
            "Cuando diga modo sigilo pon el telefono en vibracion",
            baseTelemetry
        )
        assertEquals(JarvisIntentType.TEACH_NEURAL_NET, teach.intent)

        val learned = brain.interpretCommand("modo sigilo", baseTelemetry)
        assertEquals(JarvisIntentType.SET_RINGER_MODE, learned.intent)
        assertEquals("vibrate", learned.ringerMode)

        val scheduled = brain.interpretCommand("En 5 minutos apaga la linterna", baseTelemetry)
        assertEquals(JarvisIntentType.SCHEDULE_COMMAND, scheduled.intent)
        assertEquals(300, scheduled.durationSeconds)
        assertEquals("apaga la linterna", scheduled.scheduledActionText)

        val saveNote = brain.interpretCommand("Guarda una nota comprar bateria externa", baseTelemetry)
        assertEquals(JarvisIntentType.SAVE_NOTE, saveNote.intent)
        val notes = brain.interpretCommand("Lee mis notas", baseTelemetry)
        assertTrue(notes.spokenResponse.contains("comprar bateria externa"))

        val math = brain.interpretCommand("Cuánto es el 15% de 800", baseTelemetry)
        assertEquals(JarvisIntentType.CONVERSATION, math.intent)
        assertTrue(math.spokenResponse.contains("120"))
    }
}

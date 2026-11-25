package com.example

import com.example.ai.LocalNeuralNetwork
import com.example.ai.TextVectorizer
import com.example.model.JarvisIntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalNeuralNetworkTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `neural network trains from scratch and generalizes to unseen paraphrases`() {
        val weightsFile = File(tempFolder.root, "test_weights.bin")
        val nn = LocalNeuralNetwork(weightsFile)
        nn.initializeOrTrain()

        assertTrue("Expected ready network", nn.isReady)
        assertTrue(
            "Expected high training accuracy, got ${nn.lastTrainingAccuracy}",
            nn.lastTrainingAccuracy >= 0.85f
        )
        assertTrue("Expected serialized weights file", weightsFile.exists())

        val lightPred = nn.predict("esta muy oscuro en el cuarto dame luz")
        assertEquals(JarvisIntentType.TOGGLE_FLASHLIGHT, lightPred.intent)
        assertTrue(lightPred.confidence > 0.4f)

        val volPred = nn.predict("subele mas al sonido multimedia")
        assertEquals(JarvisIntentType.SET_VOLUME, volPred.intent)

        val batteryPred = nn.predict("cuanta bateria queda disponible")
        assertEquals(JarvisIntentType.QUERY_DEVICE_STATUS, batteryPred.intent)
    }

    @Test
    fun `neural network supports incremental fine tuning and persists weights`() {
        val weightsFile = File(tempFolder.root, "adaptive_weights.bin")
        val nn1 = LocalNeuralNetwork(weightsFile)
        nn1.initializeOrTrain()

        nn1.fineTune("protocolo eclipse total", JarvisIntentType.TOGGLE_FLASHLIGHT, epochs = 22)

        // Reload weights into a fresh neural network instance
        val nn2 = LocalNeuralNetwork(weightsFile)
        nn2.initializeOrTrain()

        val pred = nn2.predict("protocolo eclipse total")
        assertEquals(JarvisIntentType.TOGGLE_FLASHLIGHT, pred.intent)
        assertTrue(pred.confidence > 0.5f)
    }

    @Test
    fun `vectorizer produces L2 unit-length vectors`() {
        val vec = TextVectorizer.vectorize("Enciende la linterna al 100%")
        var sumSq = 0f
        for (v in vec) sumSq += v * v
        assertEquals(1.0f, sumSq, 0.02f)
    }
}

package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.spanish.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object MlKitReader {

    private const val TAG = "MlKitReader"
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val extractedText = result.text.trim()
            if (extractedText.isNotBlank()) {
                "Texto detectado en la imagen: ${extractedText.take(300)}"
            } else {
                "No se detectó texto legible en la imagen, señor."
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit text recognition failed", e)
            "Error al procesar el texto con visión artificial local."
        }
    }
}

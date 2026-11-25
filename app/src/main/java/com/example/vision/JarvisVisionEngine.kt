package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object JarvisVisionEngine {

    private const val TAG = "JarvisVisionEngine"
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val text = result.text.trim()
            if (text.isNotBlank()) text else "No se detectó texto legible en la imagen, señor."
        } catch (e: Exception) {
            Log.e(TAG, "Error in ML Kit text recognition", e)
            "Error al procesar el documento con visión artificial: ${e.localizedMessage}"
        }
    }

    suspend fun recognizeTextFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            val result = recognizer.process(image).await()
            val text = result.text.trim()
            if (text.isNotBlank()) text else "No se detectó texto legible en el documento analizado."
        } catch (e: Exception) {
            Log.e(TAG, "Error in ML Kit text recognition from file", e)
            "Error al leer el archivo del documento: ${e.localizedMessage}"
        }
    }
}

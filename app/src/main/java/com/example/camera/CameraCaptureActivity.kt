package com.example.camera

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.bus.JarvisStateBus
import com.example.bus.JarvisSystemEvent
import java.text.SimpleDateFormat
import java.util.Locale

class CameraCaptureActivity : ComponentActivity() {

    private var imageCapture: ImageCapture? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)

                // 2-second countdown before taking photo
                Toast.makeText(this, "JARVIS: Capturando en 2 segundos...", Toast.LENGTH_SHORT).show()
                mainHandler.postDelayed({
                    takePhoto()
                }, 2000L)
            } catch (e: Exception) {
                Log.e("CameraCaptureActivity", "Error binding camera", e)
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: run {
            finish()
            return
        }

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "JARVIS_$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Jarvis")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri
                    Log.i("CameraCaptureActivity", "Photo saved: $savedUri")
                    Toast.makeText(this@CameraCaptureActivity, "JARVIS: Foto guardada en Galería", Toast.LENGTH_SHORT).show()
                    JarvisStateBus.postEvent(JarvisSystemEvent.PhotoCaptured(savedUri?.toString() ?: ""))
                    finish()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraCaptureActivity", "Photo capture failed", exception)
                    finish()
                }
            }
        )
    }
}

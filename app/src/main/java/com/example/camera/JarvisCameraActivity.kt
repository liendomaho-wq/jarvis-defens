package com.example.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una captura explícita, visible y con permiso; no se usa para vigilancia en segundo plano. */
class JarvisCameraActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private var captureStarted = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (requiredPermissions().all { result[it] == true || hasPermission(it) }) startCamera()
        else status.text = "No se tomó la foto: se necesitan los permisos de cámara y almacenamiento."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureStarted = savedInstanceState?.getBoolean("captureStarted") ?: false
        previewView = PreviewView(this)
        status = TextView(this).apply {
            text = "Preparando cámara…"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            setPadding(20, 20, 20, 20)
        }
        val close = Button(this).apply { text = "VOLVER A JARVIS"; setOnClickListener { finish() } }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC07121A.toInt())
            addView(status)
            addView(close)
        }
        setContentView(FrameLayout(this).apply {
            addView(previewView, FrameLayout.LayoutParams(-1, -1))
            addView(footer, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        })
        if (captureStarted) {
            status.text = "La captura ya se inició. Vuelve a Jarvis para solicitar otra foto."
        } else if (requiredPermissions().all(::hasPermission)) startCamera()
        else permissions.launch(requiredPermissions())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("captureStarted", captureStarted)
        super.onSaveInstanceState(outState)
    }

    private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT <= 28)
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    else arrayOf(Manifest.permission.CAMERA)

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                if (isFinishing || isDestroyed || captureStarted) return@addListener
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder().build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                status.text = "Cámara activa. Preparando foto…"
                previewView.postDelayed({
                    if (!isFinishing && !isDestroyed && !captureStarted) takePhoto(capture)
                }, 1000)
            } catch (_: Exception) {
                status.text = "No se pudo iniciar la cámara. Comprueba el permiso y que no esté en uso."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto(capture: ImageCapture) {
        captureStarted = true
        val name = "JARVIS_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Jarvis")
        }
        val output = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        capture.takePicture(output, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                status.text = "Foto guardada en la galería."
            }
            override fun onError(exception: ImageCaptureException) {
                status.text = "No se pudo guardar la foto. Inténtalo de nuevo desde Jarvis."
            }
        })
    }
}

package com.example.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.camera.CameraCaptureActivity
import com.example.data.db.JarvisRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.net.URLEncoder

class SentinelManager(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val repository = JarvisRepository(context)

    fun activateSentinelProtocol(onStatus: (String) -> Unit) {
        onStatus("Activando Protocolo Centinela de emergencia, señor.")

        // 1. Capture silent photo via CameraCaptureActivity
        try {
            val cameraIntent = android.content.Intent(context, CameraCaptureActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(cameraIntent)
        } catch (e: Exception) {
            Log.w("SentinelManager", "Cannot launch silent camera capture", e)
        }

        // 2. Get GPS Location and send SMS + WhatsApp alert
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location: Location? ->
                        val lat = location?.latitude ?: 0.0
                        val lon = location?.longitude ?: 0.0
                        val mapsLink = "https://maps.google.com/?q=$lat,$lon"
                        val alertMessage = "🚨 ALERTA CENTINELA JARVIS: Me encuentro en situación de emergencia. Mis coordenadas GPS actuales son: $mapsLink"

                        sendEmergencySms(alertMessage)
                        sendEmergencyWhatsApp(alertMessage)
                        onStatus("Protocolo Centinela ejecutado. Alerta y ubicación GPS enviadas.")
                    }
                    .addOnFailureListener {
                        sendEmergencySms("🚨 ALERTA CENTINELA JARVIS: Emergencia activada pero no se pudo obtener GPS preciso.")
                        onStatus("Protocolo Centinela activado (GPS no disponible, SMS enviado).")
                    }
            } catch (e: Exception) {
                Log.e("SentinelManager", "Error fetching location", e)
            }
        } else {
            sendEmergencySms("🚨 ALERTA CENTINELA JARVIS: Emergencia activada.")
            onStatus("Protocolo Centinela activado (Permiso de ubicación requerido para GPS exacto).")
        }
    }

    private fun sendEmergencySms(message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            // Default emergency test number or placeholder
            val emergencyNumber = "5551234567"
            smsManager.sendTextMessage(emergencyNumber, null, message, null, null)
            Log.i("SentinelManager", "Emergency SMS sent to $emergencyNumber")
        } catch (e: Exception) {
            Log.e("SentinelManager", "Failed sending emergency SMS", e)
        }
    }

    private fun sendEmergencyWhatsApp(message: String) {
        try {
            val encoded = URLEncoder.encode(message, "UTF-8")
            val uri = android.net.Uri.parse("https://wa.me/?text=$encoded")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w("SentinelManager", "Failed launching WhatsApp emergency broadcast", e)
        }
    }
}

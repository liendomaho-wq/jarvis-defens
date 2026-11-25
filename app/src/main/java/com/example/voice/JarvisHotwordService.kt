package com.example.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.text.Normalizer
import java.util.Locale

class JarvisHotwordService : Service() {

    companion object {
        const val ACTION_HOTWORD_DETECTED = "com.example.ACTION_HOTWORD_DETECTED"
        const val EXTRA_HOTWORD_TEXT = "EXTRA_HOTWORD_TEXT"
        const val CHANNEL_ID = "jarvis_hotword_channel"
        const val NOTIF_ID = 1001

        var isRunning = false
            private set
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListeningSession = false

    private val restartListeningRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                startHotwordListening()
                handler.postDelayed(this, 12000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        initRecognizer()
        handler.post(restartListeningRunnable)
        Log.i("JarvisHotwordService", "Jarvis Hotword Background Service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Hotword Background Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene a Jarvis a la escucha de la palabra de activación 'JARVIS ESTAS DESPIERTO?'"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis: Escucha en segundo plano")
            .setContentText("Di 'JARVIS ESTAS DESPIERTO?' para activarme.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun initRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListeningSession = false
                    }
                    override fun onError(error: Int) {
                        isListeningSession = false
                    }
                    override fun onResults(results: Bundle?) {
                        isListeningSession = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            for (match in matches) {
                                if (checkHotwordMatch(match)) {
                                    triggerHotwordActivation(match)
                                    break
                                }
                            }
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            for (match in matches) {
                                if (checkHotwordMatch(match)) {
                                    triggerHotwordActivation(match)
                                    break
                                }
                            }
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e("JarvisHotwordService", "Error initializing recognizer", e)
        }
    }

    private fun startHotwordListening() {
        if (isListeningSession) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
            isListeningSession = true
        } catch (e: Exception) {
            Log.w("JarvisHotwordService", "Failed to start hotword listening session", e)
            isListeningSession = false
        }
    }

    private fun checkHotwordMatch(text: String): Boolean {
        val norm = normalize(text)
        return (norm.contains("jarvis") && norm.contains("despierto")) ||
               norm.contains("jarvis estas despierto") ||
               norm.contains("jarvis estas ahi")
    }

    private fun triggerHotwordActivation(matchedText: String) {
        Log.i("JarvisHotwordService", "Hotword detected: $matchedText")
        val broadcastIntent = Intent(ACTION_HOTWORD_DETECTED).apply {
            putExtra(EXTRA_HOTWORD_TEXT, matchedText)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_HOTWORD_DETECTED
            putExtra("HOTWORD_TRIGGER", true)
            putExtra(EXTRA_HOTWORD_TEXT, matchedText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(mainActivityIntent)
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(restartListeningRunnable)
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        super.onDestroy()
        Log.i("JarvisHotwordService", "Jarvis Hotword Service destroyed.")
    }
}

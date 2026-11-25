package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.system.SystemControlManager

class JarvisWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_VOICE = "com.example.widget.ACTION_VOICE"
        const val ACTION_WIDGET_TORCH = "com.example.widget.ACTION_TORCH"
        const val ACTION_WIDGET_NIGHT = "com.example.widget.ACTION_NIGHT"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val systemManager = SystemControlManager(context)
        when (intent.action) {
            ACTION_WIDGET_VOICE -> {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("AUTO_LISTEN", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launchIntent)
            }
            ACTION_WIDGET_TORCH -> {
                val current = systemManager.isTorchOn.value
                systemManager.setFlashlight(!current)
                refreshAllWidgets(context)
            }
            ACTION_WIDGET_NIGHT -> {
                systemManager.setRingerMode("silent")
                systemManager.setBrightness(15, false, null)
                systemManager.setFlashlight(false)
                refreshAllWidgets(context)
            }
        }
    }

    private fun refreshAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, JarvisWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val systemManager = SystemControlManager(context)
            val telemetry = systemManager.getTelemetry()

            val views = RemoteViews(context.packageName, R.layout.jarvis_app_widget).apply {
                setTextViewText(
                    R.id.widget_status_text,
                    "BAT ${telemetry.batteryPercent}% • ${if (telemetry.isNetworkConnected) telemetry.networkType else "OFFLINE"}"
                )

                // Main Intent
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val mainPending = PendingIntent.getActivity(
                    context, 0, mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_container, mainPending)
                setOnClickPendingIntent(R.id.widget_title, mainPending)

                // Voice Action PendingIntent
                val voiceIntent = Intent(context, JarvisWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_VOICE
                }
                val voicePending = PendingIntent.getBroadcast(
                    context, 1, voiceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_btn_voice, voicePending)

                // Torch Action PendingIntent
                val torchIntent = Intent(context, JarvisWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_TORCH
                }
                val torchPending = PendingIntent.getBroadcast(
                    context, 2, torchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_btn_torch, torchPending)

                // Night Action PendingIntent
                val nightIntent = Intent(context, JarvisWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_NIGHT
                }
                val nightPending = PendingIntent.getBroadcast(
                    context, 3, nightIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_btn_night, nightPending)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

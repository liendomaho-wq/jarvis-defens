package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.system.SystemControlManager

class JarvisWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
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

                // Tap widget opens MainActivity
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val mainPending = PendingIntent.getActivity(
                    context, 0, mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_container, mainPending)
                setOnClickPendingIntent(R.id.widget_core_btn, mainPending)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

package com.screenarc.app.receiver

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.screenarc.app.data.AppSettingsRepository
import com.screenarc.app.service.OverlayService

class ServiceWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WATCHDOG_PING) return
        Log.d(TAG, "Watchdog ping received")
        val settingsRepo = AppSettingsRepository(context)
        if (!settingsRepo.settings.value.isServiceEnabled) {
            Log.d(TAG, "Service is disabled by user, skipping watchdog restart")
            return
        }
        if (!isServiceRunning(context)) {
            Log.d(TAG, "OverlayService is not running, restarting...")
            OverlayService.startService(context)
        } else {
            Log.d(TAG, "OverlayService is already running, no action needed")
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == OverlayService::class.java.name }
    }

    companion object {
        private const val TAG = "ServiceWatchdogReceiver"
        const val ACTION_WATCHDOG_PING = "com.screenarc.app.action.WATCHDOG_PING"
        private const val WATCHDOG_REQUEST_CODE = 7777
        // 5 minutes in milliseconds
        private const val INTERVAL_MS = 5 * 60 * 1000L

        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ServiceWatchdogReceiver::class.java).apply {
                action = ACTION_WATCHDOG_PING
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                WATCHDOG_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Cancel any existing alarm first
            alarmManager.cancel(pendingIntent)
            // Use inexact repeating - battery efficient, fires approximately every 5 min
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                android.os.SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                pendingIntent
            )
            Log.d(TAG, "Watchdog alarm scheduled (every ~5 minutes)")
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ServiceWatchdogReceiver::class.java).apply {
                action = ACTION_WATCHDOG_PING
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                WATCHDOG_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Watchdog alarm cancelled")
        }
    }
}

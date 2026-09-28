package com.screenarc.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.screenarc.app.data.AppSettingsRepository
import com.screenarc.app.service.OverlayService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed broadcast received: ${intent.action}")
            val settingsRepo = AppSettingsRepository(context)
            if (settingsRepo.settings.value.isServiceEnabled) {
                Log.d(TAG, "Restarting OverlayService on boot")
                OverlayService.startService(context)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}

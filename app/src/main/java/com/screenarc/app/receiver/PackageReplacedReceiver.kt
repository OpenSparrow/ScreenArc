package com.screenarc.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.screenarc.app.data.AppSettingsRepository
import com.screenarc.app.service.OverlayService

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "My package replaced broadcast received")
            val settingsRepo = AppSettingsRepository(context)
            if (settingsRepo.settings.value.isServiceEnabled) {
                Log.d(TAG, "Restarting OverlayService after package update")
                OverlayService.startService(context)
            }
        }
    }

    companion object {
        private const val TAG = "PackageReplacedReceiver"
    }
}

package com.screenarc.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.screenarc.app.di.AppContainer
import com.screenarc.app.di.DefaultAppContainer

class ScreenArcApplication : Application(), ImageLoaderFactory {

    lateinit var appContainer: AppContainer
        private set

    companion object {
        const val CHANNEL_ID = "screenarc_overlay_channel"
        lateinit var instance: ScreenArcApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        appContainer = DefaultAppContainer(this)
        createNotificationChannel()
        com.screenarc.app.worker.OcrBackgroundWorker.enqueuePeriodicWork(this)
        com.screenarc.app.worker.AutoArchiveWorker.schedulePeriodicWork(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            appContainer.ocrRepository.closeRecognizers()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.service_notification_channel_name)
            val descriptionText = getString(R.string.service_notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}


package com.screenarc.app.worker

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.screenarc.app.ScreenArcApplication
import com.screenarc.app.data.AppSettingsRepository
import com.screenarc.app.data.ScreenshotRepository
import com.screenarc.app.storage.MediaStoreOrganizer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AutoArchiveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ScreenArcApplication
        val settingsRepo = app?.appContainer?.settingsRepository ?: AppSettingsRepository(applicationContext)
        val screenshotRepo = app?.appContainer?.screenshotRepository ?: ScreenshotRepository(applicationContext)

        val settings = settingsRepo.settings.value
        val archiveDays = settings.autoArchiveDays

        if (archiveDays <= 0) {
            Log.d(TAG, "Auto-archive is disabled (autoArchiveDays=$archiveDays)")
            return Result.success()
        }

        return try {
            val cutoffTimestampSec = (System.currentTimeMillis() / 1000L) - (archiveDays * 24L * 60L * 60L)
            Log.d(TAG, "Scanning for uncategorized screenshots older than $archiveDays days (cutoff: $cutoffTimestampSec)")

            val allScreenshots = screenshotRepo.getAllScreenshotsFlow().first()
            val oldUncategorized = allScreenshots.filter { item ->
                // MUST NEVER touch categorized screenshots
                val entity = item.screenshot
                entity.categoryId == null && entity.dateAdded > 0 && entity.dateAdded < cutoffTimestampSec
            }

            Log.d(TAG, "Found ${oldUncategorized.size} uncategorized screenshots eligible for auto-trash")

            var trashedCount = 0
            for (item in oldUncategorized) {
                val uiModel = item.toUiModel()
                val deleted = screenshotRepo.deleteScreenshot(uiModel)
                if (deleted.isSuccess) {
                    trashedCount++
                }
            }

            Log.d(TAG, "Auto-archive complete: trashed $trashedCount screenshots")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error running auto-archive worker: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "AutoArchiveWorker"
        const val WORK_NAME = "AutoArchivePeriodicWorker"

        fun schedulePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<AutoArchiveWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        }
    }
}

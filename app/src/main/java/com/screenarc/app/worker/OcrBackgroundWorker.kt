package com.screenarc.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.screenarc.app.ScreenArcApplication
import com.screenarc.app.data.OcrRepositoryImpl
import java.util.concurrent.TimeUnit

class OcrBackgroundWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ScreenArcApplication
        val ocrRepo = app?.appContainer?.ocrRepository ?: OcrRepositoryImpl(applicationContext)
        return try {
            val batchResult = ocrRepo.recognizePendingScreenshots()
            if (batchResult.failureCount > 0 && batchResult.successCount == 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "OcrBackgroundPeriodicWorker"

        fun enqueuePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<OcrBackgroundWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
        }
    }
}


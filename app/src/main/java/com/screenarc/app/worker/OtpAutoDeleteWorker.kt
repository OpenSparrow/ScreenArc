package com.screenarc.app.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.screenarc.app.ScreenArcApplication
import com.screenarc.app.data.ScreenshotRepository
import java.util.concurrent.TimeUnit

class OtpAutoDeleteWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_SCREENSHOT_URI) ?: return Result.failure()
        return try {
            val app = applicationContext as? ScreenArcApplication
            val repo = app?.appContainer?.screenshotRepository ?: ScreenshotRepository(applicationContext)

            val deleteResult = repo.deleteScreenshotByUri(uriString)
            if (deleteResult.isSuccess) {
                Log.d(TAG, "Deleted OTP screenshot $uriString from storage and database")
                Result.success()
            } else {
                Log.w(TAG, "Failed deleting OTP screenshot $uriString")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error auto-deleting OTP screenshot $uriString: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "OtpAutoDeleteWorker"
        const val KEY_SCREENSHOT_URI = "key_screenshot_uri"

        fun scheduleAutoDelete(context: Context, screenshotUri: String, delayHours: Long = 24) {
            val data = Data.Builder()
                .putString(KEY_SCREENSHOT_URI, screenshotUri)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<OtpAutoDeleteWorker>()
                .setInitialDelay(delayHours, TimeUnit.HOURS)
                .setInputData(data)
                .build()

            val uniqueName = "otp_delete_${screenshotUri.hashCode()}"
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "Scheduled auto-deletion for $screenshotUri in $delayHours hours")
        }

        fun cancelAutoDelete(context: Context, screenshotUri: String) {
            val uniqueName = "otp_delete_${screenshotUri.hashCode()}"
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName)
        }
    }
}

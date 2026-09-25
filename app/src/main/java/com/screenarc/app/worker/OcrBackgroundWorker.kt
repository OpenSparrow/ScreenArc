package com.screenarc.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.screenarc.app.data.OcrRepositoryImpl

class OcrBackgroundWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ocrRepo = OcrRepositoryImpl(applicationContext)
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
}

package com.screenarc.app.data

import android.net.Uri
import com.screenarc.app.data.local.db.entity.ScreenshotEntity
import com.screenarc.app.model.OcrBatchResult
import com.screenarc.app.model.OcrResult

interface OcrRepository {
    suspend fun recognizeText(uri: Uri): OcrResult
    suspend fun recognizeTextForScreenshot(screenshot: ScreenshotEntity): OcrResult
    suspend fun recognizePendingScreenshots(): OcrBatchResult
    suspend fun cancelPendingOcr()
}

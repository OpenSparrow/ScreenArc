package com.screenarc.app.data

import android.net.Uri
import com.screenarc.app.data.local.db.entity.ScreenshotEntity
import com.screenarc.app.model.OcrBatchProgress
import com.screenarc.app.model.OcrBatchResult
import com.screenarc.app.model.OcrResult
import com.screenarc.app.ocr.OcrEngineProvider
import com.screenarc.app.ocr.OcrLanguage
import kotlinx.coroutines.flow.StateFlow

interface OcrRepository {
    val batchProgress: StateFlow<OcrBatchProgress>
    val activeProvider: OcrEngineProvider

    suspend fun recognizeText(uri: Uri, language: OcrLanguage? = null): OcrResult
    suspend fun recognizeTextForScreenshot(screenshot: ScreenshotEntity, language: OcrLanguage? = null): OcrResult
    suspend fun recognizePendingScreenshots(language: OcrLanguage? = null): OcrBatchResult
    suspend fun processAndIndexAllImages(forceReindex: Boolean = false, language: OcrLanguage? = null): OcrBatchProgress
    suspend fun cancelPendingOcr()
}

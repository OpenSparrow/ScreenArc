package com.screenarc.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.screenarc.app.data.local.db.AppDatabase
import com.screenarc.app.data.local.db.entity.OcrTextEntity
import com.screenarc.app.data.local.db.entity.ScreenshotEntity
import com.screenarc.app.model.OcrBatchProgress
import com.screenarc.app.model.OcrBatchResult
import com.screenarc.app.model.OcrBlock
import com.screenarc.app.model.OcrResult
import com.screenarc.app.ocr.MlKitOcrProvider
import com.screenarc.app.ocr.OcrEngineProvider
import com.screenarc.app.ocr.OcrLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val settingsRepository: AppSettingsRepository = AppSettingsRepository(context),
    private val screenshotRepository: ScreenshotRepository = ScreenshotRepository(context, database),
    override val activeProvider: OcrEngineProvider = MlKitOcrProvider()
) : OcrRepository {

    private val ocrDao = database.ocrDao()
    private val screenshotDao = database.screenshotDao()

    // Cached TextRecognizer instances per OcrLanguage
    private val recognizerCache = ConcurrentHashMap<OcrLanguage, TextRecognizer>()

    // Bounded concurrency semaphore: max 1 simultaneous OCR job to maintain thermal & CPU balance
    private val ocrSemaphore = Semaphore(1)
    private var activeBatchJob: Job? = null
    private val isCancelled = AtomicBoolean(false)

    private val _batchProgress = MutableStateFlow(OcrBatchProgress.IDLE)
    override val batchProgress: StateFlow<OcrBatchProgress> = _batchProgress.asStateFlow()

    private fun getOrCreateRecognizer(language: OcrLanguage): TextRecognizer {
        return recognizerCache.computeIfAbsent(language) { lang ->
            activeProvider.createRecognizer(lang)
        }
    }

    override fun closeRecognizers() {
        for ((_, recognizer) in recognizerCache) {
            try {
                recognizer.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing cached TextRecognizer", e)
            }
        }
        recognizerCache.clear()
    }

    override suspend fun recognizeText(uri: Uri, language: OcrLanguage?): OcrResult = withContext(Dispatchers.IO) {
        val targetLanguage = language ?: settingsRepository.settings.value.ocrLanguage
        val recognizer = getOrCreateRecognizer(targetLanguage)
        recognizeWithRecognizer(uri, recognizer)
    }

    private suspend fun recognizeWithRecognizer(
        uri: Uri,
        recognizer: TextRecognizer
    ): OcrResult = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        val processedTime = System.currentTimeMillis()

        // Update status to PROCESSING
        screenshotDao.updateOcrStatus(uriString, "PROCESSING", processedTime)
        ocrDao.insertOrUpdate(
            OcrTextEntity(
                screenshotUri = uriString,
                recognizedText = "",
                normalizedText = "",
                processingStatus = "PROCESSING",
                errorMessage = null,
                processedAt = null,
                updatedAt = processedTime
            )
        )

        ocrSemaphore.withPermit {
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val visionText: Text = suspendCancellableCoroutine { continuation ->
                    val task = recognizer.process(inputImage)
                    task.addOnSuccessListener { textResult ->
                        if (continuation.isActive) {
                            continuation.resume(textResult)
                        }
                    }.addOnFailureListener { exception ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(exception)
                        }
                    }
                    continuation.invokeOnCancellation {
                        // cancellation handle
                    }
                }

                val rawText = visionText.text
                val normalized = OcrTextEntity.normalizeText(rawText)

                val blocks = mutableListOf<OcrBlock>()
                val blocksJsonArray = JSONArray()

                val textBlocksList = visionText.textBlocks
                for (i in 0 until textBlocksList.size) {
                    val block = textBlocksList[i]
                    val box = block.boundingBox
                    val ocrBlock = OcrBlock(
                        text = block.text,
                        left = box?.left,
                        top = box?.top,
                        right = box?.right,
                        bottom = box?.bottom
                    )
                    blocks.add(ocrBlock)

                    val blockObj = JSONObject().apply {
                        put("text", block.text)
                        put("left", box?.left ?: 0)
                        put("top", box?.top ?: 0)
                        put("right", box?.right ?: 0)
                        put("bottom", box?.bottom ?: 0)
                    }
                    blocksJsonArray.put(blockObj)
                }

                val status = if (rawText.isBlank()) "NO_TEXT" else "COMPLETED"

                val entity = OcrTextEntity(
                    screenshotUri = uriString,
                    recognizedText = rawText,
                    normalizedText = normalized,
                    processingStatus = status,
                    errorMessage = null,
                    processedAt = processedTime,
                    updatedAt = System.currentTimeMillis(),
                    blocksJson = blocksJsonArray.toString()
                )

                ocrDao.insertOrUpdate(entity)
                screenshotDao.updateOcrStatus(uriString, status, System.currentTimeMillis())

                OcrResult(
                    screenshotUri = uriString,
                    recognizedText = rawText,
                    blocks = blocks,
                    processedAt = processedTime,
                    success = true,
                    errorMessage = null
                )
            } catch (e: CancellationException) {
                val cancelEntity = OcrTextEntity(
                    screenshotUri = uriString,
                    recognizedText = "",
                    normalizedText = "",
                    processingStatus = "CANCELLED",
                    errorMessage = "OCR processing cancelled",
                    processedAt = null,
                    updatedAt = System.currentTimeMillis()
                )
                ocrDao.insertOrUpdate(cancelEntity)
                screenshotDao.updateOcrStatus(uriString, "CANCELLED", System.currentTimeMillis())
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed for $uri: ${e.message}", e)
                val failEntity = OcrTextEntity(
                    screenshotUri = uriString,
                    recognizedText = "",
                    normalizedText = "",
                    processingStatus = "FAILED",
                    errorMessage = e.message ?: "Unknown error",
                    processedAt = null,
                    updatedAt = System.currentTimeMillis()
                )
                ocrDao.insertOrUpdate(failEntity)
                screenshotDao.updateOcrStatus(uriString, "FAILED", System.currentTimeMillis())

                OcrResult(
                    screenshotUri = uriString,
                    recognizedText = "",
                    blocks = emptyList(),
                    processedAt = processedTime,
                    success = false,
                    errorMessage = e.message
                )
            }
        }
    }

    override suspend fun recognizeTextForScreenshot(
        screenshot: ScreenshotEntity,
        language: OcrLanguage?
    ): OcrResult {
        return recognizeText(Uri.parse(screenshot.uri), language)
    }

    override suspend fun recognizePendingScreenshots(language: OcrLanguage?): OcrBatchResult = withContext(Dispatchers.IO) {
        val targetLanguage = language ?: settingsRepository.settings.value.ocrLanguage
        val recognizer = getOrCreateRecognizer(targetLanguage)
        isCancelled.set(false)

        val unprocessed = screenshotDao.getUnprocessedScreenshots()
        var successCount = 0
        var failCount = 0
        var processedCount = 0

        for (screenshot in unprocessed) {
            if (isCancelled.get()) break
            try {
                val result = recognizeWithRecognizer(Uri.parse(screenshot.uri), recognizer)
                processedCount++
                if (result.success) {
                    successCount++
                } else {
                    failCount++
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                failCount++
            }
        }

        OcrBatchResult(
            processedCount = processedCount,
            successCount = successCount,
            failureCount = failCount
        )
    }

    /**
     * Process & Index All Images:
     * 1. Discovers all images from device storage via ScreenshotRepository.
     * 2. Reuses a single loaded OCR model throughout the entire batch for high throughput and efficiency.
     * 3. Handles per-image errors gracefully without failing the batch.
     * 4. Updates real-time reactive progress (processed, indexed, skipped, failed).
     */
    override suspend fun processAndIndexAllImages(
        forceReindex: Boolean,
        language: OcrLanguage?
    ): OcrBatchProgress = withContext(Dispatchers.IO) {
        val targetLanguage = language ?: settingsRepository.settings.value.ocrLanguage
        isCancelled.set(false)

        // 1. Sync all images from MediaStore into database
        screenshotRepository.syncAllScreenshots()

        val allScreenshots = screenshotDao.getAllScreenshots()
        val toProcess = if (forceReindex) {
            allScreenshots
        } else {
            allScreenshots.filter { item ->
                item.screenshot.ocrStatus != "COMPLETED" && item.screenshot.ocrStatus != "NO_TEXT"
            }
        }

        val totalToProcess = toProcess.size
        val skippedCount = allScreenshots.size - totalToProcess

        if (totalToProcess == 0) {
            val completedProgress = OcrBatchProgress(
                isRunning = false,
                total = allScreenshots.size,
                current = allScreenshots.size,
                currentFileName = "No unprocessed images",
                currentLanguage = targetLanguage.displayName,
                indexedCount = 0,
                skippedCount = skippedCount,
                failedCount = 0,
                lastError = null,
                isCompleted = true,
                isCancelled = false
            )
            _batchProgress.value = completedProgress
            return@withContext completedProgress
        }

        _batchProgress.value = OcrBatchProgress(
            isRunning = true,
            total = totalToProcess,
            current = 0,
            currentFileName = "",
            currentLanguage = targetLanguage.displayName,
            indexedCount = 0,
            skippedCount = skippedCount,
            failedCount = 0,
            lastError = null,
            isCompleted = false,
            isCancelled = false
        )

        // Reuse cached model for the batch
        val recognizer = getOrCreateRecognizer(targetLanguage)
        var indexed = 0
        var failed = 0
        var lastErr: String? = null

        try {
            for ((index, item) in toProcess.withIndex()) {
                if (isCancelled.get()) {
                    break
                }

                val currentIdx = index + 1
                val screenshot = item.screenshot
                val uri = Uri.parse(screenshot.uri)

                _batchProgress.value = _batchProgress.value.copy(
                    current = currentIdx,
                    currentFileName = screenshot.displayName
                )

                try {
                    val result = recognizeWithRecognizer(uri, recognizer)
                    if (result.success) {
                        indexed++
                    } else {
                        failed++
                        lastErr = "Failed ${screenshot.displayName}: ${result.errorMessage ?: "Unknown error"}"
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    failed++
                    lastErr = "Failed ${screenshot.displayName}: ${e.message}"
                    Log.e(TAG, "Batch error processing ${screenshot.displayName}: ${e.message}", e)
                }

                _batchProgress.value = _batchProgress.value.copy(
                    indexedCount = indexed,
                    failedCount = failed,
                    lastError = lastErr
                )
                kotlinx.coroutines.yield()
            }
        } finally {
            val wasCancelled = isCancelled.get()
            val finalProgress = _batchProgress.value.copy(
                isRunning = false,
                isCompleted = !wasCancelled,
                isCancelled = wasCancelled,
                indexedCount = indexed,
                failedCount = failed,
                lastError = lastErr
            )
            _batchProgress.value = finalProgress
        }

        _batchProgress.value
    }

    override suspend fun cancelPendingOcr() {
        isCancelled.set(true)
        activeBatchJob?.cancel()
        _batchProgress.value = _batchProgress.value.copy(
            isRunning = false,
            isCancelled = true
        )
    }

    companion object {
        private const val TAG = "OcrRepository"

        fun parseBlocksFromJson(jsonString: String?): List<OcrBlock> {
            if (jsonString.isNullOrBlank()) return emptyList()
            val list = mutableListOf<OcrBlock>()
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        OcrBlock(
                            text = obj.optString("text", ""),
                            left = obj.optInt("left", 0),
                            top = obj.optInt("top", 0),
                            right = obj.optInt("right", 0),
                            bottom = obj.optInt("bottom", 0)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing OCR blocks: ${e.message}")
            }
            return list
        }
    }
}

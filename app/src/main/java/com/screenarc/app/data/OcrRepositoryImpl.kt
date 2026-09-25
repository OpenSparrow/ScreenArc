package com.screenarc.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.screenarc.app.data.local.db.AppDatabase
import com.screenarc.app.data.local.db.entity.OcrTextEntity
import com.screenarc.app.data.local.db.entity.ScreenshotEntity
import com.screenarc.app.model.OcrBatchResult
import com.screenarc.app.model.OcrBlock
import com.screenarc.app.model.OcrResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) : OcrRepository {

    private val ocrDao = database.ocrDao()
    private val screenshotDao = database.screenshotDao()

    // Bounded concurrency semaphore: max 2 simultaneous OCR jobs
    private val ocrSemaphore = Semaphore(2)
    private var activeBatchJob: Job? = null
    private val isCancelled = AtomicBoolean(false)

    override suspend fun recognizeText(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
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
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
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
            } finally {
                recognizer.close()
            }
        }
    }

    override suspend fun recognizeTextForScreenshot(screenshot: ScreenshotEntity): OcrResult {
        return recognizeText(Uri.parse(screenshot.uri))
    }

    override suspend fun recognizePendingScreenshots(): OcrBatchResult = withContext(Dispatchers.IO) {
        isCancelled.set(false)
        val unprocessed = screenshotDao.getUnprocessedScreenshots()
        var successCount = 0
        var failCount = 0
        var processedCount = 0

        for (screenshot in unprocessed) {
            if (isCancelled.get()) break
            try {
                val result = recognizeText(Uri.parse(screenshot.uri))
                processedCount++
                if (result.success) {
                    successCount++
                } else {
                    failCount++
                }
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

    override suspend fun cancelPendingOcr() {
        isCancelled.set(true)
        activeBatchJob?.cancel()
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

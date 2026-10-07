package com.screenarc.app.service

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.screenarc.app.model.ScreenshotItem
import com.screenarc.app.palette.PaletteExtractor
import com.screenarc.app.storage.MediaStoreOrganizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class ScreenshotObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onScreenshotDetected: (ScreenshotItem) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val processedIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
    private val processedUris = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var debounceJob: Job? = null

    fun markProcessed(id: Long, uri: Uri? = null) {
        processedIds.add(id)
        uri?.let { processedUris.add(it.toString()) }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "MediaStore change detected: $uri")

        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.IO) {
            // Small initial debounce to let screenshot writer finish flushing image data
            delay(350)
            checkLatestScreenshot(attempt = 1, triggerUri = uri)
        }
    }

    private suspend fun checkLatestScreenshot(attempt: Int = 1, triggerUri: Uri? = null) {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.IS_PENDING
            )
        } else {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATA
            )
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        try {
            // If triggerUri is a specific image uri (e.g. content://media/external/images/media/123), try querying it directly first
            val cursor = if (triggerUri != null && triggerUri.toString().contains("/images/media/")) {
                context.contentResolver.query(triggerUri, projection, null, null, null)
                    ?: context.contentResolver.query(collection, projection, null, null, sortOrder)
            } else {
                context.contentResolver.query(collection, projection, null, null, sortOrder)
            }

            cursor?.use {
                var checkedCount = 0
                while (it.moveToNext() && checkedCount < 5) {
                    checkedCount++
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        it.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                    } else {
                        it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val isPendingIndex = it.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                        if (isPendingIndex != -1 && it.getInt(isPendingIndex) == 1) {
                            Log.d(TAG, "Screenshot is still pending/writing (attempt $attempt), retrying...")
                            if (attempt < 4) {
                                scope.launch(Dispatchers.IO) {
                                    delay(250L * attempt)
                                    checkLatestScreenshot(attempt + 1, triggerUri)
                                }
                            }
                            return
                        }
                    }

                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn) ?: ""
                    val rawDateAdded = it.getLong(dateColumn)
                    val size = it.getLong(sizeColumn)
                    val mime = it.getString(mimeColumn) ?: "image/png"
                    val path = it.getString(pathColumn) ?: ""

                    // If file is 0 bytes, it might still be flushing to storage
                    if (size <= 0 && attempt < 4) {
                        Log.d(TAG, "Screenshot size is 0 bytes (attempt $attempt), scheduling retry...")
                        scope.launch(Dispatchers.IO) {
                            delay(250L * attempt)
                            checkLatestScreenshot(attempt + 1, triggerUri)
                        }
                        return
                    }

                    // Check if already processed
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    if (processedIds.contains(id) || processedUris.contains(contentUri.toString())) {
                        continue
                    }

                    // Verify if it's in an already categorized subfolder
                    val normalizedPath = path.replace('\\', '/').trim('/')
                    val isCategorizedSubfolder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        normalizedPath.startsWith("Pictures/Screenshots/", ignoreCase = true) &&
                                normalizedPath.length > "Pictures/Screenshots/".length
                    } else {
                        val file = java.io.File(path)
                        val parentName = file.parentFile?.name ?: ""
                        parentName != "Screenshots" && path.contains("Screenshots", ignoreCase = true)
                    }

                    if (isCategorizedSubfolder) {
                        Log.d(TAG, "Ignoring screenshot in categorized subfolder: $path")
                        markProcessed(id, contentUri)
                        continue
                    }

                    // Normalize timestamp to seconds
                    val dateAddedSec = if (rawDateAdded > 100_000_000_000L) rawDateAdded / 1000 else rawDateAdded
                    val currentTimeSec = System.currentTimeMillis() / 1000
                    val isRecent = dateAddedSec == 0L || kotlin.math.abs(currentTimeSec - dateAddedSec) < 180

                    // Support diverse OEM screenshot directories & naming patterns
                    val isScreenshotPath = path.contains("screenshot", ignoreCase = true) ||
                            path.contains("screencapture", ignoreCase = true) ||
                            path.contains("screen_capture", ignoreCase = true) ||
                            path.contains("screen-capture", ignoreCase = true)

                    val isScreenshotName = name.contains("screenshot", ignoreCase = true) ||
                            name.startsWith("screen_", ignoreCase = true) ||
                            name.startsWith("capture_", ignoreCase = true) ||
                            name.contains("screen_shot", ignoreCase = true) ||
                            name.contains("screencap", ignoreCase = true) ||
                            name.contains("screen-shot", ignoreCase = true)

                    if (isRecent && (isScreenshotPath || isScreenshotName)) {
                        markProcessed(id, contentUri)
                        Log.d(TAG, "Confirmed new screenshot: $name at $contentUri")

                        // Load preview bitmap & extract dynamic palette
                        val bitmap = MediaStoreOrganizer.loadThumbnail(context, contentUri, targetSize = 350)
                        val palette = bitmap?.let { bmp -> PaletteExtractor.extractColors(bmp) }
                            ?: com.screenarc.app.model.DynamicPaletteColors()

                        val item = ScreenshotItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            dateAdded = dateAddedSec,
                            size = size,
                            relativePath = path,
                            mimeType = mime,
                            bitmapPreview = bitmap,
                            paletteColors = palette
                        )

                        scope.launch(Dispatchers.Main) {
                            onScreenshotDetected(item)
                        }
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking latest screenshot: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "ScreenshotObserver"
    }
}

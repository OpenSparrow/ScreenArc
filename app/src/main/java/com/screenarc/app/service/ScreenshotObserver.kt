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
    private val observerStartTimeSec: Long = System.currentTimeMillis() / 1000
    private var debounceJob: Job? = null

    init {
        // Pre-populate processed IDs with existing screenshots so they are not falsely detected on startup
        try {
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            context.contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                var count = 0
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                while (cursor.moveToNext() && count < 25) {
                    if (idCol != -1) {
                        processedIds.add(cursor.getLong(idCol))
                    }
                    count++
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not pre-populate existing screenshot IDs: ${e.message}")
        }
    }

    fun markProcessed(id: Long, uri: Uri? = null) {
        processedIds.add(id)
        excludeId(id)
        uri?.let {
            processedUris.add(it.toString())
            excludeUri(it)
        }
    }

    /** Marks a URI as handled without knowing its MediaStore id (e.g. a copy the
     *  app itself wrote to Pictures/Screenshots) so it is never re-detected. */
    fun markProcessedUri(uri: Uri) {
        processedUris.add(uri.toString())
        excludeUri(uri)
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

                    // Check if already processed or excluded
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    if (processedIds.contains(id) || processedUris.contains(contentUri.toString()) || isExcluded(id, contentUri, name)) {
                        markProcessed(id, contentUri)
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

                    // Ignore internally generated / edited screenshots (redacted, edited, screenarc)
                    val isInternallyGenerated = name.contains("redacted", ignoreCase = true) ||
                            name.contains("screenarc", ignoreCase = true) ||
                            name.contains("_edited", ignoreCase = true) ||
                            name.startsWith("screenshot_redacted", ignoreCase = true)

                    if (isInternallyGenerated) {
                        Log.d(TAG, "Ignoring internally generated / redacted screenshot: $name")
                        markProcessed(id, contentUri)
                        continue
                    }

                    // Normalize timestamp to seconds
                    val dateAddedSec = if (rawDateAdded > 100_000_000_000L) rawDateAdded / 1000 else rawDateAdded
                    val currentTimeSec = System.currentTimeMillis() / 1000
                    val isRecent = dateAddedSec > (observerStartTimeSec - 15) && dateAddedSec > 0L && kotlin.math.abs(currentTimeSec - dateAddedSec) < 60

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

        private val globalExcludedIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
        private val globalExcludedUris = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        private val globalExcludedFileNames = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        fun excludeUri(uri: Uri) {
            val uriStr = uri.toString()
            globalExcludedUris.add(uriStr)
            uri.lastPathSegment?.toLongOrNull()?.let { globalExcludedIds.add(it) }
            if (uriStr.contains("external_primary")) {
                globalExcludedUris.add(uriStr.replace("external_primary", "external"))
            } else if (uriStr.contains("/external/")) {
                globalExcludedUris.add(uriStr.replace("/external/", "/external_primary/"))
            }
        }

        fun excludeId(id: Long) {
            globalExcludedIds.add(id)
        }

        fun excludeFileName(fileName: String) {
            globalExcludedFileNames.add(fileName.lowercase())
        }

        fun isExcluded(id: Long, uri: Uri?, name: String?): Boolean {
            if (globalExcludedIds.contains(id)) return true
            if (uri != null) {
                val uriStr = uri.toString()
                if (globalExcludedUris.contains(uriStr)) return true
                if (uriStr.contains("external_primary") && globalExcludedUris.contains(uriStr.replace("external_primary", "external"))) return true
                if (uriStr.contains("/external/") && globalExcludedUris.contains(uriStr.replace("/external/", "/external_primary/"))) return true
            }
            if (!name.isNullOrBlank()) {
                val lower = name.lowercase()
                if (globalExcludedFileNames.contains(lower)) return true
                if (lower.contains("redacted") || lower.contains("screenarc") || lower.contains("_edited") || lower.startsWith("screenshot_redacted")) return true
            }
            return false
        }
    }
}

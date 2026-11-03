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
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
            )?.use { cursor ->
                var count = 0
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                while (cursor.moveToNext() && count < 50) {
                    if (idCol != -1) {
                        processedIds.add(cursor.getLong(idCol))
                    }
                    count++
                }
            }
            Log.d(TAG, "Pre-populated ${processedIds.size} existing screenshot IDs at observer startup")
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
            // Wait slightly for system screenshot tool to begin flushing file
            delay(300)
            checkLatestScreenshot(attempt = 1, triggerUri = uri)
        }
    }

    private suspend fun checkLatestScreenshot(attempt: Int = 1, triggerUri: Uri? = null) {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATE_TAKEN,
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
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATA
            )
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        try {
            // Check if triggerUri points to a specific item ID (e.g. content://media/external/images/media/12345)
            val isSpecificItem = triggerUri != null && triggerUri.lastPathSegment?.toLongOrNull() != null

            val cursor = if (isSpecificItem) {
                context.contentResolver.query(triggerUri!!, projection, null, null, null)
                    ?: context.contentResolver.query(collection, projection, null, null, sortOrder)
            } else {
                context.contentResolver.query(collection, projection, null, null, sortOrder)
            }

            cursor?.use {
                var checkedCount = 0
                while (it.moveToNext() && checkedCount < 10) {
                    checkedCount++
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateAddedCol = it.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    val dateModifiedCol = it.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                    val dateTakenCol = it.getColumnIndex("datetaken")
                    val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    } else {
                        it.getColumnIndex(MediaStore.Images.Media.DATA)
                    }

                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn) ?: ""
                    val size = it.getLong(sizeColumn)
                    val mime = it.getString(mimeColumn) ?: "image/png"
                    val path = if (pathColumn != -1) it.getString(pathColumn) ?: "" else ""

                    // Check if file is still writing (IS_PENDING = 1 or 0-byte file)
                    val isPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val isPendingIndex = it.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                        isPendingIndex != -1 && it.getInt(isPendingIndex) == 1
                    } else false

                    if (isPending || size <= 0) {
                        if (attempt < 8) {
                            Log.d(TAG, "Screenshot still pending or size=0 (attempt $attempt), retrying in 300ms...")
                            scope.launch(Dispatchers.IO) {
                                delay(300L)
                                checkLatestScreenshot(attempt + 1, triggerUri)
                            }
                            return
                        }
                    }

                    // Check if already processed or globally excluded
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
                    val lowerName = name.lowercase()
                    val isInternallyGenerated = lowerName.contains("redacted") ||
                            lowerName.contains("screenarc") ||
                            lowerName.contains("_edited") ||
                            lowerName.startsWith("screenshot_redacted")

                    if (isInternallyGenerated) {
                        Log.d(TAG, "Ignoring internally generated / redacted screenshot: $name")
                        markProcessed(id, contentUri)
                        continue
                    }

                    // Normalize timestamps
                    val rawDateAdded = if (dateAddedCol != -1) it.getLong(dateAddedCol) else 0L
                    val rawDateModified = if (dateModifiedCol != -1) it.getLong(dateModifiedCol) else 0L
                    val rawDateTaken = if (dateTakenCol != -1) it.getLong(dateTakenCol) else 0L

                    val dateSec = when {
                        rawDateAdded > 0L -> if (rawDateAdded > 100_000_000_000L) rawDateAdded / 1000 else rawDateAdded
                        rawDateModified > 0L -> if (rawDateModified > 100_000_000_000L) rawDateModified / 1000 else rawDateModified
                        rawDateTaken > 0L -> if (rawDateTaken > 100_000_000_000L) rawDateTaken / 1000 else rawDateTaken
                        else -> System.currentTimeMillis() / 1000
                    }
                    val currentTimeSec = System.currentTimeMillis() / 1000

                    // Recency check: within the last 10 minutes or taken since observer started (with grace period)
                    val isRecent = (currentTimeSec - dateSec) in -60..600 || dateSec >= (observerStartTimeSec - 60)

                    // Support diverse OEM screenshot directories & naming patterns
                    val lowerPath = path.lowercase().replace('\\', '/')
                    val isScreenshotPath = lowerPath.contains("screenshot") ||
                            lowerPath.contains("screencap") ||
                            lowerPath.contains("screen_capture") ||
                            lowerPath.contains("screencapture") ||
                            lowerPath.contains("screen-capture")

                    val isScreenshotName = lowerName.contains("screenshot") ||
                            lowerName.contains("screen_shot") ||
                            lowerName.contains("screen-shot") ||
                            lowerName.contains("screencap") ||
                            lowerName.contains("screen_cap") ||
                            lowerName.contains("screencapture") ||
                            lowerName.contains("screen_capture") ||
                            lowerName.startsWith("screen") ||
                            lowerName.startsWith("capture") ||
                            lowerName.contains("smartselect") ||
                            lowerName.contains("quickmemo")

                    if (isRecent && (isScreenshotPath || isScreenshotName)) {
                        markProcessed(id, contentUri)
                        Log.d(TAG, "Confirmed new screenshot detected: $name at $contentUri")

                        // Load preview bitmap & extract dynamic palette
                        val bitmap = MediaStoreOrganizer.loadThumbnail(context, contentUri, targetSize = 350)
                        val palette = bitmap?.let { bmp -> PaletteExtractor.extractColors(bmp) }
                            ?: com.screenarc.app.model.DynamicPaletteColors()

                        val item = ScreenshotItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            dateAdded = dateSec,
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

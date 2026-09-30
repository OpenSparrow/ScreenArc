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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ScreenshotObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onScreenshotDetected: (ScreenshotItem) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val processedIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
    private val processedUris = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val scanMutex = Mutex()
    private var debounceJob: Job? = null

    fun markProcessed(id: Long, uri: Uri? = null) {
        processedIds.add(id)
        uri?.let { processedUris.add(it.toString()) }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "MediaStore change detected: $uri")
        scheduleRecentScreenshotScan()
    }

    fun scanRecentScreenshots() {
        scheduleRecentScreenshotScan(delayMillis = 0L)
    }

    private fun scheduleRecentScreenshotScan(delayMillis: Long = INITIAL_SCAN_DELAY_MS) {
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.IO) {
            delay(delayMillis)
            scope.launch(Dispatchers.IO) {
                scanMutex.withLock {
                    for (attempt in 1..MAX_ATTEMPTS) {
                        if (!checkRecentScreenshots(attempt)) return@withLock
                        if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS * attempt)
                    }
                }
            }
        }
    }

    private suspend fun checkRecentScreenshots(attempt: Int): Boolean {
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
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
        val pathColumnName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            MediaStore.Images.Media.DATA
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val cutoff = System.currentTimeMillis() / 1000 - RECENT_WINDOW_SECONDS

        try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Images.Media.DATE_ADDED} >= ? AND " +
                    "(${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR $pathColumnName LIKE ?)",
                arrayOf(cutoff.toString(), "%Screenshot%", "%Screenshots%"),
                sortOrder
            ) ?: run {
                Log.w(TAG, "MediaStore returned no cursor while scanning for screenshots")
                return false
            }

            cursor.use {
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
                val isPendingIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                } else {
                    -1
                }

                var scannedRows = 0
                while (it.moveToNext() && scannedRows++ < MAX_ROWS_TO_SCAN) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn).orEmpty()
                    val dateAdded = it.getLong(dateColumn)
                    val size = it.getLong(sizeColumn)
                    val mime = it.getString(mimeColumn) ?: "image/png"
                    val path = it.getString(pathColumn).orEmpty()
                    if (dateAdded < cutoff || !ScreenshotDetection.isScreenshotCandidate(name, path)) continue

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    if (processedIds.contains(id) || processedUris.contains(contentUri.toString())) continue

                    if (ScreenshotDetection.isCategorizedSubfolder(
                            path,
                            scopedStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        )
                    ) {
                        Log.d(TAG, "Ignoring screenshot in categorized subfolder: $path")
                        markProcessed(id, contentUri)
                        continue
                    }

                    val isPending = isPendingIndex != -1 && it.getInt(isPendingIndex) == 1
                    if (isPending || size <= 0L) {
                        Log.d(TAG, "Screenshot is still being written (attempt $attempt): $name")
                        return true
                    }

                    markProcessed(id, contentUri)
                    Log.d(TAG, "Confirmed new screenshot: $name at $contentUri")

                    val bitmap = MediaStoreOrganizer.loadThumbnail(context, contentUri, targetSize = 350)
                    val palette = bitmap?.let { PaletteExtractor.extractColors(it) }
                        ?: com.screenarc.app.model.DynamicPaletteColors()
                    val item = ScreenshotItem(
                        id = id,
                        uri = contentUri,
                        displayName = name,
                        dateAdded = dateAdded,
                        size = size,
                        relativePath = path,
                        mimeType = mime,
                        bitmapPreview = bitmap,
                        paletteColors = palette
                    )
                    scope.launch(Dispatchers.Main) {
                        onScreenshotDetected(item)
                    }
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking latest screenshot: ${e.message}", e)
            return false
        }
        return false
    }

    companion object {
        private const val TAG = "ScreenshotObserver"
        private const val INITIAL_SCAN_DELAY_MS = 350L
        private const val RETRY_DELAY_MS = 250L
        private const val MAX_ATTEMPTS = 4
        private const val MAX_ROWS_TO_SCAN = 100
        private const val RECENT_WINDOW_SECONDS = 90L
    }
}

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

    private val lastProcessedId = AtomicLong(-1L)
    private var debounceJob: Job? = null

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "MediaStore change detected: $uri")

        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.IO) {
            // Small debounce to let screenshot writer finish flushing image data
            delay(500)
            checkLatestScreenshot()
        }
    }

    private suspend fun checkLatestScreenshot() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                if (it.moveToFirst()) {
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

                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn) ?: ""
                    val dateAdded = it.getLong(dateColumn)
                    val size = it.getLong(sizeColumn)
                    val mime = it.getString(mimeColumn) ?: "image/png"
                    val path = it.getString(pathColumn) ?: ""

                    // Check if it's a new item we haven't processed yet
                    if (id == lastProcessedId.get()) {
                        return
                    }

                    // Verify if it's a screenshot:
                    val currentTimeSec = System.currentTimeMillis() / 1000
                    val isRecent = (currentTimeSec - dateAdded) < 35 // within last 35 seconds
                    val isScreenshotPath = path.contains("Screenshots", ignoreCase = true)
                    val isScreenshotName = name.contains("Screenshot", ignoreCase = true)

                    if (isRecent && (isScreenshotPath || isScreenshotName)) {
                        lastProcessedId.set(id)
                        val contentUri = ContentUris.withAppendedId(collection, id)
                        Log.d(TAG, "Confirmed new screenshot: $name at $contentUri")

                        // Load preview bitmap & extract dynamic palette
                        val bitmap = MediaStoreOrganizer.loadThumbnail(context, contentUri, targetSize = 350)
                        val palette = bitmap?.let { bmp -> PaletteExtractor.extractColors(bmp) }
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

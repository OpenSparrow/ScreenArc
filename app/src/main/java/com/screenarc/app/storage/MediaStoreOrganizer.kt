package com.screenarc.app.storage

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.screenarc.app.model.Category
import com.screenarc.app.model.ScreenshotItem
import com.screenarc.app.palette.PaletteExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaStoreOrganizer {
    private const val TAG = "MediaStoreOrganizer"

    /**
     * Move a screenshot item to a sub-folder under Pictures/Screenshots/<category.subfolderName>/
     */
    suspend fun moveToCategory(
        context: Context,
        screenshot: ScreenshotItem,
        category: Category
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val relativeSubfolder = "Pictures/Screenshots/${category.subfolderName}/"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Try direct metadata update first (fastest and keeps same URI)
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativeSubfolder)
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }

                try {
                    val rows = resolver.update(screenshot.uri, updateValues, null, null)
                    if (rows > 0) {
                        Log.d(TAG, "Successfully updated relative path via MediaStore for ${screenshot.displayName}")
                        return@withContext Result.success(screenshot.uri)
                    }
                } catch (secEx: SecurityException) {
                    Log.w(TAG, "Direct update threw SecurityException, falling back to copy-and-insert: ${secEx.message}")
                }

                // Fallback: Copy content to new URI in destination folder and delete original
                val newUri = copyToNewFolder(context, screenshot, relativeSubfolder)
                if (newUri != null) {
                    try {
                        resolver.delete(screenshot.uri, null, null)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not delete original after copy: ${e.message}")
                    }
                    return@withContext Result.success(newUri)
                }
            } else {
                // Pre-Android 10 legacy fallback
                val newUri = copyToNewFolderPreQ(context, screenshot, category.subfolderName)
                if (newUri != null) {
                    try {
                        resolver.delete(screenshot.uri, null, null)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not delete original: ${e.message}")
                    }
                    return@withContext Result.success(newUri)
                }
            }
            Result.failure(Exception("Failed to move screenshot to ${category.name}"))
        } catch (e: Exception) {
            Log.e(TAG, "Error moving screenshot: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun copyToNewFolderPublic(
        context: Context,
        screenshot: ScreenshotItem,
        targetRelativePath: String
    ): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyToNewFolder(context, screenshot, targetRelativePath)
        } else {
            val subfolder = targetRelativePath.removePrefix("Pictures/Screenshots/").removeSuffix("/")
            copyToNewFolderPreQ(context, screenshot, subfolder)
        }
    }

    private fun copyToNewFolder(
        context: Context,
        screenshot: ScreenshotItem,
        targetRelativePath: String
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, screenshot.displayName)
            put(MediaStore.Images.Media.MIME_TYPE, screenshot.mimeType.ifEmpty { "image/png" })
            put(MediaStore.Images.Media.RELATIVE_PATH, targetRelativePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val newUri = resolver.insert(collection, contentValues) ?: return null

        try {
            resolver.openInputStream(screenshot.uri)?.use { input ->
                resolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(newUri, publishValues, null, null)
            }
            return newUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed stream copy: ${e.message}", e)
            resolver.delete(newUri, null, null)
            return null
        }
    }

    private fun copyToNewFolderPreQ(
        context: Context,
        screenshot: ScreenshotItem,
        subfolderName: String
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, screenshot.displayName)
            put(MediaStore.Images.Media.MIME_TYPE, screenshot.mimeType.ifEmpty { "image/png" })
        }
        val newUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null

        return try {
            resolver.openInputStream(screenshot.uri)?.use { input ->
                resolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            newUri
        } catch (e: Exception) {
            resolver.delete(newUri, null, null)
            null
        }
    }


    /**
     * Load screenshots from MediaStore
     */
    suspend fun getRecentScreenshots(context: Context, limit: Int = 100): List<ScreenshotItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ScreenshotItem>()
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
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            } else {
                "${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            }
            val selectionArgs = arrayOf("%Screenshots%", "%Screenshot%")

            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                }

                var count = 0
                while (cursor.moveToNext() && (limit <= 0 || count < limit)) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Screenshot"
                    val dateAdded = cursor.getLong(dateColumn)
                    val size = cursor.getLong(sizeColumn)
                    val mime = cursor.getString(mimeColumn) ?: "image/png"
                    val path = cursor.getString(pathColumn) ?: ""
                    val contentUri = ContentUris.withAppendedId(collection, id)

                    items.add(
                        ScreenshotItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            dateAdded = dateAdded,
                            size = size,
                            relativePath = path,
                            mimeType = mime
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying screenshots: ${e.message}", e)
        }
        items
    }

    /**
     * Load all screenshots from MediaStore without limit for bulk processing
     */
    suspend fun getAllScreenshots(context: Context): List<ScreenshotItem> = getRecentScreenshots(context, limit = 0)


    /**
     * Load full bitmap or thumbnail for a screenshot uri
     */
    suspend fun loadThumbnail(context: Context, uri: Uri, targetSize: Int = 300): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    return@withContext context.contentResolver.loadThumbnail(
                        uri,
                        android.util.Size(targetSize, targetSize),
                        null
                    )
                } catch (e: Exception) {
                    // fallback to stream
                }
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)

                val sampleSize = calculateInSampleSize(options, targetSize, targetSize)
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }

                context.contentResolver.openInputStream(uri)?.use { decodeStream ->
                    BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load thumbnail for $uri: ${e.message}")
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Helper to create a simulated screenshot for testing/demoing the floating overlay in emulator or device
     */
    suspend fun createSimulatedScreenshot(
        context: Context,
        label: String = "Design Mockup",
        colorTheme: Int = 0xFF6750A4.toInt()
    ): ScreenshotItem = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
        val fileName = "Screenshot_$dateFormat.png"

        // Create a stylish demo bitmap
        val width = 720
        val height = 1280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val bgPaint = Paint().apply {
            color = colorTheme
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Gradient or card shapes
        val cardPaint = Paint().apply {
            color = AndroidColor.WHITE
            alpha = 230
            isAntiAlias = true
        }
        canvas.drawRoundRect(60f, 180f, (width - 60).toFloat(), 600f, 40f, 40f, cardPaint)
        canvas.drawRoundRect(60f, 660f, (width - 60).toFloat(), 1080f, 40f, 40f, cardPaint)

        // Text
        val textPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 52f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("ScreenArc Test Capture", 100f, 280f, textPaint)

        val subPaint = Paint().apply {
            color = AndroidColor.DKGRAY
            textSize = 34f
            isAntiAlias = true
        }
        canvas.drawText(label, 100f, 360f, subPaint)
        canvas.drawText("Captured at $dateFormat", 100f, 420f, subPaint)

        // Save to MediaStore
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots/")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        var savedUri = context.contentResolver.insert(collection, contentValues)
        if (savedUri != null) {
            context.contentResolver.openOutputStream(savedUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(savedUri, finishValues, null, null)
            }
        } else {
            savedUri = Uri.parse("content://media/external/images/media/simulated_${timestamp}")
        }

        val realId = savedUri?.let { uri ->
            try { ContentUris.parseId(uri) } catch (e: Exception) { null }
        } ?: timestamp

        val palette = PaletteExtractor.extractColors(bitmap)

        ScreenshotItem(
            id = realId,
            uri = savedUri,
            displayName = fileName,
            dateAdded = timestamp / 1000,
            size = (width * height * 4).toLong(),
            relativePath = "Pictures/Screenshots/",
            mimeType = "image/png",
            bitmapPreview = bitmap,
            paletteColors = palette
        )
    }
}

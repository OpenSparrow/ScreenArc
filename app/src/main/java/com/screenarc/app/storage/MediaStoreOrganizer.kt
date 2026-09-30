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
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
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
    ): Result<Uri> = moveToRelativeFolder(
        context = context,
        screenshot = screenshot,
        targetRelativePath = "Pictures/Screenshots/${category.subfolderName}/"
    )

    suspend fun moveToRelativeFolder(
        context: Context,
        screenshot: ScreenshotItem,
        targetRelativePath: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val relativeSubfolder = try {
            normalizeScreenshotRelativePath(targetRelativePath)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(e)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val destinationName = uniqueDisplayName(
                    resolver = resolver,
                    screenshot = screenshot,
                    targetRelativePath = relativeSubfolder
                )
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativeSubfolder)
                    put(MediaStore.Images.Media.DISPLAY_NAME, destinationName)
                }

                try {
                    val rows = resolver.update(screenshot.uri, updateValues, null, null)
                    if (rows > 0) {
                        Log.d(TAG, "Moved ${screenshot.displayName} to $relativeSubfolder via MediaStore")
                        return@withContext Result.success(screenshot.uri)
                    }
                } catch (secEx: SecurityException) {
                    Log.w(TAG, "MediaStore update denied; using copy-and-delete fallback: ${secEx.message}")
                }

                val newUri = copyToNewFolder(context, screenshot, relativeSubfolder, destinationName)
                    ?: return@withContext Result.failure(Exception("Could not copy screenshot to $relativeSubfolder"))
                try {
                    check(resolver.delete(screenshot.uri, null, null) > 0) {
                        "Copied screenshot but could not remove the original"
                    }
                } catch (e: Exception) {
                    try {
                        resolver.delete(newUri, null, null)
                    } catch (cleanupError: Exception) {
                        e.addSuppressed(cleanupError)
                    }
                    return@withContext Result.failure(e)
                }
                return@withContext Result.success(newUri)
            } else {
                val subfolder = relativeSubfolder.removePrefix("Pictures/Screenshots/").removeSuffix("/")
                val newUri = copyToNewFolderPreQ(context, screenshot, subfolder)
                if (newUri != null) {
                    try {
                        check(resolver.delete(screenshot.uri, null, null) > 0) {
                            "Copied screenshot but could not remove the original"
                        }
                    } catch (e: Exception) {
                        try {
                            resolver.delete(newUri, null, null)
                        } catch (cleanupError: Exception) {
                            e.addSuppressed(cleanupError)
                        }
                        return@withContext Result.failure(e)
                    }
                    return@withContext Result.success(newUri)
                }
            }
            Result.failure(Exception("Failed to move screenshot to $relativeSubfolder"))
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
        val normalizedPath = try {
            normalizeScreenshotRelativePath(targetRelativePath)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid screenshot destination path: ${e.message}", e)
            return@withContext null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val displayName = uniqueDisplayName(context.contentResolver, screenshot, normalizedPath)
            copyToNewFolder(context, screenshot, normalizedPath, displayName)
        } else {
            val subfolder = normalizedPath.removePrefix("Pictures/Screenshots/").removeSuffix("/")
            copyToNewFolderPreQ(context, screenshot, subfolder)
        }
    }

    suspend fun getDisplayName(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("MediaStore returned an empty display name for $uri")
            } else {
                throw IllegalStateException("MediaStore could not find moved screenshot $uri")
            }
        } ?: throw IllegalStateException("MediaStore could not query moved screenshot $uri")
    }

    private fun copyToNewFolder(
        context: Context,
        screenshot: ScreenshotItem,
        targetRelativePath: String,
        displayName: String
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, screenshot.mimeType.ifEmpty { "image/png" })
            put(MediaStore.Images.Media.RELATIVE_PATH, targetRelativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val newUri = resolver.insert(collection, contentValues) ?: return null

        return try {
            val copiedBytes = resolver.openInputStream(screenshot.uri)?.use { input ->
                resolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                } ?: throw IllegalStateException("Could not open destination stream")
            } ?: throw IllegalStateException("Could not open source stream")
            check(copiedBytes > 0L) { "Screenshot copy was empty" }

            val publishValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            check(resolver.update(newUri, publishValues, null, null) > 0) {
                "Could not publish copied screenshot"
            }
            newUri
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
        val screenshotsDirectory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Screenshots"
        ).canonicalFile
        val destinationDirectory = File(screenshotsDirectory, subfolderName).canonicalFile
        require(destinationDirectory.path.startsWith(screenshotsDirectory.path + File.separator) ||
            destinationDirectory == screenshotsDirectory
        ) { "Screenshot destination must be inside Pictures/Screenshots" }
        if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
            throw IllegalStateException("Could not create screenshot destination folder")
        }

        val destinationFile = availableLegacyDestination(destinationDirectory, screenshot.displayName)
        val temporaryFile = File(destinationDirectory, ".${destinationFile.name}.${System.nanoTime()}.tmp")
        return try {
            val copiedBytes = resolver.openInputStream(screenshot.uri)?.use { input ->
                FileOutputStream(temporaryFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Could not open source screenshot")
            check(copiedBytes > 0L) { "Screenshot copy was empty" }
            check(temporaryFile.renameTo(destinationFile)) { "Could not finalize screenshot copy" }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, destinationFile.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, destinationFile.name)
                put(MediaStore.Images.Media.MIME_TYPE, screenshot.mimeType.ifEmpty { "image/png" })
            }
            val newUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (newUri == null) {
                destinationFile.delete()
                return null
            }
            newUri
        } catch (e: Exception) {
            temporaryFile.delete()
            destinationFile.delete()
            Log.e(TAG, "Failed legacy storage copy: ${e.message}", e)
            null
        }
    }

    private fun availableLegacyDestination(directory: File, originalName: String): File {
        val original = File(directory, originalName)
        if (!original.exists()) return original

        val extensionIndex = originalName.lastIndexOf('.').takeIf { it > 0 } ?: originalName.length
        val baseName = originalName.substring(0, extensionIndex)
        val extension = originalName.substring(extensionIndex)
        val timestamp = Instant.now().toString()
        var candidate = File(directory, "${baseName}_$timestamp$extension")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_${timestamp}_${suffix++}$extension")
        }
        return candidate
    }

    private fun uniqueDisplayName(
        resolver: ContentResolver,
        screenshot: ScreenshotItem,
        targetRelativePath: String
    ): String {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        if (!displayNameExists(
                resolver,
                collection,
                targetRelativePath,
                screenshot.displayName,
                screenshot.uri,
                screenshot.relativePath
            )
        ) {
            return screenshot.displayName
        }

        val extensionIndex = screenshot.displayName.lastIndexOf('.')
            .takeIf { it > 0 }
            ?: screenshot.displayName.length
        val baseName = screenshot.displayName.substring(0, extensionIndex)
        val extension = screenshot.displayName.substring(extensionIndex)
        val timestampedName = "${baseName}_${Instant.now()}$extension"
        var candidate = timestampedName
        var suffix = 1
        while (displayNameExists(
                resolver,
                collection,
                targetRelativePath,
                candidate,
                screenshot.uri,
                screenshot.relativePath
            )
        ) {
            candidate = "${baseName}_${Instant.now()}_${suffix++}$extension"
        }
        return candidate
    }

    private fun displayNameExists(
        resolver: ContentResolver,
        collection: Uri,
        relativePath: String,
        displayName: String,
        sourceUri: Uri,
        sourceRelativePath: String
    ): Boolean {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} = ?"
        val cursor = resolver.query(collection, projection, selection, arrayOf(relativePath, displayName), null)
            ?: throw IllegalStateException("Could not check destination filename in MediaStore")
        cursor.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val existingId = cursor.getLong(idColumn)
                val isSource = sourceRelativePath.trimEnd('/') == relativePath.trimEnd('/') &&
                    sourceUri.lastPathSegment == existingId.toString()
                if (!isSource) return true
            }
        }
        return false
    }

    private fun normalizeScreenshotRelativePath(path: String): String {
        val segments = path.replace('\\', '/').split('/').filter(String::isNotBlank)
        require(segments.isNotEmpty() && segments.none { it == "." || it == ".." }) {
            "Invalid screenshot destination path"
        }
        val normalizedSegments = when {
            segments.first().equals("Pictures", ignoreCase = true) -> {
                require(segments.size >= 2 && segments[1].equals("Screenshots", ignoreCase = true)) {
                    "Destinations must be inside Pictures/Screenshots"
                }
                segments
            }
            segments.first().equals("Screenshots", ignoreCase = true) -> listOf("Pictures") + segments
            else -> listOf("Pictures", "Screenshots") + segments
        }
        return normalizedSegments.joinToString("/", postfix = "/")
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

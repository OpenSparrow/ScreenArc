package com.screenarc.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.screenarc.app.model.OcrBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class RedactionType {
    BLACKOUT,
    PIXELATE
}

data class RedactionItem(
    val block: OcrBlock,
    val type: RedactionType
)

data class FreehandPath(
    val points: List<androidx.compose.ui.geometry.Offset>,
    val strokeWidth: Float = 36f
)

object PrivacyRedactor {
    private const val TAG = "PrivacyRedactor"

    /**
     * Renders redacting blackout/pixelation blocks and freehand blur/blackout strokes
     * onto a new copy of the bitmap, saves to MediaStore, and returns the new image Uri.
     * ORIGINAL IMAGE IS NEVER MODIFIED.
     */
    suspend fun saveRedactedScreenshot(
        context: Context,
        originalUri: Uri,
        redactions: List<RedactionItem>,
        freehandPaths: List<FreehandPath>,
        imageWidth: Int,
        imageHeight: Int
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val baseBitmap: Bitmap = resolver.openInputStream(originalUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext null

            // Create mutable copy
            val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
            if (baseBitmap != mutableBitmap) {
                baseBitmap.recycle()
            }
            val canvas = Canvas(mutableBitmap)

            val blackoutPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            // Apply block redactions
            for (item in redactions) {
                val block = item.block
                val left = (block.left ?: 0).coerceAtLeast(0)
                val top = (block.top ?: 0).coerceAtLeast(0)
                val right = (block.right ?: 0).coerceAtMost(mutableBitmap.width)
                val bottom = (block.bottom ?: 0).coerceAtMost(mutableBitmap.height)
                val w = right - left
                val h = bottom - top
                if (w <= 0 || h <= 0) continue

                when (item.type) {
                    RedactionType.BLACKOUT -> {
                        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), blackoutPaint)
                    }
                    RedactionType.PIXELATE -> {
                        pixelateRect(mutableBitmap, left, top, w, h, pixelSize = 18)
                    }
                }
            }

            // Apply freehand blur/blackout brush strokes
            val brushPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }

            for (pathItem in freehandPaths) {
                if (pathItem.points.size < 2) continue
                brushPaint.strokeWidth = pathItem.strokeWidth
                val path = Path()
                path.moveTo(pathItem.points[0].x, pathItem.points[0].y)
                for (i in 1 until pathItem.points.size) {
                    path.lineTo(pathItem.points[i].x, pathItem.points[i].y)
                }
                canvas.drawPath(path, brushPaint)
            }

            // Save new image to MediaStore
            val timestamp = System.currentTimeMillis()
            val fileName = "Screenshot_Redacted_${timestamp}.png"
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

            val newUri = resolver.insert(collection, contentValues) ?: return@withContext null

            resolver.openOutputStream(newUri)?.use { out ->
                mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(newUri, publishValues, null, null)
            }

            mutableBitmap.recycle()
            Log.d(TAG, "Saved redacted copy to $newUri")
            newUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving redacted screenshot: ${e.message}", e)
            null
        }
    }

    private fun pixelateRect(bitmap: Bitmap, startX: Int, startY: Int, width: Int, height: Int, pixelSize: Int) {
        val safePixelSize = pixelSize.coerceAtLeast(4)
        for (x in startX until startX + width step safePixelSize) {
            for (y in startY until startY + height step safePixelSize) {
                val blockW = minOf(safePixelSize, startX + width - x)
                val blockH = minOf(safePixelSize, startY + height - y)

                // Sample center pixel
                val sampleX = (x + blockW / 2).coerceIn(0, bitmap.width - 1)
                val sampleY = (y + blockH / 2).coerceIn(0, bitmap.height - 1)
                val pixelColor = bitmap.getPixel(sampleX, sampleY)

                for (bx in x until x + blockW) {
                    for (by in y until y + blockH) {
                        if (bx < bitmap.width && by < bitmap.height) {
                            bitmap.setPixel(bx, by, pixelColor)
                        }
                    }
                }
            }
        }
    }

    fun shareRedactedImage(context: Context, imageUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Redacted Screenshot"))
    }
}

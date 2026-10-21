package com.screenarc.app.ui.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.screenarc.app.service.ScreenshotObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manages full-resolution, non-destructive export of image annotations and redactions.
 *
 * Privacy & Security Guarantees:
 * - Flattened bitmap output: no vectors, layers, or history metadata are preserved in the saved file.
 * - Strips all EXIF location and camera tags by writing a clean bitmap stream.
 * - Enforces 100% opaque black for solid redaction tools.
 */
class ExportManager(private val context: Context) {

    companion object {
        private const val TAG = "ExportManager"

        enum class ExportFormat(val mimeType: String, val extension: String, val compressFormat: Bitmap.CompressFormat) {
            PNG("image/png", ".png", Bitmap.CompressFormat.PNG),
            JPEG("image/jpeg", ".jpg", Bitmap.CompressFormat.JPEG)
        }
    }

    /**
     * Exports the edited screenshot at full original resolution by replaying all EditOps
     * with scaled coordinates onto the master bitmap.
     */
    suspend fun exportImage(
        originalUri: Uri,
        ops: List<EditOp>,
        previewWidth: Int,
        previewHeight: Int,
        format: ExportFormat = ExportFormat.PNG,
        quality: Int = 100
    ): Result<Uri> = withContext(Dispatchers.IO) {
        var baseBitmap: Bitmap? = null
        var fullResMosaic: Bitmap? = null
        var exportedBitmap: Bitmap? = null

        try {
            val resolver = context.contentResolver

            // 1. Decode original full-resolution bitmap with graceful OOM fallback
            baseBitmap = decodeFullResolution(originalUri)
                ?: return@withContext Result.failure(Exception("Unable to decode source image from URI: $originalUri"))

            val originalWidth = baseBitmap.width
            val originalHeight = baseBitmap.height

            val scaleX = if (previewWidth > 0) originalWidth.toFloat() / previewWidth.toFloat() else 1.0f
            val scaleY = if (previewHeight > 0) originalHeight.toFloat() / previewHeight.toFloat() else 1.0f
            val scaleAvg = (scaleX + scaleY) / 2f

            // 2. Create mutable copy for drawing
            exportedBitmap = try {
                baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
            } catch (e: OutOfMemoryError) {
                // If ARGB_8888 copy fails on 2GB devices, fallback to RGB_565
                Log.w(TAG, "ARGB_8888 allocation failed, falling back to RGB_565 for export")
                baseBitmap.copy(Bitmap.Config.RGB_565, true)
            }

            if (baseBitmap != exportedBitmap) {
                baseBitmap.recycle()
                baseBitmap = null
            }

            val canvas = Canvas(exportedBitmap)

            // 3. Precompute full-res mosaic if pixelate operations exist
            val hasPixelateOps = ops.any { it is EditOp.PixelateStroke }
            if (hasPixelateOps) {
                val pixelateRenderer = PixelateRenderer()
                fullResMosaic = pixelateRenderer.createFullResMosaic(
                    exportedBitmap,
                    (PixelateRenderer.DEFAULT_BLOCK_SIZE * scaleAvg).toInt()
                )
            }

            // 4. Replay all EditOps in vector order onto the full-resolution canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            for (op in ops) {
                when (op) {
                    is EditOp.FreehandStroke -> {
                        paint.style = Paint.Style.STROKE
                        paint.color = op.color
                        paint.alpha = (op.alpha.coerceIn(0f, 1f) * 255).toInt()
                        paint.strokeWidth = op.strokeWidth * scaleAvg

                        val path = buildSmoothedPath(op.points, scaleX, scaleY)
                        canvas.drawPath(path, paint)
                    }

                    is EditOp.PixelateStroke -> {
                        if (fullResMosaic != null && !fullResMosaic.isRecycled) {
                            val path = buildSmoothedPath(op.points, scaleX, scaleY)
                            canvas.save()
                            try {
                                canvas.clipPath(path)
                                canvas.drawBitmap(fullResMosaic, 0f, 0f, null)
                            } finally {
                                canvas.restore()
                            }
                        }
                    }

                    is EditOp.SolidRedactStroke -> {
                        paint.style = Paint.Style.STROKE
                        paint.color = Color.BLACK
                        paint.alpha = 255 // Strictly opaque
                        paint.strokeWidth = op.strokeWidth * scaleAvg

                        val path = buildSmoothedPath(op.points, scaleX, scaleY)
                        canvas.drawPath(path, paint)
                    }

                    is EditOp.SolidRedactBox -> {
                        paint.style = Paint.Style.FILL
                        paint.color = Color.BLACK
                        paint.alpha = 255 // Strictly opaque

                        val rect = RectF(
                            minOf(op.left, op.right) * scaleX,
                            minOf(op.top, op.bottom) * scaleY,
                            maxOf(op.left, op.right) * scaleX,
                            maxOf(op.top, op.bottom) * scaleY
                        )
                        canvas.drawRect(rect, paint)
                    }

                    is EditOp.ShapeOp -> {
                        paint.style = if (op.isFilled || op.isSolidRedact) Paint.Style.FILL else Paint.Style.STROKE
                        paint.color = if (op.isSolidRedact) Color.BLACK else op.color
                        paint.alpha = if (op.isSolidRedact) 255 else (op.alpha.coerceIn(0f, 1f) * 255).toInt()
                        paint.strokeWidth = op.strokeWidth * scaleAvg

                        val left = minOf(op.startX, op.endX) * scaleX
                        val top = minOf(op.startY, op.endY) * scaleY
                        val right = maxOf(op.startX, op.endX) * scaleX
                        val bottom = maxOf(op.startY, op.endY) * scaleY

                        when (op.shapeType) {
                            ShapeType.RECTANGLE -> {
                                canvas.drawRect(left, top, right, bottom, paint)
                            }
                            ShapeType.ROUNDED_RECTANGLE -> {
                                val radius = op.cornerRadius * scaleAvg
                                canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
                            }
                            ShapeType.OVAL -> {
                                canvas.drawOval(RectF(left, top, right, bottom), paint)
                            }
                            ShapeType.LINE -> {
                                canvas.drawLine(
                                    op.startX * scaleX,
                                    op.startY * scaleY,
                                    op.endX * scaleX,
                                    op.endY * scaleY,
                                    paint
                                )
                            }
                            ShapeType.ARROW -> {
                                drawArrowOnCanvas(
                                    canvas,
                                    paint,
                                    op.startX * scaleX,
                                    op.startY * scaleY,
                                    op.endX * scaleX,
                                    op.endY * scaleY,
                                    scaleAvg
                                )
                            }
                        }
                    }

                    is EditOp.TextOp -> {
                        paint.style = Paint.Style.FILL
                        paint.color = op.color
                        paint.alpha = (op.alpha.coerceIn(0f, 1f) * 255).toInt()
                        paint.textSize = op.textSize * scaleAvg
                        paint.isFakeBoldText = op.isBold

                        // Optional background plate behind text for legibility
                        op.backgroundColor?.let { bgColor ->
                            val bgPaint = Paint().apply {
                                color = bgColor
                                style = Paint.Style.FILL
                            }
                            val textWidth = paint.measureText(op.text)
                            val textHeight = op.textSize * scaleAvg
                            val bgRect = RectF(
                                (op.x * scaleX) - 8f,
                                (op.y * scaleY) - textHeight,
                                (op.x * scaleX) + textWidth + 8f,
                                (op.y * scaleY) + 8f
                            )
                            canvas.drawRoundRect(bgRect, 8f * scaleAvg, 8f * scaleAvg, bgPaint)
                        }

                        canvas.drawText(op.text, op.x * scaleX, op.y * scaleY, paint)
                    }
                }
            }

            // 5. Save flattened image to MediaStore without EXIF metadata
            val timestamp = System.currentTimeMillis()
            val fileName = "ScreenArc_Edit_${timestamp}${format.extension}"
            ScreenshotObserver.excludeFileName(fileName)

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
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

            val outputUri = resolver.insert(collection, contentValues)
                ?: return@withContext Result.failure(Exception("Failed creating MediaStore entry"))

            ScreenshotObserver.excludeUri(outputUri)

            resolver.openOutputStream(outputUri)?.use { outStream: OutputStream ->
                exportedBitmap.compress(format.compressFormat, quality, outStream)
            } ?: return@withContext Result.failure(Exception("Failed opening output stream for MediaStore Uri"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(outputUri, completeValues, null, null)
            }

            Log.d(TAG, "Successfully exported redacted image to $outputUri")
            Result.success(outputUri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed during image export: ${e.message}", e)
            Result.failure(e)
        } finally {
            try {
                baseBitmap?.recycle()
                fullResMosaic?.recycle()
                exportedBitmap?.recycle()
            } catch (_: Exception) {}
        }
    }

    /**
     * Decodes the original image with OOM fallback handling.
     */
    private fun decodeFullResolution(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        return try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM decoding full image, falling back to inSampleSize = 2")
            try {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Fatal failure decoding image: ${e2.message}", e2)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening URI stream: ${e.message}", e)
            null
        }
    }

    /**
     * Builds a quadratic bezier smoothed path from points with scale factors applied.
     */
    private fun buildSmoothedPath(points: List<OpPoint>, scaleX: Float, scaleY: Float): Path {
        val path = Path()
        if (points.isEmpty()) return path
        if (points.size == 1) {
            val p = points[0]
            path.moveTo(p.x * scaleX, p.y * scaleY)
            path.lineTo(p.x * scaleX + 0.1f, p.y * scaleY + 0.1f)
            return path
        }

        path.moveTo(points[0].x * scaleX, points[0].y * scaleY)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val midX = (prev.x + curr.x) / 2f * scaleX
            val midY = (prev.y + curr.y) / 2f * scaleY
            path.quadTo(prev.x * scaleX, prev.y * scaleY, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x * scaleX, last.y * scaleY)
        return path
    }

    /**
     * Helper to render an arrow on the canvas.
     */
    private fun drawArrowOnCanvas(
        canvas: Canvas,
        paint: Paint,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        scale: Float
    ) {
        canvas.drawLine(x1, y1, x2, y2, paint)

        val arrowHeadLength = 28f * scale
        val arrowAngle = Math.toRadians(30.0).toFloat()
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()

        val x3 = (x2 - arrowHeadLength * cos((angle - arrowAngle).toDouble())).toFloat()
        val y3 = (y2 - arrowHeadLength * sin((angle - arrowAngle).toDouble())).toFloat()
        val x4 = (x2 - arrowHeadLength * cos((angle + arrowAngle).toDouble())).toFloat()
        val y4 = (y2 - arrowHeadLength * sin((angle + arrowAngle).toDouble())).toFloat()

        val headPath = Path().apply {
            moveTo(x2, y2)
            lineTo(x3, y3)
            lineTo(x4, y4)
            close()
        }
        val headPaint = Paint(paint).apply { style = Paint.Style.FILL }
        canvas.drawPath(headPath, headPaint)
    }

    /**
     * Creates a share intent for the exported image.
     */
    fun shareImage(uri: Uri, title: String = "Share Edited Screenshot") {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

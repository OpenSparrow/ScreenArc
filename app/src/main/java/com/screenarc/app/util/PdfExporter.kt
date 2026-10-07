package com.screenarc.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.screenarc.app.model.ScreenshotUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfExporter {
    private const val TAG = "PdfExporter"

    /**
     * Exports multiple screenshots as a paginated PDF document:
     * - 1 page per screenshot
     * - Preserves aspect ratio with standard page bounds (595 x 842 pt, A4 size)
     * - Does work off the main thread with progress callback
     * - Saves via MediaStore (API 29+) or Documents directory, returning output Uri
     */
    suspend fun exportScreenshotsToPdf(
        context: Context,
        screenshots: List<ScreenshotUiModel>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Uri? = withContext(Dispatchers.IO) {
        if (screenshots.isEmpty()) return@withContext null

        val pdfDocument = PdfDocument()
        val total = screenshots.size

        // Standard A4 dimensions in PostScript points: 595 x 842
        val pageWidth = 595
        val pageHeight = 842

        try {
            for (index in screenshots.indices) {
                val item = screenshots[index]
                onProgress(index + 1, total)

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Load screenshot bitmap
                val bitmap: Bitmap? = try {
                    context.contentResolver.openInputStream(item.contentUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load bitmap for ${item.displayName}: ${e.message}")
                    null
                }

                if (bitmap != null) {
                    // Compute aspect-ratio fitting dimensions inside A4 page with 20pt margin
                    val margin = 20f
                    val maxW = pageWidth - 2 * margin
                    val maxH = pageHeight - 2 * margin

                    val bmpW = bitmap.width.toFloat()
                    val bmpH = bitmap.height.toFloat()
                    val scale = minOf(maxW / bmpW, maxH / bmpH)

                    val destW = bmpW * scale
                    val destH = bmpH * scale
                    val left = margin + (maxW - destW) / 2f
                    val top = margin + (maxH - destH) / 2f

                    val srcRect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                    val dstRect = android.graphics.RectF(left, top, left + destW, top + destH)

                    val paint = Paint().apply {
                        isFilterBitmap = true
                        isAntiAlias = true
                    }
                    canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                    bitmap.recycle()
                }

                pdfDocument.finishPage(page)
            }

            // Save PDF
            val timestamp = System.currentTimeMillis()
            val fileName = "Screenshots_Export_${timestamp}.pdf"
            val resolver = context.contentResolver

            val outputUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        pdfDocument.writeTo(out)
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    pdfDocument.writeTo(out)
                }
                Uri.fromFile(file)
            }

            pdfDocument.close()
            Log.d(TAG, "Successfully exported PDF to $outputUri")
            outputUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting PDF: ${e.message}", e)
            try { pdfDocument.close() } catch (ignored: Exception) {}
            null
        }
    }

    fun sharePdf(context: Context, pdfUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Exported PDF"))
    }
}

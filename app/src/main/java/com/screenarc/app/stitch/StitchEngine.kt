package com.screenarc.app.stitch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import com.screenarc.app.stitch.model.StitchConfig
import com.screenarc.app.stitch.model.StitchFormat
import com.screenarc.app.stitch.model.StitchImageItem
import com.screenarc.app.stitch.model.StitchMode
import com.screenarc.app.stitch.model.StitchResult
import com.screenarc.app.stitch.model.StitchSeam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure, UI-independent engine for stitching multiple screenshots.
 * Handles width normalization, static chrome masking, downsampled row-difference overlap detection,
 * manual seam fine-tuning, and memory-safe streaming/rendering.
 */
object StitchEngine {

    private const val TAG = "StitchEngine"

    // Analysis thumbnail width for fast, low-memory overlap detection
    const val ANALYSIS_WIDTH = 240

    // Minimum overlap ratio (5% of height) to prevent spurious matching
    const val MIN_OVERLAP_RATIO = 0.05f

    // Maximum search overlap ratio (85% of height)
    const val MAX_OVERLAP_RATIO = 0.85f

    // Mean Absolute Difference (MAD) threshold for grayscale row comparison (0..255)
    const val OVERLAP_CONFIDENCE_THRESHOLD = 14.0f

    /**
     * Inspects a list of image URIs and retrieves their dimensions without loading full bitmaps into memory.
     */
    suspend fun inspectImages(
        context: Context,
        uris: List<Uri>
    ): List<StitchImageItem> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        uris.mapIndexedNotNull { index, uri ->
            coroutineContext.ensureActive()
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                if (options.outWidth > 0 && options.outHeight > 0) {
                    val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Screenshot_${index + 1}.png"
                    StitchImageItem(
                        id = index.toLong(),
                        uri = uri,
                        displayName = displayName,
                        originalWidth = options.outWidth,
                        originalHeight = options.outHeight
                    )
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Failed inspecting image dimensions for $uri: ${e.message}")
                null
            }
        }
    }

    /**
     * Finds the most common (mode) width among items to use as target width.
     */
    fun calculateTargetWidth(items: List<StitchImageItem>): Int {
        if (items.isEmpty()) return 1080
        val widthFrequencies = items.groupingBy { it.originalWidth }.eachCount()
        return widthFrequencies.maxByOrNull { it.value }?.key ?: items.first().originalWidth
    }

    /**
     * Estimates default status bar and navigation bar heights based on image height.
     */
    fun estimateSystemBars(imageHeight: Int): Pair<Int, Int> {
        // Standard Android status bar is ~3.2% - 4% of display height
        val statusBar = (imageHeight * 0.035f).roundToInt().coerceIn(48, 140)
        // Standard navigation / gesture bar is ~2.5% - 3.5% of display height
        val navBar = (imageHeight * 0.030f).roundToInt().coerceIn(36, 120)
        return statusBar to navBar
    }

    /**
     * Computes overlap seams between consecutive image pairs.
     * In AUTO mode, uses downsampled row-difference matching.
     * In MANUAL mode, sets overlap to 0.
     */
    suspend fun analyzeSeams(
        context: Context,
        images: List<StitchImageItem>,
        config: StitchConfig,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<StitchSeam> = withContext(Dispatchers.Default) {
        if (images.size < 2) return@withContext emptyList()

        val seams = mutableListOf<StitchSeam>()
        val totalPairs = images.size - 1

        for (i in 0 until totalPairs) {
            coroutineContext.ensureActive()
            onProgress?.invoke(i + 1, totalPairs)

            val itemA = images[i]
            val itemB = images[i + 1]

            if (config.mode == StitchMode.MANUAL) {
                seams.add(
                    StitchSeam(
                        index = i,
                        overlapHeight = 0,
                        manualOffsetAdjustment = 0,
                        hasConfidentMatch = true,
                        statusMessage = "Manual Mode"
                    )
                )
                continue
            }

            // Load analysis-sized grayscale representations
            val (statusBarA, navBarA) = estimateSystemBars(itemA.originalHeight)
            val (statusBarB, navBarB) = estimateSystemBars(itemB.originalHeight)

            val cropTopA = if (config.cropStatusBar && i > 0) statusBarA else 0
            val cropBottomA = if (config.cropNavigationBar) navBarA else 0

            val cropTopB = if (config.cropStatusBar) statusBarB else 0
            val cropBottomB = if (config.cropNavigationBar && i + 1 < images.lastIndex) navBarB else 0

            val bitmapA = loadSampledBitmap(context, itemA.uri, ANALYSIS_WIDTH)
            val bitmapB = loadSampledBitmap(context, itemB.uri, ANALYSIS_WIDTH)

            if (bitmapA == null || bitmapB == null) {
                seams.add(
                    StitchSeam(
                        index = i,
                        overlapHeight = 0,
                        hasConfidentMatch = false,
                        statusMessage = "Could not decode image"
                    )
                )
                bitmapA?.recycle()
                bitmapB?.recycle()
                continue
            }

            val scaleA = itemA.originalWidth.toFloat() / bitmapA.width.toFloat()
            val scaleB = itemB.originalWidth.toFloat() / bitmapB.width.toFloat()

            val scaledCropTopA = (cropTopA / scaleA).roundToInt().coerceAtMost(bitmapA.height / 4)
            val scaledCropBottomA = (cropBottomA / scaleA).roundToInt().coerceAtMost(bitmapA.height / 4)

            val scaledCropTopB = (cropTopB / scaleB).roundToInt().coerceAtMost(bitmapB.height / 4)
            val scaledCropBottomB = (cropBottomB / scaleB).roundToInt().coerceAtMost(bitmapB.height / 4)

            val overlapResult = detectVerticalOverlap(
                bitmapA = bitmapA,
                bitmapB = bitmapB,
                cropTopA = scaledCropTopA,
                cropBottomA = scaledCropBottomA,
                cropTopB = scaledCropTopB,
                cropBottomB = scaledCropBottomB
            )

            bitmapA.recycle()
            bitmapB.recycle()

            if (overlapResult.isConfident) {
                // Map overlap back to original image coordinates (relative to original A)
                val originalOverlap = (overlapResult.overlapRows * scaleA).roundToInt()
                seams.add(
                    StitchSeam(
                        index = i,
                        overlapHeight = originalOverlap,
                        manualOffsetAdjustment = 0,
                        hasConfidentMatch = true,
                        statusMessage = "Overlap: ${originalOverlap}px"
                    )
                )
            } else {
                seams.add(
                    StitchSeam(
                        index = i,
                        overlapHeight = 0,
                        manualOffsetAdjustment = 0,
                        hasConfidentMatch = false,
                        statusMessage = "No overlap found"
                    )
                )
            }
        }

        seams
    }

    /**
     * Detection result for two bitmaps or luminance grids.
     */
    data class OverlapDetectionResult(
        val overlapRows: Int,
        val meanDifference: Float,
        val isConfident: Boolean
    )

    /**
     * Compares rows of two bitmaps to find the vertical overlap.
     * Compares the bottom region of bitmapA with the top region of bitmapB.
     */
    fun detectVerticalOverlap(
        bitmapA: Bitmap,
        bitmapB: Bitmap,
        cropTopA: Int = 0,
        cropBottomA: Int = 0,
        cropTopB: Int = 0,
        cropBottomB: Int = 0
    ): OverlapDetectionResult {
        val width = minOf(bitmapA.width, bitmapB.width)
        val validHeightA = (bitmapA.height - cropBottomA)
        val validHeightB = (bitmapB.height - cropTopB)

        if (validHeightA <= 0 || validHeightB <= 0 || width <= 0) {
            return OverlapDetectionResult(0, Float.MAX_VALUE, false)
        }

        // Convert rows to luminance profiles: 2D array [row][x]
        val luminanceA = extractLuminanceGrid(bitmapA, 0, cropTopA, width, validHeightA - cropTopA)
        val luminanceB = extractLuminanceGrid(bitmapB, 0, cropTopB, width, validHeightB)

        return detectVerticalOverlapFromLuminance(luminanceA, luminanceB)
    }

    /**
     * Pure calculation: Finds the optimal vertical overlap between two luminance grids.
     * Compares bottom rows of [luminanceA] with top rows of [luminanceB].
     */
    fun detectVerticalOverlapFromLuminance(
        luminanceA: Array<IntArray>,
        luminanceB: Array<IntArray>,
        minOverlapRatio: Float = MIN_OVERLAP_RATIO,
        maxOverlapRatio: Float = MAX_OVERLAP_RATIO,
        confidenceThreshold: Float = OVERLAP_CONFIDENCE_THRESHOLD
    ): OverlapDetectionResult {
        val heightA = luminanceA.size
        val heightB = luminanceB.size

        if (heightA <= 0 || heightB <= 0) {
            return OverlapDetectionResult(0, Float.MAX_VALUE, false)
        }

        val minOverlap = (minOf(heightA, heightB) * minOverlapRatio).roundToInt().coerceAtLeast(8)
        val maxOverlap = (minOf(heightA, heightB) * maxOverlapRatio).roundToInt()

        if (minOverlap >= maxOverlap) {
            return OverlapDetectionResult(0, Float.MAX_VALUE, false)
        }

        var bestOverlap = 0
        var minDifference = Float.MAX_VALUE

        // Candidate overlap k rows:
        // Bottom k rows of A (from heightA - k to heightA - 1)
        // should match top k rows of B (from 0 to k - 1)
        val step = if (maxOverlap - minOverlap > 300) 2 else 1

        for (k in minOverlap..maxOverlap step step) {
            var totalRowDiff = 0.0
            val sampleRows = minOf(k, 120)
            val rowStep = maxOf(1, k / sampleRows)
            var comparedRows = 0

            var r = 0
            while (r < k) {
                val rowA = heightA - k + r
                val rowB = r

                if (rowA in 0 until heightA && rowB in 0 until heightB) {
                    val diff = computeRowDifference(luminanceA[rowA], luminanceB[rowB])
                    totalRowDiff += diff
                    comparedRows++
                }
                r += rowStep
            }

            if (comparedRows > 0) {
                val avgDiff = (totalRowDiff / comparedRows).toFloat()
                if (avgDiff < minDifference) {
                    minDifference = avgDiff
                    bestOverlap = k
                }
            }
        }

        // Fine-tune around best match if we stepped
        if (step > 1 && bestOverlap > 0) {
            val fineStart = (bestOverlap - step).coerceAtLeast(minOverlap)
            val fineEnd = (bestOverlap + step).coerceAtMost(maxOverlap)
            for (k in fineStart..fineEnd) {
                var totalRowDiff = 0.0
                var comparedRows = 0
                for (r in 0 until k) {
                    val rowA = heightA - k + r
                    val rowB = r
                    if (rowA in 0 until heightA && rowB in 0 until heightB) {
                        totalRowDiff += computeRowDifference(luminanceA[rowA], luminanceB[rowB])
                        comparedRows++
                    }
                }
                if (comparedRows > 0) {
                    val avgDiff = (totalRowDiff / comparedRows).toFloat()
                    if (avgDiff < minDifference) {
                        minDifference = avgDiff
                        bestOverlap = k
                    }
                }
            }
        }

        val isConfident = minDifference <= confidenceThreshold && bestOverlap >= minOverlap
        return OverlapDetectionResult(bestOverlap, minDifference, isConfident)
    }

    /**
     * Computes Mean Absolute Difference between two row luminance arrays.
     */
    fun computeRowDifference(rowA: IntArray, rowB: IntArray): Double {
        val len = minOf(rowA.size, rowB.size)
        if (len == 0) return 255.0
        var sum = 0L
        for (i in 0 until len) {
            sum += abs(rowA[i] - rowB[i])
        }
        return sum.toDouble() / len
    }

    /**
     * Extracts an array of grayscale luminance [0..255] for every pixel in the specified rect.
     */
    private fun extractLuminanceGrid(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Array<IntArray> {
        val safeW = width.coerceAtMost(bitmap.width - x)
        val safeH = height.coerceAtMost(bitmap.height - y)
        if (safeW <= 0 || safeH <= 0) return emptyArray()

        val pixels = IntArray(safeW * safeH)
        bitmap.getPixels(pixels, 0, safeW, x, y, safeW, safeH)

        return Array(safeH) { row ->
            val rowArr = IntArray(safeW)
            val offset = row * safeW
            for (col in 0 until safeW) {
                val color = pixels[offset + col]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                // Standard Rec. 601 luma formula
                rowArr[col] = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt()
            }
            rowArr
        }
    }

    /**
     * Calculates the estimated total stitched height.
     */
    fun calculateTotalHeight(
        images: List<StitchImageItem>,
        seams: List<StitchSeam>,
        config: StitchConfig,
        targetWidth: Int
    ): Int {
        if (images.isEmpty()) return 0
        var totalH = 0

        for (i in images.indices) {
            val item = images[i]
            val scale = targetWidth.toFloat() / item.originalWidth.toFloat()
            val (statusH, navH) = estimateSystemBars(item.originalHeight)

            val cropTop = if (config.cropStatusBar && i > 0) (statusH * scale).roundToInt() else 0
            val cropBottom = if (config.cropNavigationBar && i < images.lastIndex) (navH * scale).roundToInt() else 0

            val itemScaledH = (item.originalHeight * scale).roundToInt()
            val visibleH = (itemScaledH - cropTop - cropBottom).coerceAtLeast(1)

            val seamOverlap = if (i > 0 && i - 1 < seams.size) {
                (seams[i - 1].effectiveOverlap * scale).roundToInt()
            } else 0

            val gap = if (config.mode == StitchMode.MANUAL && i > 0) config.manualGapPx else 0

            totalH += (visibleH - seamOverlap).coerceAtLeast(1) + gap
        }

        return totalH
    }

    /**
     * Renders a low-memory downsampled preview bitmap suitable for interactive UI display.
     */
    suspend fun renderPreview(
        context: Context,
        images: List<StitchImageItem>,
        seams: List<StitchSeam>,
        config: StitchConfig,
        previewWidth: Int = 720
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (images.isEmpty()) return@withContext null

        val targetWidth = previewWidth
        val estimatedH = calculateTotalHeight(images, seams, config, targetWidth)
        if (estimatedH <= 0 || estimatedH > config.maxOutputHeightPx) {
            Log.w(TAG, "Invalid preview height: $estimatedH")
            return@withContext null
        }

        val previewBitmap = try {
            Bitmap.createBitmap(targetWidth, estimatedH, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM creating preview bitmap of size ${targetWidth}x$estimatedH", oom)
            return@withContext null
        }

        val canvas = Canvas(previewBitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        var currentY = 0

        for (i in images.indices) {
            coroutineContext.ensureActive()
            val item = images[i]
            val sourceBitmap = loadSampledBitmap(context, item.uri, targetWidth) ?: continue

            val scaleToPreview = targetWidth.toFloat() / sourceBitmap.width.toFloat()
            val (statusH, navH) = estimateSystemBars(sourceBitmap.height)

            val cropTop = if (config.cropStatusBar && i > 0) statusH else 0
            val cropBottom = if (config.cropNavigationBar && i < images.lastIndex) navH else 0

            val srcRect = Rect(
                0,
                cropTop,
                sourceBitmap.width,
                (sourceBitmap.height - cropBottom).coerceAtLeast(cropTop + 1)
            )

            val overlap = if (i > 0 && i - 1 < seams.size) {
                val scaleFromOriginal = sourceBitmap.width.toFloat() / item.originalWidth.toFloat()
                (seams[i - 1].effectiveOverlap * scaleFromOriginal).roundToInt()
            } else 0

            val gap = if (config.mode == StitchMode.MANUAL && i > 0) config.manualGapPx else 0

            currentY -= overlap
            currentY += gap

            val destHeight = (srcRect.height() * scaleToPreview).roundToInt()
            val destRect = Rect(0, currentY, targetWidth, currentY + destHeight)

            canvas.drawBitmap(sourceBitmap, srcRect, destRect, paint)
            currentY += destHeight

            sourceBitmap.recycle()
        }

        previewBitmap
    }

    /**
     * Renders and streams the final high-resolution stitched output into [outputStream].
     * Memory-safe: decodes images with exact sampled scaling and draws them directly to target canvas.
     */
    suspend fun renderAndStreamTo(
        context: Context,
        images: List<StitchImageItem>,
        seams: List<StitchSeam>,
        config: StitchConfig,
        outputStream: OutputStream,
        onProgress: ((progressPercent: Float, status: String) -> Unit)? = null
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        require(images.size >= 2) { "At least 2 screenshots are required to stitch." }
        require(images.size <= config.maxImageCount) { "Maximum ${config.maxImageCount} screenshots allowed." }

        val targetWidth = calculateTargetWidth(images)
        val totalHeight = calculateTotalHeight(images, seams, config, targetWidth)

        if (totalHeight > config.maxOutputHeightPx) {
            throw IllegalArgumentException("Stitched height ($totalHeight px) exceeds maximum limit (${config.maxOutputHeightPx} px).")
        }

        onProgress?.invoke(0.1f, "Allocating canvas…")

        val targetBitmap = try {
            Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            throw IllegalStateException("Insufficient memory to render high-resolution stitched image. Try reducing the number of screenshots.", oom)
        }

        val canvas = Canvas(targetBitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        var currentY = 0

        for (i in images.indices) {
            coroutineContext.ensureActive()
            val progress = 0.1f + (0.75f * (i.toFloat() / images.size))
            onProgress?.invoke(progress, "Stitching frame ${i + 1} of ${images.size}…")

            val item = images[i]
            val fullBitmap = decodeFullBitmap(context, item.uri)
                ?: throw IllegalStateException("Could not decode image at ${item.uri}")

            val scaleToTarget = targetWidth.toFloat() / fullBitmap.width.toFloat()
            val (statusH, navH) = estimateSystemBars(fullBitmap.height)

            val cropTop = if (config.cropStatusBar && i > 0) statusH else 0
            val cropBottom = if (config.cropNavigationBar && i < images.lastIndex) navH else 0

            val srcRect = Rect(
                0,
                cropTop,
                fullBitmap.width,
                (fullBitmap.height - cropBottom).coerceAtLeast(cropTop + 1)
            )

            val overlap = if (i > 0 && i - 1 < seams.size) {
                val scaleFromOriginal = fullBitmap.width.toFloat() / item.originalWidth.toFloat()
                (seams[i - 1].effectiveOverlap * scaleFromOriginal).roundToInt()
            } else 0

            val gap = if (config.mode == StitchMode.MANUAL && i > 0) config.manualGapPx else 0

            currentY -= overlap
            currentY += gap

            val destHeight = (srcRect.height() * scaleToTarget).roundToInt()
            val destRect = Rect(0, currentY, targetWidth, currentY + destHeight)

            canvas.drawBitmap(fullBitmap, srcRect, destRect, paint)
            currentY += destHeight

            fullBitmap.recycle()
        }

        onProgress?.invoke(0.9f, "Encoding image to storage…")

        when (config.format) {
            StitchFormat.PNG -> {
                targetBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            StitchFormat.JPEG -> {
                targetBitmap.compress(Bitmap.CompressFormat.JPEG, config.jpegQuality.coerceIn(50, 100), outputStream)
            }
        }

        targetBitmap.recycle()
        onProgress?.invoke(1.0f, "Completed")

        targetWidth to totalHeight
    }

    /**
     * Loads a sampled bitmap scaled close to [targetWidth].
     */
    private fun loadSampledBitmap(context: Context, uri: Uri, targetWidth: Int): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            var sampleSize = 1
            while (boundsOptions.outWidth / (sampleSize * 2) >= targetWidth) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading sampled bitmap for $uri: ${e.message}")
            null
        }
    }

    /**
     * Safely decodes full-resolution bitmap on Dispatchers.IO.
     */
    private fun decodeFullBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading full bitmap for $uri: ${e.message}", e)
            null
        }
    }
}

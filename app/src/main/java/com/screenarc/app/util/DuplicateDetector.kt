package com.screenarc.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.screenarc.app.model.ScreenshotUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class DuplicateGroup(
    val primary: ScreenshotUiModel,
    val duplicates: List<ScreenshotUiModel>,
    val similarityReason: String
) {
    val allItems: List<ScreenshotUiModel> get() = listOf(primary) + duplicates
}

object DuplicateDetector {

    private const val TIME_WINDOW_MS = 5 * 60 * 1000L // 5 minutes window

    /**
     * Finds groups of duplicates or near-duplicates among the provided screenshots.
     * Uses:
     * 1. Perceptual Difference Hash (dHash) with Hamming distance threshold <= 10 (approx >85% visual similarity)
     * 2. OCR text Jaccard similarity > 85%
     * Both evaluated within a 5-minute capture window.
     */
    suspend fun findDuplicates(
        context: Context,
        screenshots: List<ScreenshotUiModel>
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        if (screenshots.size < 2) return@withContext emptyList()

        // Sort by date added descending
        val sorted = screenshots.sortedByDescending { it.dateAdded }
        val visitedUris = mutableSetOf<String>()
        val groups = mutableListOf<DuplicateGroup>()

        // Cache computed hashes to avoid redundant bitmap decoding
        val hashCache = mutableMapOf<String, Long>()

        for (i in sorted.indices) {
            val itemA = sorted[i]
            if (visitedUris.contains(itemA.uri)) continue

            val currentDuplicates = mutableListOf<ScreenshotUiModel>()
            var matchReason = ""

            for (j in i + 1 until sorted.size) {
                val itemB = sorted[j]
                if (visitedUris.contains(itemB.uri)) continue

                // Check 5-minute time window
                val timeDiffMs = abs(itemA.dateAdded - itemB.dateAdded) * 1000L
                if (timeDiffMs > TIME_WINDOW_MS) {
                    // Since list is sorted descending by dateAdded, further items could be outside the window
                    // but continue to be safe if sorting had ties
                    continue
                }

                var isDuplicate = false

                // 1. Check OCR text similarity (Jaccard > 0.85)
                val textA = itemA.recognizedText?.trim()
                val textB = itemB.recognizedText?.trim()
                if (!textA.isNullOrEmpty() && !textB.isNullOrEmpty()) {
                    val similarity = computeJaccardSimilarity(textA, textB)
                    if (similarity >= 0.85f) {
                        isDuplicate = true
                        matchReason = "Text Similarity (${(similarity * 100).toInt()}%)"
                    }
                }

                // 2. Check perceptual hash (dHash) if not already matched
                if (!isDuplicate) {
                    val hashA = hashCache.getOrPut(itemA.uri) { computeDHash(context, itemA.contentUri) }
                    val hashB = hashCache.getOrPut(itemB.uri) { computeDHash(context, itemB.contentUri) }
                    if (hashA != 0L && hashB != 0L) {
                        val distance = hammingDistance(hashA, hashB)
                        // 64-bit dHash: distance <= 10 corresponds to ~85% visual match
                        if (distance <= 10) {
                            val simPercent = ((64 - distance) * 100) / 64
                            isDuplicate = true
                            matchReason = "Visual Match ($simPercent%)"
                        }
                    }
                }

                if (isDuplicate) {
                    currentDuplicates.add(itemB)
                    visitedUris.add(itemB.uri)
                }
            }

            if (currentDuplicates.isNotEmpty()) {
                visitedUris.add(itemA.uri)
                // Pick the "best" item to keep:
                // Prioritize categorized over uncategorized, higher resolution/size, or more OCR text
                val allCandidate = listOf(itemA) + currentDuplicates
                val bestItem = allCandidate.maxWithOrNull(
                    compareBy<ScreenshotUiModel> { it.categoryId != null }
                        .thenBy { it.recognizedText?.length ?: 0 }
                        .thenBy { it.size }
                ) ?: itemA

                val remaining = allCandidate.filter { it.uri != bestItem.uri }
                groups.add(
                    DuplicateGroup(
                        primary = bestItem,
                        duplicates = remaining,
                        similarityReason = matchReason.ifEmpty { "Near Duplicate" }
                    )
                )
            }
        }

        groups
    }

    /**
     * Perceptual dHash (difference hash):
     * 1. Resizes bitmap down to 9x8 grayscale
     * 2. Compares adjacent pixels: left > right -> 1 bit else 0 bit
     * Produces a 64-bit Long hash invariant to scaling and minor compression.
     */
    fun computeDHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = scaled.getPixel(x, y)
                val right = scaled.getPixel(x + 1, y)

                // Grayscale luminance
                val leftLuma = (0.299 * (left shr 16 and 0xFF) + 0.587 * (left shr 8 and 0xFF) + 0.114 * (left and 0xFF)).toInt()
                val rightLuma = (0.299 * (right shr 16 and 0xFF) + 0.587 * (right shr 8 and 0xFF) + 0.114 * (right and 0xFF)).toInt()

                if (leftLuma > rightLuma) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
        }
        if (scaled != bitmap) {
            scaled.recycle()
        }
        return hash
    }

    private fun computeDHash(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = 2 // Quick sub-sample before resize
                }
                val bmp = BitmapFactory.decodeStream(stream, null, opts)
                bmp?.let {
                    val h = computeDHash(it)
                    it.recycle()
                    h
                } ?: 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    /**
     * Compute token Jaccard similarity between two texts: |A ∩ B| / |A ∪ B|
     */
    fun computeJaccardSimilarity(text1: String, text2: String): Float {
        val tokens1 = text1.lowercase()
            .split(Regex("\\W+"))
            .filter { it.isNotBlank() }
            .toSet()
        val tokens2 = text2.lowercase()
            .split(Regex("\\W+"))
            .filter { it.isNotBlank() }
            .toSet()

        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0f
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0f

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size

        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }
}

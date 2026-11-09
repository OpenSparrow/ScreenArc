package com.screenarc.app.stitch.model

import android.net.Uri

/**
 * Stitching modes:
 * AUTO: Automatically detects scrolling overlap and removes duplicate content.
 * MANUAL: Simple vertical concatenation with configurable gap or divider.
 */
enum class StitchMode {
    AUTO,
    MANUAL
}

/**
 * Output image format.
 */
enum class StitchFormat(val mimeType: String, val extension: String) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg")
}

/**
 * Represents a single screenshot in the stitch sequence.
 */
data class StitchImageItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val cropTopPx: Int = 0,
    val cropBottomPx: Int = 0
)

/**
 * Represents the seam / join between two consecutive images [itemA] and [itemB].
 * [overlapHeight]: Number of overlapping pixels between bottom of A and top of B to be removed.
 * [manualOffsetAdjustment]: User nudge to fine-tune the seam position (+/- pixels).
 * [hasConfidentMatch]: Whether Auto mode found a statistically sound match.
 * [statusMessage]: Diagnostic or UI status (e.g. "Overlap: 340px", "No overlap found").
 */
data class StitchSeam(
    val index: Int, // between image index and index + 1
    val overlapHeight: Int = 0,
    val manualOffsetAdjustment: Int = 0,
    val hasConfidentMatch: Boolean = true,
    val statusMessage: String = ""
) {
    val effectiveOverlap: Int
        get() = (overlapHeight + manualOffsetAdjustment).coerceAtLeast(0)
}

/**
 * Configuration options for the stitching process.
 */
data class StitchConfig(
    val mode: StitchMode = StitchMode.AUTO,
    val format: StitchFormat = StitchFormat.PNG,
    val jpegQuality: Int = 92,
    val cropStatusBar: Boolean = true,
    val cropNavigationBar: Boolean = true,
    val manualGapPx: Int = 0,
    val maxOutputHeightPx: Int = 32_000,
    val maxImageCount: Int = 20
)

/**
 * Result of stitching computation and rendering.
 */
sealed class StitchResult {
    data class Success(
        val outputUri: Uri,
        val width: Int,
        val height: Int,
        val totalSourceCount: Int,
        val sourceUris: List<Uri>
    ) : StitchResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : StitchResult()
}

/**
 * UI State for the Stitch Screen.
 */
sealed class StitchUiState {
    object Idle : StitchUiState()

    data class Analyzing(
        val progressPercent: Float = 0f,
        val message: String = ""
    ) : StitchUiState()

    data class Preview(
        val images: List<StitchImageItem>,
        val seams: List<StitchSeam>,
        val config: StitchConfig,
        val previewUri: Uri? = null,
        val estimatedHeight: Int = 0,
        val isReordering: Boolean = false
    ) : StitchUiState()

    data class Saving(
        val progressPercent: Float = 0f,
        val message: String = ""
    ) : StitchUiState()

    data class Saved(
        val outputUri: Uri,
        val sourceUris: List<Uri>,
        val width: Int,
        val height: Int
    ) : StitchUiState()

    data class Failure(
        val errorMessage: String,
        val canRetry: Boolean = true
    ) : StitchUiState()
}

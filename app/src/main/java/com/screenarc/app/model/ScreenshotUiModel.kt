package com.screenarc.app.model

import android.graphics.Bitmap
import android.net.Uri

data class ScreenshotUiModel(
    val id: Long,
    val uri: String,
    val displayName: String,
    val dateAdded: Long,
    val size: Long,
    val relativePath: String,
    val mimeType: String = "image/png",
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val ocrStatus: String = "NOT_STARTED", // "NOT_STARTED", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED", "NO_TEXT"
    val recognizedText: String? = null,
    val normalizedText: String? = null,
    val bitmapPreview: Bitmap? = null,
    val paletteColors: DynamicPaletteColors = DynamicPaletteColors(),
    val blocks: List<OcrBlock> = emptyList()
) {
    val contentUri: Uri
        get() = Uri.parse(uri)
}

package com.screenarc.app.model

import android.graphics.Bitmap
import android.net.Uri

data class ScreenshotItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val size: Long,
    val relativePath: String,
    val mimeType: String = "image/png",
    val bitmapPreview: Bitmap? = null,
    val paletteColors: DynamicPaletteColors = DynamicPaletteColors(),
    val assignedCategory: String? = null
)

package com.screenarc.app.model

data class OcrResult(
    val screenshotUri: String,
    val recognizedText: String,
    val blocks: List<OcrBlock>,
    val processedAt: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

data class OcrBlock(
    val text: String,
    val left: Int?,
    val top: Int?,
    val right: Int?,
    val bottom: Int?
)

data class OcrBatchResult(
    val processedCount: Int,
    val successCount: Int,
    val failureCount: Int
)

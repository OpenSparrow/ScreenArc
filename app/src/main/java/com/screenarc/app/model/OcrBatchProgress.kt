package com.screenarc.app.model

/**
 * Real-time progress model for bulk image OCR and indexing tasks.
 */
data class OcrBatchProgress(
    val isRunning: Boolean = false,
    val total: Int = 0,
    val current: Int = 0,
    val currentFileName: String = "",
    val currentLanguage: String = "",
    val indexedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val lastError: String? = null,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false
) {
    val progressFraction: Float
        get() = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    companion object {
        val IDLE = OcrBatchProgress()
    }
}

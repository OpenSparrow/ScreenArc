package com.screenarc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ocr_texts",
    indices = [
        Index("normalizedText"),
        Index("screenshotUri"),
        Index("processingStatus"),
        Index("processedAt")
    ]
)
data class OcrTextEntity(
    @PrimaryKey
    val screenshotUri: String,
    val recognizedText: String,
    val normalizedText: String,
    val processingStatus: String, // "NOT_STARTED", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED", "NO_TEXT"
    val errorMessage: String? = null,
    val processedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val blocksJson: String? = null
) {
    companion object {
        fun normalizeText(raw: String): String {
            if (raw.isBlank()) return ""
            return raw
                .lowercase()
                .replace(Regex("[\\r\\n]+"), " ")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
        }
    }
}

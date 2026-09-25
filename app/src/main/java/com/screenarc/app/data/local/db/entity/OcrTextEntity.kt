package com.screenarc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

import java.text.Normalizer
import java.util.Locale

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
        /**
         * Robust Unicode normalization ensuring compatibility across Indian scripts (Devanagari, Tamil,
         * Telugu, Kannada, Malayalam, Bengali) and Latin text.
         * Applies Unicode NFKC (Compatibility Decomposition followed by Canonical Composition),
         * converts case safely, and strips redundant whitespaces and linebreaks.
         */
        fun normalizeText(raw: String): String {
            if (raw.isBlank()) return ""
            val nfkcNormalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            return nfkcNormalized
                .lowercase(Locale.ROOT)
                .replace(Regex("[\\r\\n]+"), " ")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
        }
    }
}

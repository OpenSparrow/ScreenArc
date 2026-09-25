package com.screenarc.app.ocr

/**
 * Represents supported OCR languages and script models.
 *
 * @param id Unique identifier for persistence.
 * @param displayName Human-readable name shown in UI.
 * @param scriptName Primary script(s) supported.
 * @param isBundled Whether model weights are bundled / immediately available on-device.
 * @param description Brief description of language family and coverage.
 */
enum class OcrLanguage(
    val id: String,
    val displayName: String,
    val scriptName: String,
    val isBundled: Boolean = true,
    val description: String = ""
) {
    LATIN(
        id = "latin",
        displayName = "English / Latin",
        scriptName = "Latin",
        isBundled = true,
        description = "Optimized for English and other Latin-script languages."
    ),
    DEVANAGARI(
        id = "devanagari",
        displayName = "Hindi / Devanagari + English",
        scriptName = "Devanagari + Latin",
        isBundled = true,
        description = "Optimized for Hindi, Marathi, Sanskrit, Nepali, Konkani, Bodo, etc. and bilingual English text."
    );

    companion object {
        fun fromId(id: String?, default: OcrLanguage = DEVANAGARI): OcrLanguage {
            if (id.isNullOrBlank()) return default
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: default
        }
    }
}

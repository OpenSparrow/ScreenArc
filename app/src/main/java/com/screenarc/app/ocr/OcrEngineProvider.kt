package com.screenarc.app.ocr

import com.google.mlkit.vision.text.TextRecognizer

/**
 * Common abstraction interface for OCR Providers and model implementations.
 * Enables plugging in multiple OCR engines or script models seamlessly.
 */
interface OcrEngineProvider {
    val engineName: String
    val supportedLanguages: List<OcrLanguage>
    fun createRecognizer(language: OcrLanguage): TextRecognizer
    fun isLanguageAvailable(language: OcrLanguage): Boolean
}

package com.screenarc.app.ocr

import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Google ML Kit on-device Text Recognition implementation.
 * Supports Latin/English as well as Devanagari script (Hindi, Marathi, Sanskrit, Nepali, etc.)
 * with native bilingual English recognition capabilities.
 */
class MlKitOcrProvider : OcrEngineProvider {
    override val engineName: String = "Google ML Kit (On-Device)"

    override val supportedLanguages: List<OcrLanguage> = listOf(
        OcrLanguage.DEVANAGARI,
        OcrLanguage.LATIN
    )

    override fun createRecognizer(language: OcrLanguage): TextRecognizer {
        return when (language) {
            OcrLanguage.DEVANAGARI -> {
                val options = DevanagariTextRecognizerOptions.Builder().build()
                TextRecognition.getClient(options)
            }
            OcrLanguage.LATIN -> {
                val options = TextRecognizerOptions.DEFAULT_OPTIONS
                TextRecognition.getClient(options)
            }
        }
    }

    override fun isLanguageAvailable(language: OcrLanguage): Boolean {
        return supportedLanguages.contains(language)
    }
}

package com.screenarc.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.screenarc.app.ocr.OcrLanguage

enum class OcrProcessingMode {
    MANUAL_ONLY,
    ASK_BEFORE_PROCESSING,
    AUTOMATIC
}

data class AppSettings(
    val isServiceEnabled: Boolean = false,
    val bubbleSizeDp: Int = 64,
    val arcRadiusDp: Int = 135,
    val autoDismissSeconds: Int = 10,
    val hapticFeedbackEnabled: Boolean = true,
    val dynamicPaletteEnabled: Boolean = true,
    val autoExpandOnCapture: Boolean = true,
    val playSoundOnDrop: Boolean = false,
    val ocrProcessingMode: OcrProcessingMode = OcrProcessingMode.ASK_BEFORE_PROCESSING,
    val ocrLanguage: OcrLanguage = OcrLanguage.DEVANAGARI,
    val arcDesignStyle: com.screenarc.app.model.ArcDesignStyle = com.screenarc.app.model.ArcDesignStyle.ORBIT,
    val autoOcrEnabled: Boolean = false,
    val autoAssignRulesEnabled: Boolean = false
)

class AppSettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("screenarc_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val ocrModeName = prefs.getString("ocr_processing_mode", OcrProcessingMode.ASK_BEFORE_PROCESSING.name)
        val ocrMode = try {
            OcrProcessingMode.valueOf(ocrModeName ?: OcrProcessingMode.ASK_BEFORE_PROCESSING.name)
        } catch (e: Exception) {
            OcrProcessingMode.ASK_BEFORE_PROCESSING
        }

        val langId = prefs.getString("ocr_language", OcrLanguage.DEVANAGARI.id)
        val ocrLang = OcrLanguage.fromId(langId, OcrLanguage.DEVANAGARI)

        val styleName = prefs.getString("arc_design_style", com.screenarc.app.model.ArcDesignStyle.ORBIT.name)
        val arcStyle = try {
            com.screenarc.app.model.ArcDesignStyle.valueOf(styleName ?: com.screenarc.app.model.ArcDesignStyle.ORBIT.name)
        } catch (e: Exception) {
            com.screenarc.app.model.ArcDesignStyle.ORBIT
        }

        val autoOcr = prefs.getBoolean("auto_ocr_enabled", false)
        val autoRules = prefs.getBoolean("auto_assign_rules_enabled", false)

        return AppSettings(
            isServiceEnabled = prefs.getBoolean("is_service_enabled", false),
            bubbleSizeDp = prefs.getInt("bubble_size_dp", 64),
            arcRadiusDp = prefs.getInt("arc_radius_dp", 135),
            autoDismissSeconds = prefs.getInt("auto_dismiss_seconds", 10),
            hapticFeedbackEnabled = prefs.getBoolean("haptic_feedback_enabled", true),
            dynamicPaletteEnabled = prefs.getBoolean("dynamic_palette_enabled", true),
            autoExpandOnCapture = prefs.getBoolean("auto_expand_capture", true),
            playSoundOnDrop = prefs.getBoolean("play_sound_drop", false),
            ocrProcessingMode = ocrMode,
            ocrLanguage = ocrLang,
            arcDesignStyle = arcStyle,
            autoOcrEnabled = autoOcr,
            autoAssignRulesEnabled = if (autoOcr) autoRules else false
        )
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val rawUpdated = transform(_settings.value)
        // Enforce dependency: if autoOcr is disabled, autoAssignRules must be disabled.
        // If autoAssignRules is enabled, autoOcr must also be enabled.
        val finalUpdated = if (!rawUpdated.autoOcrEnabled) {
            rawUpdated.copy(autoAssignRulesEnabled = false)
        } else {
            rawUpdated
        }

        prefs.edit().apply {
            putBoolean("is_service_enabled", finalUpdated.isServiceEnabled)
            putInt("bubble_size_dp", finalUpdated.bubbleSizeDp)
            putInt("arc_radius_dp", finalUpdated.arcRadiusDp)
            putInt("auto_dismiss_seconds", finalUpdated.autoDismissSeconds)
            putBoolean("haptic_feedback_enabled", finalUpdated.hapticFeedbackEnabled)
            putBoolean("dynamic_palette_enabled", finalUpdated.dynamicPaletteEnabled)
            putBoolean("auto_expand_capture", finalUpdated.autoExpandOnCapture)
            putBoolean("play_sound_drop", finalUpdated.playSoundOnDrop)
            putString("ocr_processing_mode", finalUpdated.ocrProcessingMode.name)
            putString("ocr_language", finalUpdated.ocrLanguage.id)
            putString("arc_design_style", finalUpdated.arcDesignStyle.name)
            putBoolean("auto_ocr_enabled", finalUpdated.autoOcrEnabled)
            putBoolean("auto_assign_rules_enabled", finalUpdated.autoAssignRulesEnabled)
        }.apply()
        _settings.value = finalUpdated
    }
}

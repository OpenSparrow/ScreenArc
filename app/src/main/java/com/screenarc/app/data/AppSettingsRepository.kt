package com.screenarc.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val ocrProcessingMode: OcrProcessingMode = OcrProcessingMode.ASK_BEFORE_PROCESSING
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

        return AppSettings(
            isServiceEnabled = prefs.getBoolean("is_service_enabled", false),
            bubbleSizeDp = prefs.getInt("bubble_size_dp", 64),
            arcRadiusDp = prefs.getInt("arc_radius_dp", 135),
            autoDismissSeconds = prefs.getInt("auto_dismiss_seconds", 10),
            hapticFeedbackEnabled = prefs.getBoolean("haptic_feedback_enabled", true),
            dynamicPaletteEnabled = prefs.getBoolean("dynamic_palette_enabled", true),
            autoExpandOnCapture = prefs.getBoolean("auto_expand_capture", true),
            playSoundOnDrop = prefs.getBoolean("play_sound_drop", false),
            ocrProcessingMode = ocrMode
        )
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        prefs.edit().apply {
            putBoolean("is_service_enabled", updated.isServiceEnabled)
            putInt("bubble_size_dp", updated.bubbleSizeDp)
            putInt("arc_radius_dp", updated.arcRadiusDp)
            putInt("auto_dismiss_seconds", updated.autoDismissSeconds)
            putBoolean("haptic_feedback_enabled", updated.hapticFeedbackEnabled)
            putBoolean("dynamic_palette_enabled", updated.dynamicPaletteEnabled)
            putBoolean("auto_expand_capture", updated.autoExpandOnCapture)
            putBoolean("play_sound_drop", updated.playSoundOnDrop)
            putString("ocr_processing_mode", updated.ocrProcessingMode.name)
        }.apply()
        _settings.value = updated
    }
}


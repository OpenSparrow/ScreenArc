package com.screenarc.app.data

import android.content.Context
import android.content.SharedPreferences
import com.screenarc.app.model.AppThemePreset
import com.screenarc.app.ocr.OcrLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UiDesignStyle(val id: String, val displayName: String, val subtitle: String) {
    MATERIAL("material", "Material UI", "Clean Material 3 surfaces with standard elevation and borders"),
    GLASSMORPHISM("glassmorphism", "Glassmorphism", "Frosted glass acrylic aesthetic with translucent blur highlights")
}

enum class OverlayPlacement(val id: String, val displayName: String) {
    TOP_LEFT("top_left", "Left Top"),
    CENTER_LEFT("center_left", "Left Middle"),
    BOTTOM_LEFT("bottom_left", "Left Bottom"),
    TOP_RIGHT("top_right", "Right Top"),
    CENTER_RIGHT("center_right", "Right Middle"),
    BOTTOM_RIGHT("bottom_right", "Right Bottom");

    fun toEdgeAnchor(): com.screenarc.app.ui.overlay.OverlayEdgeAnchor = when (this) {
        TOP_LEFT -> com.screenarc.app.ui.overlay.OverlayEdgeAnchor.TOP_LEFT
        CENTER_LEFT -> com.screenarc.app.ui.overlay.OverlayEdgeAnchor.CENTER_LEFT
        BOTTOM_LEFT -> com.screenarc.app.ui.overlay.OverlayEdgeAnchor.BOTTOM_LEFT
        TOP_RIGHT -> com.screenarc.app.ui.overlay.OverlayEdgeAnchor.TOP_RIGHT
        CENTER_RIGHT -> com.screenarc.app.ui.overlay.OverlayEdgeAnchor.CENTER_RIGHT
        BOTTOM_RIGHT -> com.screenarc.app.ui.overlay.OverlayEdgeAnchor.BOTTOM_RIGHT
    }

    companion object {
        fun fromId(id: String?, default: OverlayPlacement = BOTTOM_RIGHT): OverlayPlacement {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: default
        }

        fun fromEdgeAnchor(anchor: com.screenarc.app.ui.overlay.OverlayEdgeAnchor, default: OverlayPlacement = BOTTOM_RIGHT): OverlayPlacement = when (anchor) {
            com.screenarc.app.ui.overlay.OverlayEdgeAnchor.TOP_LEFT -> TOP_LEFT
            com.screenarc.app.ui.overlay.OverlayEdgeAnchor.CENTER_LEFT -> CENTER_LEFT
            com.screenarc.app.ui.overlay.OverlayEdgeAnchor.BOTTOM_LEFT -> BOTTOM_LEFT
            com.screenarc.app.ui.overlay.OverlayEdgeAnchor.TOP_RIGHT -> TOP_RIGHT
            com.screenarc.app.ui.overlay.OverlayEdgeAnchor.CENTER_RIGHT -> CENTER_RIGHT
            com.screenarc.app.ui.overlay.OverlayEdgeAnchor.BOTTOM_RIGHT -> BOTTOM_RIGHT
            else -> default
        }
    }
}

data class AppSettings(
    val isServiceEnabled: Boolean = false,
    val bubbleSizeDp: Int = 64,
    val arcRadiusDp: Int = 135,
    val autoDismissSeconds: Int = 10,
    val hapticFeedbackEnabled: Boolean = true,
    val dynamicPaletteEnabled: Boolean = true,
    val playSoundOnDrop: Boolean = false,
    val ocrLanguage: OcrLanguage = OcrLanguage.DEVANAGARI,
    val arcDesignStyle: com.screenarc.app.model.ArcDesignStyle = com.screenarc.app.model.ArcDesignStyle.ORBIT,
    val autoOcrEnabled: Boolean = false,
    val autoAssignRulesEnabled: Boolean = false,
    val autoArchiveDays: Int = 0, // 0 = Off, 30, 60, 90 days
    val themePreset: AppThemePreset = AppThemePreset.GLASSMORPHIC,
    val uiDesignStyle: UiDesignStyle = UiDesignStyle.MATERIAL,
    val duplicateFinderEnabled: Boolean = true,
    val overlayPlacement: OverlayPlacement = OverlayPlacement.BOTTOM_RIGHT,
    val isOnboardingCompleted: Boolean = false
)

class AppSettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("screenarc_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        // Migration: drop the removed "auto expand arc on capture" preference —
        // the overlay always starts as the small bubble circle now.
        if (prefs.contains("auto_expand_capture")) {
            prefs.edit().remove("auto_expand_capture").apply()
        }

        // Migration: If legacy ocr_processing_mode exists and auto_ocr_enabled hasn't been set explicitly,
        // map "AUTOMATIC" to true, everything else to false.
        val autoOcr = if (prefs.contains("auto_ocr_enabled")) {
            prefs.getBoolean("auto_ocr_enabled", false)
        } else if (prefs.contains("ocr_processing_mode")) {
            val legacyMode = prefs.getString("ocr_processing_mode", null)
            val mappedValue = legacyMode == "AUTOMATIC"
            prefs.edit().putBoolean("auto_ocr_enabled", mappedValue).remove("ocr_processing_mode").apply()
            mappedValue
        } else {
            false
        }

        val langId = prefs.getString("ocr_language", OcrLanguage.DEVANAGARI.id)
        val ocrLang = OcrLanguage.fromId(langId, OcrLanguage.DEVANAGARI)

        val styleName = prefs.getString("arc_design_style", com.screenarc.app.model.ArcDesignStyle.ORBIT.name)
        val arcStyle = try {
            com.screenarc.app.model.ArcDesignStyle.valueOf(styleName ?: com.screenarc.app.model.ArcDesignStyle.ORBIT.name)
        } catch (e: Exception) {
            com.screenarc.app.model.ArcDesignStyle.ORBIT
        }

        val themePresetId = prefs.getString("theme_preset", AppThemePreset.GLASSMORPHIC.id)
        val themePreset = AppThemePreset.fromId(themePresetId, AppThemePreset.GLASSMORPHIC)

        val uiStyleName = prefs.getString("ui_design_style", UiDesignStyle.MATERIAL.name)
        val uiStyle = try {
            UiDesignStyle.valueOf(uiStyleName ?: UiDesignStyle.MATERIAL.name)
        } catch (e: Exception) {
            UiDesignStyle.MATERIAL
        }

        val autoRules = prefs.getBoolean("auto_assign_rules_enabled", false)

        val placementId = prefs.getString("overlay_placement", OverlayPlacement.BOTTOM_RIGHT.id)
        val overlayPlacement = OverlayPlacement.fromId(placementId, OverlayPlacement.BOTTOM_RIGHT)

        return AppSettings(
            isServiceEnabled = prefs.getBoolean("is_service_enabled", false),
            bubbleSizeDp = prefs.getInt("bubble_size_dp", 64),
            arcRadiusDp = prefs.getInt("arc_radius_dp", 135),
            autoDismissSeconds = prefs.getInt("auto_dismiss_seconds", 10),
            hapticFeedbackEnabled = prefs.getBoolean("haptic_feedback_enabled", true),
            dynamicPaletteEnabled = prefs.getBoolean("dynamic_palette_enabled", true),
            playSoundOnDrop = prefs.getBoolean("play_sound_drop", false),
            ocrLanguage = ocrLang,
            arcDesignStyle = arcStyle,
            autoOcrEnabled = autoOcr,
            autoAssignRulesEnabled = if (autoOcr) autoRules else false,
            autoArchiveDays = prefs.getInt("auto_archive_days", 0),
            themePreset = themePreset,
            uiDesignStyle = uiStyle,
            duplicateFinderEnabled = prefs.getBoolean("duplicate_finder_enabled", true),
            overlayPlacement = overlayPlacement,
            isOnboardingCompleted = prefs.getBoolean("onboarding_completed", false)
        )
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val rawUpdated = transform(_settings.value)
        // Enforce dependency: if autoOcr is disabled, autoAssignRules must be disabled.
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
            putBoolean("play_sound_drop", finalUpdated.playSoundOnDrop)
            putString("ocr_language", finalUpdated.ocrLanguage.id)
            putString("arc_design_style", finalUpdated.arcDesignStyle.name)
            putBoolean("auto_ocr_enabled", finalUpdated.autoOcrEnabled)
            putBoolean("auto_assign_rules_enabled", finalUpdated.autoAssignRulesEnabled)
            putInt("auto_archive_days", finalUpdated.autoArchiveDays)
            putString("theme_preset", finalUpdated.themePreset.id)
            putString("ui_design_style", finalUpdated.uiDesignStyle.name)
            putBoolean("duplicate_finder_enabled", finalUpdated.duplicateFinderEnabled)
            putString("overlay_placement", finalUpdated.overlayPlacement.id)
            putBoolean("onboarding_completed", finalUpdated.isOnboardingCompleted)
        }.apply()
        _settings.value = finalUpdated
    }

    /**
     * Tab/Section expanded state persistence across re-renders and app sessions.
     */
    fun isSectionExpanded(sectionKey: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean("section_expanded_$sectionKey", defaultValue)
    }

    fun setSectionExpanded(sectionKey: String, isExpanded: Boolean) {
        prefs.edit().putBoolean("section_expanded_$sectionKey", isExpanded).apply()
    }

    fun setOnboardingCompleted(completed: Boolean) {
        updateSettings { it.copy(isOnboardingCompleted = completed) }
    }

    fun setAllSectionsExpanded(sectionKeys: List<String>, isExpanded: Boolean) {
        prefs.edit().apply {
            sectionKeys.forEach { key ->
                putBoolean("section_expanded_$key", isExpanded)
            }
        }.apply()
    }
}

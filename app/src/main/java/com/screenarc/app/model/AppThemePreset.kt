package com.screenarc.app.model

/**
 * Visual Theme Presets:
 * Simplified to Dynamic Material You (system), Light, Dark, and 3 neutral accents (Indigo, Teal, Monochrome).
 * Gracefully falls back to DYNAMIC_SYSTEM if a previously saved preset no longer exists.
 */
enum class AppThemePreset(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val surfaceColorHex: Long,
    val isDarkOnly: Boolean = false,
    val isLightOnly: Boolean = false,
    val isGlass: Boolean = false
) {
    DYNAMIC_SYSTEM(
        id = "dynamic_system",
        displayName = "Dynamic Material You",
        subtitle = "Colors dynamically adapted from system wallpaper",
        primaryColorHex = 0xFF6750A4,
        secondaryColorHex = 0xFF625B71,
        surfaceColorHex = 0xFF141218
    ),
    LIGHT(
        id = "light",
        displayName = "Clean Light",
        subtitle = "Bright daylight surfaces with crisp contrast",
        primaryColorHex = 0xFF0061A4,
        secondaryColorHex = 0xFF535F70,
        surfaceColorHex = 0xFFFDFBFF,
        isLightOnly = true
    ),
    DARK(
        id = "dark",
        displayName = "Deep Dark",
        subtitle = "Comfortable low-glare dark surfaces",
        primaryColorHex = 0xFFD0BCFF,
        secondaryColorHex = 0xFFCCC2DC,
        surfaceColorHex = 0xFF141218,
        isDarkOnly = true
    ),
    INDIGO(
        id = "indigo",
        displayName = "Indigo Accent",
        subtitle = "Neutral modern theme with deep indigo accents",
        primaryColorHex = 0xFF3F51B5,
        secondaryColorHex = 0xFF5C6BC0,
        surfaceColorHex = 0xFF1A1C2E
    ),
    TEAL(
        id = "teal",
        displayName = "Teal Accent",
        subtitle = "Refreshing neutral theme with balanced teal tones",
        primaryColorHex = 0xFF00796B,
        secondaryColorHex = 0xFF26A69A,
        surfaceColorHex = 0xFF121E1C
    ),
    MONOCHROME(
        id = "monochrome",
        displayName = "Monochrome Accent",
        subtitle = "Minimalist grayscale palette with zero color distraction",
        primaryColorHex = 0xFF616161,
        secondaryColorHex = 0xFF9E9E9E,
        surfaceColorHex = 0xFF1E1E1E
    );

    companion object {
        fun fromId(id: String?, default: AppThemePreset = DYNAMIC_SYSTEM): AppThemePreset {
            if (id.isNullOrBlank()) return default
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: default
        }
    }
}

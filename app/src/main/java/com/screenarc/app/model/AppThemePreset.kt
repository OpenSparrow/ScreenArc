package com.screenarc.app.model

/**
 * ScreenArc Visual Theme Presets:
 * 7 Presets with "Dark Glassmorphism with Soft Glows" (Electric Indigo) as the default hero theme:
 * 1. GLASSMORPHIC: Dark Glassmorphic with Electric Indigo & soft ambient glows (DEFAULT)
 * 2. DYNAMIC_SYSTEM: Monet Dynamic adapted from Android system wallpaper (Android 12+)
 * 3. MIDNIGHT_AMOLED: Pitch black 0% battery draw with vivid cyan & neon purple accents
 * 4. CATPPUCCIN: Warm cozy espresso surfaces with soothing pastel lavender & sky blue
 * 5. NEON_GLOW: Cyberpunk high-contrast electric neon yellow & cyan on deep violet
 * 6. NORDIC_BLUE: Polar night surfaces with glacial frost blues & arctic highlights
 * 7. SUNSET_ROSE: Dusk rose and cherry blossom highlights on midnight plum
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
    GLASSMORPHIC(
        id = "glassmorphic",
        displayName = "Dark Glassmorphic",
        subtitle = "Electric indigo glow with frosted acrylic translucent surfaces",
        primaryColorHex = 0xFF6C63FF,
        secondaryColorHex = 0xFF00F2FE,
        surfaceColorHex = 0xFF08070D,
        isDarkOnly = true,
        isGlass = true
    ),
    DYNAMIC_SYSTEM(
        id = "dynamic_system",
        displayName = "Monet Dynamic",
        subtitle = "Colors dynamically adapted from system wallpaper",
        primaryColorHex = 0xFF6750A4,
        secondaryColorHex = 0xFF625B71,
        surfaceColorHex = 0xFF141218
    ),
    MIDNIGHT_AMOLED(
        id = "midnight_amoled",
        displayName = "Midnight AMOLED",
        subtitle = "Pitch black 0% battery draw with vivid cyan accents",
        primaryColorHex = 0xFF00E5FF,
        secondaryColorHex = 0xFFB388FF,
        surfaceColorHex = 0xFF000000,
        isDarkOnly = true
    ),
    CATPPUCCIN(
        id = "catppuccin",
        displayName = "Catppuccin Mocha",
        subtitle = "Warm cozy espresso surfaces with soothing pastel lavender",
        primaryColorHex = 0xFFCBA6F7,
        secondaryColorHex = 0xFF89B4FA,
        surfaceColorHex = 0xFF1E1E2E,
        isDarkOnly = true
    ),
    NEON_GLOW(
        id = "neon_glow",
        displayName = "Neon Glow",
        subtitle = "Cyberpunk high-contrast electric neon accents",
        primaryColorHex = 0xFFFFE600,
        secondaryColorHex = 0xFF00F0FF,
        surfaceColorHex = 0xFF0A0711,
        isDarkOnly = true
    ),
    NORDIC_BLUE(
        id = "nordic_blue",
        displayName = "Nordic Blue",
        subtitle = "Glacial frost blues and clean arctic surfaces",
        primaryColorHex = 0xFF88C0D0,
        secondaryColorHex = 0xFF81A1C1,
        surfaceColorHex = 0xFF242933
    ),
    SUNSET_ROSE(
        id = "sunset_rose",
        displayName = "Sunset Rose",
        subtitle = "Warm dusk rose and cherry blossom highlights",
        primaryColorHex = 0xFFF06292,
        secondaryColorHex = 0xFFCE93D8,
        surfaceColorHex = 0xFF1E1419
    );

    companion object {
        fun fromId(id: String?, default: AppThemePreset = GLASSMORPHIC): AppThemePreset {
            if (id.isNullOrBlank()) return default
            // Direct id or enum name match
            entries.find { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }?.let { return it }
            // Backwards-compatible aliases
            return when (id.lowercase()) {
                "light" -> NORDIC_BLUE
                "dark" -> GLASSMORPHIC
                "indigo" -> GLASSMORPHIC
                "teal" -> NORDIC_BLUE
                "monochrome" -> MIDNIGHT_AMOLED
                "catppuccin_mocha", "catppuccin_latte" -> CATPPUCCIN
                "dracula", "cyberpunk" -> NEON_GLOW
                "nord_frost", "nord_snow" -> NORDIC_BLUE
                "sakura", "sakura_dark", "sakura_light" -> SUNSET_ROSE
                "glass_aurora", "glass_frost" -> GLASSMORPHIC
                else -> default
            }
        }
    }
}

package com.screenarc.app.model

/**
 * Visual Theme Presets inspired by Material 3 Expressive and DropSauce styling.
 */
enum class AppThemePreset(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val surfaceColorHex: Long,
    val isDarkOnly: Boolean = false
) {
    DYNAMIC_SYSTEM(
        id = "dynamic_system",
        displayName = "Monet Dynamic",
        subtitle = "Material You extracted from device wallpaper",
        primaryColorHex = 0xFF6750A4,
        secondaryColorHex = 0xFF625B71,
        surfaceColorHex = 0xFF141218
    ),
    MIDNIGHT_AMOLED(
        id = "midnight_amoled",
        displayName = "Midnight AMOLED",
        subtitle = "Pure pitch black 0% battery draw with electric cyan",
        primaryColorHex = 0xFF00E5FF,
        secondaryColorHex = 0xFFB388FF,
        surfaceColorHex = 0xFF000000,
        isDarkOnly = true
    ),
    CATPPUCCIN_MOCHA(
        id = "catppuccin_mocha",
        displayName = "Catppuccin Mocha",
        subtitle = "Soothing espresso dark base with pastel lavender & sapphire",
        primaryColorHex = 0xFFCBA6F7,
        secondaryColorHex = 0xFF89B4FA,
        surfaceColorHex = 0xFF1E1E2E
    ),
    DRACULA(
        id = "dracula",
        displayName = "Dracula",
        subtitle = "Vampire slate palette with vibrant pink, purple & spring green",
        primaryColorHex = 0xFFFF79C6,
        secondaryColorHex = 0xFFBD93F9,
        surfaceColorHex = 0xFF282A36,
        isDarkOnly = true
    ),
    NORD_FROST(
        id = "nord_frost",
        displayName = "Nord Frost",
        subtitle = "Arctic polar night with glacial cyan & aurora green",
        primaryColorHex = 0xFF88C0D0,
        secondaryColorHex = 0xFF81A1C1,
        surfaceColorHex = 0xFF2E3440
    ),
    SAKURA_BLOSSOM(
        id = "sakura_blossom",
        displayName = "Sakura Blossom",
        subtitle = "Delicate Japanese cherry blossom pink and rose petal tones",
        primaryColorHex = 0xFFF06292,
        secondaryColorHex = 0xFFBA68C8,
        surfaceColorHex = 0xFF281C23
    ),
    CYBERPUNK_NEON(
        id = "cyberpunk_neon",
        displayName = "Cyberpunk Neon",
        subtitle = "High-voltage neon yellow, vibrant cyan & dark obsidian",
        primaryColorHex = 0xFFFFE600,
        secondaryColorHex = 0xFF00F0FF,
        surfaceColorHex = 0xFF120E1E,
        isDarkOnly = true
    );

    companion object {
        fun fromId(id: String?, default: AppThemePreset = DYNAMIC_SYSTEM): AppThemePreset {
            return values().find { it.id.equals(id, ignoreCase = true) } ?: default
        }
    }
}

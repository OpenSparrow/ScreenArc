package com.screenarc.app.model

/**
 * Visual Theme Presets inspired by Material 3 Expressive and DropSauce styling.
 */
enum class ThemeCategory(val displayName: String) {
    ALL("All"),
    LIGHT("Light"),
    DARK("Dark"),
    GLASS("Glassmorphism");

    fun getPresets(): List<AppThemePreset> = AppThemePreset.values().filter { it.matchesCategory(this) }

    companion object {
        fun fromName(name: String?, default: ThemeCategory = ALL): ThemeCategory {
            return values().find { it.name.equals(name, ignoreCase = true) } ?: default
        }
    }
}

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
        displayName = "Monet Dynamic",
        subtitle = "Material You extracted from device wallpaper",
        primaryColorHex = 0xFF6750A4,
        secondaryColorHex = 0xFF625B71,
        surfaceColorHex = 0xFF141218
    ),
    MATERIAL_LIGHT(
        id = "material_light",
        displayName = "Material Clean Light",
        subtitle = "Crisp, bright daylight surfaces with vibrant indigo accents",
        primaryColorHex = 0xFF0061A4,
        secondaryColorHex = 0xFF535F70,
        surfaceColorHex = 0xFFFDFBFF,
        isLightOnly = true
    ),
    PASTEL_CREAM(
        id = "pastel_cream",
        displayName = "Pastel Cream Light",
        subtitle = "Warm soothing ivory & amber palette for daylight comfort",
        primaryColorHex = 0xFF8C5000,
        secondaryColorHex = 0xFF715B2E,
        surfaceColorHex = 0xFFFFF8F4,
        isLightOnly = true
    ),
    NORD_SNOW(
        id = "nord_snow",
        displayName = "Nordic Snow Light",
        subtitle = "Pristine arctic snow with ice-blue & frosted mint",
        primaryColorHex = 0xFF5E81AC,
        secondaryColorHex = 0xFF88C0D0,
        surfaceColorHex = 0xFFECEFF4,
        isLightOnly = true
    ),
    GLASS_FROST(
        id = "glass_frost",
        displayName = "Frosted Glass Light",
        subtitle = "Glassmorphism with translucent ice cyan & frosted acrylic",
        primaryColorHex = 0xFF00677D,
        secondaryColorHex = 0xFF4B626A,
        surfaceColorHex = 0xFFF0F7F9,
        isLightOnly = true,
        isGlass = true
    ),
    GLASS_AURORA(
        id = "glass_aurora",
        displayName = "Glassmorphism Aurora",
        subtitle = "Acrylic dark glass with electric violet & emerald aura",
        primaryColorHex = 0xFFD0BCFF,
        secondaryColorHex = 0xFF7D5260,
        surfaceColorHex = 0xFF181524,
        isDarkOnly = true,
        isGlass = true
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

    fun matchesCategory(category: ThemeCategory): Boolean = when (category) {
        ThemeCategory.ALL -> true
        ThemeCategory.LIGHT -> isLightOnly
        ThemeCategory.DARK -> isDarkOnly || (!isLightOnly && !isGlass)
        ThemeCategory.GLASS -> isGlass
    }

    fun primaryCategory(): ThemeCategory = when {
        isGlass -> ThemeCategory.GLASS
        isLightOnly -> ThemeCategory.LIGHT
        else -> ThemeCategory.DARK
    }

    companion object {
        fun fromId(id: String?, default: AppThemePreset = DYNAMIC_SYSTEM): AppThemePreset {
            return values().find { it.id.equals(id, ignoreCase = true) } ?: default
        }
    }
}

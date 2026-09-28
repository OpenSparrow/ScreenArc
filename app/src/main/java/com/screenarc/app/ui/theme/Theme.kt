package com.screenarc.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.screenarc.app.data.UiDesignStyle
import com.screenarc.app.model.AppThemePreset

// Default Monet / Standard Schemas
private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh
)

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh
)

// 1. Midnight AMOLED
private val MidnightAmoledColorScheme = darkColorScheme(
    primary = amoled_primary,
    onPrimary = amoled_onPrimary,
    primaryContainer = amoled_primaryContainer,
    onPrimaryContainer = amoled_onPrimaryContainer,
    secondary = amoled_secondary,
    onSecondary = amoled_onSecondary,
    secondaryContainer = amoled_secondaryContainer,
    onSecondaryContainer = amoled_onSecondaryContainer,
    tertiary = amoled_tertiary,
    onTertiary = amoled_onTertiary,
    tertiaryContainer = amoled_tertiaryContainer,
    onTertiaryContainer = amoled_onTertiaryContainer,
    background = amoled_background,
    onBackground = amoled_onBackground,
    surface = amoled_surface,
    onSurface = amoled_onSurface,
    surfaceVariant = amoled_surfaceVariant,
    onSurfaceVariant = amoled_onSurfaceVariant,
    outline = amoled_outline,
    surfaceContainerHigh = amoled_surfaceContainerHigh
)

// 2. Catppuccin Mocha / Latte
private val CatppuccinMochaColorScheme = darkColorScheme(
    primary = catppuccin_mocha_primary,
    onPrimary = catppuccin_mocha_onPrimary,
    primaryContainer = catppuccin_mocha_primaryContainer,
    onPrimaryContainer = catppuccin_mocha_onPrimaryContainer,
    secondary = catppuccin_mocha_secondary,
    onSecondary = catppuccin_mocha_onSecondary,
    secondaryContainer = catppuccin_mocha_secondaryContainer,
    onSecondaryContainer = catppuccin_mocha_onSecondaryContainer,
    tertiary = catppuccin_mocha_tertiary,
    onTertiary = catppuccin_mocha_onTertiary,
    tertiaryContainer = catppuccin_mocha_tertiaryContainer,
    onTertiaryContainer = catppuccin_mocha_onTertiaryContainer,
    background = catppuccin_mocha_background,
    onBackground = catppuccin_mocha_onBackground,
    surface = catppuccin_mocha_surface,
    onSurface = catppuccin_mocha_onSurface,
    surfaceVariant = catppuccin_mocha_surfaceVariant,
    onSurfaceVariant = catppuccin_mocha_onSurfaceVariant,
    outline = catppuccin_mocha_outline,
    surfaceContainerHigh = catppuccin_mocha_surfaceContainerHigh
)

private val CatppuccinLatteColorScheme = lightColorScheme(
    primary = catppuccin_latte_primary,
    onPrimary = catppuccin_latte_onPrimary,
    primaryContainer = catppuccin_latte_primaryContainer,
    onPrimaryContainer = catppuccin_latte_onPrimaryContainer,
    secondary = catppuccin_latte_secondary,
    onSecondary = catppuccin_latte_onSecondary,
    secondaryContainer = catppuccin_latte_secondaryContainer,
    onSecondaryContainer = catppuccin_latte_onSecondaryContainer,
    tertiary = catppuccin_latte_tertiary,
    background = catppuccin_latte_background,
    onBackground = catppuccin_latte_onBackground,
    surface = catppuccin_latte_surface,
    onSurface = catppuccin_latte_onSurface,
    surfaceVariant = catppuccin_latte_surfaceVariant,
    onSurfaceVariant = catppuccin_latte_onSurfaceVariant,
    outline = catppuccin_latte_outline,
    surfaceContainerHigh = catppuccin_latte_surfaceContainerHigh
)

// 3. Dracula
private val DraculaColorScheme = darkColorScheme(
    primary = dracula_primary,
    onPrimary = dracula_onPrimary,
    primaryContainer = dracula_primaryContainer,
    onPrimaryContainer = dracula_onPrimaryContainer,
    secondary = dracula_secondary,
    onSecondary = dracula_onSecondary,
    secondaryContainer = dracula_secondaryContainer,
    onSecondaryContainer = dracula_onSecondaryContainer,
    tertiary = dracula_tertiary,
    onTertiary = dracula_onTertiary,
    tertiaryContainer = dracula_tertiaryContainer,
    onTertiaryContainer = dracula_onTertiaryContainer,
    background = dracula_background,
    onBackground = dracula_onBackground,
    surface = dracula_surface,
    onSurface = dracula_onSurface,
    surfaceVariant = dracula_surfaceVariant,
    onSurfaceVariant = dracula_onSurfaceVariant,
    outline = dracula_outline,
    surfaceContainerHigh = dracula_surfaceContainerHigh
)

// 4. Nord Frost
private val NordFrostDarkColorScheme = darkColorScheme(
    primary = nord_frost_primary,
    onPrimary = nord_frost_onPrimary,
    primaryContainer = nord_frost_primaryContainer,
    onPrimaryContainer = nord_frost_onPrimaryContainer,
    secondary = nord_frost_secondary,
    onSecondary = nord_frost_onSecondary,
    secondaryContainer = nord_frost_secondaryContainer,
    onSecondaryContainer = nord_frost_onSecondaryContainer,
    tertiary = nord_frost_tertiary,
    onTertiary = nord_frost_onTertiary,
    background = nord_frost_background,
    onBackground = nord_frost_onBackground,
    surface = nord_frost_surface,
    onSurface = nord_frost_onSurface,
    surfaceVariant = nord_frost_surfaceVariant,
    onSurfaceVariant = nord_frost_onSurfaceVariant,
    outline = nord_frost_outline,
    surfaceContainerHigh = nord_frost_surfaceContainerHigh
)

private val NordFrostLightColorScheme = lightColorScheme(
    primary = nord_snow_primary,
    onPrimary = nord_snow_onPrimary,
    primaryContainer = nord_snow_primaryContainer,
    onPrimaryContainer = nord_snow_onPrimaryContainer,
    secondary = nord_snow_secondary,
    onSecondary = nord_snow_onSecondary,
    secondaryContainer = nord_snow_secondaryContainer,
    onSecondaryContainer = nord_snow_onSecondaryContainer,
    tertiary = nord_snow_tertiary,
    background = nord_snow_background,
    onBackground = nord_snow_onBackground,
    surface = nord_snow_surface,
    onSurface = nord_snow_onSurface,
    surfaceVariant = nord_snow_surfaceVariant,
    onSurfaceVariant = nord_snow_onSurfaceVariant,
    outline = nord_snow_outline,
    surfaceContainerHigh = nord_snow_surfaceContainerHigh
)

// 5. Sakura Blossom
private val SakuraBlossomDarkColorScheme = darkColorScheme(
    primary = sakura_dark_primary,
    onPrimary = sakura_dark_onPrimary,
    primaryContainer = sakura_dark_primaryContainer,
    onPrimaryContainer = sakura_dark_onPrimaryContainer,
    secondary = sakura_dark_secondary,
    onSecondary = sakura_dark_onSecondary,
    secondaryContainer = sakura_dark_secondaryContainer,
    onSecondaryContainer = sakura_dark_onSecondaryContainer,
    tertiary = sakura_dark_tertiary,
    onTertiary = sakura_dark_onTertiary,
    background = sakura_dark_background,
    onBackground = sakura_dark_onBackground,
    surface = sakura_dark_surface,
    onSurface = sakura_dark_onSurface,
    surfaceVariant = sakura_dark_surfaceVariant,
    onSurfaceVariant = sakura_dark_onSurfaceVariant,
    outline = sakura_dark_outline,
    surfaceContainerHigh = sakura_dark_surfaceContainerHigh
)

private val SakuraBlossomLightColorScheme = lightColorScheme(
    primary = sakura_light_primary,
    onPrimary = sakura_light_onPrimary,
    primaryContainer = sakura_light_primaryContainer,
    onPrimaryContainer = sakura_light_onPrimaryContainer,
    secondary = sakura_light_secondary,
    onSecondary = sakura_light_onSecondary,
    secondaryContainer = sakura_light_secondaryContainer,
    onSecondaryContainer = sakura_light_onSecondaryContainer,
    tertiary = sakura_light_tertiary,
    background = sakura_light_background,
    onBackground = sakura_light_onBackground,
    surface = sakura_light_surface,
    onSurface = sakura_light_onSurface,
    surfaceVariant = sakura_light_surfaceVariant,
    onSurfaceVariant = sakura_light_onSurfaceVariant,
    outline = sakura_light_outline,
    surfaceContainerHigh = sakura_light_surfaceContainerHigh
)

// 6. Cyberpunk Neon
private val CyberpunkNeonColorScheme = darkColorScheme(
    primary = cyberpunk_primary,
    onPrimary = cyberpunk_onPrimary,
    primaryContainer = cyberpunk_primaryContainer,
    onPrimaryContainer = cyberpunk_onPrimaryContainer,
    secondary = cyberpunk_secondary,
    onSecondary = cyberpunk_onSecondary,
    secondaryContainer = cyberpunk_secondaryContainer,
    onSecondaryContainer = cyberpunk_onSecondaryContainer,
    tertiary = cyberpunk_tertiary,
    onTertiary = cyberpunk_onTertiary,
    tertiaryContainer = cyberpunk_tertiaryContainer,
    onTertiaryContainer = cyberpunk_onTertiaryContainer,
    background = cyberpunk_background,
    onBackground = cyberpunk_onBackground,
    surface = cyberpunk_surface,
    onSurface = cyberpunk_onSurface,
    surfaceVariant = cyberpunk_surfaceVariant,
    onSurfaceVariant = cyberpunk_onSurfaceVariant,
    outline = cyberpunk_outline,
    surfaceContainerHigh = cyberpunk_surfaceContainerHigh
)

// 7. Material Clean Light
private val MaterialCleanLightColorScheme = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    surfaceContainerHigh = Color(0xFFE8ECF4)
)

// 8. Pastel Cream Light
private val PastelCreamColorScheme = lightColorScheme(
    primary = Color(0xFF8C5000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC1),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF715B2E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFDE0A6),
    onSecondaryContainer = Color(0xFF261900),
    tertiary = Color(0xFF52643F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5EABA),
    onTertiaryContainer = Color(0xFF101F03),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF211A14),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF211A14),
    surfaceVariant = Color(0xFFF3DFD1),
    onSurfaceVariant = Color(0xFF51443B),
    outline = Color(0xFF84746A),
    surfaceContainerHigh = Color(0xFFF7ECE4)
)

// 9. Glassmorphism Frost Light
private val GlassFrostLightColorScheme = lightColorScheme(
    primary = Color(0xFF007A99),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0x99B3EBFF),
    onPrimaryContainer = Color(0xFF001F27),
    secondary = Color(0xFF4B626A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0x88CEE7F0),
    onSecondaryContainer = Color(0xFF061E25),
    tertiary = Color(0xFF585C7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0x99DFE0FF),
    onTertiaryContainer = Color(0xFF151937),
    background = Color(0xFFEBF3F8),
    onBackground = Color(0xFF191C1D),
    surface = Color(0x99FFFFFF),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0x73D8E8F0),
    onSurfaceVariant = Color(0xFF3F484B),
    outline = Color(0x80FFFFFF),
    surfaceContainerHigh = Color(0xCCFFFFFF)
)

// 10. Glassmorphism Aurora Dark
private val GlassAuroraDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0x664F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0x554A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0x66633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF0C0916),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0x35231B38),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0x45342754),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0x66FFFFFF),
    surfaceContainerHigh = Color(0x5436285C)
)

@Composable
fun ScreenArcTheme(
    themePreset: AppThemePreset = AppThemePreset.DYNAMIC_SYSTEM,
    uiDesignStyle: UiDesignStyle = UiDesignStyle.MATERIAL,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isGlassmorphic = uiDesignStyle == UiDesignStyle.GLASSMORPHISM || themePreset.isGlass

    val baseColorScheme: ColorScheme = when (themePreset) {
        AppThemePreset.DYNAMIC_SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        AppThemePreset.MATERIAL_LIGHT -> MaterialCleanLightColorScheme
        AppThemePreset.PASTEL_CREAM -> PastelCreamColorScheme
        AppThemePreset.NORD_SNOW -> NordFrostLightColorScheme
        AppThemePreset.GLASS_FROST -> GlassFrostLightColorScheme
        AppThemePreset.GLASS_AURORA -> GlassAuroraDarkColorScheme
        AppThemePreset.MIDNIGHT_AMOLED -> MidnightAmoledColorScheme
        AppThemePreset.CATPPUCCIN_MOCHA -> if (darkTheme) CatppuccinMochaColorScheme else CatppuccinLatteColorScheme
        AppThemePreset.DRACULA -> DraculaColorScheme
        AppThemePreset.NORD_FROST -> if (darkTheme) NordFrostDarkColorScheme else NordFrostLightColorScheme
        AppThemePreset.SAKURA_BLOSSOM -> if (darkTheme) SakuraBlossomDarkColorScheme else SakuraBlossomLightColorScheme
        AppThemePreset.CYBERPUNK_NEON -> CyberpunkNeonColorScheme
    }

    // When glassmorphism is active, ensure surface & container layers are translucent with specular highlights
    // and explicitly enforce high-contrast typography tokens across Dark and Light modes.
    val effectiveColorScheme = if (isGlassmorphic) {
        val isEffectiveDark = darkTheme || themePreset.isDarkOnly
        if (isEffectiveDark) {
            baseColorScheme.copy(
                background = Color(0xFF0C0916),
                onBackground = Color(0xFFF2EFF8),
                surface = Color(0x35231B38),
                onSurface = Color(0xFFF2EFF8),
                surfaceVariant = Color(0x45342754),
                onSurfaceVariant = Color(0xFFDDD7E8),
                surfaceContainer = Color(0x42281E46),
                surfaceContainerHigh = Color(0x5436285C),
                surfaceContainerHighest = Color(0x66443372),
                outline = Color(0x66FFFFFF),
                outlineVariant = Color(0x33FFFFFF)
            )
        } else {
            baseColorScheme.copy(
                background = Color(0xFFEBF3F8),
                onBackground = Color(0xFF141C24),
                surface = Color(0x99FFFFFF),
                onSurface = Color(0xFF141C24),
                surfaceVariant = Color(0x73D8E8F0),
                onSurfaceVariant = Color(0xFF38454F),
                surfaceContainer = Color(0xB3FFFFFF),
                surfaceContainerHigh = Color(0xCCFFFFFF),
                surfaceContainerHighest = Color(0xDDFFFFFF),
                outline = Color(0x80FFFFFF),
                outlineVariant = Color(0x4DFFFFFF)
            )
        }
    } else {
        baseColorScheme
    }

    CompositionLocalProvider(
        LocalIsGlassmorphic provides isGlassmorphic,
        LocalUiDesignStyle provides uiDesignStyle,
        androidx.compose.material3.LocalContentColor provides effectiveColorScheme.onSurface
    ) {
        MaterialTheme(
            colorScheme = effectiveColorScheme,
            typography = Typography,
            content = content
        )
    }
}

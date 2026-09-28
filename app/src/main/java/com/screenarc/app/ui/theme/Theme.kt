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
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun ScreenArcTheme(
    themePreset: AppThemePreset = AppThemePreset.DYNAMIC_SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme: ColorScheme = when (themePreset) {
        AppThemePreset.DYNAMIC_SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        AppThemePreset.MIDNIGHT_AMOLED -> MidnightAmoledColorScheme
        AppThemePreset.CATPPUCCIN_MOCHA -> if (darkTheme) CatppuccinMochaColorScheme else CatppuccinLatteColorScheme
        AppThemePreset.DRACULA -> DraculaColorScheme
        AppThemePreset.NORD_FROST -> if (darkTheme) NordFrostDarkColorScheme else NordFrostLightColorScheme
        AppThemePreset.SAKURA_BLOSSOM -> if (darkTheme) SakuraBlossomDarkColorScheme else SakuraBlossomLightColorScheme
        AppThemePreset.CYBERPUNK_NEON -> CyberpunkNeonColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

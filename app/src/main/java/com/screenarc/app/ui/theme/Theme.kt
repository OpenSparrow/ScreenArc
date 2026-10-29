package com.screenarc.app.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.screenarc.app.data.UiDesignStyle
import com.screenarc.app.model.AppThemePreset

// =================================================================
// 1. HERO PRESET: Dark Glassmorphic with Soft Glows (DEFAULT)
// =================================================================
private val DarkGlassmorphicColorScheme = darkColorScheme(
    primary = ElectricIndigo,
    onPrimary = Color.White,
    primaryContainer = ElectricIndigoContainer,
    onPrimaryContainer = OnElectricIndigoContainer,
    secondary = AuroraCyan,
    onSecondary = Color(0xFF041820),
    secondaryContainer = AuroraCyanContainer,
    onSecondaryContainer = OnAuroraCyanContainer,
    tertiary = Ultraviolet,
    onTertiary = Color.White,
    tertiaryContainer = UltravioletContainer,
    onTertiaryContainer = OnUltravioletContainer,
    background = DarkGlassBackground,
    onBackground = DarkGlassOnSurface,
    surface = DarkGlassSurface,
    onSurface = DarkGlassOnSurface,
    surfaceVariant = DarkGlassSurfaceVariant,
    onSurfaceVariant = DarkGlassOnSurfaceVariant,
    surfaceContainer = DarkGlassSurface,
    surfaceContainerHigh = DarkGlassSurfaceElevated,
    surfaceContainerHighest = DarkGlassSurfaceHighest,
    outline = DarkGlassOutline,
    outlineVariant = DarkGlassOutlineVariant
)

// =================================================================
// 2. Monet / Standard Schemas
// =================================================================
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

// =================================================================
// 3. Midnight AMOLED
// =================================================================
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

// =================================================================
// 4. Catppuccin Mocha & Latte
// =================================================================
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

// =================================================================
// 5. Neon Glow (Cyberpunk)
// =================================================================
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

// =================================================================
// 6. Nordic Blue (Nord Frost)
// =================================================================
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

// =================================================================
// 7. Sunset Rose (Sakura Blossom)
// =================================================================
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

@Composable
fun ScreenArcTheme(
    themePreset: AppThemePreset = AppThemePreset.GLASSMORPHIC,
    uiDesignStyle: UiDesignStyle = UiDesignStyle.GLASSMORPHISM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isGlassmorphic = uiDesignStyle == UiDesignStyle.GLASSMORPHISM || themePreset.isGlass

    val baseColorScheme: ColorScheme = when (themePreset) {
        AppThemePreset.GLASSMORPHIC -> DarkGlassmorphicColorScheme
        AppThemePreset.DYNAMIC_SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        AppThemePreset.MIDNIGHT_AMOLED -> MidnightAmoledColorScheme
        AppThemePreset.CATPPUCCIN -> if (darkTheme) CatppuccinMochaColorScheme else CatppuccinLatteColorScheme
        AppThemePreset.NEON_GLOW -> CyberpunkNeonColorScheme
        AppThemePreset.NORDIC_BLUE -> if (darkTheme) NordFrostDarkColorScheme else NordFrostLightColorScheme
        AppThemePreset.SUNSET_ROSE -> if (darkTheme) SakuraBlossomDarkColorScheme else SakuraBlossomLightColorScheme
    }

    // When glassmorphism is active, ensure surface & container layers are translucent with specular rim highlights
    val effectiveColorScheme = if (isGlassmorphic) {
        val isEffectiveDark = darkTheme || themePreset.isDarkOnly
        if (isEffectiveDark) {
            baseColorScheme.copy(
                background = DarkGlassBackground,
                onBackground = DarkGlassOnSurface,
                surface = Color(0x351F1B38),
                onSurface = DarkGlassOnSurface,
                surfaceVariant = Color(0x452E2754),
                onSurfaceVariant = DarkGlassOnSurfaceVariant,
                surfaceContainer = Color(0x4018152B),
                surfaceContainerHigh = Color(0x55282247),
                surfaceContainerHighest = Color(0x66393064),
                outline = Color(0x44FFFFFF),
                outlineVariant = Color(0x22FFFFFF)
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

    val animatedColorScheme = animateColorScheme(effectiveColorScheme)

    val customColors = ScreenArcCustomColors(
        primaryGlow = animatedColorScheme.primary.copy(alpha = 0.5f),
        secondaryGlow = animatedColorScheme.secondary.copy(alpha = 0.4f),
        tertiaryGlow = animatedColorScheme.tertiary.copy(alpha = 0.4f),
        arcGradient = listOf(animatedColorScheme.secondary, animatedColorScheme.primary, animatedColorScheme.tertiary),
        glassSurface = if (darkTheme || themePreset.isDarkOnly) GlassFrostDark else Color(0xB3FFFFFF),
        glassSurfaceElevated = if (darkTheme || themePreset.isDarkOnly) GlassFrostDarkElevated else Color(0xCCFFFFFF),
        glassRimTop = if (darkTheme || themePreset.isDarkOnly) GlassRimTop else Color(0x99FFFFFF),
        glassRimBottom = if (darkTheme || themePreset.isDarkOnly) GlassRimBottom else Color(0x33000000),
        shimmerBase = if (darkTheme || themePreset.isDarkOnly) ShimmerBaseDark else ShimmerBaseLight,
        shimmerHighlight = if (darkTheme || themePreset.isDarkOnly) ShimmerHighlightDark else ShimmerHighlightLight
    )

    CompositionLocalProvider(
        LocalIsGlassmorphic provides isGlassmorphic,
        LocalUiDesignStyle provides uiDesignStyle,
        LocalScreenArcCustomColors provides customColors,
        androidx.compose.material3.LocalContentColor provides animatedColorScheme.onSurface
    ) {
        MaterialTheme(
            colorScheme = animatedColorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = 300)
    val primary by animateColorAsState(target.primary, spec, label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "onPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, spec, label = "secondary")
    val onSecondary by animateColorAsState(target.onSecondary, spec, label = "onSecondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, spec, label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, spec, label = "onSecondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, spec, label = "tertiary")
    val onTertiary by animateColorAsState(target.onTertiary, spec, label = "onTertiary")
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, spec, label = "tertiaryContainer")
    val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, spec, label = "onTertiaryContainer")
    val background by animateColorAsState(target.background, spec, label = "background")
    val onBackground by animateColorAsState(target.onBackground, spec, label = "onBackground")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "onSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant")
    val surfaceContainer by animateColorAsState(target.surfaceContainer, spec, label = "surfaceContainer")
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, spec, label = "surfaceContainerHigh")
    val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, spec, label = "surfaceContainerHighest")
    val outline by animateColorAsState(target.outline, spec, label = "outline")
    val outlineVariant by animateColorAsState(target.outlineVariant, spec, label = "outlineVariant")
    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        outline = outline,
        outlineVariant = outlineVariant
    )
}

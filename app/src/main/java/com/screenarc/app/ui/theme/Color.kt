package com.screenarc.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// =================================================================
// 1. HERO PALETTE: Dark Glassmorphism with Soft Glows (DEFAULT)
// =================================================================
val ElectricIndigo = Color(0xFF6C63FF)
val ElectricIndigoDark = Color(0xFF4D44DB)
val ElectricIndigoContainer = Color(0xFF231F4A)
val OnElectricIndigoContainer = Color(0xFFE4E1FF)

val AuroraCyan = Color(0xFF00F2FE)
val AuroraCyanDark = Color(0xFF00A8B2)
val AuroraCyanContainer = Color(0xFF003842)
val OnAuroraCyanContainer = Color(0xFF8FF6FF)

val Ultraviolet = Color(0xFF8E2DE2)
val UltravioletDark = Color(0xFF6318A8)
val UltravioletContainer = Color(0xFF38105B)
val OnUltravioletContainer = Color(0xFFF0D6FF)

// Hero Tonal Dark Surfaces
val DarkGlassBackground = Color(0xFF08070D)
val DarkGlassSurface = Color(0xFF12101C)
val DarkGlassSurfaceElevated = Color(0xFF1A1828)
val DarkGlassSurfaceHighest = Color(0xFF26223B)
val DarkGlassSurfaceVariant = Color(0xFF1E1A2F)
val DarkGlassOnSurface = Color(0xFFF3F1FA)
val DarkGlassOnSurfaceVariant = Color(0xFFB5B0CA)
val DarkGlassOutline = Color(0xFF332E4E)
val DarkGlassOutlineVariant = Color(0xFF211D33)
val DarkGlassOutlineGlow = Color(0x666C63FF)
val DarkGlassSpecularRim = Color(0x40FFFFFF)

// Translucent Frosted Glass Tokens
val GlassFrostDark = Color(0x2E161329)
val GlassFrostDarkElevated = Color(0x44221C3D)
val GlassRimTop = Color(0x55FFFFFF)
val GlassRimBottom = Color(0x15FFFFFF)

// Shimmer Animation Tokens
val ShimmerBaseDark = Color(0xFF181528)
val ShimmerHighlightDark = Color(0xFF2E294B)
val ShimmerBaseLight = Color(0xFFE2E8F0)
val ShimmerHighlightLight = Color(0xFFF8FAFC)

// Arc Hero Gradients
val ArcHeroGradientColors = listOf(AuroraCyan, ElectricIndigo, Ultraviolet)
val ArcHeroBrush = Brush.linearGradient(ArcHeroGradientColors)

// =================================================================
// 2. MD3 Standard / Monet Light & Dark
// =================================================================
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

val md_theme_light_primary = Color(0xFF6750A4)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFEADDFF)
val md_theme_light_onPrimaryContainer = Color(0xFF21005D)
val md_theme_light_secondary = Color(0xFF625B71)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE8DEF8)
val md_theme_light_onSecondaryContainer = Color(0xFF1D192B)
val md_theme_light_tertiary = Color(0xFF7D5260)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFFFD8E4)
val md_theme_light_onTertiaryContainer = Color(0xFF31111D)
val md_theme_light_background = Color(0xFFFEF7FF)
val md_theme_light_onBackground = Color(0xFF1D1B20)
val md_theme_light_surface = Color(0xFFFEF7FF)
val md_theme_light_onSurface = Color(0xFF1D1B20)
val md_theme_light_surfaceVariant = Color(0xFFE7E0EC)
val md_theme_light_onSurfaceVariant = Color(0xFF49454F)
val md_theme_light_outline = Color(0xFF79747E)
val md_theme_light_surfaceContainerHigh = Color(0xFFECE6F0)

val md_theme_dark_primary = Color(0xFFD0BCFF)
val md_theme_dark_onPrimary = Color(0xFF381E72)
val md_theme_dark_primaryContainer = Color(0xFF4F378B)
val md_theme_dark_onPrimaryContainer = Color(0xFFEADDFF)
val md_theme_dark_secondary = Color(0xFFCCC2DC)
val md_theme_dark_onSecondary = Color(0xFF332D41)
val md_theme_dark_secondaryContainer = Color(0xFF4A4458)
val md_theme_dark_onSecondaryContainer = Color(0xFFE8DEF8)
val md_theme_dark_tertiary = Color(0xFFEFB8C8)
val md_theme_dark_onTertiary = Color(0xFF492532)
val md_theme_dark_tertiaryContainer = Color(0xFF633B48)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFD8E4)
val md_theme_dark_background = Color(0xFF141218)
val md_theme_dark_onBackground = Color(0xFFE6E0E9)
val md_theme_dark_surface = Color(0xFF141218)
val md_theme_dark_onSurface = Color(0xFFE6E0E9)
val md_theme_dark_surfaceVariant = Color(0xFF49454F)
val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4D0)
val md_theme_dark_outline = Color(0xFF938F99)
val md_theme_dark_surfaceContainerHigh = Color(0xFF2B2930)

// =================================================================
// 3. Midnight AMOLED (Pure Pitch Black 0% Battery Draw)
// =================================================================
val amoled_primary = Color(0xFF00E5FF)
val amoled_onPrimary = Color(0xFF000000)
val amoled_primaryContainer = Color(0xFF00363F)
val amoled_onPrimaryContainer = Color(0xFF80F2FF)
val amoled_secondary = Color(0xFFB388FF)
val amoled_onSecondary = Color(0xFF000000)
val amoled_secondaryContainer = Color(0xFF351C5E)
val amoled_onSecondaryContainer = Color(0xFFE2D1FF)
val amoled_tertiary = Color(0xFF00E676)
val amoled_onTertiary = Color(0xFF000000)
val amoled_tertiaryContainer = Color(0xFF00381B)
val amoled_onTertiaryContainer = Color(0xFF76FFAE)
val amoled_background = Color(0xFF000000)
val amoled_onBackground = Color(0xFFF0F0F0)
val amoled_surface = Color(0xFF000000)
val amoled_onSurface = Color(0xFFFFFFFF)
val amoled_surfaceVariant = Color(0xFF141414)
val amoled_onSurfaceVariant = Color(0xFFB8B8B8)
val amoled_outline = Color(0xFF2B2B2B)
val amoled_surfaceContainerHigh = Color(0xFF0A0A0A)

// =================================================================
// 4. Catppuccin Mocha & Latte
// =================================================================
val catppuccin_mocha_primary = Color(0xFFCBA6F7)
val catppuccin_mocha_onPrimary = Color(0xFF1E1E2E)
val catppuccin_mocha_primaryContainer = Color(0xFF45475A)
val catppuccin_mocha_onPrimaryContainer = Color(0xFFF5E0DC)
val catppuccin_mocha_secondary = Color(0xFF89B4FA)
val catppuccin_mocha_onSecondary = Color(0xFF1E1E2E)
val catppuccin_mocha_secondaryContainer = Color(0xFF313244)
val catppuccin_mocha_onSecondaryContainer = Color(0xFFB4BEFE)
val catppuccin_mocha_tertiary = Color(0xFFF5C2E7)
val catppuccin_mocha_onTertiary = Color(0xFF1E1E2E)
val catppuccin_mocha_tertiaryContainer = Color(0xFF585B70)
val catppuccin_mocha_onTertiaryContainer = Color(0xFFF5C2E7)
val catppuccin_mocha_background = Color(0xFF181825)
val catppuccin_mocha_onBackground = Color(0xFFCDD6F4)
val catppuccin_mocha_surface = Color(0xFF1E1E2E)
val catppuccin_mocha_onSurface = Color(0xFFCDD6F4)
val catppuccin_mocha_surfaceVariant = Color(0xFF313244)
val catppuccin_mocha_onSurfaceVariant = Color(0xFFA6ADC8)
val catppuccin_mocha_outline = Color(0xFF585B70)
val catppuccin_mocha_surfaceContainerHigh = Color(0xFF24273A)

val catppuccin_latte_primary = Color(0xFF8839EF)
val catppuccin_latte_onPrimary = Color(0xFFFFFFFF)
val catppuccin_latte_primaryContainer = Color(0xFFEA76CB)
val catppuccin_latte_onPrimaryContainer = Color(0xFF4C4F69)
val catppuccin_latte_secondary = Color(0xFF1E66F5)
val catppuccin_latte_onSecondary = Color(0xFFFFFFFF)
val catppuccin_latte_secondaryContainer = Color(0xFFCCD0DA)
val catppuccin_latte_onSecondaryContainer = Color(0xFF4C4F69)
val catppuccin_latte_tertiary = Color(0xFF40A02B)
val catppuccin_latte_background = Color(0xFFEFF1F5)
val catppuccin_latte_onBackground = Color(0xFF4C4F69)
val catppuccin_latte_surface = Color(0xFFE6E9EF)
val catppuccin_latte_onSurface = Color(0xFF4C4F69)
val catppuccin_latte_surfaceVariant = Color(0xFFDCE0E8)
val catppuccin_latte_onSurfaceVariant = Color(0xFF5C5F77)
val catppuccin_latte_outline = Color(0xFF9CA0B0)
val catppuccin_latte_surfaceContainerHigh = Color(0xFFDFE3EC)

// =================================================================
// 5. Dracula / Neon Glow
// =================================================================
val dracula_primary = Color(0xFFFF79C6)
val dracula_onPrimary = Color(0xFF282A36)
val dracula_primaryContainer = Color(0xFF44475A)
val dracula_onPrimaryContainer = Color(0xFFFF92D0)
val dracula_secondary = Color(0xFFBD93F9)
val dracula_onSecondary = Color(0xFF282A36)
val dracula_secondaryContainer = Color(0xFF3B3E52)
val dracula_onSecondaryContainer = Color(0xFFD6ACFF)
val dracula_tertiary = Color(0xFF50FA7B)
val dracula_onTertiary = Color(0xFF282A36)
val dracula_tertiaryContainer = Color(0xFF284832)
val dracula_onTertiaryContainer = Color(0xFF8AFF9E)
val dracula_background = Color(0xFF1E1F29)
val dracula_onBackground = Color(0xFFF8F8F2)
val dracula_surface = Color(0xFF282A36)
val dracula_onSurface = Color(0xFFF8F8F2)
val dracula_surfaceVariant = Color(0xFF44475A)
val dracula_onSurfaceVariant = Color(0xFFBFBFBA)
val dracula_outline = Color(0xFF6272A4)
val dracula_surfaceContainerHigh = Color(0xFF343746)

val cyberpunk_primary = Color(0xFFFFE600)
val cyberpunk_onPrimary = Color(0xFF000000)
val cyberpunk_primaryContainer = Color(0xFF474000)
val cyberpunk_onPrimaryContainer = Color(0xFFFFF380)
val cyberpunk_secondary = Color(0xFF00F0FF)
val cyberpunk_onSecondary = Color(0xFF000000)
val cyberpunk_secondaryContainer = Color(0xFF004347)
val cyberpunk_onSecondaryContainer = Color(0xFF99F8FF)
val cyberpunk_tertiary = Color(0xFFFF0055)
val cyberpunk_onTertiary = Color(0xFFFFFFFF)
val cyberpunk_tertiaryContainer = Color(0xFF52001B)
val cyberpunk_onTertiaryContainer = Color(0xFFFF99BB)
val cyberpunk_background = Color(0xFF0A0711)
val cyberpunk_onBackground = Color(0xFFF2F0FA)
val cyberpunk_surface = Color(0xFF120E1E)
val cyberpunk_onSurface = Color(0xFFF2F0FA)
val cyberpunk_surfaceVariant = Color(0xFF261D3D)
val cyberpunk_onSurfaceVariant = Color(0xFFD0C7E8)
val cyberpunk_outline = Color(0xFF6B5899)
val cyberpunk_surfaceContainerHigh = Color(0xFF1E1730)

// =================================================================
// 6. Nordic Blue (Nord Frost)
// =================================================================
val nord_frost_primary = Color(0xFF88C0D0)
val nord_frost_onPrimary = Color(0xFF2E3440)
val nord_frost_primaryContainer = Color(0xFF3B4252)
val nord_frost_onPrimaryContainer = Color(0xFF8FBCBB)
val nord_frost_secondary = Color(0xFF81A1C1)
val nord_frost_onSecondary = Color(0xFF2E3440)
val nord_frost_secondaryContainer = Color(0xFF434C5E)
val nord_frost_onSecondaryContainer = Color(0xFFD8DEE9)
val nord_frost_tertiary = Color(0xFFA3BE8C)
val nord_frost_onTertiary = Color(0xFF2E3440)
val nord_frost_background = Color(0xFF242933)
val nord_frost_onBackground = Color(0xFFECEFF4)
val nord_frost_surface = Color(0xFF2E3440)
val nord_frost_onSurface = Color(0xFFECEFF4)
val nord_frost_surfaceVariant = Color(0xFF3B4252)
val nord_frost_onSurfaceVariant = Color(0xFFD8DEE9)
val nord_frost_outline = Color(0xFF4C566A)
val nord_frost_surfaceContainerHigh = Color(0xFF3B4252)

val nord_snow_primary = Color(0xFF5E81AC)
val nord_snow_onPrimary = Color(0xFFFFFFFF)
val nord_snow_primaryContainer = Color(0xFFD8DEE9)
val nord_snow_onPrimaryContainer = Color(0xFF2E3440)
val nord_snow_secondary = Color(0xFF81A1C1)
val nord_snow_onSecondary = Color(0xFFFFFFFF)
val nord_snow_secondaryContainer = Color(0xFFE5E9F0)
val nord_snow_onSecondaryContainer = Color(0xFF3B4252)
val nord_snow_tertiary = Color(0xFF8FBCBB)
val nord_snow_background = Color(0xFFECEFF4)
val nord_snow_onBackground = Color(0xFF2E3440)
val nord_snow_surface = Color(0xFFE5E9F0)
val nord_snow_onSurface = Color(0xFF2E3440)
val nord_snow_surfaceVariant = Color(0xFFD8DEE9)
val nord_snow_onSurfaceVariant = Color(0xFF4C566A)
val nord_snow_outline = Color(0xFF98A2B3)
val nord_snow_surfaceContainerHigh = Color(0xFFD8DEE9)

// =================================================================
// 7. Sunset Rose (Sakura Blossom)
// =================================================================
val sakura_dark_primary = Color(0xFFF06292)
val sakura_dark_onPrimary = Color(0xFF3E1124)
val sakura_dark_primaryContainer = Color(0xFF5C1D38)
val sakura_dark_onPrimaryContainer = Color(0xFFFFD8E4)
val sakura_dark_secondary = Color(0xFFCE93D8)
val sakura_dark_onSecondary = Color(0xFF38143F)
val sakura_dark_secondaryContainer = Color(0xFF4E2055)
val sakura_dark_onSecondaryContainer = Color(0xFFF3E5F5)
val sakura_dark_tertiary = Color(0xFFFF80AB)
val sakura_dark_onTertiary = Color(0xFF3B0B1D)
val sakura_dark_background = Color(0xFF1E1419)
val sakura_dark_onBackground = Color(0xFFF9EAEF)
val sakura_dark_surface = Color(0xFF281C23)
val sakura_dark_onSurface = Color(0xFFF9EAEF)
val sakura_dark_surfaceVariant = Color(0xFF3C2C35)
val sakura_dark_onSurfaceVariant = Color(0xFFE0CAD3)
val sakura_dark_outline = Color(0xFF755B67)
val sakura_dark_surfaceContainerHigh = Color(0xFF32232C)

val sakura_light_primary = Color(0xFFC2185B)
val sakura_light_onPrimary = Color(0xFFFFFFFF)
val sakura_light_primaryContainer = Color(0xFFFCE4EC)
val sakura_light_onPrimaryContainer = Color(0xFF880E4F)
val sakura_light_secondary = Color(0xFF8E24AA)
val sakura_light_onSecondary = Color(0xFFFFFFFF)
val sakura_light_secondaryContainer = Color(0xFFF3E5F5)
val sakura_light_onSecondaryContainer = Color(0xFF4A148C)
val sakura_light_tertiary = Color(0xFFD81B60)
val sakura_light_background = Color(0xFFFFF7F9)
val sakura_light_onBackground = Color(0xFF2C1E23)
val sakura_light_surface = Color(0xFFFFF0F5)
val sakura_light_onSurface = Color(0xFF2C1E23)
val sakura_light_surfaceVariant = Color(0xFFF5DFE6)
val sakura_light_onSurfaceVariant = Color(0xFF5A444C)
val sakura_light_outline = Color(0xFF9E7E88)
val sakura_light_surfaceContainerHigh = Color(0xFFFCE4EC)

// =================================================================
// ScreenArc Extended Semantic Tokens
// =================================================================
data class ScreenArcCustomColors(
    val primaryGlow: Color = ElectricIndigo.copy(alpha = 0.5f),
    val secondaryGlow: Color = AuroraCyan.copy(alpha = 0.4f),
    val tertiaryGlow: Color = Ultraviolet.copy(alpha = 0.4f),
    val arcGradient: List<Color> = ArcHeroGradientColors,
    val glassSurface: Color = GlassFrostDark,
    val glassSurfaceElevated: Color = GlassFrostDarkElevated,
    val glassRimTop: Color = GlassRimTop,
    val glassRimBottom: Color = GlassRimBottom,
    val shimmerBase: Color = ShimmerBaseDark,
    val shimmerHighlight: Color = ShimmerHighlightDark
)

val LocalScreenArcCustomColors = staticCompositionLocalOf { ScreenArcCustomColors() }

val androidx.compose.material3.MaterialTheme.customColors: ScreenArcCustomColors
    @Composable
    @ReadOnlyComposable
    get() = LocalScreenArcCustomColors.current

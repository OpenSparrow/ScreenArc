package com.screenarc.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.screenarc.app.data.UiDesignStyle

val LocalIsGlassmorphic = compositionLocalOf { false }
val LocalUiDesignStyle = compositionLocalOf { UiDesignStyle.MATERIAL }

/**
 * Ambient backdrop with glowing organic aurora orbs.
 * This is the optical foundation of Glassmorphism: without background light and color,
 * translucent frosted glass cannot refract or be visible.
 */
@Composable
fun GlassmorphicBackdrop(
    isGlassmorphic: Boolean,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Subtle breathing animation for ambient aurora orbs
    val infiniteTransition = rememberInfiniteTransition(label = "GlassAurora")
    val animOffset by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuroraOffset"
    )

    val backgroundModifier = if (isGlassmorphic) {
        modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0C0916) else Color(0xFFEBF3F8))
    } else {
        modifier.fillMaxSize()
    }

    Box(modifier = backgroundModifier) {
        if (isGlassmorphic) {
            // Multi-layered glowing ambient orbs
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                if (isDark) {
                    // Orb 1: Deep Electric Violet (top-left)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF7B1FA2).copy(alpha = 0.38f), Color.Transparent),
                            center = Offset(width * 0.15f + animOffset, height * 0.12f),
                            radius = width * 0.70f
                        )
                    )

                    // Orb 2: Neon Cyan / Sapphire (top-right to mid)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.24f), Color.Transparent),
                            center = Offset(width * 0.85f - animOffset, height * 0.35f),
                            radius = width * 0.75f
                        )
                    )

                    // Orb 3: Emerald Aura (center-bottom)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF00E676).copy(alpha = 0.18f), Color.Transparent),
                            center = Offset(width * 0.30f + animOffset * 0.5f, height * 0.75f),
                            radius = width * 0.65f
                        )
                    )

                    // Orb 4: Magenta Accent (bottom-right)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF4081).copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(width * 0.80f, height * 0.90f - animOffset * 0.5f),
                            radius = width * 0.60f
                        )
                    )
                } else {
                    // Light mode aurora: pastel sky blue, peach blossom, and fresh mint
                    // Orb 1: Soft Sky Blue
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF80D8FF).copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(width * 0.15f + animOffset, height * 0.15f),
                            radius = width * 0.70f
                        )
                    )

                    // Orb 2: Soft Peach / Blossom
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFD1DC).copy(alpha = 0.60f), Color.Transparent),
                            center = Offset(width * 0.85f - animOffset, height * 0.35f),
                            radius = width * 0.75f
                        )
                    )

                    // Orb 3: Fresh Mint / Sage
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFB2DFDB).copy(alpha = 0.50f), Color.Transparent),
                            center = Offset(width * 0.25f, height * 0.80f),
                            radius = width * 0.65f
                        )
                    )
                }
            }
        }

        content()
    }
}

/**
 * Modifier extension to apply authentic frosted glass effect to any card or surface:
 * 1. Translucent frosted body gradient
 * 2. High-precision specular top-left rim light border
 * 3. Subtle top reflection shine
 */
fun Modifier.glassCard(
    cornerRadius: Dp = 24.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    isDark: Boolean = true,
    frostAlpha: Float = if (isDark) 0.16f else 0.72f,
    borderAlpha: Float = if (isDark) 0.40f else 0.75f
): Modifier {
    val topBorderColor = Color.White.copy(alpha = borderAlpha)
    val bottomBorderColor = Color.White.copy(alpha = borderAlpha * 0.18f)

    return this
        .clip(shape)
        .background(
            brush = if (isDark) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF382C58).copy(alpha = frostAlpha * 1.35f),
                        Color(0xFF140D24).copy(alpha = frostAlpha * 1.75f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = frostAlpha * 1.15f),
                        Color(0xFFE4EFF5).copy(alpha = frostAlpha * 0.85f)
                    )
                )
            },
            shape = shape
        )
        .border(
            width = 1.2.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    topBorderColor,
                    bottomBorderColor,
                    topBorderColor.copy(alpha = borderAlpha * 0.45f)
                ),
                start = Offset(0f, 0f),
                end = Offset.Infinite
            ),
            shape = shape
        )
        .drawWithContent {
            drawContent()
            // Top specular shine bar (simulates light reflection off polished glass edge)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.24f else 0.45f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = 16.dp.toPx()
                ),
                size = Size(size.width, 16.dp.toPx())
            )
        }
}

/**
 * Universal Card component that dynamically morphs between:
 * - Material 3 Expressive (ElevatedCard with surfaceContainerHigh)
 * - True Glassmorphism (Frosted acrylic slab with gradient specular rim light)
 */
@Composable
fun GlassElevatedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    cornerRadius: Dp = 24.dp,
    colors: CardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ),
    elevation: CardElevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isGlass = LocalIsGlassmorphic.current
    val isDark = isSystemInDarkTheme()

    if (isGlass) {
        Card(
            modifier = modifier.glassCard(
                cornerRadius = cornerRadius,
                shape = shape,
                isDark = isDark
            ),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content
        )
    } else {
        ElevatedCard(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            content = content
        )
    }
}

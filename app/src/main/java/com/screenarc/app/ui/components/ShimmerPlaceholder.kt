package com.screenarc.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.screenarc.app.ui.theme.LocalReducedMotion
import com.screenarc.app.ui.theme.Motion
import com.screenarc.app.ui.theme.ShimmerBaseDark
import com.screenarc.app.ui.theme.ShimmerBaseLight
import com.screenarc.app.ui.theme.ShimmerHighlightDark
import com.screenarc.app.ui.theme.ShimmerHighlightLight

/**
 * Modifier to apply an animated skeleton shimmer gradient effect.
 * If system reduced motion is enabled, uses a static muted placeholder color.
 */
fun Modifier.shimmerPlaceholder(
    visible: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp)
): Modifier = composed {
    if (!visible) return@composed this

    val isReducedMotion = LocalReducedMotion.current
    val isDark = isSystemInDarkTheme()

    val baseColor = if (isDark) ShimmerBaseDark else ShimmerBaseLight
    val highlightColor = if (isDark) ShimmerHighlightDark else ShimmerHighlightLight

    if (isReducedMotion) {
        return@composed this
            .clip(shape)
            .background(baseColor, shape)
    }

    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerOffset"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(progress - 300f, progress - 300f),
        end = Offset(progress, progress)
    )

    this
        .clip(shape)
        .background(shimmerBrush, shape)
}

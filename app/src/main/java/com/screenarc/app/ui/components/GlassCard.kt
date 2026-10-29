package com.screenarc.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.screenarc.app.ui.theme.LocalIsGlassmorphic
import com.screenarc.app.ui.theme.ScreenArcShapes
import com.screenarc.app.ui.theme.glassCard

/**
 * Premium GlassCard component:
 * Adapts between frosted acrylic glassmorphism (translucent gradient with specular rim border)
 * and Material 3 elevated surface card based on the active design style.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = ScreenArcShapes.Card,
    cornerRadius: Dp = 24.dp,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    glowColor: Color = MaterialTheme.colorScheme.primary,
    hasGlow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val isGlass = LocalIsGlassmorphic.current
    val isDark = isSystemInDarkTheme()

    val glowModifier = if (hasGlow) {
        Modifier.shadow(
            elevation = 12.dp,
            shape = shape,
            ambientColor = glowColor.copy(alpha = 0.35f),
            spotColor = glowColor.copy(alpha = 0.45f)
        )
    } else {
        Modifier
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    if (isGlass) {
        Card(
            modifier = modifier
                .then(glowModifier)
                .glassCard(
                    cornerRadius = cornerRadius,
                    shape = shape,
                    isDark = isDark
                )
                .clip(shape)
                .then(clickableModifier),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content
        )
    } else {
        val effectiveBorder = border ?: BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ElevatedCard(
            modifier = modifier
                .then(glowModifier)
                .clip(shape)
                .border(effectiveBorder, shape)
                .then(clickableModifier),
            shape = shape,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (hasGlow) 4.dp else 1.5.dp),
            content = content
        )
    }
}

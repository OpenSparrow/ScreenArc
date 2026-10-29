package com.screenarc.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * ScreenArc Shape System:
 * Based on 8dp grid system with refined 16-28dp corner radii for smooth, modern glassmorphic feel.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object ScreenArcShapes {
    val Chip = RoundedCornerShape(16.dp)
    val Card = RoundedCornerShape(24.dp)
    val GlassCard = RoundedCornerShape(24.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val Button = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(50)
}

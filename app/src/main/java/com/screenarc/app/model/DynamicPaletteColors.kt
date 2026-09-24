package com.screenarc.app.model

import androidx.compose.ui.graphics.Color

data class DynamicPaletteColors(
    val dominant: Color = Color(0xFF6750A4),
    val vibrant: Color = Color(0xFF7D5260),
    val vibrantDark: Color = Color(0xFF4F378B),
    val vibrantLight: Color = Color(0xFFE8DEF8),
    val muted: Color = Color(0xFF625B71),
    val mutedDark: Color = Color(0xFF332D41),
    val mutedLight: Color = Color(0xFFCCC2DC),
    val onDominant: Color = Color.White,
    val onVibrant: Color = Color.White
)

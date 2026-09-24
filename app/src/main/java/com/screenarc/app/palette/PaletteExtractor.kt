package com.screenarc.app.palette

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.screenarc.app.model.DynamicPaletteColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PaletteExtractor {

    suspend fun extractColors(bitmap: Bitmap): DynamicPaletteColors = withContext(Dispatchers.Default) {
        try {
            // Scale down bitmap for fast palette generation
            val scaled = if (bitmap.width > 200 || bitmap.height > 200) {
                val scale = 200f / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val palette = Palette.from(scaled)
                .maximumColorCount(16)
                .generate()

            val dominant = palette.dominantSwatch?.rgb?.let { Color(it) } ?: Color(0xFF6750A4)
            val vibrant = palette.vibrantSwatch?.rgb?.let { Color(it) } ?: dominant
            val vibrantDark = palette.darkVibrantSwatch?.rgb?.let { Color(it) } ?: Color(0xFF381E72)
            val vibrantLight = palette.lightVibrantSwatch?.rgb?.let { Color(it) } ?: Color(0xFFE8DEF8)
            val muted = palette.mutedSwatch?.rgb?.let { Color(it) } ?: Color(0xFF625B71)
            val mutedDark = palette.darkMutedSwatch?.rgb?.let { Color(it) } ?: Color(0xFF332D41)
            val mutedLight = palette.lightMutedSwatch?.rgb?.let { Color(it) } ?: Color(0xFFCCC2DC)

            val onDominant = palette.dominantSwatch?.titleTextColor?.let { Color(it) } ?: Color.White
            val onVibrant = palette.vibrantSwatch?.titleTextColor?.let { Color(it) } ?: Color.White

            DynamicPaletteColors(
                dominant = dominant,
                vibrant = vibrant,
                vibrantDark = vibrantDark,
                vibrantLight = vibrantLight,
                muted = muted,
                mutedDark = mutedDark,
                mutedLight = mutedLight,
                onDominant = onDominant,
                onVibrant = onVibrant
            )
        } catch (e: Exception) {
            e.printStackTrace()
            DynamicPaletteColors()
        }
    }
}

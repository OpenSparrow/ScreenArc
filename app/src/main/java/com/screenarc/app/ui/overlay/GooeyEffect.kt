package com.screenarc.app.ui.overlay

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Liquid Metaball / Gooey Effect Design System:
 * - On API 33+ (Android 13+): Uses AGSL RuntimeShader for high-fidelity fluid metaball thresholding.
 * - On API 31–32 (Android 12/12L): Uses RenderEffect blur + alpha threshold ColorMatrix.
 * - Below API 31: Graceful fallback to pure scale, alpha, and ambient glow (no crash, zero overhead).
 */
object GooeyEffectHelper {

    private const val AGSL_METABALL_SRC = """
        uniform shader image;
        uniform float uThreshold;

        half4 main(float2 fragCoord) {
            half4 color = image.eval(fragCoord);
            if (color.a < uThreshold) {
                return half4(0.0);
            }
            // Smooth edge anti-aliasing
            half alpha = smoothstep(uThreshold, uThreshold + 0.08, color.a);
            return half4(color.rgb, alpha * color.a);
        }
    """

    @RequiresApi(Build.VERSION_CODES.S)
    fun createGooeyRenderEffect(
        blurRadius: Float = 22f,
        threshold: Float = 0.5f
    ): androidx.compose.ui.graphics.RenderEffect? {
        return try {
            val blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val shader = RuntimeShader(AGSL_METABALL_SRC)
                    shader.setFloatUniform("uThreshold", threshold)
                    val agslEffect = RenderEffect.createRuntimeShaderEffect(shader, "image")
                    return RenderEffect.createChainEffect(agslEffect, blur).asComposeRenderEffect()
                } catch (_: Throwable) {
                    // Fall back to RenderEffect matrix threshold
                }
            }

            // High-contrast alpha threshold matrix:
            // Multiplies alpha by 18 and subtracts offset to create organic gooey bridges between blurred circles
            val matrix = ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, 0f,
                        0f, 0f, 0f, 18f, -7f * 255f
                    )
                )
            }
            val alphaThreshold = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
            RenderEffect.createChainEffect(alphaThreshold, blur).asComposeRenderEffect()
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Modifier to apply liquid gooey metaball rendering to fluid background shapes.
 */
fun Modifier.liquidGooeyLayer(
    enabled: Boolean = true,
    blurRadius: Float = 22f
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this
    }
    val effect = GooeyEffectHelper.createGooeyRenderEffect(blurRadius = blurRadius) ?: return this
    return this.graphicsLayer {
        this.renderEffect = effect
    }
}

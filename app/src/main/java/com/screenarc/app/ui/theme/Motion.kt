package com.screenarc.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * ScreenArc Unified Motion & Physics Design System.
 * Standardized spring specs, durations, and fluid easings across the entire app and radial overlay.
 */
object Motion {
    // -------------------------------------------------------------
    // Physics-Based Spring Specs
    // -------------------------------------------------------------

    /** Snappy, responsive spring with subtle overshoot for touch feedback, buttons, and selection */
    val SnappySpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Bouncy spring with expressive overshoot for radial arc blooms and hero entrance */
    val BouncySpring: SpringSpec<Float> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    )

    /** Gentle, non-bouncy spring for modal dialogs, sheets, and scrim fades */
    val GentleSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** Fluid organic spring for magnetic hover, drag-through physics, and cursor tracking */
    val FluidSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.80f,
        stiffness = 400f
    )

    // -------------------------------------------------------------
    // Standard Durations (in milliseconds)
    // -------------------------------------------------------------
    const val DurationFast: Int = 150
    const val DurationNormal: Int = 250
    const val DurationMedium: Int = 380
    const val DurationLong: Int = 500

    // -------------------------------------------------------------
    // Fluid & Expressive Easings
    // -------------------------------------------------------------
    /** Modern fluid curve with fast start and gentle natural settling */
    val FluidEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)

    /** Material 3 Emphasized curve for standard transitions */
    val EmphasizedEasing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.0f)

    /** Emphasized Decelerate for elements entering the screen */
    val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.0f)

    /** Emphasized Accelerate for elements exiting the screen */
    val EmphasizedAccelerateEasing = CubicBezierEasing(0.30f, 0.0f, 0.80f, 0.15f)

    // -------------------------------------------------------------
    // Stagger Utilities
    // -------------------------------------------------------------
    /** Computes staggered delay for sequential arc blooming / collapsing */
    fun staggerDelay(index: Int, stepMs: Int = 35): Int = (index * stepMs).coerceAtLeast(0)
}

val LocalReducedMotion = compositionLocalOf { false }

/**
 * Returns whether system-wide reduced motion or zero animator scale is enabled.
 */
@Composable
fun rememberIsReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Helper to select between a standard animation spec and an immediate snap spec when reduced motion is requested.
 */
@Composable
fun <T> responsiveSpec(
    normalSpec: AnimationSpec<T>,
    reducedMotionSpec: AnimationSpec<T> = snap()
): AnimationSpec<T> {
    val isReduced = LocalReducedMotion.current || rememberIsReducedMotion()
    return if (isReduced) reducedMotionSpec else normalSpec
}

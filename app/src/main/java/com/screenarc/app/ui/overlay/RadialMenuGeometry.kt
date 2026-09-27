package com.screenarc.app.ui.overlay

import androidx.compose.ui.geometry.Offset
import com.screenarc.app.model.ArcDesignStyle
import com.screenarc.app.model.CategoryUiModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed class HoverTarget {
    data class Category(val category: CategoryUiModel) : HoverTarget()
    data object Delete : HoverTarget()
    data object Share : HoverTarget()
}

data class RadialTargetLayout(
    val categoriesWithPos: List<Pair<CategoryUiModel, Offset>>,
    val deletePos: Offset,
    val sharePos: Offset? = null
)

object RadialMenuGeometry {

    /**
     * Compute exact positions for categories, delete, and share based on the selected ArcDesignStyle.
     */
    fun calculateLayout(
        categories: List<CategoryUiModel>,
        radiusPx: Float,
        style: ArcDesignStyle
    ): RadialTargetLayout {
        val total = categories.size
        val catPositions = mutableListOf<Pair<CategoryUiModel, Offset>>()

        when (style) {
            ArcDesignStyle.ORBIT -> {
                // Orbit mode: Categories gracefully distributed across upper/side arc (-165° to -15°)
                // Bottom center (+90°) is dedicated to Delete, bottom left (+145°) to Share
                if (total == 1) {
                    val angle = -PI.toFloat() / 2f
                    catPositions.add(categories[0] to Offset(radiusPx * cos(angle), radiusPx * sin(angle)))
                } else if (total > 1) {
                    val startAngle = -165f * (PI.toFloat() / 180f)
                    val endAngle = -15f * (PI.toFloat() / 180f)
                    val step = (endAngle - startAngle) / (total - 1)
                    categories.forEachIndexed { i, cat ->
                        val a = startAngle + i * step
                        catPositions.add(cat to Offset(radiusPx * cos(a), radiusPx * sin(a)))
                    }
                }

                // Delete target at bottom center (+90° = PI / 2)
                val deleteAngle = PI.toFloat() / 2f
                val deletePos = Offset(radiusPx * cos(deleteAngle), radiusPx * sin(deleteAngle))

                // Share target at bottom left (+142°)
                val shareAngle = 142f * (PI.toFloat() / 180f)
                val sharePos = Offset(radiusPx * cos(shareAngle), radiusPx * sin(shareAngle))

                return RadialTargetLayout(catPositions, deletePos, sharePos)
            }

            ArcDesignStyle.FAN -> {
                // Fan Arc mode: Ergonomic 180° semi-circle arc blooming above the center (-170° to -10°)
                // Delete target placed directly below at bottom center (90°)
                // Share target placed at bottom right (+38°)
                if (total == 1) {
                    val angle = -PI.toFloat() / 2f
                    catPositions.add(categories[0] to Offset(radiusPx * cos(angle), radiusPx * sin(angle)))
                } else if (total > 1) {
                    val startAngle = -170f * (PI.toFloat() / 180f)
                    val endAngle = -10f * (PI.toFloat() / 180f)
                    val step = (endAngle - startAngle) / (total - 1)
                    categories.forEachIndexed { i, cat ->
                        val a = startAngle + i * step
                        catPositions.add(cat to Offset(radiusPx * cos(a), radiusPx * sin(a)))
                    }
                }

                val deletePos = Offset(0f, radiusPx * 0.96f)
                val shareAngle = 40f * (PI.toFloat() / 180f)
                val sharePos = Offset(radiusPx * cos(shareAngle), radiusPx * sin(shareAngle))

                return RadialTargetLayout(catPositions, deletePos, sharePos)
            }

            ArcDesignStyle.DIAL -> {
                // Segmented Dial mode: Tactile radial dial sectors
                // Bottom sector (+65° to +115°) reserved for Delete
                // Remaining sectors around the dial distributed for Categories & Share
                val spanStart = 135f * (PI.toFloat() / 180f)
                val spanEnd = (360f + 45f) * (PI.toFloat() / 180f)
                val totalSpan = spanEnd - spanStart
                val step = if (total > 1) totalSpan / (total - 1) else 0f

                categories.forEachIndexed { i, cat ->
                    val a = if (total == 1) -PI.toFloat() / 2f else spanStart + i * step
                    catPositions.add(cat to Offset(radiusPx * cos(a), radiusPx * sin(a)))
                }

                // Delete target centered in bottom sector (+90°)
                val deletePos = Offset(0f, radiusPx)

                // Share target at right (+35°)
                val shareAngle = 35f * (PI.toFloat() / 180f)
                val sharePos = Offset(radiusPx * cos(shareAngle), radiusPx * sin(shareAngle))

                return RadialTargetLayout(catPositions, deletePos, sharePos)
            }
        }
    }

    /**
     * Hit-test a drag offset against layout targets.
     */
    fun findHoverTarget(
        dragOffset: Offset,
        layout: RadialTargetLayout,
        hitThresholdPx: Float
    ): HoverTarget? {
        // 1. Check Delete target
        val dDel = (dragOffset - layout.deletePos).getDistance()
        if (dDel <= hitThresholdPx) {
            return HoverTarget.Delete
        }

        // 2. Check Share target
        layout.sharePos?.let { sPos ->
            val dShare = (dragOffset - sPos).getDistance()
            if (dShare <= hitThresholdPx) {
                return HoverTarget.Share
            }
        }

        // 3. Check Categories (find closest if within threshold)
        var closestCat: CategoryUiModel? = null
        var minDistance = hitThresholdPx
        for ((cat, pos) in layout.categoriesWithPos) {
            val dist = (dragOffset - pos).getDistance()
            if (dist <= minDistance) {
                minDistance = dist
                closestCat = cat
            }
        }

        return closestCat?.let { HoverTarget.Category(it) }
    }
}

private fun Offset.getDistance(): Float {
    return sqrt(x * x + y * y)
}

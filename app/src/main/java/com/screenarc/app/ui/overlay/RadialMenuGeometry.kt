package com.screenarc.app.ui.overlay

import androidx.compose.ui.geometry.Offset
import com.screenarc.app.model.ArcDesignStyle
import com.screenarc.app.model.CategoryUiModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class Handedness {
    AUTO,
    RIGHT_HANDED,
    LEFT_HANDED
}

enum class OverlayEdgeAnchor {
    AUTO,
    BOTTOM_RIGHT,
    BOTTOM_LEFT,
    CENTER
}

sealed class HoverTarget {
    data class Category(val category: CategoryUiModel) : HoverTarget()
    data object Delete : HoverTarget()
    data object Share : HoverTarget()
    data object More : HoverTarget()
}

data class RadialTargetLayout(
    val categoriesWithPos: List<Pair<CategoryUiModel, Offset>>,
    val deletePos: Offset,
    val sharePos: Offset? = null,
    val morePos: Offset? = null,
    val isDualRing: Boolean = false,
    val suggestedCategoryId: Long? = null,
    val overflowCategories: List<CategoryUiModel> = emptyList()
)

object RadialMenuGeometry {

    /**
     * Orders categories by suggested category first, followed by usage frequency (descending),
     * and preserves fallback orderIndex.
     */
    fun orderCategories(
        categories: List<CategoryUiModel>,
        usageCountMap: Map<Long, Int> = emptyMap(),
        suggestedCategoryId: Long? = null
    ): List<CategoryUiModel> {
        return categories.sortedWith(
            compareByDescending<CategoryUiModel> { it.id == suggestedCategoryId }
                .thenByDescending { usageCountMap[it.id] ?: 0 }
                .thenBy { it.orderIndex }
        )
    }

    /**
     * Compute exact positions for categories, delete, share, and overflow 'More' based on
     * ArcDesignStyle, screen edge anchor, handedness, and RTL settings.
     */
    fun calculateLayout(
        categories: List<CategoryUiModel>,
        radiusPx: Float,
        style: ArcDesignStyle,
        isRtl: Boolean = false,
        handedness: Handedness = Handedness.AUTO,
        edgeAnchor: OverlayEdgeAnchor = OverlayEdgeAnchor.AUTO,
        suggestedCategoryId: Long? = null,
        isOverflowExpanded: Boolean = false
    ): RadialTargetLayout {
        // Resolve effective handedness
        val isLeftHanded = when (handedness) {
            Handedness.LEFT_HANDED -> true
            Handedness.RIGHT_HANDED -> false
            Handedness.AUTO -> isRtl || edgeAnchor == OverlayEdgeAnchor.BOTTOM_LEFT
        }

        val total = categories.size
        val catPositions = mutableListOf<Pair<CategoryUiModel, Offset>>()

        // Overflow strategy: If more than 6 categories and not expanded, show top 5 + "More" bubble,
        // or layout in dual-ring if expanded.
        val maxSingleRing = 6
        val hasOverflow = total > maxSingleRing
        val displayedCategories = if (hasOverflow && !isOverflowExpanded) {
            categories.take(5)
        } else {
            categories
        }

        var morePos: Offset? = null
        val overflowList = if (hasOverflow) categories.drop(5) else emptyList()

        when (edgeAnchor) {
            OverlayEdgeAnchor.BOTTOM_RIGHT -> {
                // Anchored to bottom-right corner bubble: expand inward into the upper-left quadrant
                val (startDeg, endDeg) = Pair(-100f, -165f)
                val count = displayedCategories.size + (if (hasOverflow && !isOverflowExpanded) 1 else 0)

                if (count == 1) {
                    val angle = -135f * (PI.toFloat() / 180f)
                    catPositions.add(displayedCategories[0] to Offset(radiusPx * cos(angle), radiusPx * sin(angle)))
                } else if (count > 1) {
                    val startAngle = startDeg * (PI.toFloat() / 180f)
                    val endAngle = endDeg * (PI.toFloat() / 180f)
                    val step = (endAngle - startAngle) / (count - 1)

                    displayedCategories.forEachIndexed { i, cat ->
                        val isOuter = isOverflowExpanded && i >= 5
                        val effectiveRadius = if (isOuter) radiusPx * 1.35f else radiusPx
                        val a = if (isOuter) {
                            val outerStep = (endAngle - startAngle) / (total - 5).coerceAtLeast(1)
                            startAngle + (i - 5) * outerStep
                        } else {
                            startAngle + i * step
                        }
                        catPositions.add(cat to Offset(effectiveRadius * cos(a), effectiveRadius * sin(a)))
                    }

                    if (hasOverflow && !isOverflowExpanded) {
                        val moreAngle = -170f * (PI.toFloat() / 180f)
                        morePos = Offset(radiusPx * cos(moreAngle), radiusPx * sin(moreAngle))
                    }
                }

                // Delete target placed leftward from bubble (-185°)
                val deleteAngle = -185f * (PI.toFloat() / 180f)
                val deletePos = Offset(radiusPx * cos(deleteAngle), radiusPx * sin(deleteAngle))

                // Share target placed upward from bubble (-80°)
                val shareAngle = -80f * (PI.toFloat() / 180f)
                val sharePos = Offset(radiusPx * cos(shareAngle), radiusPx * sin(shareAngle))

                return RadialTargetLayout(
                    categoriesWithPos = catPositions,
                    deletePos = deletePos,
                    sharePos = sharePos,
                    morePos = morePos,
                    isDualRing = isOverflowExpanded && total > 6,
                    suggestedCategoryId = suggestedCategoryId,
                    overflowCategories = overflowList
                )
            }

            OverlayEdgeAnchor.BOTTOM_LEFT -> {
                // Anchored to bottom-left corner bubble: expand inward into the upper-right quadrant
                val (startDeg, endDeg) = Pair(-80f, -15f)
                val count = displayedCategories.size + (if (hasOverflow && !isOverflowExpanded) 1 else 0)

                if (count == 1) {
                    val angle = -45f * (PI.toFloat() / 180f)
                    catPositions.add(displayedCategories[0] to Offset(radiusPx * cos(angle), radiusPx * sin(angle)))
                } else if (count > 1) {
                    val startAngle = startDeg * (PI.toFloat() / 180f)
                    val endAngle = endDeg * (PI.toFloat() / 180f)
                    val step = (endAngle - startAngle) / (count - 1)

                    displayedCategories.forEachIndexed { i, cat ->
                        val isOuter = isOverflowExpanded && i >= 5
                        val effectiveRadius = if (isOuter) radiusPx * 1.35f else radiusPx
                        val a = if (isOuter) {
                            val outerStep = (endAngle - startAngle) / (total - 5).coerceAtLeast(1)
                            startAngle + (i - 5) * outerStep
                        } else {
                            startAngle + i * step
                        }
                        catPositions.add(cat to Offset(effectiveRadius * cos(a), effectiveRadius * sin(a)))
                    }

                    if (hasOverflow && !isOverflowExpanded) {
                        val moreAngle = -10f * (PI.toFloat() / 180f)
                        morePos = Offset(radiusPx * cos(moreAngle), radiusPx * sin(moreAngle))
                    }
                }

                // Delete target placed rightward from bubble (-5°)
                val deleteAngle = -5f * (PI.toFloat() / 180f)
                val deletePos = Offset(radiusPx * cos(deleteAngle), radiusPx * sin(deleteAngle))

                // Share target placed upward from bubble (-100°)
                val shareAngle = -100f * (PI.toFloat() / 180f)
                val sharePos = Offset(radiusPx * cos(shareAngle), radiusPx * sin(shareAngle))

                return RadialTargetLayout(
                    categoriesWithPos = catPositions,
                    deletePos = deletePos,
                    sharePos = sharePos,
                    morePos = morePos,
                    isDualRing = isOverflowExpanded && total > 6,
                    suggestedCategoryId = suggestedCategoryId,
                    overflowCategories = overflowList
                )
            }

            OverlayEdgeAnchor.CENTER, OverlayEdgeAnchor.AUTO -> {
                when (style) {
                    ArcDesignStyle.ORBIT -> {
                        // Orbit mode: Distributes nodes along upper and side quadrant.
                        // Left handed fans rightward (-15° to -165° inverted), Right handed fans leftward.
                        val (startDeg, endDeg) = if (isLeftHanded) {
                            Pair(-15f, -165f)
                        } else {
                            Pair(-165f, -15f)
                        }

                        val count = displayedCategories.size + (if (hasOverflow && !isOverflowExpanded) 1 else 0)

                        if (count == 1) {
                            val angle = -PI.toFloat() / 2f
                            catPositions.add(displayedCategories[0] to Offset(radiusPx * cos(angle), radiusPx * sin(angle)))
                        } else if (count > 1) {
                            val startAngle = startDeg * (PI.toFloat() / 180f)
                            val endAngle = endDeg * (PI.toFloat() / 180f)
                            val step = (endAngle - startAngle) / (count - 1)

                            displayedCategories.forEachIndexed { i, cat ->
                                // If dual ring when expanded and total > 6
                                val isOuter = isOverflowExpanded && i >= 5
                                val effectiveRadius = if (isOuter) radiusPx * 1.38f else radiusPx
                                val a = if (isOuter) {
                                    val outerStep = (endAngle - startAngle) / (total - 5).coerceAtLeast(1)
                                    startAngle + (i - 5) * outerStep
                                } else {
                                    startAngle + i * step
                                }
                                catPositions.add(cat to Offset(effectiveRadius * cos(a), effectiveRadius * sin(a)))
                            }

                            if (hasOverflow && !isOverflowExpanded) {
                                val moreAngle = startAngle + displayedCategories.size * step
                                morePos = Offset(radiusPx * cos(moreAngle), radiusPx * sin(moreAngle))
                            }
                        }

                        // Delete target at bottom center (+90°)
                        val deleteAngle = PI.toFloat() / 2f
                        val deletePos = Offset(radiusPx * cos(deleteAngle), radiusPx * sin(deleteAngle))

                        // Share target positioned opposite to primary thumb reach
                        val shareDeg = if (isLeftHanded) 38f else 142f
                        val shareAngle = shareDeg * (PI.toFloat() / 180f)
                        val sharePos = Offset(radiusPx * cos(shareAngle), radiusPx * sin(shareAngle))

                        return RadialTargetLayout(
                            categoriesWithPos = catPositions,
                            deletePos = deletePos,
                            sharePos = sharePos,
                            morePos = morePos,
                            isDualRing = isOverflowExpanded && total > 6,
                            suggestedCategoryId = suggestedCategoryId,
                            overflowCategories = overflowList
                        )
                    }

                    ArcDesignStyle.FAN -> {
                        // Fan Arc mode: Ergonomic 180° semi-circle blooming above center (-170° to -10°)
                        val (startDeg, endDeg) = if (isLeftHanded) {
                            Pair(-10f, -170f)
                        } else {
                            Pair(-170f, -10f)
                        }

                        val count = displayedCategories.size + (if (hasOverflow && !isOverflowExpanded) 1 else 0)

                        if (count == 1) {
                            val angle = -PI.toFloat() / 2f
                            catPositions.add(displayedCategories[0] to Offset(radiusPx * cos(angle), radiusPx * sin(angle)))
                        } else if (count > 1) {
                            val startAngle = startDeg * (PI.toFloat() / 180f)
                            val endAngle = endDeg * (PI.toFloat() / 180f)
                            val step = (endAngle - startAngle) / (count - 1)

                            displayedCategories.forEachIndexed { i, cat ->
                                val isOuter = isOverflowExpanded && i >= 5
                                val effectiveRadius = if (isOuter) radiusPx * 1.35f else radiusPx
                                val a = if (isOuter) {
                                    val outerStep = (endAngle - startAngle) / (total - 5).coerceAtLeast(1)
                                    startAngle + (i - 5) * outerStep
                                } else {
                                    startAngle + i * step
                                }
                                catPositions.add(cat to Offset(effectiveRadius * cos(a), effectiveRadius * sin(a)))
                            }

                            if (hasOverflow && !isOverflowExpanded) {
                                val moreAngle = startAngle + displayedCategories.size * step
                                morePos = Offset(radiusPx * cos(moreAngle), radiusPx * sin(moreAngle))
                            }
                        }

                        val deletePos = Offset(0f, radiusPx * 0.96f)
                        val shareDeg = if (isLeftHanded) 140f else 40f
                        val shareAngle = shareDeg * (PI.toFloat() / 180f)
                        val sharePos = Offset(radiusPx * cos(shareAngle), radiusPx * sin(shareAngle))

                        return RadialTargetLayout(
                            categoriesWithPos = catPositions,
                            deletePos = deletePos,
                            sharePos = sharePos,
                            morePos = morePos,
                            isDualRing = isOverflowExpanded && total > 6,
                            suggestedCategoryId = suggestedCategoryId,
                            overflowCategories = overflowList
                        )
                    }

                    ArcDesignStyle.DIAL -> {
                        // Segmented Dial mode: Tactile radial dial sectors
                        val spanStartDeg = if (isLeftHanded) 45f else 135f
                        val spanEndDeg = if (isLeftHanded) -135f else 405f
                        val spanStart = spanStartDeg * (PI.toFloat() / 180f)
                        val spanEnd = spanEndDeg * (PI.toFloat() / 180f)
                        val totalSpan = spanEnd - spanStart
                        val count = displayedCategories.size + (if (hasOverflow && !isOverflowExpanded) 1 else 0)
                        val step = if (count > 1) totalSpan / (count - 1) else 0f

                        displayedCategories.forEachIndexed { i, cat ->
                            val isOuter = isOverflowExpanded && i >= 5
                            val effectiveRadius = if (isOuter) radiusPx * 1.35f else radiusPx
                            val a = if (isOuter) {
                                val outerStep = totalSpan / (total - 5).coerceAtLeast(1)
                                spanStart + (i - 5) * outerStep
                            } else {
                                if (count == 1) -PI.toFloat() / 2f else spanStart + i * step
                            }
                            catPositions.add(cat to Offset(effectiveRadius * cos(a), effectiveRadius * sin(a)))
                        }

                        if (hasOverflow && !isOverflowExpanded) {
                            val moreAngle = spanStart + displayedCategories.size * step
                            morePos = Offset(radiusPx * cos(moreAngle), radiusPx * sin(moreAngle))
                        }

                        val deletePos = Offset(0f, radiusPx)
                        val shareDeg = if (isLeftHanded) 145f else 35f
                        val shareAngle = shareDeg * (PI.toFloat() / 180f)
                        val sharePos = Offset(radiusPx * cos(shareAngle), radiusPx * sin(shareAngle))

                        return RadialTargetLayout(
                            categoriesWithPos = catPositions,
                            deletePos = deletePos,
                            sharePos = sharePos,
                            morePos = morePos,
                            isDualRing = isOverflowExpanded && total > 6,
                            suggestedCategoryId = suggestedCategoryId,
                            overflowCategories = overflowList
                        )
                    }
                }
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

        // 3. Check More overflow node
        layout.morePos?.let { mPos ->
            val dMore = (dragOffset - mPos).getDistance()
            if (dMore <= hitThresholdPx) {
                return HoverTarget.More
            }
        }

        // 4. Check Categories (find closest if within threshold)
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

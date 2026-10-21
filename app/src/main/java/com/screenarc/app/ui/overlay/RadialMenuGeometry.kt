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
    TOP_RIGHT,
    TOP_LEFT,
    CENTER
}

sealed class HoverTarget {
    data class Category(val category: CategoryUiModel) : HoverTarget()
    data object Share : HoverTarget()
    data object ShareAndDelete : HoverTarget()
    data object Delete : HoverTarget()
    data object Copy : HoverTarget()
    data object More : HoverTarget()
}

data class RadialTargetLayout(
    val categoriesWithPos: List<Pair<CategoryUiModel, Offset>>,
    val deletePos: Offset,
    val sharePos: Offset? = null,
    val shareAndDeletePos: Offset? = null,
    val copyPos: Offset? = null,
    val morePos: Offset? = null,
    val isDualRing: Boolean = false,
    val suggestedCategoryId: Long? = null,
    val overflowCategories: List<CategoryUiModel> = emptyList(),
    val innerRadiusPx: Float = 0f,
    val outerRadiusPx: Float = 0f
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
        style: ArcDesignStyle = ArcDesignStyle.ORBIT,
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
            Handedness.AUTO -> isRtl || edgeAnchor == OverlayEdgeAnchor.BOTTOM_LEFT || edgeAnchor == OverlayEdgeAnchor.TOP_LEFT
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
                // Layer 1 (Inner Arc - Actions: Copy, Share, Share & Delete, Delete)
                val innerRadius = radiusPx * 0.62f
                val copyAngle = -92f * (PI.toFloat() / 180f)
                val shareAngle = -120f * (PI.toFloat() / 180f)
                val shareAndDelAngle = -148f * (PI.toFloat() / 180f)
                val deleteAngle = -176f * (PI.toFloat() / 180f)

                val copyPos = Offset(innerRadius * cos(copyAngle), innerRadius * sin(copyAngle))
                val sharePos = Offset(innerRadius * cos(shareAngle), innerRadius * sin(shareAngle))
                val shareAndDeletePos = Offset(innerRadius * cos(shareAndDelAngle), innerRadius * sin(shareAndDelAngle))
                val deletePos = Offset(innerRadius * cos(deleteAngle), innerRadius * sin(deleteAngle))

                // Layer 2 (Outer Arc - Categories structured in a fluid, circular arc)
                val outerRadius = radiusPx * 1.35f
                val (startDeg, endDeg) = Pair(-91f, -179f)
                val count = displayedCategories.size + (if (hasOverflow && !isOverflowExpanded) 1 else 0)

                if (count == 1) {
                    val angle = -135f * (PI.toFloat() / 180f)
                    catPositions.add(displayedCategories[0] to Offset(outerRadius * cos(angle), outerRadius * sin(angle)))
                } else if (count > 1) {
                    val startAngle = startDeg * (PI.toFloat() / 180f)
                    val endAngle = endDeg * (PI.toFloat() / 180f)
                    val step = (endAngle - startAngle) / (count - 1)

                    displayedCategories.forEachIndexed { i, cat ->
                        val isOuter = isOverflowExpanded && i >= 5
                        val effectiveRadius = if (isOuter) outerRadius * 1.30f else outerRadius
                        val a = if (isOuter) {
                            val outerStep = (endAngle - startAngle) / (total - 5).coerceAtLeast(1)
                            startAngle + (i - 5) * outerStep
                        } else {
                            startAngle + i * step
                        }
                        catPositions.add(cat to Offset(effectiveRadius * cos(a), effectiveRadius * sin(a)))
                    }

                    if (hasOverflow && !isOverflowExpanded) {
                        val moreAngle = -179f * (PI.toFloat() / 180f)
                        morePos = Offset(outerRadius * cos(moreAngle), outerRadius * sin(moreAngle))
                    }
                }

                return RadialTargetLayout(
                    categoriesWithPos = catPositions,
                    deletePos = deletePos,
                    sharePos = sharePos,
                    shareAndDeletePos = shareAndDeletePos,
                    copyPos = copyPos,
                    morePos = morePos,
                    isDualRing = isOverflowExpanded && total > 6,
                    suggestedCategoryId = suggestedCategoryId,
                    overflowCategories = overflowList,
                    innerRadiusPx = innerRadius,
                    outerRadiusPx = outerRadius
                )
            }

            OverlayEdgeAnchor.BOTTOM_LEFT -> {
                // Anchored to bottom-left corner bubble: expand inward into the upper-right quadrant
                val innerRadius = radiusPx * 0.62f
                val copyAngle = -86f * (PI.toFloat() / 180f)
                val shareAngle = -58f * (PI.toFloat() / 180f)
                val shareAndDelAngle = -30f * (PI.toFloat() / 180f)
                val deleteAngle = -2f * (PI.toFloat() / 180f)

                val copyPos = Offset(innerRadius * cos(copyAngle), innerRadius * sin(copyAngle))
                val sharePos = Offset(innerRadius * cos(shareAngle), innerRadius * sin(shareAngle))
                val shareAndDeletePos = Offset(innerRadius * cos(shareAndDelAngle), innerRadius * sin(shareAndDelAngle))
                val deletePos = Offset(innerRadius * cos(deleteAngle), innerRadius * sin(deleteAngle))

                val outerRadius = radiusPx * 1.35f
                val (startDeg, endDeg) = Pair(-89f, -1f)
                val count = displayedCategories.size + (if (hasOverflow && !isOverflowExpanded) 1 else 0)

                if (count == 1) {
                    val angle = -45f * (PI.toFloat() / 180f)
                    catPositions.add(displayedCategories[0] to Offset(outerRadius * cos(angle), outerRadius * sin(angle)))
                } else if (count > 1) {
                    val startAngle = startDeg * (PI.toFloat() / 180f)
                    val endAngle = endDeg * (PI.toFloat() / 180f)
                    val step = (endAngle - startAngle) / (count - 1)

                    displayedCategories.forEachIndexed { i, cat ->
                        val isOuter = isOverflowExpanded && i >= 5
                        val effectiveRadius = if (isOuter) outerRadius * 1.30f else outerRadius
                        val a = if (isOuter) {
                            val outerStep = (endAngle - startAngle) / (total - 5).coerceAtLeast(1)
                            startAngle + (i - 5) * outerStep
                        } else {
                            startAngle + i * step
                        }
                        catPositions.add(cat to Offset(effectiveRadius * cos(a), effectiveRadius * sin(a)))
                    }

                    if (hasOverflow && !isOverflowExpanded) {
                        val moreAngle = -1f * (PI.toFloat() / 180f)
                        morePos = Offset(outerRadius * cos(moreAngle), outerRadius * sin(moreAngle))
                    }
                }

                return RadialTargetLayout(
                    categoriesWithPos = catPositions,
                    deletePos = deletePos,
                    sharePos = sharePos,
                    shareAndDeletePos = shareAndDeletePos,
                    copyPos = copyPos,
                    morePos = morePos,
                    isDualRing = isOverflowExpanded && total > 6,
                    suggestedCategoryId = suggestedCategoryId,
                    overflowCategories = overflowList,
                    innerRadiusPx = innerRadius,
                    outerRadiusPx = outerRadius
                )
            }

            OverlayEdgeAnchor.TOP_RIGHT -> {
                // Anchored to top-right corner bubble: expand inward into the lower-left quadrant
                val innerRadius = radiusPx * 0.62f
                val copyAngle = 92f * (PI.toFloat() / 180f)
                val shareAngle = 120f * (PI.toFloat() / 180f)
                val shareAndDelAngle = 148f * (PI.toFloat() / 180f)
                val deleteAngle = 176f * (PI.toFloat() / 180f)

                val copyPos = Offset(innerRadius * cos(copyAngle), innerRadius * sin(copyAngle))
                val sharePos = Offset(innerRadius * cos(shareAngle), innerRadius * sin(shareAngle))
                val shareAndDeletePos = Offset(innerRadius * cos(shareAndDelAngle), innerRadius * sin(shareAndDelAngle))
                val deletePos = Offset(innerRadius * cos(deleteAngle), innerRadius * sin(deleteAngle))

                val outerRadius = radiusPx * 1.35f
                val (startDeg, endDeg) = Pair(91f, 179f)
                val count = displayedCategories.size + (if (hasOverflow && !isOverflowExpanded) 1 else 0)

                if (count == 1) {
                    val angle = 135f * (PI.toFloat() / 180f)
                    catPositions.add(displayedCategories[0] to Offset(outerRadius * cos(angle), outerRadius * sin(angle)))
                } else if (count > 1) {
                    val startAngle = startDeg * (PI.toFloat() / 180f)
                    val endAngle = endDeg * (PI.toFloat() / 180f)
                    val step = (endAngle - startAngle) / (count - 1)

                    displayedCategories.forEachIndexed { i, cat ->
                        val isOuter = isOverflowExpanded && i >= 5
                        val effectiveRadius = if (isOuter) outerRadius * 1.30f else outerRadius
                        val a = if (isOuter) {
                            val outerStep = (endAngle - startAngle) / (total - 5).coerceAtLeast(1)
                            startAngle + (i - 5) * outerStep
                        } else {
                            startAngle + i * step
                        }
                        catPositions.add(cat to Offset(effectiveRadius * cos(a), effectiveRadius * sin(a)))
                    }

                    if (hasOverflow && !isOverflowExpanded) {
                        val moreAngle = 179f * (PI.toFloat() / 180f)
                        morePos = Offset(outerRadius * cos(moreAngle), outerRadius * sin(moreAngle))
                    }
                }

                return RadialTargetLayout(
                    categoriesWithPos = catPositions,
                    deletePos = deletePos,
                    sharePos = sharePos,
                    shareAndDeletePos = shareAndDeletePos,
                    copyPos = copyPos,
                    morePos = morePos,
                    isDualRing = isOverflowExpanded && total > 6,
                    suggestedCategoryId = suggestedCategoryId,
                    overflowCategories = overflowList,
                    innerRadiusPx = innerRadius,
                    outerRadiusPx = outerRadius
                )
            }

            OverlayEdgeAnchor.TOP_LEFT -> {
                // Anchored to top-left corner bubble: expand inward into the lower-right quadrant
                val innerRadius = radiusPx * 0.62f
                val copyAngle = 86f * (PI.toFloat() / 180f)
                val shareAngle = 58f * (PI.toFloat() / 180f)
                val shareAndDelAngle = 30f * (PI.toFloat() / 180f)
                val deleteAngle = 2f * (PI.toFloat() / 180f)

                val copyPos = Offset(innerRadius * cos(copyAngle), innerRadius * sin(copyAngle))
                val sharePos = Offset(innerRadius * cos(shareAngle), innerRadius * sin(shareAngle))
                val shareAndDeletePos = Offset(innerRadius * cos(shareAndDelAngle), innerRadius * sin(shareAndDelAngle))
                val deletePos = Offset(innerRadius * cos(deleteAngle), innerRadius * sin(deleteAngle))

                val outerRadius = radiusPx * 1.35f
                val (startDeg, endDeg) = Pair(89f, 1f)
                val count = displayedCategories.size + (if (hasOverflow && !isOverflowExpanded) 1 else 0)

                if (count == 1) {
                    val angle = 45f * (PI.toFloat() / 180f)
                    catPositions.add(displayedCategories[0] to Offset(outerRadius * cos(angle), outerRadius * sin(angle)))
                } else if (count > 1) {
                    val startAngle = startDeg * (PI.toFloat() / 180f)
                    val endAngle = endDeg * (PI.toFloat() / 180f)
                    val step = (endAngle - startAngle) / (count - 1)

                    displayedCategories.forEachIndexed { i, cat ->
                        val isOuter = isOverflowExpanded && i >= 5
                        val effectiveRadius = if (isOuter) outerRadius * 1.30f else outerRadius
                        val a = if (isOuter) {
                            val outerStep = (endAngle - startAngle) / (total - 5).coerceAtLeast(1)
                            startAngle + (i - 5) * outerStep
                        } else {
                            startAngle + i * step
                        }
                        catPositions.add(cat to Offset(effectiveRadius * cos(a), effectiveRadius * sin(a)))
                    }

                    if (hasOverflow && !isOverflowExpanded) {
                        val moreAngle = 1f * (PI.toFloat() / 180f)
                        morePos = Offset(outerRadius * cos(moreAngle), outerRadius * sin(moreAngle))
                    }
                }

                return RadialTargetLayout(
                    categoriesWithPos = catPositions,
                    deletePos = deletePos,
                    sharePos = sharePos,
                    shareAndDeletePos = shareAndDeletePos,
                    copyPos = copyPos,
                    morePos = morePos,
                    isDualRing = isOverflowExpanded && total > 6,
                    suggestedCategoryId = suggestedCategoryId,
                    overflowCategories = overflowList,
                    innerRadiusPx = innerRadius,
                    outerRadiusPx = outerRadius
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
                            shareAndDeletePos = Offset(-sharePos.x, sharePos.y),
                            copyPos = Offset(0f, -radiusPx * 0.5f),
                            morePos = morePos,
                            isDualRing = isOverflowExpanded && total > 6,
                            suggestedCategoryId = suggestedCategoryId,
                            overflowCategories = overflowList,
                            innerRadiusPx = radiusPx * 0.6f,
                            outerRadiusPx = radiusPx
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
                            shareAndDeletePos = Offset(-sharePos.x, sharePos.y),
                            copyPos = Offset(0f, -radiusPx * 0.5f),
                            morePos = morePos,
                            isDualRing = isOverflowExpanded && total > 6,
                            suggestedCategoryId = suggestedCategoryId,
                            overflowCategories = overflowList,
                            innerRadiusPx = radiusPx * 0.6f,
                            outerRadiusPx = radiusPx
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
                            shareAndDeletePos = Offset(-sharePos.x, sharePos.y),
                            copyPos = Offset(0f, -radiusPx * 0.5f),
                            morePos = morePos,
                            isDualRing = isOverflowExpanded && total > 6,
                            suggestedCategoryId = suggestedCategoryId,
                            overflowCategories = overflowList,
                            innerRadiusPx = radiusPx * 0.6f,
                            outerRadiusPx = radiusPx
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

        // 2. Check Share & Delete target
        layout.shareAndDeletePos?.let { sdPos ->
            val dSd = (dragOffset - sdPos).getDistance()
            if (dSd <= hitThresholdPx) {
                return HoverTarget.ShareAndDelete
            }
        }

        // 3. Check Share target
        layout.sharePos?.let { sPos ->
            val dShare = (dragOffset - sPos).getDistance()
            if (dShare <= hitThresholdPx) {
                return HoverTarget.Share
            }
        }

        // 4. Check Copy target
        layout.copyPos?.let { cPos ->
            val dCopy = (dragOffset - cPos).getDistance()
            if (dCopy <= hitThresholdPx) {
                return HoverTarget.Copy
            }
        }

        // 5. Check More overflow node
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

    /**
     * Determines whether a touch point (relative to the menu anchor/origin) is within the
     * active radial menu's interactive bounding area.
     * Touches outside this area can immediately collapse the menu and pass through to underlying apps.
     */
    fun isPointInsideRadialMenu(
        offsetFromAnchor: Offset,
        outerRadiusPx: Float,
        paddingPx: Float = 48f
    ): Boolean {
        val distance = offsetFromAnchor.getDistance()
        // Active interactive radius with safety padding
        val maxInteractiveRadius = outerRadiusPx + paddingPx
        if (distance > maxInteractiveRadius) return false

        // Within minimum center bubble distance
        if (distance < 50f) return true

        return true
    }

    /**
     * Determines the closest screen corner given a finger position offset from the screen center.
     */
    fun findNearestCorner(offsetFromCenter: Offset): OverlayEdgeAnchor {
        return when {
            offsetFromCenter.x >= 0 && offsetFromCenter.y >= 0 -> OverlayEdgeAnchor.BOTTOM_RIGHT
            offsetFromCenter.x < 0 && offsetFromCenter.y >= 0 -> OverlayEdgeAnchor.BOTTOM_LEFT
            offsetFromCenter.x >= 0 && offsetFromCenter.y < 0 -> OverlayEdgeAnchor.TOP_RIGHT
            else -> OverlayEdgeAnchor.TOP_LEFT
        }
    }
}

private fun Offset.getDistance(): Float {
    return sqrt(x * x + y * y)
}

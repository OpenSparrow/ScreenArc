package com.screenarc.app.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.screenarc.app.model.ArcDesignStyle
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.DynamicPaletteColors
import com.screenarc.app.ui.theme.AuroraCyan
import com.screenarc.app.ui.theme.ElectricIndigo
import com.screenarc.app.ui.theme.LocalIsGlassmorphic
import com.screenarc.app.ui.theme.Motion
import com.screenarc.app.ui.theme.Ultraviolet
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Premium 2-Layer Radial Arc Overlay Menu:
 * - Staggered bloom with physics-based overshoot spring
 * - Magnetic drag-through selection with neighbor push-away
 * - Liquid metaball connective bridges (API 33+ AGSL / API 31+ RenderEffect)
 * - 4 distinct visual styles: ORBIT, MINIMAL, PALETTE, BLOOM
 * - Floating hover labels & non-gesture accessible tap alternative
 */
@Composable
fun ArcMenuLayout(
    categories: List<CategoryUiModel>,
    isExpanded: Boolean,
    radiusDp: Dp,
    dragOffsetPx: Offset,
    paletteColors: DynamicPaletteColors,
    dynamicPaletteEnabled: Boolean,
    hoveredCategoryId: Long?,
    isDeleteHovered: Boolean,
    isShareHovered: Boolean,
    isShareAndDeleteHovered: Boolean = false,
    isCopyHovered: Boolean = false,
    isMoreHovered: Boolean = false,
    isOverflowExpanded: Boolean = false,
    suggestedCategoryId: Long? = null,
    handedness: Handedness = Handedness.AUTO,
    edgeAnchor: OverlayEdgeAnchor = OverlayEdgeAnchor.AUTO,
    arcDesignStyle: ArcDesignStyle = ArcDesignStyle.ORBIT,
    anchorOffset: Offset = Offset.Zero,
    onCategoryClick: (CategoryUiModel) -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onShareAndDeleteClick: () -> Unit = {},
    onCopyClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val radiusPx = with(density) { radiusDp.toPx() }

    // Staggered Layer 1 (Actions) and Layer 2 (Categories) bloom animations
    val layer1Progress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = if (isExpanded) Motion.BouncySpring else tween(durationMillis = Motion.DurationFast, easing = Motion.EmphasizedAccelerateEasing),
        label = "Layer1ExpansionProgress"
    )

    val layer2Progress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = if (isExpanded) Motion.FluidSpring else tween(durationMillis = Motion.DurationFast, easing = Motion.EmphasizedAccelerateEasing),
        label = "Layer2ExpansionProgress"
    )

    val lineDismissProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.DurationFast, easing = Motion.FluidEasing),
        label = "LineDismissProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "SuggestedAndWarningPulse")
    val deletePulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DeletePulse"
    )

    val suggestedGlowPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SuggestedGlowPulse"
    )

    if (layer1Progress > 0.01f || layer2Progress > 0.01f) {
        val layout = remember(categories, radiusPx, arcDesignStyle, isRtl, handedness, edgeAnchor, suggestedCategoryId, isOverflowExpanded) {
            RadialMenuGeometry.calculateLayout(
                categories = categories,
                radiusPx = radiusPx,
                style = arcDesignStyle,
                isRtl = isRtl,
                handedness = handedness,
                edgeAnchor = edgeAnchor,
                suggestedCategoryId = suggestedCategoryId,
                isOverflowExpanded = isOverflowExpanded
            )
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val isGlass = LocalIsGlassmorphic.current

        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val centerOffset = Offset(anchorOffset.x, anchorOffset.y)

            // ─── 0. Liquid Metaball Connective Bridge Layer (Behind All Content) ───
            if (arcDesignStyle != ArcDesignStyle.MINIMAL) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .liquidGooeyLayer(enabled = isExpanded, blurRadius = 20f)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasCenter = Offset(size.width / 2f + anchorOffset.x, size.height / 2f + anchorOffset.y)
                        val originRadius = 26.dp.toPx() * layer1Progress

                        // Draw trigger origin blob
                        if (originRadius > 2f) {
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.40f * layer1Progress),
                                radius = originRadius,
                                center = canvasCenter
                            )
                        }

                        // Draw bridges to Layer 1 action pods
                        val innerRad = (if (layout.innerRadiusPx > 0f) layout.innerRadiusPx else radiusPx * 0.62f) * layer1Progress
                        if (innerRad > 10f) {
                            listOfNotNull(layout.copyPos, layout.sharePos, layout.shareAndDeletePos, layout.deletePos).forEach { podOffset ->
                                val target = canvasCenter + podOffset * layer1Progress
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.35f * layer1Progress),
                                    radius = 16.dp.toPx() * layer1Progress,
                                    center = target
                                )
                            }
                        }

                        // Draw bridges to Layer 2 category capsules
                        layout.categoriesWithPos.forEachIndexed { i, (_, basePos) ->
                            val waveProg = (layer2Progress * 1.12f - (i * 0.022f)).coerceIn(0f, 1f)
                            val target = canvasCenter + basePos * waveProg
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.30f * waveProg),
                                radius = 18.dp.toPx() * waveProg,
                                center = target
                            )
                        }
                    }
                }
            }

            // ─── 1. Concentric Radial Guide Tracks & Laser Tethers ───────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasCenter = Offset(size.width / 2f + anchorOffset.x, size.height / 2f + anchorOffset.y)

                // Track Arc 1 (Inner Action Layer)
                val track1Radius = (if (layout.innerRadiusPx > 0f) layout.innerRadiusPx else radiusPx * 0.62f) * layer1Progress
                if (track1Radius > 10f) {
                    val trackStrokeWidth = when (arcDesignStyle) {
                        ArcDesignStyle.MINIMAL -> 1.0.dp.toPx()
                        ArcDesignStyle.BLOOM -> 2.2.dp.toPx()
                        else -> 1.5.dp.toPx()
                    }
                    drawCircle(
                        color = primaryColor.copy(alpha = if (arcDesignStyle == ArcDesignStyle.MINIMAL) 0.08f else 0.16f * layer1Progress),
                        radius = track1Radius,
                        center = canvasCenter,
                        style = Stroke(
                            width = trackStrokeWidth,
                            pathEffect = if (arcDesignStyle == ArcDesignStyle.MINIMAL) null else PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                        )
                    )
                }

                // Track Arc 2 (Outer Categories Layer)
                val track2Radius = (if (layout.outerRadiusPx > 0f) layout.outerRadiusPx else radiusPx * 1.35f) * layer2Progress
                if (track2Radius > 10f) {
                    val track2Color = when (arcDesignStyle) {
                        ArcDesignStyle.PALETTE -> if (dynamicPaletteEnabled) paletteColors.vibrant else AuroraCyan
                        ArcDesignStyle.BLOOM -> Ultraviolet
                        ArcDesignStyle.MINIMAL -> primaryColor.copy(alpha = 0.08f)
                        else -> AuroraCyan
                    }
                    drawCircle(
                        color = track2Color.copy(alpha = 0.15f * layer2Progress),
                        radius = track2Radius,
                        center = canvasCenter,
                        style = Stroke(
                            width = if (arcDesignStyle == ArcDesignStyle.MINIMAL) 1.0.dp.toPx() else 1.8.dp.toPx(),
                            pathEffect = if (arcDesignStyle == ArcDesignStyle.MINIMAL) null else PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    )
                }

                // Dynamic Laser Beam from Dragged Item to Active Target
                val effectiveLineAlpha = lineDismissProgress * (layer1Progress.coerceAtMost(layer2Progress))
                val dragDistance = sqrt(dragOffsetPx.x * dragOffsetPx.x + dragOffsetPx.y * dragOffsetPx.y)
                if (dragDistance > 12.dp.toPx() && effectiveLineAlpha > 0.02f) {
                    val draggedScreenPoint = canvasCenter + dragOffsetPx

                    val activeTargetPoint: Offset? = when {
                        isDeleteHovered -> canvasCenter + layout.deletePos * layer1Progress
                        isShareAndDeleteHovered -> layout.shareAndDeletePos?.let { canvasCenter + it * layer1Progress }
                        isShareHovered -> layout.sharePos?.let { canvasCenter + it * layer1Progress }
                        isCopyHovered -> layout.copyPos?.let { canvasCenter + it * layer1Progress }
                        isMoreHovered -> layout.morePos?.let { canvasCenter + it * layer2Progress }
                        hoveredCategoryId != null -> {
                            val hoveredPair = layout.categoriesWithPos.find { it.first.id == hoveredCategoryId }
                            hoveredPair?.let { canvasCenter + it.second * layer2Progress }
                        }
                        else -> null
                    }

                    if (activeTargetPoint != null) {
                        val beamColor = when {
                            isDeleteHovered -> Color(0xFFFF1744)
                            isShareAndDeleteHovered -> Color(0xFFFF9100)
                            isShareHovered -> AuroraCyan
                            isCopyHovered -> ElectricIndigo
                            isMoreHovered -> primaryColor
                            else -> {
                                val cat = layout.categoriesWithPos.find { it.first.id == hoveredCategoryId }?.first
                                cat?.composeColor ?: primaryColor
                            }
                        }

                        // Wide glowing aura
                        drawLine(
                            color = beamColor.copy(alpha = 0.32f * effectiveLineAlpha),
                            start = draggedScreenPoint,
                            end = activeTargetPoint,
                            strokeWidth = 6.dp.toPx() * effectiveLineAlpha,
                            cap = StrokeCap.Round
                        )
                        // Core sharp laser
                        drawLine(
                            color = beamColor.copy(alpha = effectiveLineAlpha),
                            start = draggedScreenPoint,
                            end = activeTargetPoint,
                            strokeWidth = 2.dp.toPx() * effectiveLineAlpha,
                            cap = StrokeCap.Round
                        )
                    } else {
                        // Elastic tether to origin
                        drawLine(
                            color = primaryColor.copy(alpha = 0.22f * effectiveLineAlpha),
                            start = canvasCenter,
                            end = draggedScreenPoint,
                            strokeWidth = 1.5.dp.toPx() * effectiveLineAlpha,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // ─── Layer 1 (Inner Arc): Actions (Copy, Share, Share & Delete, Delete) ───

            // 1. COPY Action Pod
            layout.copyPos?.let { cPos ->
                val copyScale by animateFloatAsState(
                    targetValue = if (isCopyHovered) 1.30f else 1.0f,
                    animationSpec = Motion.SnappySpring,
                    label = "CopyScale"
                )
                val copyElevation by animateDpAsState(
                    targetValue = if (isCopyHovered) 16.dp else 4.dp,
                    label = "CopyElevation"
                )
                val cX = cPos.x * layer1Progress
                val cY = cPos.y * layer1Progress

                Surface(
                    modifier = Modifier
                        .offset { IntOffset((anchorOffset.x + cX).roundToInt(), (anchorOffset.y + cY).roundToInt()) }
                        .scale(copyScale * layer1Progress)
                        .shadow(copyElevation, shape = RoundedCornerShape(20.dp), clip = false)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCopyClick
                        )
                        .zIndex(if (isCopyHovered) 30f else 10f)
                        .semantics {
                            contentDescription = "Copy screenshot to clipboard"
                            stateDescription = if (isCopyHovered) "Selected" else "Available"
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        isCopyHovered -> ElectricIndigo
                        isGlass -> Color(0xBF221A3D)
                        else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f)
                    },
                    border = BorderStroke(
                        width = if (isCopyHovered) 2.4.dp else 1.2.dp,
                        color = if (isCopyHovered) Color.White else ElectricIndigo.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = if (isCopyHovered) Color.White else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            color = if (isCopyHovered) Color.White else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // 2. SHARE Action Pod
            layout.sharePos?.let { sPos ->
                val shareScale by animateFloatAsState(
                    targetValue = if (isShareHovered) 1.30f else 1.0f,
                    animationSpec = Motion.SnappySpring,
                    label = "ShareScale"
                )
                val shareElevation by animateDpAsState(
                    targetValue = if (isShareHovered) 16.dp else 4.dp,
                    label = "ShareElevation"
                )
                val sX = sPos.x * layer1Progress
                val sY = sPos.y * layer1Progress

                Surface(
                    modifier = Modifier
                        .offset { IntOffset((anchorOffset.x + sX).roundToInt(), (anchorOffset.y + sY).roundToInt()) }
                        .scale(shareScale * layer1Progress)
                        .shadow(shareElevation, shape = RoundedCornerShape(20.dp), clip = false)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onShareClick
                        )
                        .zIndex(if (isShareHovered) 30f else 10f)
                        .semantics {
                            contentDescription = "Share screenshot"
                            stateDescription = if (isShareHovered) "Selected" else "Available"
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        isShareHovered -> AuroraCyan
                        isGlass -> Color(0xBF102A38)
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    },
                    border = BorderStroke(
                        width = if (isShareHovered) 2.4.dp else 1.2.dp,
                        color = if (isShareHovered) Color.White else AuroraCyan.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = if (isShareHovered) Color(0xFF041820) else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Share",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            color = if (isShareHovered) Color(0xFF041820) else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 3. SHARE & DELETE Action Pod
            layout.shareAndDeletePos?.let { sdPos ->
                val sdScale by animateFloatAsState(
                    targetValue = if (isShareAndDeleteHovered) 1.30f else 1.0f,
                    animationSpec = Motion.SnappySpring,
                    label = "ShareAndDelScale"
                )
                val sdElevation by animateDpAsState(
                    targetValue = if (isShareAndDeleteHovered) 16.dp else 4.dp,
                    label = "ShareAndDelElevation"
                )
                val sdX = sdPos.x * layer1Progress
                val sdY = sdPos.y * layer1Progress

                Surface(
                    modifier = Modifier
                        .offset { IntOffset((anchorOffset.x + sdX).roundToInt(), (anchorOffset.y + sdY).roundToInt()) }
                        .scale(sdScale * layer1Progress)
                        .shadow(sdElevation, shape = RoundedCornerShape(20.dp), clip = false)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onShareAndDeleteClick
                        )
                        .zIndex(if (isShareAndDeleteHovered) 30f else 10f)
                        .semantics {
                            contentDescription = "Share and delete screenshot"
                            stateDescription = if (isShareAndDeleteHovered) "Selected" else "Available"
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        isShareAndDeleteHovered -> Color(0xFFE65100)
                        isGlass -> Color(0xBF3B1C10)
                        else -> Color(0xFFFFE0B2).copy(alpha = 0.95f)
                    },
                    border = BorderStroke(
                        width = if (isShareAndDeleteHovered) 2.4.dp else 1.2.dp,
                        color = if (isShareAndDeleteHovered) Color.White else Color(0xFFFF9800).copy(alpha = 0.75f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = if (isShareAndDeleteHovered) Color.White else Color(0xFFBF360C),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Share & Del",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            color = if (isShareAndDeleteHovered) Color.White else Color(0xFFBF360C)
                        )
                    }
                }
            }

            // 4. DELETE Action Pod
            val delX = layout.deletePos.x * layer1Progress
            val delY = layout.deletePos.y * layer1Progress
            val delScale by animateFloatAsState(
                targetValue = if (isDeleteHovered) 1.35f * deletePulseScale else 1.0f,
                animationSpec = Motion.SnappySpring,
                label = "DelScale"
            )
            val delElevation by animateDpAsState(
                targetValue = if (isDeleteHovered) 18.dp else 4.dp,
                label = "DelElevation"
            )

            Surface(
                modifier = Modifier
                    .offset { IntOffset((anchorOffset.x + delX).roundToInt(), (anchorOffset.y + delY).roundToInt()) }
                    .scale(delScale * layer1Progress)
                    .shadow(delElevation, shape = RoundedCornerShape(20.dp), clip = false)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDeleteClick
                    )
                    .zIndex(if (isDeleteHovered) 30f else 10f)
                    .semantics {
                        contentDescription = "Delete screenshot"
                        stateDescription = if (isDeleteHovered) "Selected" else "Available"
                    },
                shape = RoundedCornerShape(20.dp),
                color = when {
                    isDeleteHovered -> Color(0xFFE53935)
                    isGlass -> Color(0xBF3E1418)
                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                },
                border = BorderStroke(
                    width = if (isDeleteHovered) 2.6.dp else 1.2.dp,
                    color = if (isDeleteHovered) Color.White else MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = if (isDeleteHovered) Color.White else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        color = if (isDeleteHovered) Color.White else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // ─── Layer 2 (Outer Arc): Structured Category Capsules with Neighbor Push-Away ───

            // Find index of currently hovered category to compute gentle neighbor push
            val hoveredIndex = layout.categoriesWithPos.indexOfFirst { it.first.id == hoveredCategoryId }

            layout.categoriesWithPos.forEachIndexed { index, (category, baseOffset) ->
                val isHovered = hoveredCategoryId == category.id
                val isSuggested = layout.suggestedCategoryId == category.id

                val waveProgress = (layer2Progress * 1.12f - (index * 0.022f)).coerceIn(0f, 1f)

                // ── Neighbor Push-Away Physics (Visual Only) ─────────────────
                // If adjacent to the hovered item, gently push radially outward/angularly away by 10dp
                val neighborDisplacement = when {
                    hoveredIndex >= 0 && index == hoveredIndex - 1 -> -10.dp
                    hoveredIndex >= 0 && index == hoveredIndex + 1 -> 10.dp
                    else -> 0.dp
                }
                val neighborDisplacementPx = with(density) { neighborDisplacement.toPx() }

                val animatedPush by animateFloatAsState(
                    targetValue = neighborDisplacementPx,
                    animationSpec = Motion.FluidSpring,
                    label = "NeighborPush_${category.id}"
                )

                // Tangential vector for smooth angular displacement
                val angle = atan2(baseOffset.y, baseOffset.x)
                val tangentX = -sin(angle) * animatedPush
                val tangentY = cos(angle) * animatedPush

                val xPx = (baseOffset.x + tangentX) * waveProgress
                val yPx = (baseOffset.y + tangentY) * waveProgress

                val chipScale by animateFloatAsState(
                    targetValue = when {
                        isHovered -> 1.30f
                        isSuggested -> 1.08f * suggestedGlowPulse
                        else -> 1.0f
                    },
                    animationSpec = Motion.FluidSpring,
                    label = "ChipScale_${category.id}"
                )

                val chipElevation by animateDpAsState(
                    targetValue = if (isHovered) 18.dp else if (isSuggested) 8.dp else 3.dp,
                    label = "ChipElevation_${category.id}"
                )

                val containerColor by animateColorAsState(
                    targetValue = when {
                        isHovered -> if (dynamicPaletteEnabled) paletteColors.vibrantLight else category.composeColor
                        isSuggested -> category.composeColor.copy(alpha = if (isGlass) 0.65f else 0.45f)
                        isGlass -> Color(0xCC1A162B)
                        dynamicPaletteEnabled -> category.composeColor.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
                    },
                    label = "ContainerColor_${category.id}"
                )

                val targetBorderColor = when {
                    isHovered -> Color.White
                    isSuggested -> category.composeColor
                    isGlass -> Color(0x66FFFFFF)
                    else -> category.composeColor.copy(alpha = 0.80f)
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset((anchorOffset.x + xPx).roundToInt(), (anchorOffset.y + yPx).roundToInt()) }
                        .scale(chipScale * waveProgress)
                        .zIndex(if (isHovered) 50f else if (isSuggested) 40f else (index + 1).toFloat())
                ) {
                    Surface(
                        modifier = Modifier
                            .shadow(chipElevation, shape = RoundedCornerShape(20.dp), clip = false)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onCategoryClick(category)
                            }
                            .drawBehind {
                                if (isHovered || isSuggested) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                category.composeColor.copy(alpha = if (isHovered) 0.55f else 0.30f),
                                                Color.Transparent
                                            ),
                                            radius = size.maxDimension * 1.1f
                                        )
                                    )
                                }
                            }
                            .semantics {
                                contentDescription = "${category.name} category"
                                stateDescription = if (isHovered) "Selected" else if (isSuggested) "Suggested" else "Available"
                            },
                        shape = RoundedCornerShape(20.dp),
                        color = containerColor,
                        border = BorderStroke(
                            width = if (isHovered) 2.4.dp else if (isSuggested) 1.8.dp else 1.2.dp,
                            color = targetBorderColor
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .widthIn(min = 68.dp, max = 80.dp)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isHovered) Color.White else category.composeColor,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = category.getIcon(),
                                        contentDescription = null,
                                        tint = if (isHovered) category.composeColor else Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isHovered || isSuggested) FontWeight.ExtraBold else FontWeight.Bold,
                                    fontSize = 11.5.sp
                                ),
                                color = if (isHovered) (if (dynamicPaletteEnabled) paletteColors.vibrantDark else Color.White) else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isSuggested && !isHovered) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Suggested",
                                    tint = category.composeColor,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }

                    // ── Floating Label Tooltip (Appears above hovered item) ──
                    if (isHovered) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xEE000000),
                            border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-32).dp)
                        ) {
                            Text(
                                text = "Drop to save to ${category.name}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // ─── Overflow "+ More" Node (When categories > 6) ────────────────────────
            layout.morePos?.let { mPos ->
                val isHovered = isMoreHovered
                val waveProgress = (layer2Progress * 1.12f - (layout.categoriesWithPos.size * 0.022f)).coerceIn(0f, 1f)
                val xPx = mPos.x * waveProgress
                val yPx = mPos.y * waveProgress

                val moreScale by animateFloatAsState(
                    targetValue = if (isHovered) 1.30f else 1.0f,
                    animationSpec = Motion.SnappySpring,
                    label = "MoreScale"
                )

                Surface(
                    modifier = Modifier
                        .offset { IntOffset((anchorOffset.x + xPx).roundToInt(), (anchorOffset.y + yPx).roundToInt()) }
                        .scale(moreScale * waveProgress)
                        .shadow(if (isHovered) 16.dp else 4.dp, shape = RoundedCornerShape(20.dp), clip = false)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onMoreClick
                        )
                        .zIndex(if (isHovered) 50f else 35f)
                        .semantics {
                            contentDescription = "Show more categories"
                            stateDescription = if (isHovered) "Selected" else "Available"
                        },
                    color = when {
                        isHovered -> primaryColor
                        isGlass -> Color(0xCC1A162B)
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
                    },
                    border = BorderStroke(
                        width = if (isHovered) 2.4.dp else 1.2.dp,
                        color = if (isHovered) Color.White else primaryColor.copy(alpha = 0.65f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(min = 68.dp, max = 80.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isHovered) Color.White else primaryColor,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = null,
                                    tint = if (isHovered) primaryColor else Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "+${layout.overflowCategories.size}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            color = if (isHovered) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

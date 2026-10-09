package com.screenarc.app.ui.overlay

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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import com.screenarc.app.ui.theme.LocalIsGlassmorphic
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.screenarc.app.model.ArcDesignStyle
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.DynamicPaletteColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
    isMoreHovered: Boolean = false,
    isOverflowExpanded: Boolean = false,
    suggestedCategoryId: Long? = null,
    handedness: Handedness = Handedness.AUTO,
    edgeAnchor: OverlayEdgeAnchor = OverlayEdgeAnchor.AUTO,
    arcDesignStyle: ArcDesignStyle,
    anchorOffset: Offset = Offset.Zero,
    onCategoryClick: (CategoryUiModel) -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val radiusPx = with(density) { radiusDp.toPx() }

    // Spring open / close animation (~220-280ms)
    val animatedProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ArcExpansionProgress"
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

    if (animatedProgress > 0.01f) {
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
        val outlineColor = MaterialTheme.colorScheme.outline
        val errorColor = MaterialTheme.colorScheme.error
        val isGlass = LocalIsGlassmorphic.current

        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 1. Background Visual Guide Tracks & Connecting Energy Tethers
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f + anchorOffset.x, size.height / 2f + anchorOffset.y)
                val currentRadius = radiusPx * animatedProgress

                // A. Visual Arc Geometry Guides
                when (arcDesignStyle) {
                    ArcDesignStyle.ORBIT -> {
                        // Inner and Outer Orbital Guide Rings
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.08f * animatedProgress),
                            radius = currentRadius * 0.65f,
                            center = centerOffset,
                            style = Stroke(
                                width = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 16f))
                            )
                        )
                        drawCircle(
                            color = (if (dynamicPaletteEnabled) paletteColors.vibrant else primaryColor).copy(
                                alpha = 0.22f * animatedProgress
                            ),
                            radius = currentRadius,
                            center = centerOffset,
                            style = Stroke(
                                width = 1.8.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
                            )
                        )
                        if (layout.isDualRing) {
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.16f * animatedProgress),
                                radius = currentRadius * 1.38f,
                                center = centerOffset,
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f))
                                )
                            )
                        }

                        // Subtle orbital tick markers
                        val (tickStartDeg, tickSweepDeg) = when (edgeAnchor) {
                            OverlayEdgeAnchor.BOTTOM_RIGHT -> Pair(-190, 110)
                            OverlayEdgeAnchor.BOTTOM_LEFT -> Pair(-85, 110)
                            OverlayEdgeAnchor.CENTER, OverlayEdgeAnchor.AUTO -> Pair(0, 360)
                        }
                        for (i in 0 until 12) {
                            val tickAngle = (tickStartDeg + (i * (tickSweepDeg / 11f))) * (PI.toFloat() / 180f)
                            val startP = centerOffset + Offset(
                                (currentRadius - 5.dp.toPx()) * cos(tickAngle),
                                (currentRadius - 5.dp.toPx()) * sin(tickAngle)
                            )
                            val endP = centerOffset + Offset(
                                (currentRadius + 5.dp.toPx()) * cos(tickAngle),
                                (currentRadius + 5.dp.toPx()) * sin(tickAngle)
                            )
                            drawLine(
                                color = primaryColor.copy(alpha = 0.25f * animatedProgress),
                                start = startP,
                                end = endP,
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    ArcDesignStyle.FAN -> {
                        // Glowing Fan Ribbon Track
                        val (fanStart, fanSweep) = when (edgeAnchor) {
                            OverlayEdgeAnchor.BOTTOM_RIGHT -> Pair(175f, 105f)
                            OverlayEdgeAnchor.BOTTOM_LEFT -> Pair(265f, 105f)
                            OverlayEdgeAnchor.CENTER, OverlayEdgeAnchor.AUTO -> Pair(180f, 180f)
                        }
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    primaryColor.copy(alpha = 0.05f),
                                    (if (dynamicPaletteEnabled) paletteColors.vibrant else primaryColor).copy(alpha = 0.28f * animatedProgress),
                                    primaryColor.copy(alpha = 0.05f)
                                )
                            ),
                            startAngle = fanStart,
                            sweepAngle = fanSweep,
                            useCenter = false,
                            topLeft = centerOffset - Offset(currentRadius, currentRadius),
                            size = androidx.compose.ui.geometry.Size(currentRadius * 2, currentRadius * 2),
                            style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                        )
                        if (layout.isDualRing) {
                            drawArc(
                                color = primaryColor.copy(alpha = 0.18f * animatedProgress),
                                startAngle = fanStart,
                                sweepAngle = fanSweep,
                                useCenter = false,
                                topLeft = centerOffset - Offset(currentRadius * 1.35f, currentRadius * 1.35f),
                                size = androidx.compose.ui.geometry.Size(currentRadius * 2.7f, currentRadius * 2.7f),
                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        // Bottom Delete cradle arc
                        drawArc(
                            color = errorColor.copy(alpha = 0.25f * animatedProgress),
                            startAngle = 60f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = centerOffset - Offset(currentRadius * 0.96f, currentRadius * 0.96f),
                            size = androidx.compose.ui.geometry.Size(currentRadius * 1.92f, currentRadius * 1.92f),
                            style = Stroke(
                                width = 3.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                                cap = StrokeCap.Round
                            )
                        )
                    }

                    ArcDesignStyle.DIAL -> {
                        // Tactile Dial with Radial Sector Dividers
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.12f * animatedProgress),
                            radius = currentRadius,
                            center = centerOffset,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawArc(
                            color = errorColor.copy(alpha = if (isDeleteHovered) 0.35f else 0.14f * animatedProgress),
                            startAngle = 65f,
                            sweepAngle = 50f,
                            useCenter = true,
                            topLeft = centerOffset - Offset(currentRadius, currentRadius),
                            size = androidx.compose.ui.geometry.Size(currentRadius * 2, currentRadius * 2)
                        )

                        // Radiating dividing spokes
                        layout.categoriesWithPos.forEach { (_, pos) ->
                            val spokeEnd = centerOffset + pos * animatedProgress
                            drawLine(
                                color = outlineColor.copy(alpha = 0.15f * animatedProgress),
                                start = centerOffset,
                                end = spokeEnd,
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }

                // B. Dynamic Connecting Laser / Energy Tether Beam from Dragged Item
                val dragDistance = sqrt(dragOffsetPx.x * dragOffsetPx.x + dragOffsetPx.y * dragOffsetPx.y)
                if (dragDistance > 12.dp.toPx()) {
                    val draggedScreenPoint = centerOffset + dragOffsetPx

                    val activeTargetPoint: Offset? = when {
                        isDeleteHovered -> centerOffset + layout.deletePos * animatedProgress
                        isShareHovered -> layout.sharePos?.let { centerOffset + it * animatedProgress }
                        isMoreHovered -> layout.morePos?.let { centerOffset + it * animatedProgress }
                        hoveredCategoryId != null -> {
                            val hoveredPair = layout.categoriesWithPos.find { it.first.id == hoveredCategoryId }
                            hoveredPair?.let { centerOffset + it.second * animatedProgress }
                        }
                        else -> null
                    }

                    if (activeTargetPoint != null) {
                        val beamColor = when {
                            isDeleteHovered -> Color(0xFFFF1744) // Warning crimson
                            isShareHovered -> Color(0xFF29B6F6) // Cyan share beam
                            isMoreHovered -> primaryColor
                            else -> {
                                val cat = layout.categoriesWithPos.find { it.first.id == hoveredCategoryId }?.first
                                cat?.composeColor ?: primaryColor
                            }
                        }

                        // Wide glowing aura line
                        drawLine(
                            color = beamColor.copy(alpha = 0.35f),
                            start = draggedScreenPoint,
                            end = activeTargetPoint,
                            strokeWidth = 9.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Core laser beam
                        drawLine(
                            color = beamColor,
                            start = draggedScreenPoint,
                            end = activeTargetPoint,
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    } else {
                        // Elastic tether to origin
                        drawLine(
                            color = primaryColor.copy(alpha = 0.22f),
                            start = centerOffset,
                            end = draggedScreenPoint,
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // 2. Category Drop Target Pods
            layout.categoriesWithPos.forEach { (category, baseOffset) ->
                val isHovered = hoveredCategoryId == category.id
                val isSuggested = layout.suggestedCategoryId == category.id

                val extraDistance = if (isHovered) with(density) { 16.dp.toPx() } else 0f
                val distanceMultiplier = (radiusPx + extraDistance) / radiusPx
                val xPx = baseOffset.x * distanceMultiplier * animatedProgress
                val yPx = baseOffset.y * distanceMultiplier * animatedProgress

                val chipScale by animateFloatAsState(
                    targetValue = when {
                        isHovered -> 1.45f
                        isSuggested -> 1.08f * suggestedGlowPulse
                        else -> 1.0f
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "ChipScale_${category.id}"
                )

                val chipElevation by animateDpAsState(
                    targetValue = if (isHovered) 16.dp else if (isSuggested) 8.dp else 4.dp,
                    animationSpec = tween(150),
                    label = "ChipElevation_${category.id}"
                )

                val targetContainerColor = when {
                    isHovered -> {
                        if (dynamicPaletteEnabled) paletteColors.vibrantLight else category.composeColor
                    }
                    isSuggested -> category.composeColor.copy(alpha = if (isGlass) 0.42f else 0.28f)
                    isGlass -> Color(0xFF1E1730).copy(alpha = 0.58f)
                    dynamicPaletteEnabled -> category.composeColor.copy(alpha = 0.24f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
                }

                val containerColor by animateColorAsState(
                    targetValue = targetContainerColor,
                    animationSpec = tween(180),
                    label = "ContainerColor_${category.id}"
                )

                val contentColor = when {
                    isHovered -> if (dynamicPaletteEnabled) paletteColors.vibrantDark else Color.White
                    else -> MaterialTheme.colorScheme.onSurface
                }

                val targetBorderColor = when {
                    isHovered -> Color.White
                    isSuggested -> category.composeColor
                    isGlass -> Color.White.copy(alpha = 0.50f)
                    else -> category.composeColor.copy(alpha = 0.65f)
                }

                Surface(
                    modifier = Modifier
                        .offset { IntOffset((anchorOffset.x + xPx).roundToInt(), (anchorOffset.y + yPx).roundToInt()) }
                        .scale(chipScale * animatedProgress)
                        .shadow(chipElevation, shape = RoundedCornerShape(22.dp), clip = false)
                        .clip(RoundedCornerShape(22.dp))
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
                                        colors = listOf(category.composeColor.copy(alpha = if (isHovered) 0.45f else 0.25f), Color.Transparent),
                                        radius = size.maxDimension * 0.85f
                                    )
                                )
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            if (isGlass) {
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                                        startY = 0f,
                                        endY = 12.dp.toPx()
                                    ),
                                    size = androidx.compose.ui.geometry.Size(size.width, 12.dp.toPx())
                                )
                            }
                        }
                        .semantics {
                            contentDescription = if (isHovered) {
                                "${category.name} category highlighted, release to drop"
                            } else if (isSuggested) {
                                "${category.name} category, recommended suggestion"
                            } else {
                                "${category.name} category, drop target"
                            }
                            stateDescription = if (isHovered) "Highlighted" else if (isSuggested) "Suggested" else "Available"
                        },
                    shape = RoundedCornerShape(22.dp),
                    color = containerColor,
                    border = BorderStroke(
                        width = if (isHovered) 2.6.dp else if (isSuggested) 2.0.dp else 1.2.dp,
                        color = targetBorderColor
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 120.dp)
                            .padding(horizontal = if (isHovered) 14.dp else 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isHovered) Color.White else category.composeColor,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = category.getIcon(),
                                        contentDescription = null,
                                        tint = if (isHovered) category.composeColor else Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isHovered || isSuggested) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = if (isHovered) 13.5.sp else 12.sp
                                ),
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isSuggested && !isHovered) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Suggested",
                                    tint = category.composeColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        if (isHovered) {
                            Text(
                                text = "Release to drop",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = contentColor.copy(alpha = 0.9f),
                                maxLines = 1
                            )
                        } else if (isSuggested) {
                            Text(
                                text = "Suggested",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = category.composeColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 3. Overflow "+ More" Node (When categories > 6)
            layout.morePos?.let { mPos ->
                val isHovered = isMoreHovered
                val extraDistance = if (isHovered) with(density) { 16.dp.toPx() } else 0f
                val distanceMultiplier = (radiusPx + extraDistance) / radiusPx
                val xPx = mPos.x * distanceMultiplier * animatedProgress
                val yPx = mPos.y * distanceMultiplier * animatedProgress

                val moreScale by animateFloatAsState(
                    targetValue = if (isHovered) 1.45f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "MoreScale"
                )

                Surface(
                    modifier = Modifier
                        .offset { IntOffset((anchorOffset.x + xPx).roundToInt(), (anchorOffset.y + yPx).roundToInt()) }
                        .scale(moreScale * animatedProgress)
                        .shadow(if (isHovered) 16.dp else 4.dp, shape = RoundedCornerShape(22.dp), clip = false)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onMoreClick
                        )
                        .semantics {
                            contentDescription = "More categories, tap or hover to expand all ${layout.overflowCategories.size + 5} categories"
                            stateDescription = if (isHovered) "Highlighted" else "Available"
                        },
                    color = when {
                        isHovered -> primaryColor
                        isGlass -> Color(0xFF1E1730).copy(alpha = 0.58f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
                    },
                    border = BorderStroke(
                        width = if (isHovered) 2.5.dp else 1.2.dp,
                        color = when {
                            isHovered -> Color.White
                            isGlass -> Color.White.copy(alpha = 0.50f)
                            else -> primaryColor.copy(alpha = 0.65f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isHovered) Color.White else primaryColor,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = null,
                                    tint = if (isHovered) primaryColor else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "+${layout.overflowCategories.size} More",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isHovered) FontWeight.ExtraBold else FontWeight.SemiBold,
                                fontSize = if (isHovered) 13.5.sp else 12.sp
                            ),
                            color = if (isHovered) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 4. Intuitive DELETE Drop Target Pod
            val deleteExtraDist = if (isDeleteHovered) with(density) { 18.dp.toPx() } else 0f
            val deleteDistMult = (radiusPx + deleteExtraDist) / radiusPx
            val delX = layout.deletePos.x * deleteDistMult * animatedProgress
            val delY = layout.deletePos.y * deleteDistMult * animatedProgress

            val deleteScale by animateFloatAsState(
                targetValue = if (isDeleteHovered) 1.48f * deletePulseScale else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "DeleteScale"
            )

            val deleteElevation by animateDpAsState(
                targetValue = if (isDeleteHovered) 20.dp else 4.dp,
                animationSpec = tween(150),
                label = "DeleteElevation"
            )

            val deleteContainerColor by animateColorAsState(
                targetValue = when {
                    isDeleteHovered -> Color(0xFFE53935)
                    isGlass -> Color(0xFF451515).copy(alpha = 0.60f)
                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.90f)
                },
                animationSpec = tween(180),
                label = "DeleteContainerColor"
            )

            val deleteContentColor = if (isDeleteHovered) Color.White else if (isGlass) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.onErrorContainer
            val deleteBorderColor = when {
                isDeleteHovered -> Color.White
                isGlass -> Color(0xFFFFB4AB).copy(alpha = 0.50f)
                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
            }

            Surface(
                modifier = Modifier
                    .offset { IntOffset((anchorOffset.x + delX).roundToInt(), (anchorOffset.y + delY).roundToInt()) }
                    .scale(deleteScale * animatedProgress)
                    .shadow(deleteElevation, shape = RoundedCornerShape(22.dp), clip = false)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDeleteClick
                    )
                    .semantics {
                        contentDescription = if (isDeleteHovered) {
                            "Delete screenshot, release to delete"
                        } else {
                            "Delete screenshot, drop target"
                        }
                        stateDescription = if (isDeleteHovered) "Highlighted" else "Available"
                    },
                shape = RoundedCornerShape(22.dp),
                color = deleteContainerColor,
                border = BorderStroke(
                    width = if (isDeleteHovered) 2.8.dp else 1.4.dp,
                    color = deleteBorderColor
                )
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 100.dp)
                        .padding(horizontal = if (isDeleteHovered) 14.dp else 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDeleteHovered) Color.White else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = if (isDeleteHovered) Color(0xFFE53935) else Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isDeleteHovered) FontWeight.ExtraBold else FontWeight.SemiBold,
                                fontSize = if (isDeleteHovered) 13.5.sp else 12.sp
                            ),
                            color = deleteContentColor,
                            maxLines = 1
                        )
                    }

                    if (isDeleteHovered) {
                        Text(
                            text = "Release to Delete",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White.copy(alpha = 0.95f),
                            maxLines = 1
                        )
                    }
                }
            }

            // 5. Quick SHARE Drop Target Pod
            layout.sharePos?.let { sPos ->
                val shareExtraDist = if (isShareHovered) with(density) { 16.dp.toPx() } else 0f
                val shareDistMult = (radiusPx + shareExtraDist) / radiusPx
                val shX = sPos.x * shareDistMult * animatedProgress
                val shY = sPos.y * shareDistMult * animatedProgress

                val shareScale by animateFloatAsState(
                    targetValue = if (isShareHovered) 1.45f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "ShareScale"
                )

                val shareElevation by animateDpAsState(
                    targetValue = if (isShareHovered) 16.dp else 4.dp,
                    animationSpec = tween(150),
                    label = "ShareElevation"
                )

                val shareContainerColor by animateColorAsState(
                    targetValue = when {
                        isShareHovered -> primaryColor
                        isGlass -> Color(0xFF1E1730).copy(alpha = 0.58f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
                    },
                    animationSpec = tween(180),
                    label = "ShareContainerColor"
                )

                val shareContentColor = if (isShareHovered) Color.White else MaterialTheme.colorScheme.onSurface
                val shareBorderColor = when {
                    isShareHovered -> Color.White
                    isGlass -> Color.White.copy(alpha = 0.50f)
                    else -> primaryColor.copy(alpha = 0.5f)
                }

                Surface(
                    modifier = Modifier
                        .offset { IntOffset((anchorOffset.x + shX).roundToInt(), (anchorOffset.y + shY).roundToInt()) }
                        .scale(shareScale * animatedProgress)
                        .shadow(shareElevation, shape = RoundedCornerShape(22.dp), clip = false)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onShareClick
                        )
                        .semantics {
                            contentDescription = if (isShareHovered) {
                                "Share screenshot, release to share"
                            } else {
                                "Share screenshot, drop target"
                            }
                            stateDescription = if (isShareHovered) "Highlighted" else "Available"
                        },
                    shape = RoundedCornerShape(22.dp),
                    color = shareContainerColor,
                    border = BorderStroke(
                        width = if (isShareHovered) 2.5.dp else 1.2.dp,
                        color = shareBorderColor
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 100.dp)
                            .padding(horizontal = if (isShareHovered) 14.dp else 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isShareHovered) Color.White else primaryColor,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = if (isShareHovered) primaryColor else Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "Share",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isShareHovered) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = if (isShareHovered) 13.5.sp else 12.sp
                                ),
                                color = shareContentColor,
                                maxLines = 1
                            )
                        }

                        if (isShareHovered) {
                            Text(
                                text = "Release to Share",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

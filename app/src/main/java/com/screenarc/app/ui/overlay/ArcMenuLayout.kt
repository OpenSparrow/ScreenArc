package com.screenarc.app.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenarc.app.model.Category
import com.screenarc.app.model.DynamicPaletteColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class ArcNodePosition(
    val category: Category,
    val offsetPx: Offset,
    val angleRad: Float
)

@Composable
fun ArcMenuLayout(
    categories: List<Category>,
    isExpanded: Boolean,
    radiusDp: Dp,
    dragOffsetPx: Offset,
    paletteColors: DynamicPaletteColors,
    dynamicPaletteEnabled: Boolean,
    hoveredCategoryId: String?,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.toPx() }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ArcExpansionProgress"
    )

    if (animatedProgress > 0.01f) {
        val total = categories.size
        // Calculate angle for each node: distribute across 360 or upper/side arc
        val angleStep = if (total > 1) (2 * PI / total).toFloat() else 0f
        // Offset start angle so top node is centered
        val startAngle = -PI.toFloat() / 2f

        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            categories.forEachIndexed { index, category ->
                val angle = startAngle + index * angleStep
                val currentRadius = radiusPx * animatedProgress

                val xPx = currentRadius * cos(angle)
                val yPx = currentRadius * sin(angle)

                val isHovered = hoveredCategoryId == category.id

                val chipScale by animateFloatAsState(
                    targetValue = if (isHovered) 1.25f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "ChipScale_${category.id}"
                )

                val chipElevation by animateDpAsState(
                    targetValue = if (isHovered) 12.dp else 4.dp,
                    animationSpec = tween(150),
                    label = "ChipElevation_${category.id}"
                )

                // Dynamic coloring from palette vs theme
                val targetContainerColor = when {
                    isHovered -> {
                        if (dynamicPaletteEnabled) paletteColors.vibrantLight else MaterialTheme.colorScheme.primaryContainer
                    }
                    dynamicPaletteEnabled -> {
                        // Blend category color or palette
                        category.composeColor.copy(alpha = 0.18f)
                    }
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }

                val containerColor by animateColorAsState(
                    targetValue = targetContainerColor,
                    animationSpec = tween(180),
                    label = "ContainerColor_${category.id}"
                )

                val contentColor = when {
                    isHovered -> {
                        if (dynamicPaletteEnabled) paletteColors.vibrantDark else MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    else -> MaterialTheme.colorScheme.onSurface
                }

                // Node Chip
                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(xPx.roundToInt(), yPx.roundToInt())
                        }
                        .scale(chipScale * animatedProgress)
                        .shadow(chipElevation, shape = RoundedCornerShape(20.dp), clip = false)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onCategoryClick(category)
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = containerColor,
                    border = BorderStroke(
                        width = if (isHovered) 2.dp else 1.dp,
                        color = if (isHovered) {
                            if (dynamicPaletteEnabled) paletteColors.vibrant else MaterialTheme.colorScheme.primary
                        } else {
                            category.composeColor.copy(alpha = 0.5f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Category dot / icon
                        Surface(
                            shape = CircleShape,
                            color = category.composeColor,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = category.getIcon(),
                                    contentDescription = category.name,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isHovered) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

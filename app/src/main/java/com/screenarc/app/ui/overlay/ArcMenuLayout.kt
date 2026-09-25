package com.screenarc.app.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.DynamicPaletteColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ArcMenuLayout(
    categories: List<CategoryUiModel>,
    isExpanded: Boolean,
    radiusDp: Dp,
    dragOffsetPx: Offset,
    paletteColors: DynamicPaletteColors,
    dynamicPaletteEnabled: Boolean,
    hoveredCategoryId: Long?,
    onCategoryClick: (CategoryUiModel) -> Unit,
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
        val angleStep = if (total > 1) (2 * PI / total).toFloat() else 0f
        val startAngle = -PI.toFloat() / 2f

        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            categories.forEachIndexed { index, category ->
                val angle = startAngle + index * angleStep
                val isHovered = hoveredCategoryId == category.id

                // Move slightly outward when active
                val extraDistance = if (isHovered) with(density) { 18.dp.toPx() } else 0f
                val currentRadius = (radiusPx + extraDistance) * animatedProgress

                val xPx = currentRadius * cos(angle)
                val yPx = currentRadius * sin(angle)

                val chipScale by animateFloatAsState(
                    targetValue = if (isHovered) 1.45f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "ChipScale_${category.id}"
                )

                val chipElevation by animateDpAsState(
                    targetValue = if (isHovered) 16.dp else 4.dp,
                    animationSpec = tween(150),
                    label = "ChipElevation_${category.id}"
                )

                val targetContainerColor = when {
                    isHovered -> {
                        if (dynamicPaletteEnabled) paletteColors.vibrantLight else category.composeColor
                    }
                    dynamicPaletteEnabled -> {
                        category.composeColor.copy(alpha = 0.22f)
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
                        if (dynamicPaletteEnabled) paletteColors.vibrantDark else Color.White
                    }
                    else -> MaterialTheme.colorScheme.onSurface
                }

                val targetBorderColor = when {
                    isHovered -> Color.White
                    else -> category.composeColor.copy(alpha = 0.6f)
                }

                val accessibleDescription = if (isHovered) {
                    "${category.name} category highlighted, release to drop"
                } else {
                    "${category.name} category, drop target"
                }

                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(xPx.roundToInt(), yPx.roundToInt())
                        }
                        .scale(chipScale * animatedProgress)
                        .shadow(chipElevation, shape = RoundedCornerShape(22.dp), clip = false)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onCategoryClick(category)
                        }
                        .semantics {
                            contentDescription = accessibleDescription
                            stateDescription = if (isHovered) "Highlighted" else "Available"
                        },
                    shape = RoundedCornerShape(22.dp),
                    color = containerColor,
                    border = BorderStroke(
                        width = if (isHovered) 2.5.dp else 1.dp,
                        color = targetBorderColor
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
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
                                    fontWeight = if (isHovered) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = if (isHovered) 13.5.sp else 12.sp
                                ),
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                        }
                    }
                }
            }
        }
    }
}

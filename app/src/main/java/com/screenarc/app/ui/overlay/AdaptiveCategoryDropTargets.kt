package com.screenarc.app.ui.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.DropTargetState
import kotlin.math.max
import kotlin.math.min

@Composable
fun AdaptiveCategoryDropTargets(
    categories: List<CategoryUiModel>,
    availableWidth: Dp,
    availableHeight: Dp,
    isDragging: Boolean,
    highlightedCategoryId: Long?,
    onCategoryDrop: (CategoryUiModel) -> Unit,
    onCategoryFocused: (CategoryUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val totalCategories = categories.size

    // Determine adaptive layout strategy based on count and dimensions
    val (targetSizeDp, usePagination, itemsPerPage) = remember(totalCategories, availableWidth, availableHeight) {
        when {
            totalCategories <= 4 -> {
                val size = if (availableWidth > 400.dp) 80.dp else 72.dp
                Triple(size, false, totalCategories)
            }
            totalCategories <= 8 -> {
                val size = if (availableWidth > 400.dp) 64.dp else 56.dp
                Triple(size, false, totalCategories)
            }
            totalCategories <= 12 -> {
                val size = if (availableWidth > 400.dp) 52.dp else 48.dp
                Triple(size, false, totalCategories)
            }
            else -> {
                // More than 12 categories: use paginated or compact tray
                val perPage = max(4, min(8, (availableWidth.value / 60).toInt()))
                Triple(48.dp, true, perPage)
            }
        }
    }

    var currentPage by remember { mutableIntStateOf(0) }
    val maxPage = remember(totalCategories, itemsPerPage) {
        if (itemsPerPage > 0) (totalCategories - 1) / itemsPerPage else 0
    }

    // Keep currentPage in bounds when categories change
    LaunchedEffect(maxPage) {
        if (currentPage > maxPage) {
            currentPage = max(0, maxPage)
        }
    }

    val visibleCategories = remember(categories, usePagination, currentPage, itemsPerPage) {
        if (!usePagination) {
            categories
        } else {
            val from = currentPage * itemsPerPage
            val to = min(categories.size, from + itemsPerPage)
            if (from < categories.size) categories.subList(from, to) else emptyList()
        }
    }

    // Previous highlighted tracking for haptic trigger
    var lastHighlightedId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(highlightedCategoryId) {
        if (highlightedCategoryId != lastHighlightedId) {
            if (highlightedCategoryId != null) {
                // Pointer entered
                triggerHapticFeedback(context, HapticType.ENTER)
                val cat = categories.find { it.id == highlightedCategoryId }
                cat?.let { onCategoryFocused(it) }
            } else if (lastHighlightedId != null) {
                // Pointer left
                triggerHapticFeedback(context, HapticType.LEAVE)
            }
            lastHighlightedId = highlightedCategoryId
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drop targets row / tray
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Previous page button if paginated
                if (usePagination && maxPage > 0) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous category page",
                            tint = if (currentPage > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Adaptive Category Chips / Drop Targets
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(visibleCategories, key = { it.id }) { category ->
                        val isHighlighted = highlightedCategoryId == category.id
                        val dropState = when {
                            isHighlighted -> DropTargetState.Highlighted
                            isDragging -> DropTargetState.Available
                            else -> DropTargetState.Normal
                        }

                        CategoryDropTargetItem(
                            category = category,
                            dropState = dropState,
                            baseSize = targetSizeDp,
                            onClick = {
                                triggerHapticFeedback(context, HapticType.DROP)
                                onCategoryDrop(category)
                            }
                        )
                    }
                }

                // Next page button if paginated
                if (usePagination && maxPage > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { if (currentPage < maxPage) currentPage++ },
                        enabled = currentPage < maxPage,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next category page",
                            tint = if (currentPage < maxPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        // Pagination indicator dots
        if (usePagination && maxPage > 0) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (p in 0..maxPage) {
                    Box(
                        modifier = Modifier
                            .size(if (p == currentPage) 8.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (p == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryDropTargetItem(
    category: CategoryUiModel,
    dropState: DropTargetState,
    baseSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHighlighted = dropState == DropTargetState.Highlighted
    val isAvailable = dropState == DropTargetState.Available

    // Smooth enlargement animation (150-250 ms)
    val targetScale by animateFloatAsState(
        targetValue = when {
            isHighlighted -> 1.42f
            isAvailable -> 1.05f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "TargetScale_${category.id}"
    )

    val targetElevation by animateDpAsState(
        targetValue = if (isHighlighted) 14.dp else 3.dp,
        animationSpec = tween(180),
        label = "TargetElevation_${category.id}"
    )

    val targetBorderColor by animateColorAsState(
        targetValue = when {
            isHighlighted -> Color.White
            isAvailable -> category.composeColor
            else -> category.composeColor.copy(alpha = 0.4f)
        },
        animationSpec = tween(180),
        label = "BorderColor_${category.id}"
    )

    val targetContainerColor by animateColorAsState(
        targetValue = when {
            isHighlighted -> category.composeColor
            isAvailable -> category.composeColor.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(180),
        label = "ContainerColor_${category.id}"
    )

    val accessibleDescription = when (dropState) {
        DropTargetState.Highlighted -> "${category.name} category highlighted, release to drop"
        DropTargetState.Available -> "${category.name} category, drop target"
        DropTargetState.Completed -> "Screenshot dropped into ${category.name}"
        else -> "${category.name} category"
    }

    Surface(
        modifier = modifier
            .scale(targetScale)
            .shadow(targetElevation, shape = RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = accessibleDescription
                stateDescription = if (isHighlighted) "Highlighted" else "Available"
            },
        shape = RoundedCornerShape(18.dp),
        color = targetContainerColor,
        border = BorderStroke(
            width = if (isHighlighted) 3.dp else 1.5.dp,
            color = targetBorderColor
        )
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = baseSize, max = baseSize + 28.dp)
                .height(baseSize + 16.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = if (isHighlighted) Color.White else category.composeColor,
                modifier = Modifier.size(maxOf(24.dp, baseSize * 0.48f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = category.getIcon(),
                        contentDescription = null,
                        tint = if (isHighlighted) category.composeColor else Color.White,
                        modifier = Modifier.size(maxOf(14.dp, baseSize * 0.28f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = category.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isHighlighted) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = if (isHighlighted) 11.sp else 10.sp
                ),
                color = if (isHighlighted) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            if (isHighlighted) {
                Text(
                    text = "Release to drop",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

enum class HapticType {
    ENTER,
    LEAVE,
    DROP,
    WARNING
}

fun triggerHapticFeedback(context: Context, type: HapticType) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            when (type) {
                HapticType.ENTER -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                HapticType.LEAVE -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                HapticType.DROP -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                HapticType.WARNING -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (type) {
                    HapticType.ENTER -> vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                    HapticType.LEAVE -> vibrator?.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE / 2))
                    HapticType.DROP -> vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                    HapticType.WARNING -> vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 35, 50, 45), -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        }
    } catch (e: Exception) {
        // ignore
    }
}

package com.screenarc.app.ui.overlay

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenarc.app.data.AppSettings
import com.screenarc.app.model.ArcDesignStyle
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.ScreenshotItem
import com.screenarc.app.ui.theme.ScreenArcTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FloatingOverlayView(
    screenshot: ScreenshotItem,
    categories: List<CategoryUiModel>,
    settings: AppSettings,
    suggestedCategoryId: Long? = null,
    categoryUsageMap: Map<Long, Int> = emptyMap(),
    onMoveToCategory: (CategoryUiModel) -> Unit,
    onUndoMove: ((CategoryUiModel) -> Unit)? = null,
    onDeleteScreenshot: () -> Unit,
    onShareScreenshot: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSearch: () -> Unit,
    onStyleChanged: ((ArcDesignStyle) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    // Order categories by suggested category + frequency of usage
    val orderedCategories = remember(categories, categoryUsageMap, suggestedCategoryId) {
        RadialMenuGeometry.orderCategories(categories, categoryUsageMap, suggestedCategoryId)
    }

    // Active design style
    var currentArcStyle by remember { mutableStateOf(settings.arcDesignStyle) }

    // UI state
    var isExpanded by remember { mutableStateOf(settings.autoExpandOnCapture) }
    var isOverflowExpanded by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var successCategory by remember { mutableStateOf<CategoryUiModel?>(null) }
    var showUndoSnackbar by remember { mutableStateOf(false) }
    var hoveredCategory by remember { mutableStateOf<CategoryUiModel?>(null) }
    var isDeleteHovered by remember { mutableStateOf(false) }
    var isShareHovered by remember { mutableStateOf(false) }
    var isMoreHovered by remember { mutableStateOf(false) }

    // Drag offset & snapping
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedDragX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "AnimatedDragX"
    )

    val animatedDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "AnimatedDragY"
    )

    // Bubble sizing
    val bubbleSize = settings.bubbleSizeDp.dp
    val arcRadius = settings.arcRadiusDp.dp
    val arcRadiusPx = with(density) { arcRadius.toPx() }
    val hitThresholdPx = with(density) { 56.dp.toPx() }

    // Auto-dismiss countdown timer
    var remainingSeconds by remember { mutableStateOf(settings.autoDismissSeconds) }
    var timerProgress by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(isDragging, isDismissing, successCategory, isDeleting, showUndoSnackbar) {
        if (isDragging || isDismissing || successCategory != null || isDeleting || showUndoSnackbar) return@LaunchedEffect
        val totalMillis = settings.autoDismissSeconds * 1000L
        val interval = 50L
        var elapsed = 0L

        while (elapsed < totalMillis) {
            delay(interval)
            elapsed += interval
            timerProgress = 1.0f - (elapsed.toFloat() / totalMillis.toFloat())
            remainingSeconds = (((totalMillis - elapsed) / 1000L) + 1).toInt()
        }

        isDismissing = true
        delay(250)
        onDismiss()
    }

    // Exit animation wrapper (~200-300ms)
    val exitScale by animateFloatAsState(
        targetValue = if (isDismissing) 0.0f else if (isDeleting) 0.1f else 1.0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "ExitScale"
    )

    val exitAlpha by animateFloatAsState(
        targetValue = if (isDismissing || isDeleting) 0.0f else 1.0f,
        animationSpec = tween(220, easing = LinearEasing),
        label = "ExitAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseGlow"
    )

    val radialLayout = remember(orderedCategories, arcRadiusPx, currentArcStyle, isRtl, isOverflowExpanded, suggestedCategoryId) {
        RadialMenuGeometry.calculateLayout(
            categories = orderedCategories,
            radiusPx = arcRadiusPx,
            style = currentArcStyle,
            isRtl = isRtl,
            suggestedCategoryId = suggestedCategoryId,
            isOverflowExpanded = isOverflowExpanded
        )
    }

    ScreenArcTheme(
        themePreset = settings.themePreset,
        uiDesignStyle = settings.uiDesignStyle
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .scale(exitScale)
                .drawBehind {
                    if (exitAlpha < 1f) {
                        drawRect(Color.Transparent)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight

            // Backdrop tap to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isDismissing && !isDeleting && !showUndoSnackbar) {
                            coroutineScope.launch {
                                isDismissing = true
                                delay(220)
                                onDismiss()
                            }
                        }
                    }
            )

            // Category targets layout
            ArcMenuLayout(
                categories = orderedCategories,
                isExpanded = isExpanded && !showUndoSnackbar,
                radiusDp = arcRadius,
                dragOffsetPx = Offset(animatedDragX, animatedDragY),
                paletteColors = screenshot.paletteColors,
                dynamicPaletteEnabled = settings.dynamicPaletteEnabled,
                hoveredCategoryId = hoveredCategory?.id,
                isDeleteHovered = isDeleteHovered,
                isShareHovered = isShareHovered,
                isMoreHovered = isMoreHovered,
                isOverflowExpanded = isOverflowExpanded,
                suggestedCategoryId = suggestedCategoryId,
                arcDesignStyle = currentArcStyle,
                onCategoryClick = { category ->
                    if (isDismissing || isDeleting || showUndoSnackbar) return@ArcMenuLayout
                    successCategory = category
                    triggerHapticFeedback(context, HapticType.DROP)
                    coroutineScope.launch {
                        delay(300)
                        onMoveToCategory(category)
                        showUndoSnackbar = true
                    }
                },
                onDeleteClick = {
                    if (isDismissing || isDeleting || showUndoSnackbar) return@ArcMenuLayout
                    isDeleting = true
                    triggerHapticFeedback(context, HapticType.DROP)
                    coroutineScope.launch {
                        delay(350)
                        onDeleteScreenshot()
                        isDismissing = true
                        delay(200)
                        onDismiss()
                    }
                },
                onShareClick = {
                    if (isDismissing || isDeleting || showUndoSnackbar) return@ArcMenuLayout
                    triggerHapticFeedback(context, HapticType.DROP)
                    coroutineScope.launch {
                        delay(150)
                        onShareScreenshot()
                        isDismissing = true
                        delay(200)
                        onDismiss()
                    }
                },
                onMoreClick = {
                    isOverflowExpanded = !isOverflowExpanded
                    triggerHapticFeedback(context, HapticType.ENTER)
                }
            )

            // Central Bubble & Drag Item
            val bubbleElevation by animateDpAsState(
                targetValue = if (isDragging) 16.dp else 6.dp,
                animationSpec = tween(150),
                label = "BubbleElevation"
            )

            // Shrink on drag over target pod for intuitive snapping feedback
            val thumbnailDragScale by animateFloatAsState(
                targetValue = if (hoveredCategory != null || isDeleteHovered || isShareHovered) 0.68f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "ThumbnailDragScale"
            )

            val currentHovered = hoveredCategory

            val bubbleBorderColor by animateColorAsState(
                targetValue = when {
                    isDeleting -> Color(0xFFE53935)
                    successCategory != null -> Color(0xFF4CAF50)
                    isDeleteHovered -> Color(0xFFE53935)
                    isShareHovered -> Color(0xFF29B6F6)
                    currentHovered != null -> {
                        if (settings.dynamicPaletteEnabled) screenshot.paletteColors.vibrant else currentHovered.composeColor
                    }
                    isDragging -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                },
                animationSpec = tween(150),
                label = "BubbleBorderColor"
            )

            if (!showUndoSnackbar) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(animatedDragX.roundToInt(), animatedDragY.roundToInt())
                        }
                        .scale(if (!isExpanded && !isDragging) pulseGlow else thumbnailDragScale)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    isExpanded = true
                                    triggerHapticFeedback(context, HapticType.ENTER)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount.x
                                    dragOffsetY += dragAmount.y

                                    val hoverTarget = RadialMenuGeometry.findHoverTarget(
                                        Offset(dragOffsetX, dragOffsetY),
                                        radialLayout,
                                        hitThresholdPx
                                    )

                                    val newHoveredCat = (hoverTarget as? HoverTarget.Category)?.category
                                    val newIsDelete = hoverTarget is HoverTarget.Delete
                                    val newIsShare = hoverTarget is HoverTarget.Share
                                    val newIsMore = hoverTarget is HoverTarget.More

                                    if (newIsDelete != isDeleteHovered) {
                                        isDeleteHovered = newIsDelete
                                        if (newIsDelete) triggerHapticFeedback(context, HapticType.WARNING)
                                    }
                                    if (newIsShare != isShareHovered) {
                                        isShareHovered = newIsShare
                                        if (newIsShare) triggerHapticFeedback(context, HapticType.ENTER)
                                    }
                                    if (newIsMore != isMoreHovered) {
                                        isMoreHovered = newIsMore
                                        if (newIsMore) {
                                            triggerHapticFeedback(context, HapticType.ENTER)
                                            isOverflowExpanded = true
                                        }
                                    }
                                    if (newHoveredCat != hoveredCategory) {
                                        if (newHoveredCat != null) triggerHapticFeedback(context, HapticType.ENTER)
                                        else if (hoveredCategory != null) triggerHapticFeedback(context, HapticType.LEAVE)
                                        hoveredCategory = newHoveredCat
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    if (isDeleteHovered) {
                                        isDeleting = true
                                        triggerHapticFeedback(context, HapticType.DROP)
                                        coroutineScope.launch {
                                            delay(350)
                                            onDeleteScreenshot()
                                            isDismissing = true
                                            delay(200)
                                            onDismiss()
                                        }
                                    } else if (isShareHovered) {
                                        triggerHapticFeedback(context, HapticType.DROP)
                                        coroutineScope.launch {
                                            delay(150)
                                            onShareScreenshot()
                                            isDismissing = true
                                            delay(200)
                                            onDismiss()
                                        }
                                    } else if (hoveredCategory != null) {
                                        val target = hoveredCategory!!
                                        successCategory = target
                                        triggerHapticFeedback(context, HapticType.DROP)
                                        coroutineScope.launch {
                                            delay(300)
                                            onMoveToCategory(target)
                                            showUndoSnackbar = true
                                        }
                                    } else {
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        hoveredCategory = null
                                        isDeleteHovered = false
                                        isShareHovered = false
                                        isMoreHovered = false
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    hoveredCategory = null
                                    isDeleteHovered = false
                                    isShareHovered = false
                                    isMoreHovered = false
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Auto-dismiss circular timer ring
                    if (!isDragging && successCategory == null && !isDeleting) {
                        CircularProgressIndicator(
                            progress = { timerProgress },
                            modifier = Modifier.size(bubbleSize + 16.dp),
                            color = if (settings.dynamicPaletteEnabled) screenshot.paletteColors.vibrant else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            strokeWidth = 3.dp
                        )
                    }

                    // Main Thumbnail Bubble Surface
                    Surface(
                        modifier = Modifier
                            .size(bubbleSize)
                            .clip(CircleShape)
                            .clickable {
                                isExpanded = !isExpanded
                                triggerHapticFeedback(context, HapticType.ENTER)
                            }
                            .semantics {
                                contentDescription = "Screenshot preview thumbnail bubble, tap to expand or drag to categorize"
                            },
                        shape = CircleShape,
                        shadowElevation = bubbleElevation,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(
                                    width = if (isDragging || successCategory != null || isDeleteHovered || isDeleting) 3.2.dp else 2.dp,
                                    color = bubbleBorderColor,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (screenshot.bitmapPreview != null) {
                                Image(
                                    bitmap = screenshot.bitmapPreview.asImageBitmap(),
                                    contentDescription = "Screenshot Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = screenshot.paletteColors.dominant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Screenshot",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            // Success checkmark overlay
                            AnimatedVisibility(
                                visible = successCategory != null,
                                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xCC4CAF50)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Categorized successfully",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            // Delete shred overlay
                            AnimatedVisibility(
                                visible = isDeleting,
                                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xCCE53935)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Deleted successfully",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            // Drag Handle hint
                            if (isExpanded && !isDragging && successCategory == null && !isDeleting) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Drag to categorize",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick dismiss 'X' button
                    if (isExpanded && !isDragging && successCategory == null && !isDeleting) {
                        Surface(
                            modifier = Modifier
                                .offset(x = (bubbleSize.value / 2).dp - 4.dp, y = -(bubbleSize.value / 2).dp + 4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable {
                                    coroutineScope.launch {
                                        isDismissing = true
                                        delay(180)
                                        onDismiss()
                                    }
                                },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Clear UNDO Snackbar after move
            AnimatedVisibility(
                visible = showUndoSnackbar && successCategory != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
            ) {
                var undoTimerSec by remember { mutableStateOf(4) }
                LaunchedEffect(showUndoSnackbar) {
                    while (undoTimerSec > 0) {
                        delay(1000)
                        undoTimerSec--
                    }
                    isDismissing = true
                    delay(200)
                    onDismiss()
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Moved to ${successCategory?.name}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.inverseOnSurface
                                )
                                Text(
                                    text = "Auto-closing in ${undoTimerSec}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        TextButton(
                            onClick = {
                                successCategory?.let { onUndoMove?.invoke(it) }
                                showUndoSnackbar = false
                                successCategory = null
                                triggerHapticFeedback(context, HapticType.DROP)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.inversePrimary
                            )
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("UNDO", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // Bottom Actions & Intuitive Arc Design Switcher
            if (!showUndoSnackbar) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Interactive Arc Design Switcher Pill (Orbit ✦ | Fan ⌓ | Dial 🝯)
                    if (isExpanded && !isDragging && successCategory == null && !isDeleting) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                            shadowElevation = 6.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ArcDesignStyle.values().forEach { style ->
                                    val isSelected = currentArcStyle == style
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                currentArcStyle = style
                                                onStyleChanged?.invoke(style)
                                                triggerHapticFeedback(context, HapticType.ENTER)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = when (style) {
                                                    ArcDesignStyle.ORBIT -> "✦ Orbit"
                                                    ArcDesignStyle.FAN -> "⌓ Fan"
                                                    ArcDesignStyle.DIAL -> "🝯 Dial"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Drag instruction & Open Search shortcut
                    AnimatedVisibility(
                        visible = isExpanded && !isDragging && successCategory == null && !isDeleting,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Drag or Tap Category to Move",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Open Search Action
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onOpenSearch()
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search & OCR in App",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Search & OCR",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

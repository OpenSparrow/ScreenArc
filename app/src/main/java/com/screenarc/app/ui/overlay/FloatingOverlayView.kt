package com.screenarc.app.ui.overlay

import android.content.Context
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.screenarc.app.data.AppSettings
import com.screenarc.app.model.ArcDesignStyle
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.ScreenshotItem
import com.screenarc.app.ui.theme.ScreenArcTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─── Overlay Phases ───────────────────────────────────────────────────────────
enum class OverlayPhase {
    /** Small corner circle thumbnail — tappable, non-blocking */
    BUBBLE,
    /** Full radial arc menu expanded for categorization */
    ARC_EXPANDED,
    /** After category selected — arc collapses, undo notification at corner */
    UNDO_NOTIFICATION
}

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
    onShareAndDeleteScreenshot: () -> Unit = {},
    onCopyScreenshot: () -> Unit = {},
    onDismiss: () -> Unit,
    onOpenSearch: () -> Unit,
    onStyleChanged: ((ArcDesignStyle) -> Unit)? = null,
    entities: List<com.screenarc.app.model.SmartEntity> = emptyList(),
    onPhaseChanged: (OverlayPhase, OverlayEdgeAnchor) -> Unit = { _, _ -> },
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

    // ─── Overlay phase & corner state ──────────────────────────────────────────
    // Always start as the small corner circle (BUBBLE); the radial arc expands
    // when the user taps or drags the circle.
    var currentCornerAnchor by remember {
        mutableStateOf(if (isRtl) OverlayEdgeAnchor.BOTTOM_LEFT else OverlayEdgeAnchor.BOTTOM_RIGHT)
    }
    var phase by remember { mutableStateOf(OverlayPhase.BUBBLE) }
    var successCategory by remember { mutableStateOf<CategoryUiModel?>(null) }
    var isDismissing by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var isOverflowExpanded by remember { mutableStateOf(false) }
    var hoveredCategory by remember { mutableStateOf<CategoryUiModel?>(null) }
    var isDeleteHovered by remember { mutableStateOf(false) }
    var isShareHovered by remember { mutableStateOf(false) }
    var isShareAndDeleteHovered by remember { mutableStateOf(false) }
    var isCopyHovered by remember { mutableStateOf(false) }
    var isMoreHovered by remember { mutableStateOf(false) }

    val isExpanded = phase == OverlayPhase.ARC_EXPANDED
    val showUndoNotification = phase == OverlayPhase.UNDO_NOTIFICATION

    // Gate visibility of the undo banner to ensure WindowManager's updateViewLayout IPC
    // finishes moving the window to Gravity.BOTTOM before Compose renders the banner
    var isUndoVisible by remember { mutableStateOf(false) }

    LaunchedEffect(phase, currentCornerAnchor) {
        onPhaseChanged(phase, currentCornerAnchor)
        if (phase == OverlayPhase.UNDO_NOTIFICATION) {
            delay(120)
            isUndoVisible = true
        } else {
            isUndoVisible = false
        }
    }

    // Drag offset & snapping (active in both BUBBLE and ARC_EXPANDED phases)
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val fluidEasing = remember { androidx.compose.animation.core.CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f) }

    val animatedDragX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = if (isDragging) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            tween(durationMillis = 180, easing = fluidEasing)
        },
        label = "AnimatedDragX"
    )

    val animatedDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = if (isDragging) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            tween(durationMillis = 180, easing = fluidEasing)
        },
        label = "AnimatedDragY"
    )

    // Fluid drag offset: during active touch, tracks finger 1:1; upon release, rapidly snaps to 0 in sync with arc collapse
    val currentDragX = if (isDragging) dragOffsetX else animatedDragX
    val currentDragY = if (isDragging) dragOffsetY else animatedDragY

    // Bubble sizing
    val bubbleSize = settings.bubbleSizeDp.dp
    val arcRadius = settings.arcRadiusDp.dp
    val arcRadiusPx = with(density) { arcRadius.toPx() }
    val hitThresholdPx = with(density) { 56.dp.toPx() }

    // Auto-dismiss countdown (only in BUBBLE phase; paused while user touches/expands arc, and resumes when dropped)
    val totalMillis = remember(settings.autoDismissSeconds) { settings.autoDismissSeconds * 1000L }
    var remainingMillis by remember { mutableStateOf(totalMillis) }
    var remainingSeconds by remember { mutableStateOf(settings.autoDismissSeconds) }
    var timerProgress by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(phase, isDragging, successCategory, isDeleting, showUndoNotification) {
        if (phase != OverlayPhase.BUBBLE || isDragging || isDismissing || successCategory != null || isDeleting || showUndoNotification) return@LaunchedEffect
        val interval = 50L
        // When resuming from an interaction, ensure at least 2.5s remaining so it doesn't instantly vanish
        if (remainingMillis < 2500L) {
            remainingMillis = 2500L
        }
        while (remainingMillis > 0L) {
            delay(interval)
            remainingMillis = (remainingMillis - interval).coerceAtLeast(0L)
            timerProgress = (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
            remainingSeconds = (((remainingMillis + 999L) / 1000L)).toInt()
        }

        isDismissing = true
    }

    // Dedicated dismissal lifecycle effect: guarantees onDismiss() and view removal always run
    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            delay(220)
            onDismiss()
        }
    }

    // Exit animation
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

    val handedness = when (currentCornerAnchor) {
        OverlayEdgeAnchor.BOTTOM_LEFT, OverlayEdgeAnchor.TOP_LEFT -> Handedness.LEFT_HANDED
        else -> if (isRtl) Handedness.LEFT_HANDED else Handedness.RIGHT_HANDED
    }

    val radialLayout = remember(orderedCategories, arcRadiusPx, currentArcStyle, isRtl, isOverflowExpanded, suggestedCategoryId, currentCornerAnchor) {
        RadialMenuGeometry.calculateLayout(
            categories = orderedCategories,
            radiusPx = arcRadiusPx,
            style = currentArcStyle,
            isRtl = isRtl,
            handedness = handedness,
            edgeAnchor = currentCornerAnchor,
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
                .then(if (isExpanded) Modifier.fillMaxSize() else Modifier.wrapContentSize())
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

            // Stable anchor point in window coordinates for the bubble & radial menu origin across all 4 corners
            val marginEndPx = with(density) { 20.dp.toPx() }
            val marginBottomPx = with(density) { 80.dp.toPx() }
            val marginTopPx = with(density) { 80.dp.toPx() }
            val bubbleRadiusPx = with(density) { bubbleSize.toPx() / 2f }

            val halfW: Float = with(density) { (screenWidth.toPx() / 2f) - bubbleRadiusPx - marginEndPx }
            val halfHBottom: Float = with(density) { (screenHeight.toPx() / 2f) - bubbleRadiusPx - marginBottomPx }
            val halfHTop: Float = with(density) { (screenHeight.toPx() / 2f) - bubbleRadiusPx - marginTopPx }

            val anchorOffset = remember(halfW, halfHBottom, halfHTop, currentCornerAnchor) {
                when (currentCornerAnchor) {
                    OverlayEdgeAnchor.BOTTOM_RIGHT -> Offset(halfW, halfHBottom)
                    OverlayEdgeAnchor.BOTTOM_LEFT -> Offset(-1f * halfW, halfHBottom)
                    OverlayEdgeAnchor.TOP_RIGHT -> Offset(halfW, -1f * halfHTop)
                    OverlayEdgeAnchor.TOP_LEFT -> Offset(-1f * halfW, -1f * halfHTop)
                    else -> Offset(halfW, halfHBottom)
                }
            }

            // ─── Phase: BUBBLE — small corner circle, non-blocking ────────────
            // Fluid backdrop opacity transition: avoids sudden black flash on layout expand
            val backdropAlpha by animateFloatAsState(
                targetValue = if (isExpanded && !showUndoNotification) 0.12f else 0.0f,
                animationSpec = tween(durationMillis = 220, easing = fluidEasing),
                label = "BackdropAlpha"
            )

            if (backdropAlpha > 0.005f) {
                // Subtle non-intrusive backdrop when arc is open (tap anywhere outside to collapse back to bubble)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = backdropAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isDismissing && !isDeleting && !showUndoNotification) {
                                // Collapse arc back to bubble and resume timeout
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                hoveredCategory = null
                                isDeleteHovered = false
                                isShareHovered = false
                                isShareAndDeleteHovered = false
                                isCopyHovered = false
                                isMoreHovered = false
                                phase = OverlayPhase.BUBBLE
                            }
                        }
                )
            }

            // ─── Arc Category Menu (only visible in ARC_EXPANDED) ────────────
            ArcMenuLayout(
                categories = orderedCategories,
                isExpanded = isExpanded && !showUndoNotification,
                radiusDp = arcRadius,
                dragOffsetPx = Offset(currentDragX, currentDragY),
                paletteColors = screenshot.paletteColors,
                dynamicPaletteEnabled = settings.dynamicPaletteEnabled,
                hoveredCategoryId = hoveredCategory?.id,
                isDeleteHovered = isDeleteHovered,
                isShareHovered = isShareHovered,
                isShareAndDeleteHovered = isShareAndDeleteHovered,
                isCopyHovered = isCopyHovered,
                isMoreHovered = isMoreHovered,
                isOverflowExpanded = isOverflowExpanded,
                suggestedCategoryId = suggestedCategoryId,
                handedness = handedness,
                edgeAnchor = currentCornerAnchor,
                arcDesignStyle = currentArcStyle,
                anchorOffset = anchorOffset,
                onCategoryClick = { category ->
                    if (isDismissing || isDeleting || showUndoNotification) return@ArcMenuLayout
                    successCategory = category
                    triggerHapticFeedback(context, HapticType.DROP)
                    coroutineScope.launch {
                        delay(300)
                        onMoveToCategory(category)
                        phase = OverlayPhase.UNDO_NOTIFICATION
                    }
                },
                onDeleteClick = {
                    if (isDismissing || isDeleting || showUndoNotification) return@ArcMenuLayout
                    isDeleting = true
                    triggerHapticFeedback(context, HapticType.DROP)
                    coroutineScope.launch {
                        delay(350)
                        onDeleteScreenshot()
                        isDismissing = true
                    }
                },
                onShareClick = {
                    if (isDismissing || isDeleting || showUndoNotification) return@ArcMenuLayout
                    triggerHapticFeedback(context, HapticType.DROP)
                    coroutineScope.launch {
                        delay(150)
                        onShareScreenshot()
                        isDismissing = true
                    }
                },
                onShareAndDeleteClick = {
                    if (isDismissing || isDeleting || showUndoNotification) return@ArcMenuLayout
                    isDeleting = true
                    triggerHapticFeedback(context, HapticType.DROP)
                    coroutineScope.launch {
                        delay(200)
                        onShareAndDeleteScreenshot()
                        isDismissing = true
                    }
                },
                onCopyClick = {
                    if (isDismissing || isDeleting || showUndoNotification) return@ArcMenuLayout
                    triggerHapticFeedback(context, HapticType.DROP)
                    coroutineScope.launch {
                        delay(150)
                        onCopyScreenshot()
                        isDismissing = true
                    }
                },
                onMoreClick = {
                    isOverflowExpanded = !isOverflowExpanded
                    triggerHapticFeedback(context, HapticType.ENTER)
                }
            )

            // ─── Central Bubble & Drag Item ───────────────────────────────────
            val bubbleElevation by animateDpAsState(
                targetValue = if (isDragging) 16.dp else 8.dp,
                animationSpec = tween(150),
                label = "BubbleElevation"
            )

            val thumbnailDragScale by animateFloatAsState(
                targetValue = if (hoveredCategory != null || isDeleteHovered || isShareHovered || isShareAndDeleteHovered || isCopyHovered) 0.68f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "ThumbnailDragScale"
            )

            val currentHovered = hoveredCategory

            val bubbleBorderColor by animateColorAsState(
                targetValue = when {
                    isDeleting -> Color(0xFFE53935)
                    successCategory != null -> Color(0xFF4CAF50)
                    isDeleteHovered -> Color(0xFFE53935)
                    isShareHovered -> Color(0xFF00E5FF)
                    isShareAndDeleteHovered -> Color(0xFFFFB300)
                    isCopyHovered -> Color(0xFF7C4DFF)
                    currentHovered != null -> {
                        if (settings.dynamicPaletteEnabled) screenshot.paletteColors.vibrant else currentHovered.composeColor
                    }
                    isDragging -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                },
                animationSpec = tween(150),
                label = "BubbleBorderColor"
            )

            val isBubblePhase = !isExpanded && !showUndoNotification

            if (!showUndoNotification) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isExpanded) {
                                Modifier.offset {
                                    val baseX = (anchorOffset.x + currentDragX).roundToInt()
                                    val baseY = (anchorOffset.y + currentDragY).roundToInt()
                                    IntOffset(baseX, baseY)
                                }
                            } else {
                                Modifier
                            }
                        )
                        .scale(
                            when {
                                isBubblePhase && !isDragging -> pulseGlow
                                else -> thumbnailDragScale
                            }
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    if (phase == OverlayPhase.BUBBLE) {
                                        phase = OverlayPhase.ARC_EXPANDED
                                    }
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
                                    val newIsShareAndDelete = hoverTarget is HoverTarget.ShareAndDelete
                                    val newIsCopy = hoverTarget is HoverTarget.Copy
                                    val newIsMore = hoverTarget is HoverTarget.More

                                    if (newIsDelete != isDeleteHovered) {
                                        isDeleteHovered = newIsDelete
                                        if (newIsDelete) triggerHapticFeedback(context, HapticType.WARNING)
                                    }
                                    if (newIsShare != isShareHovered) {
                                        isShareHovered = newIsShare
                                        if (newIsShare) triggerHapticFeedback(context, HapticType.ENTER)
                                    }
                                    if (newIsShareAndDelete != isShareAndDeleteHovered) {
                                        isShareAndDeleteHovered = newIsShareAndDelete
                                        if (newIsShareAndDelete) triggerHapticFeedback(context, HapticType.WARNING)
                                    }
                                    if (newIsCopy != isCopyHovered) {
                                        isCopyHovered = newIsCopy
                                        if (newIsCopy) triggerHapticFeedback(context, HapticType.ENTER)
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
                                        }
                                    } else if (isShareHovered) {
                                        triggerHapticFeedback(context, HapticType.DROP)
                                        coroutineScope.launch {
                                            delay(150)
                                            onShareScreenshot()
                                            isDismissing = true
                                        }
                                    } else if (isShareAndDeleteHovered) {
                                        isDeleting = true
                                        triggerHapticFeedback(context, HapticType.DROP)
                                        coroutineScope.launch {
                                            delay(200)
                                            onShareAndDeleteScreenshot()
                                            isDismissing = true
                                        }
                                    } else if (isCopyHovered) {
                                        triggerHapticFeedback(context, HapticType.DROP)
                                        coroutineScope.launch {
                                            delay(150)
                                            onCopyScreenshot()
                                            isDismissing = true
                                        }
                                    } else if (hoveredCategory != null) {
                                        val target = hoveredCategory!!
                                        successCategory = target
                                        triggerHapticFeedback(context, HapticType.DROP)
                                        coroutineScope.launch {
                                            delay(300)
                                            onMoveToCategory(target)
                                            phase = OverlayPhase.UNDO_NOTIFICATION
                                        }
                                    } else {
                                        // Dropped at corner or neutral space: snap to nearest corner
                                        val dropFingerOffset = Offset(anchorOffset.x + dragOffsetX, anchorOffset.y + dragOffsetY)
                                        currentCornerAnchor = RadialMenuGeometry.findNearestCorner(dropFingerOffset)
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        hoveredCategory = null
                                        isDeleteHovered = false
                                        isShareHovered = false
                                        isShareAndDeleteHovered = false
                                        isCopyHovered = false
                                        isMoreHovered = false
                                        phase = OverlayPhase.BUBBLE
                                        remainingMillis = 2500L
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    hoveredCategory = null
                                    isDeleteHovered = false
                                    isShareHovered = false
                                    isShareAndDeleteHovered = false
                                    isCopyHovered = false
                                    isMoreHovered = false
                                    phase = OverlayPhase.BUBBLE
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Auto-dismiss circular timer ring (only in non-drag states)
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
                                if (!isDismissing && !isDeleting) {
                                    if (phase == OverlayPhase.BUBBLE) {
                                        // Expand arc
                                        phase = OverlayPhase.ARC_EXPANDED
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    } else {
                                        // Collapse arc back to bubble
                                        phase = OverlayPhase.BUBBLE
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    }
                                    triggerHapticFeedback(context, HapticType.ENTER)
                                }
                            }
                            .semantics {
                                contentDescription = if (phase == OverlayPhase.BUBBLE)
                                    "Screenshot thumbnail — tap to open arc menu"
                                else
                                    "Screenshot thumbnail — tap to collapse arc menu"
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

                            // Drag Handle hint — only when arc is open and idle
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

                            // Tap-to-open hint — only in BUBBLE phase
                            if (phase == OverlayPhase.BUBBLE && !isDragging && successCategory == null && !isDeleting) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = "Tap to open",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick dismiss 'X' button — only when arc is expanded
                    if (isExpanded && !isDragging && successCategory == null && !isDeleting) {
                        Surface(
                            modifier = Modifier
                                .offset(x = (bubbleSize.value / 2).dp - 4.dp, y = -(bubbleSize.value / 2).dp + 4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable {
                                    isDismissing = true
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

            // ─── Bottom Actions & Arc Switcher (only when arc is expanded) ───
            if (isExpanded && !showUndoNotification) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Detected Smart Entities & Action Chips row
                    if (entities.isNotEmpty() && !isDragging && successCategory == null && !isDeleting) {
                        com.screenarc.app.ui.components.SmartEntityChipsRow(
                            entities = entities,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }



                    // Drag instruction & Open Search shortcut
                    AnimatedVisibility(
                        visible = !isDragging && successCategory == null && !isDeleting,
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

            // ─── Undo Notification (bottom center, non-blocking) ──────────────
            // Appears after categorization; user can still interact with the phone.
            // The overlay window stays up but is FLAG_NOT_TOUCH_MODAL so touches
            // outside this element pass through to apps underneath.
            AnimatedVisibility(
                visible = isUndoVisible && successCategory != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(180)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(180)
                ) + fadeOut(animationSpec = tween(150)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                var undoTimerSec by remember { mutableStateOf(5) }
                LaunchedEffect(isUndoVisible) {
                    if (!isUndoVisible) return@LaunchedEffect
                    undoTimerSec = 5
                    while (undoTimerSec > 0) {
                        delay(1000)
                        undoTimerSec--
                    }
                    isDismissing = true
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
                                    text = "Dismissing in ${undoTimerSec}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        TextButton(
                            onClick = {
                                successCategory?.let { onUndoMove?.invoke(it) }
                                // After undo, go back to BUBBLE phase
                                isUndoVisible = false
                                successCategory = null
                                phase = OverlayPhase.BUBBLE
                                remainingMillis = 2500L
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
        }
    }
}

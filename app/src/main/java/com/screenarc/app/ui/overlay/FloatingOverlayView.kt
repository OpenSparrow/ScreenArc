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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenarc.app.MainActivity
import com.screenarc.app.data.AppSettings
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.ScreenshotItem
import com.screenarc.app.ui.theme.ScreenArcTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun FloatingOverlayView(
    screenshot: ScreenshotItem,
    categories: List<CategoryUiModel>,
    settings: AppSettings,
    onMoveToCategory: (CategoryUiModel) -> Unit,
    onDismiss: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // UI state
    var isExpanded by remember { mutableStateOf(settings.autoExpandOnCapture) }
    var isDismissing by remember { mutableStateOf(false) }
    var successCategory by remember { mutableStateOf<CategoryUiModel?>(null) }
    var hoveredCategory by remember { mutableStateOf<CategoryUiModel?>(null) }

    // Drag offset
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

    // Use adaptive tray if there are many categories (> 8)
    val useAdaptiveTray = categories.size > 8

    // Auto-dismiss countdown
    var remainingSeconds by remember { mutableStateOf(settings.autoDismissSeconds) }
    var timerProgress by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(isDragging, isDismissing, successCategory) {
        if (isDragging || isDismissing || successCategory != null) return@LaunchedEffect
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

    // Check hit test for categories during drag in radial mode
    fun checkRadialHoverCategory(currentDragOffset: Offset): CategoryUiModel? {
        val total = categories.size
        val angleStep = if (total > 1) (2 * PI / total).toFloat() else 0f
        val startAngle = -PI.toFloat() / 2f

        categories.forEachIndexed { index, category ->
            val angle = startAngle + index * angleStep
            val targetX = arcRadiusPx * cos(angle)
            val targetY = arcRadiusPx * sin(angle)

            val dx = currentDragOffset.x - targetX
            val dy = currentDragOffset.y - targetY
            val distance = sqrt(dx * dx + dy * dy)

            if (distance <= hitThresholdPx) {
                return category
            }
        }
        return null
    }

    // Exit animation wrapper
    val exitScale by animateFloatAsState(
        targetValue = if (isDismissing) 0.0f else 1.0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "ExitScale"
    )

    val exitAlpha by animateFloatAsState(
        targetValue = if (isDismissing) 0.0f else 1.0f,
        animationSpec = tween(230, easing = LinearEasing),
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

    ScreenArcTheme {
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
                        if (!isDismissing) {
                            coroutineScope.launch {
                                isDismissing = true
                                delay(220)
                                onDismiss()
                            }
                        }
                    }
            )

            // Category targets layout: Radial arc if <= 8 categories, otherwise adaptive layout
            if (!useAdaptiveTray) {
                ArcMenuLayout(
                    categories = categories,
                    isExpanded = isExpanded,
                    radiusDp = arcRadius,
                    dragOffsetPx = Offset(animatedDragX, animatedDragY),
                    paletteColors = screenshot.paletteColors,
                    dynamicPaletteEnabled = settings.dynamicPaletteEnabled,
                    hoveredCategoryId = hoveredCategory?.id,
                    onCategoryClick = { category ->
                        if (isDismissing) return@ArcMenuLayout
                        successCategory = category
                        triggerHapticFeedback(context, HapticType.DROP)
                        coroutineScope.launch {
                            delay(350)
                            onMoveToCategory(category)
                            isDismissing = true
                            delay(220)
                            onDismiss()
                        }
                    }
                )
            } else {
                // Adaptive bottom category drop tray when many categories exist
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp)
                ) {
                    AdaptiveCategoryDropTargets(
                        categories = categories,
                        availableWidth = screenWidth - 32.dp,
                        availableHeight = 120.dp,
                        isDragging = isDragging,
                        highlightedCategoryId = hoveredCategory?.id,
                        onCategoryDrop = { category ->
                            if (isDismissing) return@AdaptiveCategoryDropTargets
                            successCategory = category
                            triggerHapticFeedback(context, HapticType.DROP)
                            coroutineScope.launch {
                                delay(350)
                                onMoveToCategory(category)
                                isDismissing = true
                                delay(220)
                                onDismiss()
                            }
                        },
                        onCategoryFocused = { category ->
                            hoveredCategory = category
                        }
                    )
                }
            }

            // Central Bubble & Drag Item
            val bubbleElevation by animateDpAsState(
                targetValue = if (isDragging) 14.dp else 6.dp,
                animationSpec = tween(150),
                label = "BubbleElevation"
            )

            val currentHovered = hoveredCategory

            val bubbleBorderColor by animateColorAsState(
                targetValue = when {
                    successCategory != null -> Color(0xFF4CAF50)
                    currentHovered != null -> {
                        if (settings.dynamicPaletteEnabled) screenshot.paletteColors.vibrant else MaterialTheme.colorScheme.primary
                    }
                    isDragging -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                },
                animationSpec = tween(150),
                label = "BubbleBorderColor"
            )

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(animatedDragX.roundToInt(), animatedDragY.roundToInt())
                    }
                    .scale(if (!isExpanded && !isDragging) pulseGlow else 1.0f)
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

                                if (!useAdaptiveTray) {
                                    val newHovered = checkRadialHoverCategory(Offset(dragOffsetX, dragOffsetY))
                                    if (newHovered != hoveredCategory) {
                                        if (newHovered != null) {
                                            triggerHapticFeedback(context, HapticType.ENTER)
                                        } else if (hoveredCategory != null) {
                                            triggerHapticFeedback(context, HapticType.LEAVE)
                                        }
                                        hoveredCategory = newHovered
                                    }
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                val target = hoveredCategory
                                if (target != null) {
                                    successCategory = target
                                    triggerHapticFeedback(context, HapticType.DROP)
                                    coroutineScope.launch {
                                        delay(400)
                                        onMoveToCategory(target)
                                        isDismissing = true
                                        delay(220)
                                        onDismiss()
                                    }
                                } else {
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    hoveredCategory = null
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                hoveredCategory = null
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Auto-dismiss circular timer ring
                if (!isDragging && successCategory == null) {
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
                            contentDescription = "Floating screenshot thumbnail bubble"
                        },
                    shape = CircleShape,
                    shadowElevation = bubbleElevation,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = if (isDragging || successCategory != null) 3.dp else 2.dp,
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

                        // Drag Handle hint
                        if (isExpanded && !isDragging && successCategory == null) {
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
                if (isExpanded && !isDragging && successCategory == null) {
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

            // Bottom Actions & Quick Search Action Pill
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success banner
                AnimatedVisibility(
                    visible = successCategory != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Moved to ${successCategory?.name}!",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Drag instruction & Open Search shortcut (Section 4 requirement: Open Search action in overlay)
                AnimatedVisibility(
                    visible = isExpanded && !isDragging && successCategory == null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
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
                                    text = "Drag or tap category",
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

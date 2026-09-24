package com.screenarc.app.ui.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenarc.app.data.AppSettings
import com.screenarc.app.model.Category
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
    categories: List<Category>,
    settings: AppSettings,
    onMoveToCategory: (Category) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // UI state
    var isExpanded by remember { mutableStateOf(settings.autoExpandOnCapture) }
    var isDismissing by remember { mutableStateOf(false) }
    var successCategory by remember { mutableStateOf<Category?>(null) }
    var hoveredCategory by remember { mutableStateOf<Category?>(null) }

    // Drag offset
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Smooth return to center animation when drag is released outside
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
    val hitThresholdPx = with(density) { 54.dp.toPx() }

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

        // Timeout reached -> smooth dismiss
        isDismissing = true
        delay(300)
        onDismiss()
    }

    fun triggerHaptic() {
        if (!settings.hapticFeedbackEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(20)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Check hit test for categories during drag
    fun checkHoverCategory(currentDragOffset: Offset): Category? {
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
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "ExitScale"
    )

    val exitAlpha by animateFloatAsState(
        targetValue = if (isDismissing) 0.0f else 1.0f,
        animationSpec = tween(250, easing = LinearEasing),
        label = "ExitAlpha"
    )

    // Infinite breathing / pulse animation for initial state
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseGlow"
    )

    ScreenArcTheme {
        Box(
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
            // Backdrop tap to dismiss overlay
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
                                delay(250)
                                onDismiss()
                            }
                        }
                    }
            )

            // Radial Arc Menu
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
                    triggerHaptic()
                    coroutineScope.launch {
                        delay(350)
                        onMoveToCategory(category)
                        isDismissing = true
                        delay(250)
                        onDismiss()
                    }
                }
            )

            // Center Bubble / Thumbnail & Drag Source
            val bubbleElevation by animateDpAsState(
                targetValue = if (isDragging) 12.dp else 6.dp,
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
                                triggerHaptic()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                                dragOffsetY += dragAmount.y

                                val newHovered = checkHoverCategory(Offset(dragOffsetX, dragOffsetY))
                                if (newHovered != hoveredCategory) {
                                    hoveredCategory = newHovered
                                    if (newHovered != null) {
                                        triggerHaptic()
                                    }
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                val target = hoveredCategory
                                if (target != null) {
                                    successCategory = target
                                    triggerHaptic()
                                    coroutineScope.launch {
                                        delay(400)
                                        onMoveToCategory(target)
                                        isDismissing = true
                                        delay(250)
                                        onDismiss()
                                    }
                                } else {
                                    // Reset to center with spring
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
                // Timer Progress Circle (when not dragging)
                if (!isDragging && successCategory == null) {
                    CircularProgressIndicator(
                        progress = { timerProgress },
                        modifier = Modifier.size(bubbleSize + 16.dp),
                        color = if (settings.dynamicPaletteEnabled) screenshot.paletteColors.vibrant else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        strokeWidth = 3.dp
                    )
                }

                // Main Floating Thumbnail Surface
                Surface(
                    modifier = Modifier
                        .size(bubbleSize)
                        .clip(CircleShape)
                        .clickable {
                            isExpanded = !isExpanded
                            triggerHaptic()
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

                        // Success Overlay with Checkmark
                        androidx.compose.animation.AnimatedVisibility(
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
                                    contentDescription = "Success",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Drag hint overlay icon if expanded and not dragging
                        if (isExpanded && !isDragging && successCategory == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f)),
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

                // Small quick dismiss 'X' button
                if (isExpanded && !isDragging && successCategory == null) {
                    Surface(
                        modifier = Modifier
                            .offset(x = (bubbleSize.value / 2).dp - 4.dp, y = -(bubbleSize.value / 2).dp + 4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable {
                                coroutineScope.launch {
                                    isDismissing = true
                                    delay(200)
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

            // Material 3 Toast / SnackBar Banner at Top or Bottom during drag/success
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

                // Drag instruction chip
                AnimatedVisibility(
                    visible = isExpanded && !isDragging && successCategory == null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
                                text = "Drag bubble or tap chip to categorize",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

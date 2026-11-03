package com.screenarc.app.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.screenarc.app.RedactionEditorActivity
import com.screenarc.app.model.OcrBlock
import com.screenarc.app.model.ScreenshotUiModel
import com.screenarc.app.ocr.OcrCoordinateTransformer
import com.screenarc.app.util.FreehandPath
import com.screenarc.app.util.PrivacyRedactor
import com.screenarc.app.util.RedactionItem
import com.screenarc.app.util.RedactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OcrOverlayImageViewer(
    screenshot: ScreenshotUiModel,
    blocks: List<OcrBlock> = screenshot.blocks,
    allScreenshots: List<ScreenshotUiModel> = listOf(screenshot),
    searchQuery: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentIndex by remember(screenshot.uri, allScreenshots) {
        val idx = allScreenshots.indexOfFirst { it.uri == screenshot.uri }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }

    val currentScreenshot = if (allScreenshots.isNotEmpty() && currentIndex in allScreenshots.indices) {
        allScreenshots[currentIndex]
    } else {
        screenshot
    }

    val currentBlocks = if (currentScreenshot.uri == screenshot.uri && blocks.isNotEmpty()) {
        blocks
    } else {
        currentScreenshot.blocks
    }

    var showBoxes by remember { mutableStateOf(true) }
    var rawImageWidth by remember(currentScreenshot.uri) { mutableIntStateOf(0) }
    var rawImageHeight by remember(currentScreenshot.uri) { mutableIntStateOf(0) }
    var isVisible by remember { mutableStateOf(false) }

    // Entrance animation
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val entranceScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "EntranceScale"
    )

    val entranceAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "EntranceAlpha"
    )

    // Accurate true pixel dimensions from file header
    LaunchedEffect(currentScreenshot.uri) {
        withContext(Dispatchers.IO) {
            val dims = OcrCoordinateTransformer.getImageDimensions(context, currentScreenshot.contentUri)
            if (dims != null && dims.first > 0 && dims.second > 0) {
                rawImageWidth = dims.first
                rawImageHeight = dims.second
            }
        }
    }

    // Zoom & Pan transformation states
    var zoomScale by remember(currentScreenshot.uri) { mutableFloatStateOf(1f) }
    var panOffset by remember(currentScreenshot.uri) { mutableStateOf(Offset.Zero) }

    // Interactive tapped block feedback
    var tappedBlockText by remember(currentScreenshot.uri) { mutableStateOf<String?>(null) }
    var lastTappedBlock by remember(currentScreenshot.uri) { mutableStateOf<OcrBlock?>(null) }

    // Redaction and privacy tool state
    var isRedactionActive by remember(currentScreenshot.uri) { mutableStateOf(false) }
    var redactionMode by remember { mutableStateOf(RedactionType.BLACKOUT) }
    var redactedBlocks by remember(currentScreenshot.uri) { mutableStateOf<Map<OcrBlock, RedactionType>>(emptyMap()) }
    var freehandBrushActive by remember(currentScreenshot.uri) { mutableStateOf(false) }
    var currentStrokePoints by remember(currentScreenshot.uri) { mutableStateOf<List<Offset>>(emptyList()) }
    var completedStrokes by remember(currentScreenshot.uri) { mutableStateOf<List<FreehandPath>>(emptyList()) }
    var isSavingRedacted by remember { mutableStateOf(false) }

    var dragAccumulator by remember(currentScreenshot.uri) { mutableFloatStateOf(0f) }

    fun navigateNext() {
        if (currentIndex < allScreenshots.size - 1) {
            currentIndex++
            zoomScale = 1f
            panOffset = Offset.Zero
            tappedBlockText = null
            lastTappedBlock = null
        }
    }

    fun navigatePrevious() {
        if (currentIndex > 0) {
            currentIndex--
            zoomScale = 1f
            panOffset = Offset.Zero
            tappedBlockText = null
            lastTappedBlock = null
        }
    }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        if (!freehandBrushActive) {
            val newScale = (zoomScale * zoomChange).coerceIn(1f, 6f)
            zoomScale = newScale
            if (newScale > 1f) {
                panOffset += offsetChange
            } else {
                panOffset = Offset.Zero
            }
        }
    }

    // Auto-dismiss tapped feedback
    LaunchedEffect(tappedBlockText) {
        if (tappedBlockText != null) {
            delay(3500)
            tappedBlockText = null
        }
    }

    // Filter matching blocks if search query is provided
    val query = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
    val matchCount = remember(currentBlocks, query) {
        if (query == null) 0
        else currentBlocks.count { it.text.contains(query, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = entranceScale
                    scaleY = entranceScale
                    alpha = entranceAlpha
                },
            color = Color(0xFF0A0A0A)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val containerWidth = constraints.maxWidth.toFloat()
                val containerHeight = constraints.maxHeight.toFloat()

                // Calculate image dimensions from decoded file or fall back to OCR block bounds
                val effectiveImageWidth = if (rawImageWidth > 0) {
                    rawImageWidth.toFloat()
                } else {
                    (currentBlocks.maxOfOrNull { it.right ?: 1080 } ?: 1080).toFloat()
                }

                val effectiveImageHeight = if (rawImageHeight > 0) {
                    rawImageHeight.toFloat()
                } else {
                    (currentBlocks.maxOfOrNull { it.bottom ?: 1920 } ?: 1920).toFloat()
                }

                val fitParams = remember(containerWidth, containerHeight, effectiveImageWidth, effectiveImageHeight) {
                    OcrCoordinateTransformer.computeFitParams(
                        containerWidth = containerWidth,
                        containerHeight = containerHeight,
                        imageWidth = effectiveImageWidth,
                        imageHeight = effectiveImageHeight
                    )
                }

                // Background gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF141414), Color(0xFF050505)),
                                radius = containerWidth * 0.85f
                            )
                        )
                )

                // Main Interactive Layer (Zoomable, Pannable, Tappable & Horizontal Swipe)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                        .transformable(state = transformState)
                        .pointerInput(currentScreenshot.uri, zoomScale, allScreenshots.size, isRedactionActive) {
                            if (zoomScale <= 1.08f && allScreenshots.size > 1 && !freehandBrushActive) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dragAmount ->
                                        dragAccumulator += dragAmount
                                    },
                                    onDragEnd = {
                                        if (dragAccumulator < -50f) {
                                            navigateNext()
                                        } else if (dragAccumulator > 50f) {
                                            navigatePrevious()
                                        }
                                        dragAccumulator = 0f
                                    },
                                    onDragCancel = {
                                        dragAccumulator = 0f
                                    }
                                )
                            }
                        }
                        .pointerInput(currentBlocks, fitParams, zoomScale, panOffset) {
                            detectTapGestures(
                                onDoubleTap = { tapCenter ->
                                    if (zoomScale > 1.2f) {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        zoomScale = 2.5f
                                        val centerX = containerWidth / 2f
                                        val centerY = containerHeight / 2f
                                        panOffset = Offset(
                                            (centerX - tapCenter.x) * 1.5f,
                                            (centerY - tapCenter.y) * 1.5f
                                        )
                                    }
                                },
                                onTap = { tapOffset ->
                                    val hitBlock = OcrCoordinateTransformer.findBlockAtPoint(
                                        touchX = tapOffset.x,
                                        touchY = tapOffset.y,
                                        blocks = currentBlocks,
                                        fitParams = fitParams
                                    )
                                    if (hitBlock != null) {
                                        if (isRedactionActive) {
                                            val current = redactedBlocks[hitBlock]
                                            redactedBlocks = if (current != null) {
                                                redactedBlocks - hitBlock
                                            } else {
                                                redactedBlocks + (hitBlock to redactionMode)
                                            }
                                        } else {
                                            lastTappedBlock = hitBlock
                                            tappedBlockText = hitBlock.text
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Recognized Text", hitBlock.text))
                                            Toast.makeText(context, "Copied text!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    // Full Resolution Crystal Clear Image Display
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentScreenshot.contentUri)
                            .size(coil.size.Size.ORIGINAL)
                            .crossfade(false)
                            .build(),
                        contentDescription = currentScreenshot.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { state ->
                            val d = state.result.drawable
                            if (rawImageWidth <= 0 && d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                                rawImageWidth = d.intrinsicWidth
                                rawImageHeight = d.intrinsicHeight
                            }
                        }
                    )

                    // Bounding Boxes Overlay Canvas
                    if (showBoxes && currentBlocks.isNotEmpty() && fitParams.scale > 0f) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            currentBlocks.forEach { block ->
                                val rect = OcrCoordinateTransformer.transformBlockToViewRect(block, fitParams)
                                if (rect != null && rect.width > 0 && rect.height > 0) {
                                    val isMatch = query != null && block.text.contains(query, ignoreCase = true)
                                    val isTapped = lastTappedBlock == block
                                    val cornerRadius = 4.dp.toPx()

                                    val isRedacted = redactedBlocks.containsKey(block)
                                    val redactionType = redactedBlocks[block]

                                    when {
                                        isRedacted -> {
                                            if (redactionType == RedactionType.BLACKOUT) {
                                                drawRoundRect(
                                                    color = Color.Black,
                                                    topLeft = Offset(rect.left, rect.top),
                                                    size = Size(rect.width, rect.height),
                                                    cornerRadius = CornerRadius(cornerRadius)
                                                )
                                            } else {
                                                drawRoundRect(
                                                    color = Color(0xDD37474F),
                                                    topLeft = Offset(rect.left, rect.top),
                                                    size = Size(rect.width, rect.height),
                                                    cornerRadius = CornerRadius(cornerRadius)
                                                )
                                                drawRoundRect(
                                                    color = Color(0xFF90A4AE),
                                                    topLeft = Offset(rect.left, rect.top),
                                                    size = Size(rect.width, rect.height),
                                                    cornerRadius = CornerRadius(cornerRadius),
                                                    style = Stroke(width = 1.5.dp.toPx())
                                                )
                                            }
                                        }
                                        isTapped -> {
                                            drawRoundRect(
                                                color = Color(0x4400E676),
                                                topLeft = Offset(rect.left - 3f, rect.top - 3f),
                                                size = Size(rect.width + 6f, rect.height + 6f),
                                                cornerRadius = CornerRadius(cornerRadius + 3f)
                                            )
                                            drawRoundRect(
                                                color = Color(0xCC00E676),
                                                topLeft = Offset(rect.left, rect.top),
                                                size = Size(rect.width, rect.height),
                                                cornerRadius = CornerRadius(cornerRadius),
                                                style = Stroke(width = 2.5.dp.toPx())
                                            )
                                            drawRoundRect(
                                                color = Color(0x3300E676),
                                                topLeft = Offset(rect.left, rect.top),
                                                size = Size(rect.width, rect.height),
                                                cornerRadius = CornerRadius(cornerRadius)
                                            )
                                        }
                                        isMatch -> {
                                            drawRoundRect(
                                                color = Color(0x55FFC107),
                                                topLeft = Offset(rect.left - 2f, rect.top - 2f),
                                                size = Size(rect.width + 4f, rect.height + 4f),
                                                cornerRadius = CornerRadius(cornerRadius + 2f)
                                            )
                                            drawRoundRect(
                                                color = Color(0xFFFFD54F),
                                                topLeft = Offset(rect.left, rect.top),
                                                size = Size(rect.width, rect.height),
                                                cornerRadius = CornerRadius(cornerRadius),
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                            drawRoundRect(
                                                color = Color(0x44FFC107),
                                                topLeft = Offset(rect.left, rect.top),
                                                size = Size(rect.width, rect.height),
                                                cornerRadius = CornerRadius(cornerRadius)
                                            )
                                        }
                                        else -> {
                                            drawRoundRect(
                                                color = Color(0x222196F3),
                                                topLeft = Offset(rect.left, rect.top),
                                                size = Size(rect.width, rect.height),
                                                cornerRadius = CornerRadius(cornerRadius)
                                            )
                                            drawRoundRect(
                                                color = Color(0x882196F3),
                                                topLeft = Offset(rect.left, rect.top),
                                                size = Size(rect.width, rect.height),
                                                cornerRadius = CornerRadius(cornerRadius),
                                                style = Stroke(width = 1.dp.toPx())
                                            )
                                        }
                                    }
                                }
                            }

                            // Freehand strokes
                            completedStrokes.forEach { stroke ->
                                if (stroke.points.size >= 2) {
                                    val path = Path()
                                    path.moveTo(stroke.points[0].x, stroke.points[0].y)
                                    for (i in 1 until stroke.points.size) {
                                        path.lineTo(stroke.points[i].x, stroke.points[i].y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.Black,
                                        style = Stroke(
                                            width = stroke.strokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                            if (currentStrokePoints.size >= 2) {
                                val currentPath = Path()
                                currentPath.moveTo(currentStrokePoints[0].x, currentStrokePoints[0].y)
                                for (i in 1 until currentStrokePoints.size) {
                                    currentPath.lineTo(currentStrokePoints[i].x, currentStrokePoints[i].y)
                                }
                                drawPath(
                                    path = currentPath,
                                    color = Color.Black,
                                    style = Stroke(
                                        width = 36f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }

                // Floating Previous Button on left edge
                if (currentIndex > 0 && zoomScale <= 1.08f) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(46.dp)
                            .clip(CircleShape)
                    ) {
                        IconButton(onClick = { navigatePrevious() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous image",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Floating Next Button on right edge
                if (currentIndex < allScreenshots.size - 1 && zoomScale <= 1.08f) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(46.dp)
                            .clip(CircleShape)
                    ) {
                        IconButton(onClick = { navigateNext() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next image",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Top gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xDD000000), Color.Transparent)
                            )
                        )
                )

                // Bottom gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xDD000000))
                            )
                        )
                )

                // Top Floating Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentScreenshot.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.18f)
                            ) {
                                val counterText = if (allScreenshots.size > 1) {
                                    "${currentIndex + 1} / ${allScreenshots.size}  •  ${currentBlocks.size} text blocks"
                                } else {
                                    "${currentBlocks.size} text blocks"
                                }
                                Text(
                                    text = counterText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (query != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFFD54F).copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = "$matchCount matching",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFFFD54F),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Edit & Annotate Screenshot Button (Launches RedactionEditorActivity)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                    ) {
                        IconButton(onClick = {
                            val editIntent = RedactionEditorActivity.newIntent(context, currentScreenshot, mode = "edit")
                            context.startActivity(editIntent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit and Save Copy",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Privacy Redaction Mode Toggle
                    Surface(
                        shape = CircleShape,
                        color = if (isRedactionActive) Color(0xFFE91E63).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            if (isRedactionActive) Color(0xFFFF4081) else Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                    ) {
                        IconButton(onClick = { isRedactionActive = !isRedactionActive }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = if (isRedactionActive) "Exit Redaction" else "Redact",
                                tint = if (isRedactionActive) Color(0xFFFF4081) else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Toggle OCR Boxes
                    Surface(
                        shape = CircleShape,
                        color = if (showBoxes) Color(0xFF1E88E5).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            if (showBoxes) Color(0xFF64B5F6).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                    ) {
                        IconButton(onClick = { showBoxes = !showBoxes }) {
                            Icon(
                                imageVector = if (showBoxes) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showBoxes) "Hide boxes" else "Show boxes",
                                tint = if (showBoxes) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Dismiss button
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Zoom indicator pill
                AnimatedVisibility(
                    visible = zoomScale > 1.1f,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOutMap,
                                contentDescription = "Reset zoom",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${(zoomScale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                // Bottom Floating Sheet / Tap Feedback
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .align(Alignment.BottomCenter),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tapped Block Feedback Banner
                    AnimatedVisibility(
                        visible = tappedBlockText != null,
                        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xEE1E1E1E),
                            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = tappedBlockText ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Redaction Controls
                    AnimatedVisibility(
                        visible = isRedactionActive,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xEE1E1E1E),
                            border = BorderStroke(1.dp, Color(0xFFFF4081).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Privacy Redaction Mode",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${redactedBlocks.size} block(s) redacted",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = redactionMode == RedactionType.BLACKOUT,
                                        onClick = { redactionMode = RedactionType.BLACKOUT },
                                        label = { Text("Blackout", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.Black,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = redactionMode == RedactionType.PIXELATE,
                                        onClick = { redactionMode = RedactionType.PIXELATE },
                                        label = { Text("Pixelate", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF37474F),
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = {
                                            redactedBlocks = emptyMap()
                                            completedStrokes = emptyList()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Clear", color = Color.White)
                                    }

                                    androidx.compose.material3.Button(
                                        onClick = {
                                            if (redactedBlocks.isNotEmpty() || completedStrokes.isNotEmpty()) {
                                                isSavingRedacted = true
                                                scope.launch {
                                                    val redactionItems = redactedBlocks.map { (blk, type) ->
                                                        RedactionItem(blk, type)
                                                    }
                                                    val newUri = PrivacyRedactor.saveRedactedScreenshot(
                                                        context = context,
                                                        originalUri = currentScreenshot.contentUri,
                                                        redactions = redactionItems,
                                                        freehandPaths = completedStrokes,
                                                        imageWidth = rawImageWidth,
                                                        imageHeight = rawImageHeight
                                                    )
                                                    isSavingRedacted = false
                                                    if (newUri != null) {
                                                        Toast.makeText(context, "Saved redacted copy!", Toast.LENGTH_SHORT).show()
                                                        PrivacyRedactor.shareRedactedImage(context, newUri)
                                                    } else {
                                                        Toast.makeText(context, "Failed to save redacted image", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "Tap blocks to redact first", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = !isSavingRedacted,
                                        modifier = Modifier.weight(1.5f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFE91E63)
                                        )
                                    ) {
                                        if (isSavingRedacted) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Save & Share Copy")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Gesture hint pill
                    if (tappedBlockText == null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            val hint = when {
                                isRedactionActive -> "Tap blocks to redact/unredact"
                                allScreenshots.size > 1 -> "Swipe left/right to change  •  Tap box to copy"
                                else -> "Tap a box to copy  •  Double-tap to zoom"
                            }
                            Text(
                                text = hint,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

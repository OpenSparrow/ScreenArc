package com.screenarc.app.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
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
import com.screenarc.app.model.OcrBlock
import com.screenarc.app.model.ScreenshotUiModel
import com.screenarc.app.ocr.OcrCoordinateTransformer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OcrOverlayImageViewer(
    screenshot: ScreenshotUiModel,
    blocks: List<OcrBlock>,
    searchQuery: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showBoxes by remember { mutableStateOf(true) }
    var rawImageWidth by remember { mutableIntStateOf(0) }
    var rawImageHeight by remember { mutableIntStateOf(0) }
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

    // Pre-decode dimensions if available
    LaunchedEffect(screenshot.uri) {
        val dims = OcrCoordinateTransformer.getImageDimensions(context, screenshot.contentUri)
        if (dims != null) {
            rawImageWidth = dims.first
            rawImageHeight = dims.second
        }
    }

    // Zoom & Pan transformation states
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Interactive tapped block feedback
    var tappedBlockText by remember { mutableStateOf<String?>(null) }
    var lastTappedBlock by remember { mutableStateOf<OcrBlock?>(null) }

    // Redaction and privacy tool state
    var isRedactionActive by remember { mutableStateOf(false) }
    var redactionMode by remember { mutableStateOf(com.screenarc.app.util.RedactionType.BLACKOUT) }
    var redactedBlocks by remember { mutableStateOf<Map<OcrBlock, com.screenarc.app.util.RedactionType>>(emptyMap()) }
    var freehandBrushActive by remember { mutableStateOf(false) }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var completedStrokes by remember { mutableStateOf<List<com.screenarc.app.util.FreehandPath>>(emptyList()) }
    var isSavingRedacted by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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
    val matchCount = remember(blocks, query) {
        if (query == null) 0
        else blocks.count { it.text.contains(query, ignoreCase = true) }
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

                // Calculate image dimensions fallback if unmeasured
                val effectiveImageWidth = if (rawImageWidth > 0) rawImageWidth.toFloat()
                else (blocks.maxOfOrNull { it.right ?: 1080 } ?: 1080).toFloat()

                val effectiveImageHeight = if (rawImageHeight > 0) rawImageHeight.toFloat()
                else (blocks.maxOfOrNull { it.bottom ?: 1920 } ?: 1920).toFloat()

                val fitParams = remember(containerWidth, containerHeight, effectiveImageWidth, effectiveImageHeight) {
                    OcrCoordinateTransformer.computeFitParams(
                        containerWidth = containerWidth,
                        containerHeight = containerHeight,
                        imageWidth = effectiveImageWidth,
                        imageHeight = effectiveImageHeight
                    )
                }

                // Subtle gradient background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF111111),
                                    Color(0xFF050505)
                                ),
                                radius = containerWidth * 0.8f
                            )
                        )
                )

                // Main Interactive Layer (Zoomable & Pannable & Tappable)
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
                        .pointerInput(blocks, fitParams, zoomScale, panOffset) {
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
                                        blocks = blocks,
                                        fitParams = fitParams
                                    )
                                    if (hitBlock != null) {
                                        if (isRedactionActive) {
                                            // Toggle redaction on tapped block
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
                                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    // Screenshot Image with subtle vignette
                    AsyncImage(
                        model = screenshot.contentUri,
                        contentDescription = screenshot.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { state ->
                            val d = state.result.drawable
                            if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                                rawImageWidth = d.intrinsicWidth
                                rawImageHeight = d.intrinsicHeight
                            }
                        }
                    )

                    // Bounding Boxes Overlay Canvas - with rounded corners and glow
                    if (showBoxes && blocks.isNotEmpty() && fitParams.scale > 0f) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            blocks.forEach { block ->
                                val rect = OcrCoordinateTransformer.transformBlockToViewRect(block, fitParams)
                                if (rect != null && rect.width > 0 && rect.height > 0) {
                                    val isMatch = query != null && block.text.contains(query, ignoreCase = true)
                                    val isTapped = lastTappedBlock == block

                                    val cornerRadius = 4.dp.toPx()

                                    val isRedacted = redactedBlocks.containsKey(block)
                                    val redactionType = redactedBlocks[block]

                                    when {
                                        isRedacted -> {
                                            if (redactionType == com.screenarc.app.util.RedactionType.BLACKOUT) {
                                                // Solid blackout
                                                drawRoundRect(
                                                    color = Color.Black,
                                                    topLeft = Offset(rect.left, rect.top),
                                                    size = Size(rect.width, rect.height),
                                                    cornerRadius = CornerRadius(cornerRadius)
                                                )
                                            } else {
                                                // Pixelate pattern styling
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
                                            // Glow effect for tapped block
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
                                            // Amber highlight for search matches
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
                                            // Default subtle blue
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

                            // Freehand blur/blackout strokes
                            completedStrokes.forEach { stroke ->
                                if (stroke.points.size >= 2) {
                                    val path = androidx.compose.ui.graphics.Path()
                                    path.moveTo(stroke.points[0].x, stroke.points[0].y)
                                    for (i in 1 until stroke.points.size) {
                                        path.lineTo(stroke.points[i].x, stroke.points[i].y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.Black,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = stroke.strokeWidth,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                            if (currentStrokePoints.size >= 2) {
                                val currentPath = androidx.compose.ui.graphics.Path()
                                currentPath.moveTo(currentStrokePoints[0].x, currentStrokePoints[0].y)
                                for (i in 1 until currentStrokePoints.size) {
                                    currentPath.lineTo(currentStrokePoints[i].x, currentStrokePoints[i].y)
                                }
                                drawPath(
                                    path = currentPath,
                                    color = Color.Black,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 36f,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }

                // Top gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xCC000000), Color.Transparent)
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
                                colors = listOf(Color.Transparent, Color(0xCC000000))
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
                            text = screenshot.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${blocks.size} text blocks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (query != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFFD54F).copy(alpha = 0.2f)
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

                    // Privacy Redaction Mode Toggle
                    Surface(
                        shape = CircleShape,
                        color = if (isRedactionActive) Color(0xFFE91E63).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isRedactionActive) Color(0xFFFF4081) else Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    ) {
                        IconButton(onClick = { isRedactionActive = !isRedactionActive }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.ContentCopy,
                                contentDescription = if (isRedactionActive) "Exit Redaction" else "Redact",
                                tint = if (isRedactionActive) Color(0xFFFF4081) else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Toggle OCR Boxes
                    Surface(
                        shape = CircleShape,
                        color = if (showBoxes) Color(0xFF1E88E5).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (showBoxes) Color(0xFF64B5F6).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    ) {
                        IconButton(onClick = { showBoxes = !showBoxes }) {
                            Icon(
                                imageVector = if (showBoxes) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showBoxes) "Hide boxes" else "Show boxes",
                                tint = if (showBoxes) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Dismiss button
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Zoom indicator pill (floating, top-right, when zoomed)
                AnimatedVisibility(
                    visible = zoomScale > 1.1f,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 88.dp, end = 16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E1E1E).copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${String.format("%.1f", zoomScale)}x",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.ZoomOutMap,
                                    contentDescription = "Reset Zoom",
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom area: Copied block feedback + tip
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Copied Block Feedback Banner
                    AnimatedVisibility(
                        visible = tappedBlockText != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(200))
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1A1A2E).copy(alpha = 0.97f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF00E676).copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Copied to clipboard",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF00E676)
                                        )
                                        Text(
                                            text = tappedBlockText ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(onClick = { tappedBlockText = null }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Redaction Tool Bar (Blackout / Pixelate / Save Redacted Copy & Share)
                    AnimatedVisibility(
                        visible = isRedactionActive,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF1E1E2E).copy(alpha = 0.95f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF4081).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Privacy Redaction (${redactedBlocks.size} blocks)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        androidx.compose.material3.FilterChip(
                                            selected = redactionMode == com.screenarc.app.util.RedactionType.BLACKOUT,
                                            onClick = { redactionMode = com.screenarc.app.util.RedactionType.BLACKOUT },
                                            label = { Text("Blackout", fontSize = 11.sp) }
                                        )
                                        androidx.compose.material3.FilterChip(
                                            selected = redactionMode == com.screenarc.app.util.RedactionType.PIXELATE,
                                            onClick = { redactionMode = com.screenarc.app.util.RedactionType.PIXELATE },
                                            label = { Text("Pixelate", fontSize = 11.sp) }
                                        )
                                    }
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
                                                        com.screenarc.app.util.RedactionItem(blk, type)
                                                    }
                                                    val newUri = com.screenarc.app.util.PrivacyRedactor.saveRedactedScreenshot(
                                                        context = context,
                                                        originalUri = screenshot.contentUri,
                                                        redactions = redactionItems,
                                                        freehandPaths = completedStrokes,
                                                        imageWidth = rawImageWidth,
                                                        imageHeight = rawImageHeight
                                                    )
                                                    isSavingRedacted = false
                                                    if (newUri != null) {
                                                        Toast.makeText(context, "Saved redacted copy!", Toast.LENGTH_SHORT).show()
                                                        com.screenarc.app.util.PrivacyRedactor.shareRedactedImage(context, newUri)
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
                    if (tappedBlockText == null && showBoxes && blocks.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = if (isRedactionActive) "Tap blocks to redact/unredact" else "Tap a box to copy  •  Double-tap to zoom",
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

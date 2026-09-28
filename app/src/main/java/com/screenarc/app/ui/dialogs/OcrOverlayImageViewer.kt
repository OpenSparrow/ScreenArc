package com.screenarc.app.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    val coroutineScope = rememberCoroutineScope()

    var showBoxes by remember { mutableStateOf(true) }
    var rawImageWidth by remember { mutableIntStateOf(0) }
    var rawImageHeight by remember { mutableIntStateOf(0) }

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

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (zoomScale * zoomChange).coerceIn(1f, 5f)
        zoomScale = newScale
        if (newScale > 1f) {
            panOffset += offsetChange
        } else {
            panOffset = Offset.Zero
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
            modifier = modifier.fillMaxSize(),
            color = Color.Black
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
                                        // Center zoom around tap point
                                        val centerX = containerWidth / 2f
                                        val centerY = containerHeight / 2f
                                        panOffset = Offset(
                                            (centerX - tapCenter.x) * 1.5f,
                                            (centerY - tapCenter.y) * 1.5f
                                        )
                                    }
                                },
                                onTap = { tapOffset ->
                                    // Hit-test against OCR blocks
                                    val hitBlock = OcrCoordinateTransformer.findBlockAtPoint(
                                        touchX = tapOffset.x,
                                        touchY = tapOffset.y,
                                        blocks = blocks,
                                        fitParams = fitParams
                                    )
                                    if (hitBlock != null) {
                                        lastTappedBlock = hitBlock
                                        tappedBlockText = hitBlock.text
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Recognized Text", hitBlock.text))
                                        Toast.makeText(context, "Copied: ${hitBlock.text.take(35)}...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                ) {
                    // Screenshot Bitmap Image
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

                    // Bounding Boxes Overlay Canvas
                    if (showBoxes && blocks.isNotEmpty() && fitParams.scale > 0f) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            blocks.forEach { block ->
                                val rect = OcrCoordinateTransformer.transformBlockToViewRect(block, fitParams)
                                if (rect != null && rect.width > 0 && rect.height > 0) {
                                    val isMatch = query != null && block.text.contains(query, ignoreCase = true)
                                    val isTapped = lastTappedBlock == block

                                    val (fillColor, strokeColor, strokeWidth) = when {
                                        isTapped -> Triple(
                                            Color(0x6600E676), // Bright Emerald
                                            Color(0xFF00E676),
                                            3.dp.toPx()
                                        )
                                        isMatch -> Triple(
                                            Color(0x66FFC107), // Amber Highlight
                                            Color(0xFFFFD54F),
                                            3.dp.toPx()
                                        )
                                        else -> Triple(
                                            Color(0x332196F3), // Standard Blue
                                            Color(0xFF2196F3),
                                            1.5.dp.toPx()
                                        )
                                    }

                                    // Fill bounding rect
                                    drawRect(
                                        color = fillColor,
                                        topLeft = Offset(rect.left, rect.top),
                                        size = Size(rect.width, rect.height)
                                    )
                                    // Outline bounding rect
                                    drawRect(
                                        color = strokeColor,
                                        topLeft = Offset(rect.left, rect.top),
                                        size = Size(rect.width, rect.height),
                                        style = Stroke(width = strokeWidth)
                                    )
                                }
                            }
                        }
                    }
                }

                // Top Floating Control Bar
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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
                                Text(
                                    text = "${blocks.size} OCR blocks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                if (query != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "•  $matchCount matching \"$query\"",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFFFFD54F)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Toggle OCR Boxes FilterChip
                        FilterChip(
                            selected = showBoxes,
                            onClick = { showBoxes = !showBoxes },
                            label = {
                                Text(
                                    text = if (showBoxes) "Boxes On" else "Boxes Off",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showBoxes) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (showBoxes) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E88E5).copy(alpha = 0.35f),
                                containerColor = Color.White.copy(alpha = 0.1f)
                            )
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Dismiss button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                // Zoom reset badge (floating when zoomed in)
                if (zoomScale > 1.1f) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 90.dp, end = 16.dp)
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
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Copied Block Feedback Banner at Bottom
                AnimatedVisibility(
                    visible = tappedBlockText != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E1E1E),
                        tonalElevation = 8.dp,
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
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Copied to clipboard",
                                        style = MaterialTheme.typography.labelSmall,
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
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                // Tap-to-copy tip helper at bottom when no banner is shown
                if (tappedBlockText == null && showBoxes && blocks.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                    ) {
                        Text(
                            text = "💡 Tap any box to copy text • Double-tap to zoom",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

package com.screenarc.app.ui.editor

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Size
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
import coil.compose.AsyncImage
import com.screenarc.app.data.OcrRepository
import com.screenarc.app.data.ScreenshotRepository
import com.screenarc.app.model.OcrBlock
import com.screenarc.app.model.OverlayEvent
import com.screenarc.app.model.ScreenshotUiModel
import com.screenarc.app.ocr.OcrCoordinateTransformer
import com.screenarc.app.ocr.OcrLanguage
import com.screenarc.app.service.OverlayEventBus
import com.screenarc.app.util.FreehandPath
import com.screenarc.app.util.PrivacyRedactor
import com.screenarc.app.util.RedactionItem
import com.screenarc.app.util.RedactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Redaction editor opened from the overlay arc's Share / Delete actions.
 *
 * Flow: open screenshot → run OCR → user taps OCR blocks (or draws) to
 * blackout/pixelate them → taps OK.
 *  - SHARE mode: a redacted copy is saved & shared, then the ORIGINAL screenshot
 *    is deleted. (If nothing was redacted the original itself is shared & kept.)
 *  - DELETE mode: the original screenshot is deleted.
 */
@Composable
fun RedactionEditorScreen(
    uri: Uri,
    displayName: String,
    isDeleteMode: Boolean,
    isShareAndDeleteMode: Boolean = false,
    language: OcrLanguage?,
    ocrRepository: OcrRepository,
    screenshot: ScreenshotUiModel,
    screenshotRepository: ScreenshotRepository,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ─── Image dimensions ─────────────────────────────────────────────────────
    var rawImageWidth by remember { mutableIntStateOf(0) }
    var rawImageHeight by remember { mutableIntStateOf(0) }

    // ─── OCR state ────────────────────────────────────────────────────────────
    var ocrBlocks by remember { mutableStateOf<List<OcrBlock>>(emptyList()) }
    var isOcrRunning by remember { mutableStateOf(true) }
    var ocrStatusText by remember { mutableStateOf("Running OCR…") }

    // ─── Redaction state ──────────────────────────────────────────────────────
    var redactionType by remember { mutableStateOf(RedactionType.BLACKOUT) }
    var redactedBlocks by remember { mutableStateOf<Map<OcrBlock, RedactionType>>(emptyMap()) }
    var brushActive by remember { mutableStateOf(false) }
    var completedStrokes by remember { mutableStateOf<List<FreehandPath>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isBusy by remember { mutableStateOf(false) }

    // ─── Zoom & pan ───────────────────────────────────────────────────────────
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        if (!brushActive) {
            val newScale = (zoomScale * zoomChange).coerceIn(1f, 6f)
            zoomScale = newScale
            if (newScale > 1f) {
                panOffset += offsetChange
            } else {
                panOffset = Offset.Zero
            }
        }
    }

    // Decode intrinsic dimensions + run OCR once
    LaunchedEffect(uri) {
        val dims = withContext(Dispatchers.IO) {
            OcrCoordinateTransformer.getImageDimensions(context, uri)
        }
        if (dims != null) {
            rawImageWidth = dims.first
            rawImageHeight = dims.second
        }

        try {
            val result = ocrRepository.recognizeText(uri, language)
            if (result.success) {
                ocrBlocks = result.blocks
                ocrStatusText = if (result.blocks.isEmpty()) {
                    "No text detected — you can still share or delete"
                } else {
                    "${result.blocks.size} text blocks detected"
                }
            } else {
                ocrStatusText = result.errorMessage ?: "OCR unavailable — you can still redact manually"
            }
        } catch (e: Exception) {
            ocrStatusText = "OCR failed — you can still redact manually"
        } finally {
            isOcrRunning = false
        }
    }

    // Brush width in image-space pixels
    val brushWidthPx = remember(rawImageWidth, rawImageHeight) {
        (maxOf(rawImageWidth, rawImageHeight) * 0.018f).coerceIn(24f, 90f)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0A0A0A)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()

            val effectiveImageWidth = if (rawImageWidth > 0) rawImageWidth.toFloat()
            else (ocrBlocks.maxOfOrNull { it.right ?: 1080 } ?: 1080).toFloat()

            val effectiveImageHeight = if (rawImageHeight > 0) rawImageHeight.toFloat()
            else (ocrBlocks.maxOfOrNull { it.bottom ?: 1920 } ?: 1920).toFloat()

            val fitParams = remember(containerWidth, containerHeight, effectiveImageWidth, effectiveImageHeight) {
                OcrCoordinateTransformer.computeFitParams(
                    containerWidth = containerWidth,
                    containerHeight = containerHeight,
                    imageWidth = effectiveImageWidth,
                    imageHeight = effectiveImageHeight
                )
            }
            fun mapViewToImagePoint(offset: Offset): Offset =
                OcrCoordinateTransformer.mapTouchToImageCoords(offset.x, offset.y, fitParams) ?: offset

            fun imageViewToCanvas(point: Offset): Offset =
                Offset(
                    point.x * fitParams.scale + fitParams.offsetX,
                    point.y * fitParams.scale + fitParams.offsetY
                )

            // ─── Interactive image layer (zoom / pan / tap / brush) ───────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
                    .transformable(state = transformState, enabled = !brushActive)
                    .pointerInput(brushActive, ocrBlocks, fitParams) {
                        if (brushActive) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(mapViewToImagePoint(offset))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + mapViewToImagePoint(change.position)
                                },
                                onDragEnd = {
                                    if (currentStroke.size >= 2) {
                                        completedStrokes = completedStrokes + FreehandPath(currentStroke, brushWidthPx)
                                    }
                                    currentStroke = emptyList()
                                },
                                onDragCancel = {
                                    currentStroke = emptyList()
                                }
                            )
                        } else {
                            detectTapGestures(
                                onDoubleTap = { tapCenter ->
                                    if (zoomScale > 1.2f) {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        zoomScale = 2.5f
                                        panOffset = Offset(
                                            (containerWidth / 2f - tapCenter.x) * 1.5f,
                                            (containerHeight / 2f - tapCenter.y) * 1.5f
                                        )
                                    }
                                },
                                onTap = { tapOffset ->
                                    val hitBlock = OcrCoordinateTransformer.findBlockAtPoint(
                                        touchX = tapOffset.x,
                                        touchY = tapOffset.y,
                                        blocks = ocrBlocks,
                                        fitParams = fitParams
                                    )
                                    if (hitBlock != null) {
                                        val current = redactedBlocks[hitBlock]
                                        redactedBlocks = if (current != null) {
                                            redactedBlocks - hitBlock
                                        } else {
                                            redactedBlocks + (hitBlock to redactionType)
                                        }
                                    }
                                }
                            )
                        }
                    }
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = displayName,
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

                // Redaction / OCR block overlay
                if (fitParams.scale > 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        ocrBlocks.forEach { block ->
                            val rect = OcrCoordinateTransformer.transformBlockToViewRect(block, fitParams)
                            if (rect != null && rect.width > 0 && rect.height > 0) {
                                val cornerRadius = 4.dp.toPx()
                                val type = redactedBlocks[block]
                                when (type) {
                                    RedactionType.BLACKOUT -> {
                                        drawRoundRect(
                                            color = Color.Black,
                                            topLeft = Offset(rect.left, rect.top),
                                            size = Size(rect.width, rect.height),
                                            cornerRadius = CornerRadius(cornerRadius)
                                        )
                                    }
                                    RedactionType.PIXELATE -> {
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
                                    null -> {
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

                        // Freehand blackout strokes (stored in image space)
                        val drawStroke: (List<Offset>) -> Unit = { points ->
                            if (points.size >= 2) {
                                val path = Path()
                                val first = imageViewToCanvas(points[0])
                                path.moveTo(first.x, first.y)
                                for (i in 1 until points.size) {
                                    val p = imageViewToCanvas(points[i])
                                    path.lineTo(p.x, p.y)
                                }
                                drawPath(
                                    path = path,
                                    color = Color.Black,
                                    style = Stroke(
                                        width = (brushWidthPx * fitParams.scale).coerceAtLeast(8f),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                        completedStrokes.forEach { stroke -> drawStroke(stroke.points) }
                        drawStroke(currentStroke)
                    }
                }
            }

            // ─── Top scrim + title bar ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color(0xCC000000), Color.Transparent)
                        )
                    )
            )

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
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOcrRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color(0xFF64B5F6),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = ocrStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                ) {
                    IconButton(onClick = { onFinished() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close without action",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ─── Zoom reset pill ──────────────────────────────────────────────
            if (zoomScale > 1.1f) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E1E1E).copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 88.dp, end = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%.1fx", zoomScale),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
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

            // ─── Bottom scrim + redaction toolbar ─────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Gesture hint
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Text(
                        text = if (brushActive) "Draw over anything to blackout it"
                        else "Tap a text block to redact  •  Double-tap to zoom",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E1E2E).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFFFF4081).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Redact (${redactedBlocks.size + completedStrokes.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = redactionType == RedactionType.BLACKOUT && !brushActive,
                                    onClick = {
                                        redactionType = RedactionType.BLACKOUT
                                        brushActive = false
                                    },
                                    label = { Text("Blackout", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = redactionType == RedactionType.PIXELATE && !brushActive,
                                    onClick = {
                                        redactionType = RedactionType.PIXELATE
                                        brushActive = false
                                    },
                                    label = { Text("Pixelate", fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = brushActive,
                                onClick = { brushActive = !brushActive },
                                label = { Text("Brush", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Brush,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )

                            OutlinedButton(
                                onClick = {
                                    redactedBlocks = emptyMap()
                                    completedStrokes = emptyList()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isBusy
                            ) {
                                Text("Clear", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    if (isBusy) return@Button
                                    isBusy = true
                                    scope.launch {
                                        val hasRedactions =
                                            redactedBlocks.isNotEmpty() || completedStrokes.isNotEmpty()

                                        if (isDeleteMode) {
                                            val result = screenshotRepository.deleteScreenshot(screenshot)
                                            Toast.makeText(
                                                context,
                                                if (result.isSuccess) "Screenshot deleted"
                                                else "Could not delete screenshot: ${result.exceptionOrNull()?.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isBusy = false
                                            onFinished()
                                            return@launch
                                        }

                                        // SHARE mode
                                        val shareUri: Uri = if (hasRedactions) {
                                            val items = redactedBlocks.map { (blk, type) ->
                                                RedactionItem(blk, type)
                                            }
                                            val newUri = PrivacyRedactor.saveRedactedScreenshot(
                                                context = context,
                                                originalUri = uri,
                                                redactions = items,
                                                freehandPaths = completedStrokes,
                                                imageWidth = rawImageWidth,
                                                imageHeight = rawImageHeight
                                            )
                                            if (newUri == null) {
                                                Toast.makeText(
                                                    context,
                                                    "Failed to save redacted copy",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                isBusy = false
                                                return@launch
                                            }
                                            // Prevent the overlay observer from re-picking up the copy
                                            OverlayEventBus.postEvent(OverlayEvent.ScreenshotHandled(newUri))
                                            newUri
                                        } else {
                                            uri
                                        }

                                        try {
                                            PrivacyRedactor.shareRedactedImage(context, shareUri)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "Could not share: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isBusy = false
                                            return@launch
                                        }

                                        if (hasRedactions || isShareAndDeleteMode) {
                                            val result = screenshotRepository.deleteScreenshot(screenshot)
                                            Toast.makeText(
                                                context,
                                                if (result.isSuccess) {
                                                    if (hasRedactions) "Redacted copy shared — original deleted"
                                                    else "Screenshot shared — original deleted"
                                                } else "Shared, but could not delete original",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Nothing redacted — original kept",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        isBusy = false
                                        onFinished()
                                    }
                                },
                                modifier = Modifier.weight(1.6f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDeleteMode) Color(0xFFE53935) else Color(0xFFE91E63)
                                )
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isDeleteMode) Icons.Default.Delete else Icons.Default.Share,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("OK", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = when {
                                isDeleteMode -> "OK deletes this screenshot"
                                isShareAndDeleteMode -> "OK shares this screenshot, then deletes the original"
                                else -> "OK shares the redacted image, then deletes the original"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

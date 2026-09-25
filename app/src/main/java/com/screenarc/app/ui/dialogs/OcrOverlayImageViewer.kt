package com.screenarc.app.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.screenarc.app.model.OcrBlock
import com.screenarc.app.model.ScreenshotUiModel

@Composable
fun OcrOverlayImageViewer(
    screenshot: ScreenshotUiModel,
    blocks: List<OcrBlock>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBoxes by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main image
                AsyncImage(
                    model = screenshot.contentUri,
                    contentDescription = screenshot.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Bounding boxes overlay
                if (showBoxes && blocks.isNotEmpty()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = constraints.maxWidth.toFloat()
                        val canvasHeight = constraints.maxHeight.toFloat()

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Find bounds
                            val maxR = blocks.maxOfOrNull { it.right ?: 1000 }?.coerceAtLeast(1) ?: 1000
                            val maxB = blocks.maxOfOrNull { it.bottom ?: 1920 }?.coerceAtLeast(1) ?: 1920

                            val scaleX = canvasWidth / maxR.toFloat()
                            val scaleY = canvasHeight / maxB.toFloat()
                            val scale = minOf(scaleX, scaleY)

                            val offsetX = (canvasWidth - (maxR * scale)) / 2f
                            val offsetY = (canvasHeight - (maxB * scale)) / 2f

                            blocks.forEach { block ->
                                val left = (block.left ?: 0) * scale + offsetX
                                val top = (block.top ?: 0) * scale + offsetY
                                val width = ((block.right ?: 0) - (block.left ?: 0)) * scale
                                val height = ((block.bottom ?: 0) - (block.top ?: 0)) * scale

                                if (width > 0 && height > 0) {
                                    // Highlight fill
                                    drawRect(
                                        color = Color(0x332196F3),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height)
                                    )
                                    // Border stroke
                                    drawRect(
                                        color = Color(0xFF2196F3),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }

                // Controls Bar at Top
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = screenshot.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = "${blocks.size} recognized OCR regions",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        // Toggle OCR boxes button
                        FilterChip(
                            selected = showBoxes,
                            onClick = { showBoxes = !showBoxes },
                            label = {
                                Text(
                                    text = if (showBoxes) "Boxes On" else "Boxes Off",
                                    color = Color.White
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showBoxes) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

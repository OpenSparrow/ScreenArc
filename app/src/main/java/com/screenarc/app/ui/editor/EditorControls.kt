package com.screenarc.app.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.screenarc.app.ui.theme.LocalIsGlassmorphic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 bottom toolbar and contextual controls for tool settings,
 * stroke width, color palette, and redaction options.
 */
@Composable
fun EditorControls(
    uiState: EditorUiState,
    onSelectTool: (EditTool) -> Unit,
    onSetStrokeSize: (Float) -> Unit,
    onSetStrokeColor: (Int) -> Unit,
    onSetStrokeAlpha: (Float) -> Unit,
    onSetShapeType: (ShapeType) -> Unit,
    onSetShapeFilled: (Boolean) -> Unit,
    onSetShapeSolidRedact: (Boolean) -> Unit,
    onSetPixelateBlockSize: (Int) -> Unit,
    onOpenCustomColorPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isExpandedSettingsOpen by remember { mutableStateOf(false) }

    val isGlass = LocalIsGlassmorphic.current

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = if (isGlass) Color(0x40120E22) else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)) else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            // ─── Live Parameter Controls (Size Slider, Color Chips, Shape Selectors) ─
            val showColorControls = uiState.activeTool in listOf(
                EditTool.FREEHAND_BRUSH,
                EditTool.SHAPE,
                EditTool.TEXT
            )
            val showOpacityControls = showColorControls && !uiState.isShapeSolidRedact
            val showPixelateControls = uiState.activeTool == EditTool.PIXELATE_BRUSH
            val showShapeControls = uiState.activeTool == EditTool.SHAPE

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Size Slider with Live Preview Circle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showPixelateControls) "Block" else "Size",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.width(44.dp)
                    )

                    Slider(
                        value = if (showPixelateControls) uiState.pixelateBlockSize.toFloat() else uiState.strokeSize,
                        onValueChange = { value ->
                            if (showPixelateControls) {
                                onSetPixelateBlockSize(value.toInt())
                            } else {
                                onSetStrokeSize(value)
                            }
                        },
                        valueRange = if (showPixelateControls) 16f..64f else 6f..96f,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Live preview circle showing actual stroke width
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        val circleSize = if (showPixelateControls) {
                            (uiState.pixelateBlockSize.toFloat() / 2f).coerceIn(6f, 32f).dp
                        } else {
                            (uiState.strokeSize / 3f).coerceIn(4f, 32f).dp
                        }
                        val circleColor = when (uiState.activeTool) {
                            EditTool.SOLID_REDACT_BRUSH, EditTool.SOLID_REDACT_BOX -> Color.Black
                            EditTool.PIXELATE_BRUSH -> MaterialTheme.colorScheme.primary
                            else -> Color(uiState.strokeColor).copy(alpha = uiState.strokeAlpha)
                        }

                        Box(
                            modifier = Modifier
                                .size(circleSize)
                                .clip(CircleShape)
                                .background(circleColor)
                        )
                    }
                }

                // Opacity Slider (hidden for redaction tools)
                AnimatedVisibility(
                    visible = showOpacityControls,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Opacity",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(52.dp)
                        )

                        Slider(
                            value = uiState.strokeAlpha,
                            onValueChange = onSetStrokeAlpha,
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${(uiState.strokeAlpha * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }

                // Color Palette Row
                AnimatedVisibility(
                    visible = showColorControls,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val presetColors = remember {
                        listOf(
                            android.graphics.Color.RED,
                            android.graphics.Color.BLACK,
                            android.graphics.Color.WHITE,
                            android.graphics.Color.YELLOW,
                            android.graphics.Color.GREEN,
                            android.graphics.Color.CYAN,
                            android.graphics.Color.MAGENTA,
                            0xFF7C4DFF.toInt(),
                            0xFFFF6D00.toInt()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        presetColors.forEach { colorInt ->
                            val isSelected = uiState.strokeColor == colorInt
                            val composeColor = Color(colorInt)

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(composeColor)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        onSetStrokeColor(colorInt)
                                    }
                            )
                        }

                        // Custom color picker button
                        IconButton(
                            onClick = onOpenCustomColorPicker,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ColorLens,
                                contentDescription = "Custom Color",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Shape Selectors & Options Row
                AnimatedVisibility(
                    visible = showShapeControls,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShapeType.entries.forEach { sType ->
                            FilterChip(
                                selected = uiState.shapeType == sType,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSetShapeType(sType)
                                },
                                label = { Text(sType.displayName, fontSize = 11.sp) }
                            )
                        }

                        FilterChip(
                            selected = uiState.isShapeFilled,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onSetShapeFilled(!uiState.isShapeFilled)
                            },
                            label = { Text("Fill", fontSize = 11.sp) }
                        )

                        FilterChip(
                            selected = uiState.isShapeSolidRedact,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onSetShapeSolidRedact(!uiState.isShapeSolidRedact)
                            },
                            label = { Text("Solid Black", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.Black,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ─── Primary Tool Selection Bar (48dp Touch Targets + Haptics) ─────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolButton(
                    tool = EditTool.SELECT_MOVE,
                    icon = Icons.Default.ZoomOutMap,
                    isSelected = uiState.activeTool == EditTool.SELECT_MOVE,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectTool(EditTool.SELECT_MOVE)
                    }
                )

                ToolButton(
                    tool = EditTool.FREEHAND_BRUSH,
                    icon = Icons.Default.Brush,
                    isSelected = uiState.activeTool == EditTool.FREEHAND_BRUSH,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectTool(EditTool.FREEHAND_BRUSH)
                    }
                )

                ToolButton(
                    tool = EditTool.PIXELATE_BRUSH,
                    icon = Icons.Default.Texture,
                    isSelected = uiState.activeTool == EditTool.PIXELATE_BRUSH,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectTool(EditTool.PIXELATE_BRUSH)
                    }
                )

                ToolButton(
                    tool = EditTool.SOLID_REDACT_BRUSH,
                    icon = Icons.Default.FormatPaint,
                    isSelected = uiState.activeTool == EditTool.SOLID_REDACT_BRUSH,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectTool(EditTool.SOLID_REDACT_BRUSH)
                    }
                )

                ToolButton(
                    tool = EditTool.SOLID_REDACT_BOX,
                    icon = Icons.Default.CropSquare,
                    isSelected = uiState.activeTool == EditTool.SOLID_REDACT_BOX,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectTool(EditTool.SOLID_REDACT_BOX)
                    }
                )

                ToolButton(
                    tool = EditTool.SHAPE,
                    icon = Icons.Default.Layers,
                    isSelected = uiState.activeTool == EditTool.SHAPE,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectTool(EditTool.SHAPE)
                    }
                )

                ToolButton(
                    tool = EditTool.TEXT,
                    icon = Icons.Default.TextFields,
                    isSelected = uiState.activeTool == EditTool.TEXT,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectTool(EditTool.TEXT)
                    }
                )

                ToolButton(
                    tool = EditTool.ERASER,
                    icon = Icons.Default.AutoAwesome,
                    isSelected = uiState.activeTool == EditTool.ERASER,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectTool(EditTool.ERASER)
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    tool: EditTool,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isGlass = LocalIsGlassmorphic.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            if (isGlass) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        border = if (isSelected) {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
        } else {
            BorderStroke(1.dp, if (isGlass) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        },
        modifier = Modifier
            .size(width = 68.dp, height = 48.dp) // Meets 48dp minimum touch target
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tool.displayName,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tool.displayName,
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

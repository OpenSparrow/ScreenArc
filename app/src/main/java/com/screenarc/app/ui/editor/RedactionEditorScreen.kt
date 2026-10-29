package com.screenarc.app.ui.editor

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.screenarc.app.ui.theme.LocalIsGlassmorphic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenarc.app.ScreenArcApplication
import com.screenarc.app.data.OcrRepository
import com.screenarc.app.data.ScreenshotRepository
import com.screenarc.app.ocr.OcrLanguage
import com.screenarc.app.model.ScreenshotUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen annotation and redaction editor.
 *
 * Implements non-destructive vector edits, undo/redo stacks, pixelate mosaic clipping,
 * solid opaque blackout, and full-resolution export with metadata stripping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedactionEditorScreen(
    uri: Uri,
    displayName: String,
    isShareMode: Boolean = false,
    isDeleteMode: Boolean = false,
    isShareAndDeleteMode: Boolean = false,
    language: OcrLanguage? = null,
    ocrRepository: OcrRepository? = null,
    screenshot: ScreenshotUiModel? = null,
    screenshotRepository: ScreenshotRepository? = null,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    editorViewModel: EditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isGlass = LocalIsGlassmorphic.current
    val uiState by editorViewModel.uiState.collectAsState()
    val committedOps by editorViewModel.committedOps.collectAsState()

    var canvasViewRef by remember { mutableStateOf<EditorCanvasView?>(null) }
    var pendingTextCoords by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var textInputState by remember { mutableStateOf("") }
    var editingTextOpId by remember { mutableStateOf<String?>(null) }
    var selectedExportFormat by remember { mutableStateOf(ExportManager.Companion.ExportFormat.PNG) }

    fun shareAndDispatch(exportedUri: Uri) {
        val mimeType = context.contentResolver.getType(exportedUri)
            ?: (if (selectedExportFormat == ExportManager.Companion.ExportFormat.JPEG) "image/jpeg" else "image/png")
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, exportedUri)
            clipData = ClipData.newUri(context.contentResolver, "Screenshot", exportedUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Edited Screenshot").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun deleteOriginalScreenshot(onDeleted: (() -> Unit)? = null) {
        val app = context.applicationContext as? ScreenArcApplication
        val repo = screenshotRepository ?: app?.appContainer?.screenshotRepository
        CoroutineScope(Dispatchers.IO).launch {
            if (screenshot != null && repo != null) {
                try {
                    repo.deleteScreenshot(screenshot)
                } catch (e: Exception) {
                    android.util.Log.e("RedactionEditor", "Error deleting via repository: ${e.message}", e)
                }
            }
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                android.util.Log.e("RedactionEditor", "Error deleting via contentResolver: ${e.message}", e)
            }
            withContext(Dispatchers.Main) {
                onDeleted?.invoke()
            }
        }
    }

    // Load preview bitmap on initial composition
    LaunchedEffect(uri) {
        editorViewModel.loadScreenshot(uri)
    }

    // Intercept back navigation if there are unsaved edits
    BackHandler(enabled = true) {
        if (uiState.opsCount > 0) {
            editorViewModel.setShowDiscardDialog(true)
        } else {
            onFinished()
        }
    }

    // Clean up canvas view memory on exit
    DisposableEffect(Unit) {
        onDispose {
            canvasViewRef?.cleanUp()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── Top App Bar ──────────────────────────────────────────────────
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${uiState.opsCount} edits",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.opsCount > 0) {
                                editorViewModel.setShowDiscardDialog(true)
                            } else {
                                onFinished()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close / Discard")
                    }
                },
                actions = {
                    // Undo Button
                    IconButton(
                        onClick = { editorViewModel.undo() },
                        enabled = uiState.canUndo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (uiState.canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Redo Button
                    IconButton(
                        onClick = { editorViewModel.redo() },
                        enabled = uiState.canRedo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (uiState.canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Clear All Button
                    IconButton(
                        onClick = { editorViewModel.clearAll() },
                        enabled = uiState.opsCount > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear All",
                            tint = if (uiState.opsCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    if (isShareAndDeleteMode) {
                        // In Share & Delete mode, primary action button opens format dialog for Share & Delete
                        Button(
                            onClick = { editorViewModel.setShowSaveFormatDialog(true) },
                            enabled = !uiState.isExporting && !uiState.isPreviewLoading,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share & Delete", fontSize = 12.sp)
                        }
                    } else if (isShareMode) {
                        // In Share mode, primary action button opens format dialog for Share
                        Button(
                            onClick = { editorViewModel.setShowSaveFormatDialog(true) },
                            enabled = !uiState.isExporting && !uiState.isPreviewLoading,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 12.sp)
                        }
                    } else if (isDeleteMode) {
                        // In Delete mode, primary button saves and deletes original
                        Button(
                            onClick = { editorViewModel.setShowSaveFormatDialog(true) },
                            enabled = !uiState.isExporting && !uiState.isPreviewLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save & Delete", fontSize = 12.sp)
                        }
                    } else {
                        // Regular mode: Share icon button + Save button
                        IconButton(
                            onClick = { editorViewModel.setShowSaveFormatDialog(true) },
                            enabled = !uiState.isExporting && !uiState.isPreviewLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = { editorViewModel.setShowSaveFormatDialog(true) },
                            enabled = !uiState.isExporting && !uiState.isPreviewLoading,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isGlass) Color(0x35120E22) else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = if (isGlass) Modifier.drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                } else Modifier
            )

            // ─── Canvas View (High performance 3-layer renderer) ──────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
            ) {
                if (uiState.isPreviewLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading screenshot...", color = Color.White, fontSize = 13.sp)
                        }
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            EditorCanvasView(
                                context = ctx,
                                onCommitOp = { op -> editorViewModel.commitOp(op) },
                                onEraseOp = { x, y -> editorViewModel.eraseOpAt(x, y) },
                                onRequestTextEntry = { x, y ->
                                    editingTextOpId = null
                                    textInputState = ""
                                    pendingTextCoords = x to y
                                    editorViewModel.setShowTextEntryDialog(true)
                                }
                            ).apply {
                                onSelectOp = { op -> editorViewModel.selectOp(op?.id) }
                                onUpdateOp = { op -> editorViewModel.updateOp(op) }
                                canvasViewRef = this
                            }
                        },
                        update = { view ->
                            view.previewBitmap = uiState.previewBitmap
                            view.mosaicBitmap = uiState.mosaicBitmap
                            view.activeTool = uiState.activeTool
                            view.strokeSize = uiState.strokeSize
                            view.strokeColor = uiState.strokeColor
                            view.strokeAlpha = uiState.strokeAlpha
                            view.shapeType = uiState.shapeType
                            view.isShapeFilled = uiState.isShapeFilled
                            view.isShapeSolidRedact = uiState.isShapeSolidRedact
                            view.pixelateBlockSize = uiState.pixelateBlockSize
                            view.selectedOpId = uiState.selectedOpId
                            view.setCommittedOps(committedOps)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Floating Action Pill for Selected Layer (Drag to Move / Edit / Delete / Done)
                if (uiState.selectedOpId != null) {
                    val selectedOp = committedOps.find { it.id == uiState.selectedOpId }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenWith,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (selectedOp) {
                                    is EditOp.TextOp -> "Text: Drag to place"
                                    is EditOp.ShapeOp -> "${selectedOp.shapeType.displayName}: Drag to place"
                                    is EditOp.SolidRedactBox -> "Redact Box: Drag to place"
                                    else -> "Layer: Drag to place"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            if (selectedOp is EditOp.TextOp) {
                                IconButton(
                                    onClick = {
                                        editingTextOpId = selectedOp.id
                                        textInputState = selectedOp.text
                                        editorViewModel.setShowTextEntryDialog(true)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit text",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            IconButton(
                                onClick = { editorViewModel.deleteSelectedOp() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete layer",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { editorViewModel.selectOp(null) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Done",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Banner for Share & Delete / Delete mode
                if (isShareAndDeleteMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Edited screenshot will be shared & original deleted",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else if (isShareMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Edited screenshot will be shared after editing",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else if (isDeleteMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete original screenshot after redact?",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ─── Material 3 Bottom Controls ───────────────────────────────────
            EditorControls(
                uiState = uiState,
                onSelectTool = { tool -> editorViewModel.selectTool(tool) },
                onSetStrokeSize = { size -> editorViewModel.setStrokeSize(size) },
                onSetStrokeColor = { color -> editorViewModel.setStrokeColor(color) },
                onSetStrokeAlpha = { alpha -> editorViewModel.setStrokeAlpha(alpha) },
                onSetShapeType = { shape -> editorViewModel.setShapeType(shape) },
                onSetShapeFilled = { filled -> editorViewModel.setShapeFilled(filled) },
                onSetShapeSolidRedact = { solid -> editorViewModel.setShapeSolidRedact(solid) },
                onSetPixelateBlockSize = { size -> editorViewModel.setPixelateBlockSize(size) },
                onOpenCustomColorPicker = { editorViewModel.setShowColorPickerDialog(true) }
            )
        }

        // ─── Exporting Overlay ────────────────────────────────────────────────
        if (uiState.isExporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.exportProgressText ?: "Exporting image...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Applying vector redactions at full resolution",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ─── Discard Changes Prompt ───────────────────────────────────────────
        if (uiState.showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { editorViewModel.setShowDiscardDialog(false) },
                title = { Text("Discard Changes?") },
                text = { Text("You have ${uiState.opsCount} unsaved annotations. Do you want to discard them?") },
                confirmButton = {
                    Button(
                        onClick = {
                            editorViewModel.setShowDiscardDialog(false)
                            onFinished()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Discard")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editorViewModel.setShowDiscardDialog(false) }) {
                        Text("Keep Editing")
                    }
                }
            )
        }

        // ─── Text Entry Dialog ────────────────────────────────────────────────
        if (uiState.showTextEntryDialog) {
            AlertDialog(
                onDismissRequest = {
                    editorViewModel.setShowTextEntryDialog(false)
                    editingTextOpId = null
                },
                title = { Text(if (editingTextOpId != null) "Edit Text Label" else "Add Text Label") },
                text = {
                    OutlinedTextField(
                        value = textInputState,
                        onValueChange = { textInputState = it },
                        label = { Text("Enter text") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val editId = editingTextOpId
                            if (editId != null) {
                                editorViewModel.updateTextAnnotation(editId, textInputState)
                            } else {
                                pendingTextCoords?.let { (x, y) ->
                                    editorViewModel.addTextAnnotation(textInputState, x, y)
                                }
                            }
                            editingTextOpId = null
                            editorViewModel.setShowTextEntryDialog(false)
                        }
                    ) {
                        Text(if (editingTextOpId != null) "Update" else "Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        editorViewModel.setShowTextEntryDialog(false)
                        editingTextOpId = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // ─── Save Format Dialog (PNG / JPEG Full Resolution) ──────────────────
        if (uiState.showSaveFormatDialog) {
            AlertDialog(
                onDismissRequest = { editorViewModel.setShowSaveFormatDialog(false) },
                title = {
                    Text(
                        when {
                            isShareAndDeleteMode -> "Share & Delete Screenshot"
                            isShareMode -> "Share Edited Screenshot"
                            isDeleteMode -> "Save & Delete Screenshot"
                            else -> "Save at Full Resolution"
                        }
                    )
                },
                text = {
                    Column {
                        Text("Select export image format:")
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedExportFormat == ExportManager.Companion.ExportFormat.PNG,
                                onClick = { selectedExportFormat = ExportManager.Companion.ExportFormat.PNG }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("PNG (Lossless)", fontWeight = FontWeight.Bold)
                                Text("Best for text, screenshots, and crisp redaction", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedExportFormat == ExportManager.Companion.ExportFormat.JPEG,
                                onClick = { selectedExportFormat = ExportManager.Companion.ExportFormat.JPEG }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("JPEG (Smaller file size)", fontWeight = FontWeight.Bold)
                                Text("High quality, smaller disk storage footprint", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            editorViewModel.setShowSaveFormatDialog(false)
                            editorViewModel.exportAndSave(selectedExportFormat) { savedUri ->
                                when {
                                    isShareAndDeleteMode -> {
                                        shareAndDispatch(savedUri)
                                        deleteOriginalScreenshot {
                                            Toast.makeText(context, "Shared and original screenshot deleted", Toast.LENGTH_SHORT).show()
                                            onFinished()
                                        }
                                    }
                                    isShareMode -> {
                                        shareAndDispatch(savedUri)
                                        Toast.makeText(context, "Saved and share sheet opened", Toast.LENGTH_SHORT).show()
                                        onFinished()
                                    }
                                    isDeleteMode -> {
                                        deleteOriginalScreenshot {
                                            Toast.makeText(context, "Saved and original screenshot deleted", Toast.LENGTH_SHORT).show()
                                            onFinished()
                                        }
                                    }
                                    else -> {
                                        Toast.makeText(context, "Saved to Pictures/Screenshots", Toast.LENGTH_SHORT).show()
                                        onFinished()
                                    }
                                }
                            }
                        }
                    ) {
                        Text(
                            when {
                                isShareAndDeleteMode -> "Share & Delete"
                                isShareMode -> "Share"
                                isDeleteMode -> "Save & Delete"
                                else -> "Save Full Resolution"
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editorViewModel.setShowSaveFormatDialog(false) }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // ─── Custom Color Hex Picker Dialog ───────────────────────────────────
        if (uiState.showColorPickerDialog) {
            var hexInput by remember { mutableStateOf("#FF0000") }
            AlertDialog(
                onDismissRequest = { editorViewModel.setShowColorPickerDialog(false) },
                title = { Text("Custom Color") },
                text = {
                    Column {
                        Text("Enter 6-character Hex code (e.g. #FF5722):")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { hexInput = it },
                            label = { Text("Hex Color") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            try {
                                val parsed = android.graphics.Color.parseColor(
                                    if (hexInput.startsWith("#")) hexInput else "#$hexInput"
                                )
                                editorViewModel.setStrokeColor(parsed)
                                editorViewModel.setShowColorPickerDialog(false)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Invalid hex color format", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editorViewModel.setShowColorPickerDialog(false) }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

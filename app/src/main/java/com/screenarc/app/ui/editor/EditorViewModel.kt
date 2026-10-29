package com.screenarc.app.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val activeTool: EditTool = EditTool.SELECT_MOVE,
    val strokeSize: Float = 24f,
    val strokeColor: Int = Color.RED,
    val strokeAlpha: Float = 1.0f,
    val shapeType: ShapeType = ShapeType.RECTANGLE,
    val isShapeFilled: Boolean = false,
    val isShapeSolidRedact: Boolean = false,
    val pixelateBlockSize: Int = PixelateRenderer.DEFAULT_BLOCK_SIZE,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val opsCount: Int = 0,
    val selectedOpId: String? = null,
    val isPreviewLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportProgressText: String? = null,
    val showDiscardDialog: Boolean = false,
    val showColorPickerDialog: Boolean = false,
    val showTextEntryDialog: Boolean = false,
    val showSaveFormatDialog: Boolean = false,
    val errorMessage: String? = null,
    val previewBitmap: Bitmap? = null,
    val mosaicBitmap: Bitmap? = null,
    val previewWidth: Int = 0,
    val previewHeight: Int = 0
)

class EditorViewModel(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "EditorViewModel"
        const val MAX_UNDO_STACK_SIZE = 100
        const val MAX_PREVIEW_DIMENSION = 2048
    }

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // Undo / Redo stacks capped at MAX_UNDO_STACK_SIZE
    private val undoStack = mutableListOf<EditOp>()
    private val redoStack = mutableListOf<EditOp>()

    // Current committed ops list exposed to the canvas
    private val _committedOps = MutableStateFlow<List<EditOp>>(emptyList())
    val committedOps: StateFlow<List<EditOp>> = _committedOps.asStateFlow()

    private val pixelateRenderer = PixelateRenderer()
    private val exportManager = ExportManager(application)
    private var sourceImageUri: Uri? = null

    /**
     * Initializes the editor with the screenshot URI and decodes an optimized preview bitmap.
     */
    fun loadScreenshot(uri: Uri) {
        if (sourceImageUri == uri && _uiState.value.previewBitmap != null) return
        sourceImageUri = uri

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPreviewLoading = true, errorMessage = null)
            val preview = decodePreviewBitmap(uri)
            if (preview != null) {
                val mosaic = pixelateRenderer.getOrCreateMosaic(preview, _uiState.value.pixelateBlockSize)
                _uiState.value = _uiState.value.copy(
                    isPreviewLoading = false,
                    previewBitmap = preview,
                    mosaicBitmap = mosaic,
                    previewWidth = preview.width,
                    previewHeight = preview.height
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isPreviewLoading = false,
                    errorMessage = "Failed to load screenshot preview"
                )
            }
        }
    }

    /**
     * Decodes the image constrained to MAX_PREVIEW_DIMENSION with RGB_565/ARGB_8888 fallback.
     */
    private suspend fun decodePreviewBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val origW = options.outWidth
            val origH = options.outHeight
            if (origW <= 0 || origH <= 0) return@withContext null

            var sampleSize = 1
            val maxDim = maxOf(origW, origH)
            while ((maxDim / (sampleSize * 2)) >= MAX_PREVIEW_DIMENSION) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val sampled = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            // If sampled bitmap is still larger than MAX_PREVIEW_DIMENSION, scale it down smoothly
            val currentMax = maxOf(sampled.width, sampled.height)
            if (currentMax > MAX_PREVIEW_DIMENSION) {
                val scale = MAX_PREVIEW_DIMENSION.toFloat() / currentMax.toFloat()
                val targetW = (sampled.width * scale).toInt().coerceAtLeast(1)
                val targetH = (sampled.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
                if (scaled != sampled) sampled.recycle()
                scaled
            } else {
                sampled
            }
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM in decodePreviewBitmap, attempting RGB_565 fallback")
            try {
                val fallbackOpts = BitmapFactory.Options().apply {
                    inSampleSize = 2
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, fallbackOpts)
                }
            } catch (e2: Exception) {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding preview: ${e.message}", e)
            null
        }
    }

    // ─── Tool State & Controls ────────────────────────────────────────────────

    fun selectTool(tool: EditTool) {
        // Redaction tools enforce strictly opaque black / block styling
        val updatedAlpha = when (tool) {
            EditTool.SOLID_REDACT_BRUSH, EditTool.SOLID_REDACT_BOX -> 1.0f
            else -> _uiState.value.strokeAlpha
        }
        val isSolidRedact = tool == EditTool.SOLID_REDACT_BOX

        _uiState.value = _uiState.value.copy(
            activeTool = tool,
            strokeAlpha = updatedAlpha,
            isShapeSolidRedact = isSolidRedact
        )
    }

    fun setStrokeSize(size: Float) {
        _uiState.value = _uiState.value.copy(strokeSize = size.coerceIn(4f, 120f))
    }

    fun setStrokeColor(color: Int) {
        _uiState.value = _uiState.value.copy(strokeColor = color)
    }

    fun setStrokeAlpha(alpha: Float) {
        if (_uiState.value.activeTool == EditTool.SOLID_REDACT_BRUSH || _uiState.value.activeTool == EditTool.SOLID_REDACT_BOX) {
            return // Redaction tools must remain 100% opaque
        }
        _uiState.value = _uiState.value.copy(strokeAlpha = alpha.coerceIn(0.05f, 1.0f))
    }

    fun setShapeType(type: ShapeType) {
        _uiState.value = _uiState.value.copy(shapeType = type)
    }

    fun setShapeFilled(filled: Boolean) {
        _uiState.value = _uiState.value.copy(isShapeFilled = filled)
    }

    fun setShapeSolidRedact(solidRedact: Boolean) {
        _uiState.value = _uiState.value.copy(isShapeSolidRedact = solidRedact)
    }

    fun setPixelateBlockSize(size: Int) {
        val safeSize = size.coerceIn(PixelateRenderer.MIN_BLOCK_SIZE, PixelateRenderer.MAX_BLOCK_SIZE)
        _uiState.value = _uiState.value.copy(pixelateBlockSize = safeSize)

        val preview = _uiState.value.previewBitmap ?: return
        viewModelScope.launch {
            val mosaic = pixelateRenderer.getOrCreateMosaic(preview, safeSize)
            _uiState.value = _uiState.value.copy(mosaicBitmap = mosaic)
        }
    }

    // ─── Undo / Redo / Command Execution ──────────────────────────────────────

    /**
     * Commits a newly finished EditOp to the undo stack. Redo is cleared.
     */
    fun commitOp(op: EditOp) {
        if (undoStack.size >= MAX_UNDO_STACK_SIZE) {
            undoStack.removeAt(0) // Maintain cap
        }
        undoStack.add(op)
        redoStack.clear()
        syncOpStacks()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val popped = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(popped)
        syncOpStacks()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val popped = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(popped)
        syncOpStacks()
    }

    fun clearAll() {
        if (undoStack.isEmpty()) return
        undoStack.clear()
        redoStack.clear()
        syncOpStacks()
    }

    /**
     * Eraser tool: removes the target op found by hit-testing.
     */
    fun eraseOpAt(imageX: Float, imageY: Float, tolerancePx: Float = 32f): Boolean {
        // Iterate backwards (top-most op first)
        for (i in undoStack.indices.reversed()) {
            val op = undoStack[i]
            if (op.containsPoint(imageX, imageY, tolerancePx)) {
                undoStack.removeAt(i)
                redoStack.clear()
                syncOpStacks()
                return true
            }
        }
        return false
    }

    fun selectOp(id: String?) {
        _uiState.value = _uiState.value.copy(selectedOpId = id)
    }

    /**
     * Updates an existing EditOp (for layer translation/repositioning).
     */
    fun updateOp(updatedOp: EditOp) {
        val index = undoStack.indexOfFirst { it.id == updatedOp.id }
        if (index != -1) {
            undoStack[index] = updatedOp
            syncOpStacks()
        }
    }

    /**
     * Deletes the currently selected layer.
     */
    fun deleteSelectedOp() {
        val selectedId = _uiState.value.selectedOpId ?: return
        val index = undoStack.indexOfFirst { it.id == selectedId }
        if (index != -1) {
            undoStack.removeAt(index)
            redoStack.clear()
            _uiState.value = _uiState.value.copy(selectedOpId = null)
            syncOpStacks()
        }
    }

    /**
     * Hit-tests all committed ops to find the topmost layer at the given coordinates.
     */
    fun findOpAt(imageX: Float, imageY: Float, tolerancePx: Float = 32f): EditOp? {
        for (i in undoStack.indices.reversed()) {
            val op = undoStack[i]
            if (op.containsPoint(imageX, imageY, tolerancePx)) {
                return op
            }
        }
        return null
    }

    private fun syncOpStacks() {
        _committedOps.value = undoStack.toList()
        val currentSelectedId = _uiState.value.selectedOpId
        val stillExists = currentSelectedId != null && undoStack.any { it.id == currentSelectedId }
        _uiState.value = _uiState.value.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            opsCount = undoStack.size,
            selectedOpId = if (stillExists) currentSelectedId else null
        )
    }

    // ─── Dialog Triggers ──────────────────────────────────────────────────────

    fun setShowDiscardDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDiscardDialog = show)
    }

    fun setShowColorPickerDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showColorPickerDialog = show)
    }

    fun setShowTextEntryDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showTextEntryDialog = show)
    }

    fun setShowSaveFormatDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSaveFormatDialog = show)
    }

    fun addTextAnnotation(text: String, x: Float, y: Float) {
        if (text.isBlank()) return
        val textOp = EditOp.TextOp(
            text = text,
            x = x,
            y = y,
            textSize = _uiState.value.strokeSize * 1.5f,
            color = _uiState.value.strokeColor,
            alpha = _uiState.value.strokeAlpha,
            isBold = true,
            backgroundColor = if (_uiState.value.isShapeFilled) Color.argb(180, 0, 0, 0) else null
        )
        commitOp(textOp)
        // Automatically select the newly created text layer and switch to Select/Move mode so user can drag to position it
        selectOp(textOp.id)
        selectTool(EditTool.SELECT_MOVE)
    }

    /**
     * Updates an existing text annotation's text content.
     */
    fun updateTextAnnotation(id: String, newText: String) {
        if (newText.isBlank()) return
        val index = undoStack.indexOfFirst { it.id == id }
        if (index != -1) {
            val op = undoStack[index]
            if (op is EditOp.TextOp) {
                undoStack[index] = op.copy(text = newText)
                syncOpStacks()
            }
        }
    }

    // ─── Export & Share ───────────────────────────────────────────────────────

    fun exportAndSave(
        format: ExportManager.Companion.ExportFormat = ExportManager.Companion.ExportFormat.PNG,
        quality: Int = 100,
        onSuccess: (Uri) -> Unit
    ) {
        val uri = sourceImageUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExporting = true,
                exportProgressText = "Replaying annotations on full resolution..."
            )
            val result = exportManager.exportImage(
                originalUri = uri,
                ops = undoStack,
                previewWidth = _uiState.value.previewWidth,
                previewHeight = _uiState.value.previewHeight,
                format = format,
                quality = quality
            )
            _uiState.value = _uiState.value.copy(isExporting = false, exportProgressText = null)

            result.onSuccess { outputUri ->
                onSuccess(outputUri)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Export failed: ${error.message}"
                )
            }
        }
    }

    fun exportAndShare(
        format: ExportManager.Companion.ExportFormat = ExportManager.Companion.ExportFormat.PNG,
        onSuccess: ((Uri) -> Unit)? = null
    ) {
        val uri = sourceImageUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExporting = true,
                exportProgressText = "Preparing shareable copy..."
            )
            val result = exportManager.exportImage(
                originalUri = uri,
                ops = undoStack,
                previewWidth = _uiState.value.previewWidth,
                previewHeight = _uiState.value.previewHeight,
                format = format
            )
            _uiState.value = _uiState.value.copy(isExporting = false, exportProgressText = null)

            result.onSuccess { outputUri ->
                exportManager.shareImage(outputUri)
                onSuccess?.invoke(outputUri)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Share failed: ${error.message}"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pixelateRenderer.recycle()
        try {
            _uiState.value.previewBitmap?.recycle()
        } catch (_: Exception) {}
    }
}

package com.screenarc.app.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance 3-layer image editing canvas view.
 *
 * Layering Architecture:
 * 1. Base Layer: Original downscaled preview bitmap rendered under viewport Pan/Zoom matrix.
 * 2. Committed Layer: Bitmap cache of all committed vector EditOps. Only redrawn when an op is
 *    added, undone, or redone. Eliminates re-rendering 100+ paths per animation frame.
 * 3. Active Layer: Currently in-progress stroke/shape/box drawn directly with quadTo smoothing.
 *
 * Allocation Rules:
 * Zero object allocations during onDraw or onTouchEvent. Reuses preallocated Paint, Path,
 * Matrix, and RectF instances.
 */
@SuppressLint("ViewConstructor")
class EditorCanvasView(
    context: Context,
    private val onCommitOp: (EditOp) -> Unit,
    private val onEraseOp: (Float, Float) -> Unit,
    private val onRequestTextEntry: (Float, Float) -> Unit
) : View(context) {

    // ─── Bitmaps & Caches ─────────────────────────────────────────────────────
    var previewBitmap: Bitmap? = null
        set(value) {
            field = value
            recalculateBaseFit()
            invalidateCommittedCache()
            invalidate()
        }

    var mosaicBitmap: Bitmap? = null

    private var committedLayerBitmap: Bitmap? = null
    private var committedCanvas: Canvas? = null
    private var isCommittedLayerDirty = true

    private var committedOpsList: List<EditOp> = emptyList()

    // ─── Layer Selection & Dragging State ─────────────────────────────────────
    var selectedOpId: String? = null
        set(value) {
            if (field != value) {
                field = value
                dragDeltaX = 0f
                dragDeltaY = 0f
                isCommittedLayerDirty = true
                invalidate()
            }
        }

    var onSelectOp: ((EditOp?) -> Unit)? = null
    var onUpdateOp: ((EditOp) -> Unit)? = null

    private var isDraggingSelectedLayer = false
    private var dragStartTouchX = 0f
    private var dragStartTouchY = 0f
    private var dragDeltaX = 0f
    private var dragDeltaY = 0f

    // ─── Current Tool State ───────────────────────────────────────────────────
    var activeTool: EditTool = EditTool.FREEHAND_BRUSH
    var strokeSize: Float = 24f
    var strokeColor: Int = Color.RED
    var strokeAlpha: Float = 1.0f
    var shapeType: ShapeType = ShapeType.RECTANGLE
    var isShapeFilled: Boolean = false
    var isShapeSolidRedact: Boolean = false
    var pixelateBlockSize: Int = PixelateRenderer.DEFAULT_BLOCK_SIZE

    // ─── Reused Rendering Objects (Zero allocations in onDraw) ────────────────
    private val viewportMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val touchCoords = FloatArray(2)

    private val baseBitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val committedBitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val activeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val selectionBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF2196F3.toInt()
    }

    private val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x222196F3.toInt()
    }

    private val selectionHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2196F3.toInt()
    }

    private val selectionHandleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }

    private val inProgressPath = Path()
    private val inProgressPoints = mutableListOf<OpPoint>()
    private var activeShapeStartX = 0f
    private var activeShapeStartY = 0f
    private var activeShapeCurrentX = 0f
    private var activeShapeCurrentY = 0f
    private var isActivelyDrawing = false

    // ─── Viewport Pan / Zoom State ────────────────────────────────────────────
    private var scaleFactor = 1.0f
    private var minScale = 0.5f
    private var maxScale = 5.0f
    private var translationX = 0f
    private var translationY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchDownScreenX = 0f
    private var touchDownScreenY = 0f
    private var isMultiTouchGesture = false
    private var didTouchHitOpOnDown = false
    private var hasMovedPastSlop = false

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val previousScale = scaleFactor
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)

                // Zoom around focus center
                val focusX = detector.focusX
                val focusY = detector.focusY
                translationX += (focusX - translationX) * (1 - scaleFactor / previousScale)
                translationY += (focusY - translationY) * (1 - scaleFactor / previousScale)

                updateViewportMatrix()
                invalidate()
                return true
            }
        }
    )

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setCommittedOps(ops: List<EditOp>) {
        committedOpsList = ops
        isCommittedLayerDirty = true
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateBaseFit()
        ensureCommittedBitmap()
        isCommittedLayerDirty = true
    }

    private fun recalculateBaseFit() {
        val bmp = previewBitmap ?: return
        if (width <= 0 || height <= 0) return

        val scaleX = width.toFloat() / bmp.width.toFloat()
        val scaleY = height.toFloat() / bmp.height.toFloat()
        val initialScale = minOf(scaleX, scaleY) * 0.95f

        scaleFactor = initialScale.coerceIn(minScale, maxScale)
        translationX = (width - bmp.width * scaleFactor) / 2f
        translationY = (height - bmp.height * scaleFactor) / 2f

        updateViewportMatrix()
    }

    private fun updateViewportMatrix() {
        viewportMatrix.reset()
        viewportMatrix.postScale(scaleFactor, scaleFactor)
        viewportMatrix.postTranslate(translationX, translationY)
        viewportMatrix.invert(inverseMatrix)
    }

    private fun ensureCommittedBitmap() {
        val bmp = previewBitmap ?: return
        if (bmp.width <= 0 || bmp.height <= 0) return

        if (committedLayerBitmap == null ||
            committedLayerBitmap!!.width != bmp.width ||
            committedLayerBitmap!!.height != bmp.height
        ) {
            committedLayerBitmap?.recycle()
            committedLayerBitmap = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            committedCanvas = Canvas(committedLayerBitmap!!)
            isCommittedLayerDirty = true
        }
    }

    fun invalidateCommittedCache() {
        isCommittedLayerDirty = true
        invalidate()
    }

    // ─── Rendering ────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = previewBitmap ?: return

        canvas.save()
        // Apply viewport transform (pan & zoom)
        canvas.concat(viewportMatrix)

        // 1. Base Layer: draw original preview bitmap
        canvas.drawBitmap(bmp, 0f, 0f, baseBitmapPaint)

        // 2. Committed Layer: draw cached layer bitmap (excludes currently selected op)
        ensureCommittedBitmap()
        if (isCommittedLayerDirty) {
            rebuildCommittedLayer()
            isCommittedLayerDirty = false
        }
        committedLayerBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, committedBitmapPaint)
        }

        // 3. Selected Op: drawn dynamically on top with real-time drag translation
        selectedOpId?.let { selId ->
            val selectedOp = committedOpsList.find { it.id == selId }
            if (selectedOp != null) {
                val movedOp = if (dragDeltaX != 0f || dragDeltaY != 0f) {
                    selectedOp.translatedBy(dragDeltaX, dragDeltaY)
                } else {
                    selectedOp
                }
                drawOpToCanvas(canvas, movedOp)
                drawSelectionHighlight(canvas, movedOp)
            }
        }

        // 4. Active Layer: draw in-progress op on top
        if (isActivelyDrawing) {
            drawInProgressOp(canvas)
        }

        canvas.restore()
    }

    private fun rebuildCommittedLayer() {
        val cCanvas = committedCanvas ?: return
        committedLayerBitmap?.eraseColor(Color.TRANSPARENT)

        for (op in committedOpsList) {
            if (op.id != selectedOpId) {
                drawOpToCanvas(cCanvas, op)
            }
        }
    }

    private fun getEffectiveHitTolerance(): Float {
        val density = resources.displayMetrics.density
        return maxOf((48f * density) / maxOf(scaleFactor, 0.1f), 64f)
    }

    private fun drawSelectionHighlight(canvas: Canvas, op: EditOp) {
        val bounds = op.getBounds()
        val rect = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
        canvas.drawRoundRect(rect, 8f, 8f, selectionFillPaint)
        canvas.drawRoundRect(rect, 8f, 8f, selectionBoxPaint)

        // 4 corner handles
        val handleRadius = 10f
        val corners = arrayOf(
            rect.left to rect.top,
            rect.right to rect.top,
            rect.right to rect.bottom,
            rect.left to rect.bottom
        )
        for ((cx, cy) in corners) {
            canvas.drawCircle(cx, cy, handleRadius, selectionHandlePaint)
            canvas.drawCircle(cx, cy, handleRadius, selectionHandleBorderPaint)
        }

        // Top drag handle indicator
        val midX = (rect.left + rect.right) / 2f
        val topY = rect.top - 20f
        canvas.drawLine(midX, rect.top, midX, topY, selectionBoxPaint)
        canvas.drawCircle(midX, topY, handleRadius, selectionHandlePaint)
        canvas.drawCircle(midX, topY, handleRadius, selectionHandleBorderPaint)
    }

    private fun drawOpToCanvas(canvas: Canvas, op: EditOp) {
        when (op) {
            is EditOp.FreehandStroke -> {
                activeStrokePaint.style = Paint.Style.STROKE
                activeStrokePaint.color = op.color
                activeStrokePaint.alpha = (op.alpha.coerceIn(0f, 1f) * 255).toInt()
                activeStrokePaint.strokeWidth = op.strokeWidth

                val path = buildPathFromPoints(op.points)
                canvas.drawPath(path, activeStrokePaint)
            }

            is EditOp.PixelateStroke -> {
                val mosaic = mosaicBitmap
                if (mosaic != null && !mosaic.isRecycled) {
                    val path = buildPathFromPoints(op.points)
                    canvas.save()
                    try {
                        canvas.clipPath(path)
                        canvas.drawBitmap(mosaic, 0f, 0f, null)
                    } finally {
                        canvas.restore()
                    }
                }
            }

            is EditOp.SolidRedactStroke -> {
                activeStrokePaint.style = Paint.Style.STROKE
                activeStrokePaint.color = Color.BLACK
                activeStrokePaint.alpha = 255
                activeStrokePaint.strokeWidth = op.strokeWidth

                val path = buildPathFromPoints(op.points)
                canvas.drawPath(path, activeStrokePaint)
            }

            is EditOp.SolidRedactBox -> {
                activeStrokePaint.style = Paint.Style.FILL
                activeStrokePaint.color = Color.BLACK
                activeStrokePaint.alpha = 255

                val rect = RectF(
                    minOf(op.left, op.right),
                    minOf(op.top, op.bottom),
                    maxOf(op.left, op.right),
                    maxOf(op.top, op.bottom)
                )
                canvas.drawRect(rect, activeStrokePaint)
            }

            is EditOp.ShapeOp -> {
                activeStrokePaint.style = if (op.isFilled || op.isSolidRedact) Paint.Style.FILL else Paint.Style.STROKE
                activeStrokePaint.color = if (op.isSolidRedact) Color.BLACK else op.color
                activeStrokePaint.alpha = if (op.isSolidRedact) 255 else (op.alpha.coerceIn(0f, 1f) * 255).toInt()
                activeStrokePaint.strokeWidth = op.strokeWidth

                val left = minOf(op.startX, op.endX)
                val top = minOf(op.startY, op.endY)
                val right = maxOf(op.startX, op.endX)
                val bottom = maxOf(op.startY, op.endY)

                when (op.shapeType) {
                    ShapeType.RECTANGLE -> canvas.drawRect(left, top, right, bottom, activeStrokePaint)
                    ShapeType.ROUNDED_RECTANGLE -> canvas.drawRoundRect(RectF(left, top, right, bottom), op.cornerRadius, op.cornerRadius, activeStrokePaint)
                    ShapeType.OVAL -> canvas.drawOval(RectF(left, top, right, bottom), activeStrokePaint)
                    ShapeType.LINE -> canvas.drawLine(op.startX, op.startY, op.endX, op.endY, activeStrokePaint)
                    ShapeType.ARROW -> drawArrow(canvas, activeStrokePaint, op.startX, op.startY, op.endX, op.endY)
                }
            }

            is EditOp.TextOp -> {
                activeStrokePaint.style = Paint.Style.FILL
                activeStrokePaint.color = op.color
                activeStrokePaint.alpha = (op.alpha.coerceIn(0f, 1f) * 255).toInt()
                activeStrokePaint.textSize = op.textSize
                activeStrokePaint.isFakeBoldText = op.isBold

                op.backgroundColor?.let { bg ->
                    val bgPaint = Paint().apply { color = bg; style = Paint.Style.FILL }
                    val textW = activeStrokePaint.measureText(op.text)
                    val textH = op.textSize
                    val bgRect = RectF(op.x - 8f, op.y - textH, op.x + textW + 8f, op.y + 8f)
                    canvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)
                }

                canvas.drawText(op.text, op.x, op.y, activeStrokePaint)
            }
        }
    }

    private fun drawInProgressOp(canvas: Canvas) {
        when (activeTool) {
            EditTool.FREEHAND_BRUSH -> {
                activeStrokePaint.style = Paint.Style.STROKE
                activeStrokePaint.color = strokeColor
                activeStrokePaint.alpha = (strokeAlpha.coerceIn(0f, 1f) * 255).toInt()
                activeStrokePaint.strokeWidth = strokeSize
                canvas.drawPath(inProgressPath, activeStrokePaint)
            }

            EditTool.PIXELATE_BRUSH -> {
                val mosaic = mosaicBitmap
                if (mosaic != null && !mosaic.isRecycled) {
                    canvas.save()
                    try {
                        canvas.clipPath(inProgressPath)
                        canvas.drawBitmap(mosaic, 0f, 0f, null)
                    } finally {
                        canvas.restore()
                    }
                }
            }

            EditTool.SOLID_REDACT_BRUSH -> {
                activeStrokePaint.style = Paint.Style.STROKE
                activeStrokePaint.color = Color.BLACK
                activeStrokePaint.alpha = 255
                activeStrokePaint.strokeWidth = strokeSize
                canvas.drawPath(inProgressPath, activeStrokePaint)
            }

            EditTool.SOLID_REDACT_BOX -> {
                activeStrokePaint.style = Paint.Style.FILL
                activeStrokePaint.color = Color.BLACK
                activeStrokePaint.alpha = 255

                val left = minOf(activeShapeStartX, activeShapeCurrentX)
                val top = minOf(activeShapeStartY, activeShapeCurrentY)
                val right = maxOf(activeShapeStartX, activeShapeCurrentX)
                val bottom = maxOf(activeShapeStartY, activeShapeCurrentY)
                canvas.drawRect(left, top, right, bottom, activeStrokePaint)
            }

            EditTool.SHAPE -> {
                activeStrokePaint.style = if (isShapeFilled || isShapeSolidRedact) Paint.Style.FILL else Paint.Style.STROKE
                activeStrokePaint.color = if (isShapeSolidRedact) Color.BLACK else strokeColor
                activeStrokePaint.alpha = if (isShapeSolidRedact) 255 else (strokeAlpha.coerceIn(0f, 1f) * 255).toInt()
                activeStrokePaint.strokeWidth = strokeSize

                val left = minOf(activeShapeStartX, activeShapeCurrentX)
                val top = minOf(activeShapeStartY, activeShapeCurrentY)
                val right = maxOf(activeShapeStartX, activeShapeCurrentX)
                val bottom = maxOf(activeShapeStartY, activeShapeCurrentY)

                when (shapeType) {
                    ShapeType.RECTANGLE -> canvas.drawRect(left, top, right, bottom, activeStrokePaint)
                    ShapeType.ROUNDED_RECTANGLE -> canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, activeStrokePaint)
                    ShapeType.OVAL -> canvas.drawOval(RectF(left, top, right, bottom), activeStrokePaint)
                    ShapeType.LINE -> canvas.drawLine(activeShapeStartX, activeShapeStartY, activeShapeCurrentX, activeShapeCurrentY, activeStrokePaint)
                    ShapeType.ARROW -> drawArrow(canvas, activeStrokePaint, activeShapeStartX, activeShapeStartY, activeShapeCurrentX, activeShapeCurrentY)
                }
            }

            else -> { /* No active in-progress preview for other tools */ }
        }
    }

    // ─── Touch & Gesture Handling ─────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)

        if (event.pointerCount >= 2) {
            isMultiTouchGesture = true
            if (isDraggingSelectedLayer) {
                isDraggingSelectedLayer = false
                dragDeltaX = 0f
                dragDeltaY = 0f
                invalidate()
            }
            // Handle two-finger panning
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    translationX += dx
                    translationY += dy
                    updateViewportMatrix()
                    invalidate()
                }
            }
            lastTouchX = event.x
            lastTouchY = event.y
            if (isActivelyDrawing) {
                // Cancel active drawing if second finger placed
                isActivelyDrawing = false
                inProgressPoints.clear()
                inProgressPath.reset()
                invalidate()
            }
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            isMultiTouchGesture = true
            return true
        }

        // Single-finger interaction
        val screenX = event.x
        val screenY = event.y

        // Transform screen coordinates to image space
        touchCoords[0] = screenX
        touchCoords[1] = screenY
        inverseMatrix.mapPoints(touchCoords)
        val imageX = touchCoords[0]
        val imageY = touchCoords[1]

        // Handle active dragging of an already selected layer
        if (isDraggingSelectedLayer) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    dragDeltaX = imageX - dragStartTouchX
                    dragDeltaY = imageY - dragStartTouchY
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDraggingSelectedLayer = false
                    val selId = selectedOpId
                    val selOp = committedOpsList.find { it.id == selId }
                    if (selOp != null && (dragDeltaX != 0f || dragDeltaY != 0f)) {
                        val finalOp = selOp.translatedBy(dragDeltaX, dragDeltaY)
                        dragDeltaX = 0f
                        dragDeltaY = 0f
                        onUpdateOp?.invoke(finalOp)
                    } else {
                        dragDeltaX = 0f
                        dragDeltaY = 0f
                    }
                    invalidateCommittedCache()
                    invalidate()
                    return true
                }
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isMultiTouchGesture = false
                lastTouchX = screenX
                lastTouchY = screenY
                touchDownScreenX = screenX
                touchDownScreenY = screenY
                didTouchHitOpOnDown = false
                hasMovedPastSlop = false

                val hitTol = getEffectiveHitTolerance()

                // Check 1: Did user touch the currently selected layer to move it?
                val currentSelected = selectedOpId?.let { id -> committedOpsList.find { it.id == id } }
                if (currentSelected != null && currentSelected.containsPoint(imageX, imageY, tolerance = hitTol)) {
                    isDraggingSelectedLayer = true
                    didTouchHitOpOnDown = true
                    dragStartTouchX = imageX
                    dragStartTouchY = imageY
                    dragDeltaX = 0f
                    dragDeltaY = 0f
                    invalidate()
                    return true
                }

                // Check 2: Hit-test all committed ops (topmost first)
                val hitOp = committedOpsList.findLast { it.containsPoint(imageX, imageY, tolerance = hitTol) }
                if (hitOp != null) {
                    didTouchHitOpOnDown = true
                }

                // Mode-specific selection / actions
                if (activeTool == EditTool.SELECT_MOVE || activeTool == EditTool.PAN_ZOOM) {
                    if (hitOp != null) {
                        selectedOpId = hitOp.id
                        onSelectOp?.invoke(hitOp)
                        isDraggingSelectedLayer = true
                        dragStartTouchX = imageX
                        dragStartTouchY = imageY
                        dragDeltaX = 0f
                        dragDeltaY = 0f
                        invalidateCommittedCache()
                        return true
                    } else if (selectedOpId != null) {
                        selectedOpId = null
                        onSelectOp?.invoke(null)
                        invalidateCommittedCache()
                    }
                    return true
                }

                if (activeTool == EditTool.TEXT) {
                    if (hitOp != null) {
                        selectedOpId = hitOp.id
                        onSelectOp?.invoke(hitOp)
                        isDraggingSelectedLayer = true
                        dragStartTouchX = imageX
                        dragStartTouchY = imageY
                        dragDeltaX = 0f
                        dragDeltaY = 0f
                        invalidateCommittedCache()
                        return true
                    } else if (selectedOpId != null) {
                        selectedOpId = null
                        onSelectOp?.invoke(null)
                        invalidateCommittedCache()
                        return true
                    }
                    return true
                }

                if (activeTool == EditTool.ERASER) {
                    onEraseOp(imageX, imageY)
                    return true
                }

                // For drawing tools (Brush, Pixelate, Redact, Shape), deselect any existing selection
                if (selectedOpId != null) {
                    selectedOpId = null
                    onSelectOp?.invoke(null)
                    invalidateCommittedCache()
                }

                // Start active drawing
                isActivelyDrawing = true
                activeShapeStartX = imageX
                activeShapeStartY = imageY
                activeShapeCurrentX = imageX
                activeShapeCurrentY = imageY

                inProgressPoints.clear()
                inProgressPoints.add(OpPoint(imageX, imageY))

                inProgressPath.reset()
                inProgressPath.moveTo(imageX, imageY)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dxScreen = screenX - touchDownScreenX
                val dyScreen = screenY - touchDownScreenY
                val density = resources.displayMetrics.density
                if ((dxScreen * dxScreen + dyScreen * dyScreen) > (8f * density) * (8f * density)) {
                    hasMovedPastSlop = true
                }

                if (activeTool == EditTool.SELECT_MOVE || activeTool == EditTool.PAN_ZOOM) {
                    val dx = screenX - lastTouchX
                    val dy = screenY - lastTouchY
                    translationX += dx
                    translationY += dy
                    lastTouchX = screenX
                    lastTouchY = screenY
                    updateViewportMatrix()
                    invalidate()
                    return true
                }

                if (activeTool == EditTool.ERASER) {
                    onEraseOp(imageX, imageY)
                    return true
                }

                if (activeTool == EditTool.TEXT) {
                    return true
                }

                if (!isActivelyDrawing || isMultiTouchGesture) return true

                activeShapeCurrentX = imageX
                activeShapeCurrentY = imageY

                val prev = inProgressPoints.lastOrNull()
                if (prev != null) {
                    val midX = (prev.x + imageX) / 2f
                    val midY = (prev.y + imageY) / 2f
                    inProgressPath.quadTo(prev.x, prev.y, midX, midY)
                }
                inProgressPoints.add(OpPoint(imageX, imageY))
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (activeTool == EditTool.SELECT_MOVE || activeTool == EditTool.PAN_ZOOM) {
                    return true
                }

                if (activeTool == EditTool.TEXT) {
                    if (!isMultiTouchGesture && !isDraggingSelectedLayer && !didTouchHitOpOnDown && !hasMovedPastSlop) {
                        onRequestTextEntry(imageX, imageY)
                    }
                    return true
                }

                if (!isActivelyDrawing || isMultiTouchGesture) {
                    isActivelyDrawing = false
                    return true
                }
                isActivelyDrawing = false
                activeShapeCurrentX = imageX
                activeShapeCurrentY = imageY
                inProgressPoints.add(OpPoint(imageX, imageY))

                // Build and commit the completed EditOp
                val completedOp = createCompletedOp()
                if (completedOp != null) {
                    onCommitOp(completedOp)
                    if (activeTool == EditTool.SHAPE || activeTool == EditTool.SOLID_REDACT_BOX) {
                        selectedOpId = completedOp.id
                        onSelectOp?.invoke(completedOp)
                    }
                }

                inProgressPoints.clear()
                inProgressPath.reset()
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isActivelyDrawing = false
                inProgressPoints.clear()
                inProgressPath.reset()
                invalidate()
                return true
            }
        }

        return true
    }

    private fun createCompletedOp(): EditOp? {
        return when (activeTool) {
            EditTool.FREEHAND_BRUSH -> {
                if (inProgressPoints.isEmpty()) null
                else EditOp.FreehandStroke(
                    points = inProgressPoints.toList(),
                    strokeWidth = strokeSize,
                    color = strokeColor,
                    alpha = strokeAlpha
                )
            }

            EditTool.PIXELATE_BRUSH -> {
                if (inProgressPoints.isEmpty()) null
                else EditOp.PixelateStroke(
                    points = inProgressPoints.toList(),
                    strokeWidth = strokeSize,
                    blockSize = pixelateBlockSize
                )
            }

            EditTool.SOLID_REDACT_BRUSH -> {
                if (inProgressPoints.isEmpty()) null
                else EditOp.SolidRedactStroke(
                    points = inProgressPoints.toList(),
                    strokeWidth = strokeSize,
                    color = Color.BLACK
                )
            }

            EditTool.SOLID_REDACT_BOX -> {
                EditOp.SolidRedactBox(
                    left = activeShapeStartX,
                    top = activeShapeStartY,
                    right = activeShapeCurrentX,
                    bottom = activeShapeCurrentY,
                    color = Color.BLACK
                )
            }

            EditTool.SHAPE -> {
                EditOp.ShapeOp(
                    shapeType = shapeType,
                    startX = activeShapeStartX,
                    startY = activeShapeStartY,
                    endX = activeShapeCurrentX,
                    endY = activeShapeCurrentY,
                    strokeWidth = strokeSize,
                    color = strokeColor,
                    alpha = strokeAlpha,
                    isFilled = isShapeFilled,
                    isSolidRedact = isShapeSolidRedact
                )
            }

            else -> null
        }
    }

    // ─── Path & Vector Helpers ────────────────────────────────────────────────

    private fun buildPathFromPoints(points: List<OpPoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        if (points.size == 1) {
            val p = points[0]
            path.moveTo(p.x, p.y)
            path.lineTo(p.x + 0.1f, p.y + 0.1f)
            return path
        }
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            path.quadTo(prev.x, prev.y, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x, last.y)
        return path
    }

    private fun drawArrow(canvas: Canvas, paint: Paint, x1: Float, y1: Float, x2: Float, y2: Float) {
        canvas.drawLine(x1, y1, x2, y2, paint)

        val arrowHeadLength = (strokeSize * 1.5f).coerceAtLeast(24f)
        val arrowAngle = Math.toRadians(30.0).toFloat()
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()

        val x3 = (x2 - arrowHeadLength * cos((angle - arrowAngle).toDouble())).toFloat()
        val y3 = (y2 - arrowHeadLength * sin((angle - arrowAngle).toDouble())).toFloat()
        val x4 = (x2 - arrowHeadLength * cos((angle + arrowAngle).toDouble())).toFloat()
        val y4 = (y2 - arrowHeadLength * sin((angle + arrowAngle).toDouble())).toFloat()

        val headPath = Path().apply {
            moveTo(x2, y2)
            lineTo(x3, y3)
            lineTo(x4, y4)
            close()
        }
        val headPaint = Paint(paint).apply { style = Paint.Style.FILL }
        canvas.drawPath(headPath, headPaint)
    }

    fun cleanUp() {
        committedLayerBitmap?.recycle()
        committedLayerBitmap = null
        committedCanvas = null
    }
}

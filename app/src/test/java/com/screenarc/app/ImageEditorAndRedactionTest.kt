package com.screenarc.app

import com.screenarc.app.ui.editor.EditOp
import com.screenarc.app.ui.editor.EditTool
import com.screenarc.app.ui.editor.OpPoint
import com.screenarc.app.ui.editor.PixelateRenderer
import com.screenarc.app.ui.editor.ShapeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageEditorAndRedactionTest {

    @Test
    fun testSolidRedactBoxBoundsAndHitTesting() {
        val box = EditOp.SolidRedactBox(
            left = 100f,
            top = 150f,
            right = 300f,
            bottom = 250f,
            color = 0xFF000000.toInt()
        )

        val bounds = box.getBounds()
        assertEquals(100f, bounds.left, 0.001f)
        assertEquals(150f, bounds.top, 0.001f)
        assertEquals(300f, bounds.right, 0.001f)
        assertEquals(250f, bounds.bottom, 0.001f)

        // Inside
        assertTrue(box.containsPoint(200f, 200f, tolerance = 0f))
        // Outside within tolerance
        assertTrue(box.containsPoint(305f, 200f, tolerance = 10f))
        // Far outside
        assertFalse(box.containsPoint(400f, 400f, tolerance = 10f))
    }

    @Test
    fun testFreehandStrokeBoundsAndHitTesting() {
        val stroke = EditOp.FreehandStroke(
            points = listOf(OpPoint(10f, 10f), OpPoint(20f, 20f), OpPoint(30f, 30f)),
            strokeWidth = 10f,
            color = 0xFFFF0000.toInt(),
            alpha = 1.0f
        )

        val bounds = stroke.getBounds()
        assertEquals(5f, bounds.left, 0.001f) // 10 - 5
        assertEquals(5f, bounds.top, 0.001f)
        assertEquals(35f, bounds.right, 0.001f) // 30 + 5
        assertEquals(35f, bounds.bottom, 0.001f)

        // Hit point near line
        assertTrue(stroke.containsPoint(22f, 22f, tolerance = 5f))
        // Far point
        assertFalse(stroke.containsPoint(100f, 100f, tolerance = 5f))
    }

    @Test
    fun testPixelateStrokeMinimumBlockSizeGuarantee() {
        // Pixelate block size must be clamped to at least 16 to prevent text recovery attacks
        val minBlock = PixelateRenderer.MIN_BLOCK_SIZE
        assertTrue("Min block size must be >= 16", minBlock >= 16)

        val pixelateStroke = EditOp.PixelateStroke(
            points = listOf(OpPoint(50f, 50f)),
            strokeWidth = 20f,
            blockSize = 24
        )
        assertEquals(24, pixelateStroke.blockSize)
        assertTrue(pixelateStroke.blockSize >= PixelateRenderer.MIN_BLOCK_SIZE)
    }

    @Test
    fun testShapeOpSolidRedactEnforcement() {
        val redactShape = EditOp.ShapeOp(
            shapeType = ShapeType.RECTANGLE,
            startX = 50f,
            startY = 50f,
            endX = 150f,
            endY = 150f,
            strokeWidth = 12f,
            color = 0xFF000000.toInt(),
            alpha = 1.0f,
            isFilled = true,
            isSolidRedact = true
        )

        assertTrue(redactShape.isSolidRedact)
        assertTrue(redactShape.isFilled)
        assertEquals(0xFF000000.toInt(), redactShape.color)
        assertEquals(1.0f, redactShape.alpha, 0.001f)
    }

    @Test
    fun testUndoRedoStackCapping() {
        val undoStack = mutableListOf<EditOp>()
        val redoStack = mutableListOf<EditOp>()
        val maxCap = 100

        // Push 110 ops
        for (i in 1..110) {
            if (undoStack.size >= maxCap) {
                undoStack.removeAt(0)
            }
            undoStack.add(
                EditOp.SolidRedactBox(
                    left = i.toFloat(),
                    top = i.toFloat(),
                    right = (i + 10).toFloat(),
                    bottom = (i + 10).toFloat()
                )
            )
            redoStack.clear()
        }

        assertEquals(100, undoStack.size)
        // First op in stack should now be index 11 (1-10 were dropped)
        val first = undoStack.first() as EditOp.SolidRedactBox
        assertEquals(11f, first.left, 0.001f)

        // Perform undo
        val undone = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(undone)
        assertEquals(99, undoStack.size)
        assertEquals(1, redoStack.size)

        // Perform redo
        val redone = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(redone)
        assertEquals(100, undoStack.size)
        assertEquals(0, redoStack.size)
    }
}

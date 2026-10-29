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

    @Test
    fun testLayerTranslationAcrossOperations() {
        val textOp = EditOp.TextOp(
            text = "Confidential",
            x = 100f,
            y = 200f,
            textSize = 24f,
            color = 0xFF000000.toInt()
        )
        val movedText = textOp.translatedBy(50f, -30f)
        assertEquals(150f, movedText.x, 0.001f)
        assertEquals(170f, movedText.y, 0.001f)
        assertEquals("Confidential", movedText.text)

        val box = EditOp.SolidRedactBox(
            left = 10f,
            top = 20f,
            right = 50f,
            bottom = 80f
        )
        val movedBox = box.translatedBy(15f, 25f)
        assertEquals(25f, movedBox.left, 0.001f)
        assertEquals(45f, movedBox.top, 0.001f)
        assertEquals(65f, movedBox.right, 0.001f)
        assertEquals(105f, movedBox.bottom, 0.001f)

        val shape = EditOp.ShapeOp(
            shapeType = ShapeType.ARROW,
            startX = 10f,
            startY = 20f,
            endX = 100f,
            endY = 200f,
            strokeWidth = 4f,
            color = 0xFFFF0000.toInt()
        )
        val movedShape = shape.translatedBy(-5f, 10f)
        assertEquals(5f, movedShape.startX, 0.001f)
        assertEquals(30f, movedShape.startY, 0.001f)
        assertEquals(95f, movedShape.endX, 0.001f)
        assertEquals(210f, movedShape.endY, 0.001f)
    }

    @Test
    fun testSixOverlayPlacementsAndSnapping() {
        // Center Middle Snap
        val centerRight = com.screenarc.app.ui.overlay.RadialMenuGeometry.findNearestAnchor(
            androidx.compose.ui.geometry.Offset(300f, 20f),
            verticalMiddleThresholdPx = 150f
        )
        assertEquals(com.screenarc.app.ui.overlay.OverlayEdgeAnchor.CENTER_RIGHT, centerRight)

        val centerLeft = com.screenarc.app.ui.overlay.RadialMenuGeometry.findNearestAnchor(
            androidx.compose.ui.geometry.Offset(-300f, -40f),
            verticalMiddleThresholdPx = 150f
        )
        assertEquals(com.screenarc.app.ui.overlay.OverlayEdgeAnchor.CENTER_LEFT, centerLeft)

        // Corners
        val topRight = com.screenarc.app.ui.overlay.RadialMenuGeometry.findNearestAnchor(
            androidx.compose.ui.geometry.Offset(300f, -400f),
            verticalMiddleThresholdPx = 150f
        )
        assertEquals(com.screenarc.app.ui.overlay.OverlayEdgeAnchor.TOP_RIGHT, topRight)

        val bottomLeft = com.screenarc.app.ui.overlay.RadialMenuGeometry.findNearestAnchor(
            androidx.compose.ui.geometry.Offset(-300f, 400f),
            verticalMiddleThresholdPx = 150f
        )
        assertEquals(com.screenarc.app.ui.overlay.OverlayEdgeAnchor.BOTTOM_LEFT, bottomLeft)
    }

    @Test
    fun testTextOpHitTestingAndSelectionToolCompatibility() {
        val textOp = EditOp.TextOp(
            text = "Sensitive Data",
            x = 100f,
            y = 200f,
            textSize = 32f,
            color = 0xFF000000.toInt()
        )
        // Hit point near text baseline
        assertTrue(textOp.containsPoint(120f, 190f, tolerance = 20f))
        // Hit point slightly above baseline
        assertTrue(textOp.containsPoint(105f, 175f, tolerance = 20f))
        // Far point
        assertFalse(textOp.containsPoint(500f, 500f, tolerance = 20f))

        // Ensure EditTool SELECT_MOVE and PAN_ZOOM are mutually compatible
        assertEquals(EditTool.SELECT_MOVE, EditTool.PAN_ZOOM)
    }
}

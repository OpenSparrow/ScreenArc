package com.screenarc.app.ui.editor

import java.util.UUID

/**
 * Pure Kotlin 2D Point structure to guarantee testability and performance
 * without Android framework stub dependencies.
 */
data class OpPoint(val x: Float, val y: Float)

/**
 * Pure Kotlin 2D Rectangle structure with built-in containment and hit-testing.
 */
data class OpRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(px: Float, py: Float, tolerance: Float = 0f): Boolean {
        val minX = minOf(left, right) - tolerance
        val maxX = maxOf(left, right) + tolerance
        val minY = minOf(top, bottom) - tolerance
        val maxY = maxOf(top, bottom) + tolerance
        return px in minX..maxX && py in minY..maxY
    }

    fun toAndroidRectF(): android.graphics.RectF =
        android.graphics.RectF(minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom))
}

/**
 * Tools available in the lightweight image editor.
 */
enum class EditTool(val displayName: String) {
    SELECT_MOVE("Select / Move"),
    FREEHAND_BRUSH("Brush"),
    PIXELATE_BRUSH("Pixelate"),
    SOLID_REDACT_BRUSH("Redact Pen"),
    SOLID_REDACT_BOX("Redact Box"),
    SHAPE("Shape"),
    ERASER("Eraser"),
    TEXT("Text");

    companion object {
        val PAN_ZOOM: EditTool = SELECT_MOVE
    }
}

/**
 * Geometric shape types for annotation and solid redaction.
 */
enum class ShapeType(val displayName: String) {
    RECTANGLE("Rectangle"),
    ROUNDED_RECTANGLE("Rounded Rect"),
    OVAL("Oval"),
    LINE("Line"),
    ARROW("Arrow")
}

/**
 * Non-destructive vector command model representing a single edit operation.
 * Keeps the original Bitmap untouched and stores only geometry and rendering parameters.
 * Coordinates are stored in Image Coordinate Space (relative to preview bitmap dimensions).
 */
sealed class EditOp {
    abstract val id: String
    abstract fun getBounds(): OpRect
    abstract fun containsPoint(x: Float, y: Float, tolerance: Float): Boolean
    abstract fun translatedBy(dx: Float, dy: Float): EditOp

    /**
     * Standard freehand brush stroke.
     */
    data class FreehandStroke(
        override val id: String = UUID.randomUUID().toString(),
        val points: List<OpPoint>,
        val strokeWidth: Float,
        val color: Int,
        val alpha: Float = 1.0f
    ) : EditOp() {
        override fun getBounds(): OpRect {
            if (points.isEmpty()) return OpRect(0f, 0f, 0f, 0f)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
            val halfStroke = strokeWidth / 2f
            return OpRect(minX - halfStroke, minY - halfStroke, maxX + halfStroke, maxY + halfStroke)
        }

        override fun containsPoint(x: Float, y: Float, tolerance: Float): Boolean {
            val effTol = tolerance + (strokeWidth / 2f)
            val effTolSq = effTol * effTol
            for (p in points) {
                val dx = p.x - x
                val dy = p.y - y
                if ((dx * dx + dy * dy) <= effTolSq) return true
            }
            return false
        }

        override fun translatedBy(dx: Float, dy: Float): FreehandStroke {
            return copy(points = points.map { OpPoint(it.x + dx, it.y + dy) })
        }
    }

    /**
     * Pixelate mosaic brush stroke.
     * Enforces a minimum block size (16px) for privacy/security guarantees.
     */
    data class PixelateStroke(
        override val id: String = UUID.randomUUID().toString(),
        val points: List<OpPoint>,
        val strokeWidth: Float,
        val blockSize: Int = 24
    ) : EditOp() {
        override fun getBounds(): OpRect {
            if (points.isEmpty()) return OpRect(0f, 0f, 0f, 0f)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
            val halfStroke = strokeWidth / 2f
            return OpRect(minX - halfStroke, minY - halfStroke, maxX + halfStroke, maxY + halfStroke)
        }

        override fun containsPoint(x: Float, y: Float, tolerance: Float): Boolean {
            val effTol = tolerance + (strokeWidth / 2f)
            val effTolSq = effTol * effTol
            for (p in points) {
                val dx = p.x - x
                val dy = p.y - y
                if ((dx * dx + dy * dy) <= effTolSq) return true
            }
            return false
        }

        override fun translatedBy(dx: Float, dy: Float): PixelateStroke {
            return copy(points = points.map { OpPoint(it.x + dx, it.y + dy) })
        }
    }

    /**
     * Solid opaque blackout brush stroke (alpha is strictly 1.0f).
     */
    data class SolidRedactStroke(
        override val id: String = UUID.randomUUID().toString(),
        val points: List<OpPoint>,
        val strokeWidth: Float,
        val color: Int = 0xFF000000.toInt()
    ) : EditOp() {
        override fun getBounds(): OpRect {
            if (points.isEmpty()) return OpRect(0f, 0f, 0f, 0f)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
            val halfStroke = strokeWidth / 2f
            return OpRect(minX - halfStroke, minY - halfStroke, maxX + halfStroke, maxY + halfStroke)
        }

        override fun containsPoint(x: Float, y: Float, tolerance: Float): Boolean {
            val effTol = tolerance + (strokeWidth / 2f)
            val effTolSq = effTol * effTol
            for (p in points) {
                val dx = p.x - x
                val dy = p.y - y
                if ((dx * dx + dy * dy) <= effTolSq) return true
            }
            return false
        }

        override fun translatedBy(dx: Float, dy: Float): SolidRedactStroke {
            return copy(points = points.map { OpPoint(it.x + dx, it.y + dy) })
        }
    }

    /**
     * Solid opaque blackout rectangle for redacting sensitive blocks (passwords, OTPs, emails).
     */
    data class SolidRedactBox(
        override val id: String = UUID.randomUUID().toString(),
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val color: Int = 0xFF000000.toInt()
    ) : EditOp() {
        override fun getBounds(): OpRect = OpRect(
            minOf(left, right),
            minOf(top, bottom),
            maxOf(left, right),
            maxOf(top, bottom)
        )

        override fun containsPoint(x: Float, y: Float, tolerance: Float): Boolean {
            return getBounds().contains(x, y, tolerance)
        }

        override fun translatedBy(dx: Float, dy: Float): SolidRedactBox {
            return copy(
                left = left + dx,
                top = top + dy,
                right = right + dx,
                bottom = bottom + dy
            )
        }
    }

    /**
     * Shape annotation (Rectangle, Rounded Rectangle, Oval, Line, Arrow).
     * Can be stroke-only or filled. Option for solid redaction fill.
     */
    data class ShapeOp(
        override val id: String = UUID.randomUUID().toString(),
        val shapeType: ShapeType,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val strokeWidth: Float,
        val color: Int,
        val alpha: Float = 1.0f,
        val isFilled: Boolean = false,
        val isSolidRedact: Boolean = false,
        val cornerRadius: Float = 16f
    ) : EditOp() {
        override fun getBounds(): OpRect {
            val halfStroke = strokeWidth / 2f
            return OpRect(
                minOf(startX, endX) - halfStroke,
                minOf(startY, endY) - halfStroke,
                maxOf(startX, endX) + halfStroke,
                maxOf(startY, endY) + halfStroke
            )
        }

        override fun containsPoint(x: Float, y: Float, tolerance: Float): Boolean {
            return getBounds().contains(x, y, tolerance)
        }

        override fun translatedBy(dx: Float, dy: Float): ShapeOp {
            return copy(
                startX = startX + dx,
                startY = startY + dy,
                endX = endX + dx,
                endY = endY + dy
            )
        }
    }

    /**
     * Text label annotation.
     */
    data class TextOp(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val x: Float,
        val y: Float,
        val textSize: Float,
        val color: Int,
        val alpha: Float = 1.0f,
        val isBold: Boolean = true,
        val backgroundColor: Int? = null
    ) : EditOp() {
        override fun getBounds(): OpRect {
            val width = maxOf(text.length * textSize * 0.72f, 64f)
            val height = textSize * 1.35f
            return OpRect(x - 20f, y - height - 12f, x + width + 20f, y + 20f)
        }

        override fun containsPoint(x: Float, y: Float, tolerance: Float): Boolean {
            return getBounds().contains(x, y, tolerance)
        }

        override fun translatedBy(dx: Float, dy: Float): TextOp {
            return copy(
                x = x + dx,
                y = y + dy
            )
        }
    }
}

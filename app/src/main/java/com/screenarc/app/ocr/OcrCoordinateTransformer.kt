package com.screenarc.app.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.screenarc.app.model.OcrBlock

/**
 * Utility for mapping coordinates between original screenshot bitmap space
 * and screen/canvas view space under ContentScale.Fit, including zoom/pan transforms
 * and hit-testing for interactive tap-to-copy.
 */
object OcrCoordinateTransformer {

    data class ImageFitParams(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val displayedWidth: Float,
        val displayedHeight: Float
    )

    /**
     * Compute scale and centering offset for ContentScale.Fit inside container bounds.
     */
    fun computeFitParams(
        containerWidth: Float,
        containerHeight: Float,
        imageWidth: Float,
        imageHeight: Float
    ): ImageFitParams {
        if (containerWidth <= 0f || containerHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) {
            return ImageFitParams(scale = 1f, offsetX = 0f, offsetY = 0f, displayedWidth = 0f, displayedHeight = 0f)
        }

        val scaleX = containerWidth / imageWidth
        val scaleY = containerHeight / imageHeight
        val scale = minOf(scaleX, scaleY)

        val displayedWidth = imageWidth * scale
        val displayedHeight = imageHeight * scale
        val offsetX = (containerWidth - displayedWidth) / 2f
        val offsetY = (containerHeight - displayedHeight) / 2f

        return ImageFitParams(
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            displayedWidth = displayedWidth,
            displayedHeight = displayedHeight
        )
    }

    /**
     * Maps an OCR block from intrinsic image pixel coordinates to view canvas coordinates.
     */
    fun transformBlockToViewRect(
        block: OcrBlock,
        fitParams: ImageFitParams
    ): Rect? {
        val left = block.left ?: return null
        val top = block.top ?: return null
        val right = block.right ?: return null
        val bottom = block.bottom ?: return null

        val viewLeft = left * fitParams.scale + fitParams.offsetX
        val viewTop = top * fitParams.scale + fitParams.offsetY
        val viewRight = right * fitParams.scale + fitParams.offsetX
        val viewBottom = bottom * fitParams.scale + fitParams.offsetY

        return Rect(
            left = viewLeft,
            top = viewTop,
            right = viewRight,
            bottom = viewBottom
        )
    }

    /**
     * Maps a touch point in view coordinate space back to original image space.
     */
    fun mapTouchToImageCoords(
        touchX: Float,
        touchY: Float,
        fitParams: ImageFitParams
    ): Offset? {
        if (fitParams.scale <= 0f) return null
        val imgX = (touchX - fitParams.offsetX) / fitParams.scale
        val imgY = (touchY - fitParams.offsetY) / fitParams.scale
        return Offset(imgX, imgY)
    }

    /**
     * Hit tests a tap point against a list of OCR blocks.
     * Returns the matching OcrBlock, if any.
     */
    fun findBlockAtPoint(
        touchX: Float,
        touchY: Float,
        blocks: List<OcrBlock>,
        fitParams: ImageFitParams
    ): OcrBlock? {
        val imgPoint = mapTouchToImageCoords(touchX, touchY, fitParams) ?: return null
        // Search from smallest to largest or top-down so user gets most precise box
        return blocks
            .filter { block ->
                val l = block.left ?: return@filter false
                val t = block.top ?: return@filter false
                val r = block.right ?: return@filter false
                val b = block.bottom ?: return@filter false
                imgPoint.x in l.toFloat()..r.toFloat() && imgPoint.y in t.toFloat()..b.toFloat()
            }
            .minByOrNull { block ->
                val w = (block.right ?: 0) - (block.left ?: 0)
                val h = (block.bottom ?: 0) - (block.top ?: 0)
                w * h
            }
    }

    /**
     * Decodes the intrinsic dimensions of an image without loading its pixel bytes into memory.
     */
    fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

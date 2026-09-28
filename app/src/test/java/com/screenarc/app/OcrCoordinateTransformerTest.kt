package com.screenarc.app

import com.screenarc.app.model.OcrBlock
import com.screenarc.app.ocr.OcrCoordinateTransformer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCoordinateTransformerTest {

    @Test
    fun testPortraitImageOnPortraitContainer_letterboxedHorizontal() {
        // 1080x2400 screenshot displayed on 1080x1920 container
        // scale is determined by height (1920 / 2400 = 0.8)
        val fit = OcrCoordinateTransformer.computeFitParams(
            containerWidth = 1080f,
            containerHeight = 1920f,
            imageWidth = 1080f,
            imageHeight = 2400f
        )

        assertEquals(0.8f, fit.scale, 0.001f)
        assertEquals(864f, fit.displayedWidth, 0.001f)
        assertEquals(1920f, fit.displayedHeight, 0.001f)
        assertEquals(108f, fit.offsetX, 0.001f) // (1080 - 864) / 2
        assertEquals(0f, fit.offsetY, 0.001f)

        val block = OcrBlock(
            text = "Header Text",
            left = 100,
            top = 200,
            right = 500,
            bottom = 300
        )

        val rect = OcrCoordinateTransformer.transformBlockToViewRect(block, fit)
        assertNotNull(rect)
        assertEquals(100f * 0.8f + 108f, rect!!.left, 0.001f)
        assertEquals(200f * 0.8f + 0f, rect.top, 0.001f)
        assertEquals(500f * 0.8f + 108f, rect.right, 0.001f)
        assertEquals(300f * 0.8f + 0f, rect.bottom, 0.001f)
    }

    @Test
    fun testLandscapeImageOnPortraitContainer_letterboxedVertical() {
        // 2400x1080 screenshot displayed on 1080x1920 container
        // scale is determined by width (1080 / 2400 = 0.45)
        val fit = OcrCoordinateTransformer.computeFitParams(
            containerWidth = 1080f,
            containerHeight = 1920f,
            imageWidth = 2400f,
            imageHeight = 1080f
        )

        assertEquals(0.45f, fit.scale, 0.001f)
        assertEquals(1080f, fit.displayedWidth, 0.001f)
        assertEquals(486f, fit.displayedHeight, 0.001f)
        assertEquals(0f, fit.offsetX, 0.001f)
        assertEquals(717f, fit.offsetY, 0.001f) // (1920 - 486) / 2

        val block = OcrBlock(
            text = "Game Menu",
            left = 200,
            top = 100,
            right = 800,
            bottom = 400
        )

        val rect = OcrCoordinateTransformer.transformBlockToViewRect(block, fit)
        assertNotNull(rect)
        assertEquals(200f * 0.45f + 0f, rect!!.left, 0.001f)
        assertEquals(100f * 0.45f + 717f, rect.top, 0.001f)
        assertEquals(800f * 0.45f + 0f, rect.right, 0.001f)
        assertEquals(400f * 0.45f + 717f, rect.bottom, 0.001f)
    }

    @Test
    fun testSquareImageFitAndTransform() {
        // 1000x1000 screenshot displayed on 1080x1920 container
        // scale is determined by width (1080 / 1000 = 1.08)
        val fit = OcrCoordinateTransformer.computeFitParams(
            containerWidth = 1080f,
            containerHeight = 1920f,
            imageWidth = 1000f,
            imageHeight = 1000f
        )

        assertEquals(1.08f, fit.scale, 0.001f)
        assertEquals(1080f, fit.displayedWidth, 0.001f)
        assertEquals(1080f, fit.displayedHeight, 0.001f)
        assertEquals(0f, fit.offsetX, 0.001f)
        assertEquals(420f, fit.offsetY, 0.001f) // (1920 - 1080) / 2
    }

    @Test
    fun testHitTestingAndInverseMapping() {
        val fit = OcrCoordinateTransformer.computeFitParams(
            containerWidth = 1080f,
            containerHeight = 1920f,
            imageWidth = 1080f,
            imageHeight = 2400f
        ) // scale = 0.8, offsetX = 108, offsetY = 0

        val block1 = OcrBlock(text = "Parent Box", left = 100, top = 100, right = 800, bottom = 800)
        val block2 = OcrBlock(text = "Nested Button", left = 200, top = 200, right = 400, bottom = 300)
        val block3 = OcrBlock(text = "Footer Note", left = 100, top = 2000, right = 600, bottom = 2200)

        val blocks = listOf(block1, block2, block3)

        // Point inside Nested Button:
        // imgX = 250, imgY = 250
        // viewX = 250 * 0.8 + 108 = 308, viewY = 250 * 0.8 + 0 = 200
        val found = OcrCoordinateTransformer.findBlockAtPoint(
            touchX = 308f,
            touchY = 200f,
            blocks = blocks,
            fitParams = fit
        )

        assertNotNull(found)
        assertEquals("Nested Button", found?.text)

        // Point outside all blocks (e.g. viewX = 50, viewY = 50 -> imgX < 0)
        val outside = OcrCoordinateTransformer.findBlockAtPoint(
            touchX = 50f,
            touchY = 50f,
            blocks = blocks,
            fitParams = fit
        )
        assertNull(outside)

        // Point inside block3:
        // imgX = 300, imgY = 2100
        // viewX = 300 * 0.8 + 108 = 348, viewY = 2100 * 0.8 + 0 = 1680
        val found3 = OcrCoordinateTransformer.findBlockAtPoint(
            touchX = 348f,
            touchY = 1680f,
            blocks = blocks,
            fitParams = fit
        )
        assertNotNull(found3)
        assertEquals("Footer Note", found3?.text)
    }

    @Test
    fun testInvalidOrZeroBoundsSafety() {
        val zeroParams = OcrCoordinateTransformer.computeFitParams(0f, 0f, 100f, 100f)
        assertEquals(0f, zeroParams.displayedWidth, 0.001f)
        assertEquals(1f, zeroParams.scale, 0.001f)

        val nullBlock = OcrBlock(text = "Bad", left = null, top = null, right = null, bottom = null)
        val nullRect = OcrCoordinateTransformer.transformBlockToViewRect(nullBlock, zeroParams)
        assertNull(nullRect)
    }
}

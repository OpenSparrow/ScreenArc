package com.screenarc.app.stitch

import android.net.Uri
import com.screenarc.app.stitch.model.StitchConfig
import com.screenarc.app.stitch.model.StitchFormat
import com.screenarc.app.stitch.model.StitchImageItem
import com.screenarc.app.stitch.model.StitchMode
import com.screenarc.app.stitch.model.StitchSeam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StitchEngineTest {

    @Test
    fun testExactOverlapDetection() {
        val width = 50
        val heightA = 120
        val heightB = 120
        val overlapRows = 45

        // Create synthetic luminance grids
        val gridA = Array(heightA) { row ->
            IntArray(width) { col -> (row * 2 + col) % 256 }
        }

        val gridB = Array(heightB) { row ->
            if (row < overlapRows) {
                // Exact copy of bottom rows of A: row (heightA - overlapRows + row)
                val sourceRow = heightA - overlapRows + row
                gridA[sourceRow].clone()
            } else {
                // Different content below overlap
                IntArray(width) { col -> ((row + 100) * 3 + col) % 256 }
            }
        }

        val result = StitchEngine.detectVerticalOverlapFromLuminance(gridA, gridB)

        assertTrue("Expected confident match for exact overlap", result.isConfident)
        assertEquals("Detected overlap rows must match exact overlap", overlapRows, result.overlapRows)
        assertEquals("Mean difference for exact overlap must be 0", 0.0f, result.meanDifference, 0.01f)
    }

    @Test
    fun testNoisyOverlapDetection() {
        val width = 60
        val heightA = 100
        val heightB = 100
        val overlapRows = 35

        val rnd = Random(42)

        val gridA = Array(heightA) { row ->
            IntArray(width) { col -> (row * 3 + col * 2) % 256 }
        }

        val gridB = Array(heightB) { row ->
            if (row < overlapRows) {
                val sourceRow = heightA - overlapRows + row
                IntArray(width) { col ->
                    // Add small noise +/- 3
                    val noise = rnd.nextInt(-3, 4)
                    (gridA[sourceRow][col] + noise).coerceIn(0, 255)
                }
            } else {
                IntArray(width) { col -> ((row + 50) * 7) % 256 }
            }
        }

        val result = StitchEngine.detectVerticalOverlapFromLuminance(gridA, gridB)

        assertTrue("Expected confident match with mild noise", result.isConfident)
        assertEquals("Detected overlap rows must match target overlap", overlapRows, result.overlapRows)
        assertTrue("Mean difference should be low (<= 4.0), was ${result.meanDifference}", result.meanDifference <= 4.0f)
    }

    @Test
    fun testNoOverlapDetection() {
        val width = 40
        val heightA = 100
        val heightB = 100

        // Create completely disjoint gradients
        val gridA = Array(heightA) { row ->
            IntArray(width) { 50 }
        }

        val gridB = Array(heightB) { row ->
            IntArray(width) { 220 }
        }

        val result = StitchEngine.detectVerticalOverlapFromLuminance(gridA, gridB)

        assertFalse("Expected no confident match for disjoint patterns", result.isConfident)
        assertTrue("Difference should be high for disjoint patterns", result.meanDifference > 50.0f)
    }

    @Test
    fun testWidthNormalizationCalculation() {
        val mockUri = org.mockito.Mockito.mock(Uri::class.java)

        val items = listOf(
            StitchImageItem(1, mockUri, "A.png", originalWidth = 1080, originalHeight = 2400),
            StitchImageItem(2, mockUri, "B.png", originalWidth = 1440, originalHeight = 3200),
            StitchImageItem(3, mockUri, "C.png", originalWidth = 1080, originalHeight = 2400),
            StitchImageItem(4, mockUri, "D.png", originalWidth = 1080, originalHeight = 2340)
        )

        val targetWidth = StitchEngine.calculateTargetWidth(items)
        assertEquals(1080, targetWidth)
    }

    @Test
    fun testRowDifferenceCalculation() {
        val row1 = IntArray(10) { 100 }
        val row2 = IntArray(10) { 100 }
        val row3 = IntArray(10) { 150 }

        val diffIdentical = StitchEngine.computeRowDifference(row1, row2)
        assertEquals(0.0, diffIdentical, 0.001)

        val diffOffset = StitchEngine.computeRowDifference(row1, row3)
        assertEquals(50.0, diffOffset, 0.001)
    }

    @Test
    fun testCalculateTotalHeightManualModeWithGap() {
        val mockUri = org.mockito.Mockito.mock(Uri::class.java)

        val items = listOf(
            StitchImageItem(1, mockUri, "1.png", originalWidth = 1000, originalHeight = 1500),
            StitchImageItem(2, mockUri, "2.png", originalWidth = 1000, originalHeight = 1500)
        )

        val config = StitchConfig(
            mode = StitchMode.MANUAL,
            cropStatusBar = false,
            cropNavigationBar = false,
            manualGapPx = 20
        )

        val seams = listOf(
            StitchSeam(index = 0, overlapHeight = 0, manualOffsetAdjustment = 0)
        )

        val totalHeight = StitchEngine.calculateTotalHeight(items, seams, config, targetWidth = 1000)
        // 1500 + 1500 + 20 gap = 3020
        assertEquals(3020, totalHeight)
    }

    @Test
    fun testCalculateTotalHeightAutoModeWithOverlap() {
        val mockUri = org.mockito.Mockito.mock(Uri::class.java)

        val items = listOf(
            StitchImageItem(1, mockUri, "1.png", originalWidth = 1000, originalHeight = 1000),
            StitchImageItem(2, mockUri, "2.png", originalWidth = 1000, originalHeight = 1000)
        )

        val config = StitchConfig(
            mode = StitchMode.AUTO,
            cropStatusBar = false,
            cropNavigationBar = false
        )

        val seams = listOf(
            StitchSeam(index = 0, overlapHeight = 250, manualOffsetAdjustment = 0)
        )

        val totalHeight = StitchEngine.calculateTotalHeight(items, seams, config, targetWidth = 1000)
        // Item 1: 1000. Item 2: 1000 - 250 overlap = 750. Total = 1750.
        assertEquals(1750, totalHeight)
    }
}

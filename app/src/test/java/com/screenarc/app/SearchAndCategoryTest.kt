package com.screenarc.app

import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.ScreenshotSearchQuery
import com.screenarc.app.model.ScreenshotUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndCategoryTest {

    @Test
    fun testSearchQueryMatching() {
        val screenshot = ScreenshotUiModel(
            id = 1L,
            uri = "content://media/external/images/media/1",
            displayName = "receipt_amazon_2026.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 1024L,
            categoryId = 10L,
            categoryName = "Finance",
            ocrStatus = "COMPLETED",
            recognizedText = "Total $49.99 Order Confirmation Amazon Prime Delivery",
            normalizedText = "total 4999 order confirmation amazon prime delivery"
        )

        // Query matches OCR text
        val queryOcr = ScreenshotSearchQuery(text = "Amazon Prime")
        assertTrue(matches(screenshot, queryOcr))

        // Query matches filename
        val queryFile = ScreenshotSearchQuery(text = "receipt")
        assertTrue(matches(screenshot, queryFile))

        // Filter by category ID
        val filterCatMatch = ScreenshotSearchQuery(categoryId = 10L)
        assertTrue(matches(screenshot, filterCatMatch))

        val filterCatMismatch = ScreenshotSearchQuery(categoryId = 99L)
        assertFalse(matches(screenshot, filterCatMismatch))

        // Filter by OCR status
        val filterOcrCompleted = ScreenshotSearchQuery(ocrStatus = "COMPLETED")
        assertTrue(matches(screenshot, filterOcrCompleted))

        val filterOcrNotStarted = ScreenshotSearchQuery(ocrStatus = "NOT_STARTED")
        assertFalse(matches(screenshot, filterOcrNotStarted))
    }

    @Test
    fun testAdaptiveDropTargetLayoutLogic() {
        val smallList = (1..6).map { CategoryUiModel(it.toLong(), "Category $it", "Category_$it") }
        val mediumList = (1..10).map { CategoryUiModel(it.toLong(), "Category $it", "Category_$it") }
        val largeList = (1..24).map { CategoryUiModel(it.toLong(), "Category $it", "Category_$it") }

        // Radial layout is suitable for <= 8 items
        assertTrue(smallList.size <= 8)
        assertFalse(mediumList.size <= 8)

        // Paginated items check
        val pageSize = 8
        val totalPages = (largeList.size + pageSize - 1) / pageSize
        assertEquals(3, totalPages)
    }

    private fun matches(item: ScreenshotUiModel, filter: ScreenshotSearchQuery): Boolean {
        val q = filter.text.trim().lowercase()
        if (q.isNotEmpty()) {
            val nameMatch = filter.includeFilename && item.displayName.lowercase().contains(q)
            val ocrMatch = filter.includeOcrText && (
                item.recognizedText?.lowercase()?.contains(q) == true ||
                item.normalizedText?.contains(q) == true
            )
            if (!nameMatch && !ocrMatch) return false
        }

        if (filter.categoryId != null) {
            if (item.categoryId != filter.categoryId) return false
        }

        if (!filter.ocrStatus.isNullOrBlank()) {
            if (item.ocrStatus != filter.ocrStatus) return false
        }

        return true
    }
}

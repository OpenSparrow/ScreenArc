package com.screenarc.app

import androidx.compose.ui.geometry.Offset
import com.screenarc.app.model.ArcDesignStyle
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.ui.overlay.Handedness
import com.screenarc.app.ui.overlay.HoverTarget
import com.screenarc.app.ui.overlay.OverlayEdgeAnchor
import com.screenarc.app.ui.overlay.RadialMenuGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialArcRedesignAndDuplicatePreventionTest {

    @Test
    fun testRadialMenuGeometryOrbitLayout() {
        val categories = listOf(
            CategoryUiModel(1L, "Finance", "Finance"),
            CategoryUiModel(2L, "Work", "Work"),
            CategoryUiModel(3L, "Travel", "Travel")
        )
        val radiusPx = 300f
        val layout = RadialMenuGeometry.calculateLayout(categories, radiusPx, ArcDesignStyle.ORBIT)

        assertEquals(3, layout.categoriesWithPos.size)
        assertNotNull(layout.deletePos)
        assertNotNull(layout.sharePos)

        // Delete target must be stationed at the bottom (+y direction)
        assertTrue("Delete target y should be positive (bottom)", layout.deletePos.y > 0)
        // In Orbit mode, deletePos is at 90° (x is close to 0)
        assertTrue("Delete target x should be near 0", kotlin.math.abs(layout.deletePos.x) < 5f)

        // Category targets should be stationed in the upper arc (y should be negative)
        layout.categoriesWithPos.forEach { (cat, pos) ->
            assertTrue("Category ${cat.name} should be in upper arc (y < 0)", pos.y < 0)
        }
    }

    @Test
    fun testRadialMenuGeometryFanAndDialLayouts() {
        val categories = (1..4).map { CategoryUiModel(it.toLong(), "Cat $it", "Cat_$it") }
        val radiusPx = 250f

        // Fan Arc
        val fanLayout = RadialMenuGeometry.calculateLayout(categories, radiusPx, ArcDesignStyle.FAN)
        assertEquals(4, fanLayout.categoriesWithPos.size)
        assertTrue("Fan Arc delete pos y should be positive", fanLayout.deletePos.y > 0)

        // Dial Arc
        val dialLayout = RadialMenuGeometry.calculateLayout(categories, radiusPx, ArcDesignStyle.DIAL)
        assertEquals(4, dialLayout.categoriesWithPos.size)
        assertTrue("Dial Arc delete pos y should be positive", dialLayout.deletePos.y > 0)
    }

    @Test
    fun testCategoryOrderingByUsageAndSuggested() {
        val cat1 = CategoryUiModel(1L, "Tech", "Tech", orderIndex = 0)
        val cat2 = CategoryUiModel(2L, "Docs", "Docs", orderIndex = 1)
        val cat3 = CategoryUiModel(3L, "Memes", "Memes", orderIndex = 2)
        val cat4 = CategoryUiModel(4L, "Receipts", "Receipts", orderIndex = 3)

        val usageMap = mapOf(
            1L to 5,
            2L to 12,
            3L to 1,
            4L to 20
        )

        // With no suggested category, Receipts (20) -> Docs (12) -> Tech (5) -> Memes (1)
        val orderedByUsage = RadialMenuGeometry.orderCategories(listOf(cat1, cat2, cat3, cat4), usageMap)
        assertEquals(4L, orderedByUsage[0].id)
        assertEquals(2L, orderedByUsage[1].id)
        assertEquals(1L, orderedByUsage[2].id)
        assertEquals(3L, orderedByUsage[3].id)

        // With suggested category (e.g. Memes = 3L), it should jump to position 0
        val orderedWithSuggestion = RadialMenuGeometry.orderCategories(
            listOf(cat1, cat2, cat3, cat4),
            usageMap,
            suggestedCategoryId = 3L
        )
        assertEquals(3L, orderedWithSuggestion[0].id)
        assertEquals(4L, orderedWithSuggestion[1].id)
        assertEquals(2L, orderedWithSuggestion[2].id)
        assertEquals(1L, orderedWithSuggestion[3].id)
    }

    @Test
    fun testOverflowMoreNodeWhenCategoriesExceedThreshold() {
        // 8 categories (> 6)
        val categories = (1..8).map { CategoryUiModel(it.toLong(), "Cat $it", "Cat_$it") }
        val radiusPx = 300f

        // When not expanded: displays top 5 + More node
        val unexpandedLayout = RadialMenuGeometry.calculateLayout(
            categories = categories,
            radiusPx = radiusPx,
            style = ArcDesignStyle.ORBIT,
            isOverflowExpanded = false
        )
        assertEquals(5, unexpandedLayout.categoriesWithPos.size)
        assertNotNull("More node should be present when total > 6 and not expanded", unexpandedLayout.morePos)
        assertEquals(3, unexpandedLayout.overflowCategories.size)

        // When expanded: dual-ring layout with all 8 categories
        val expandedLayout = RadialMenuGeometry.calculateLayout(
            categories = categories,
            radiusPx = radiusPx,
            style = ArcDesignStyle.ORBIT,
            isOverflowExpanded = true
        )
        assertEquals(8, expandedLayout.categoriesWithPos.size)
        assertTrue(expandedLayout.isDualRing)
        assertNull("More node is replaced by full expanded categories", expandedLayout.morePos)
    }

    @Test
    fun testHandednessAndRtlInversion() {
        val categories = listOf(
            CategoryUiModel(1L, "Cat 1", "Cat_1"),
            CategoryUiModel(2L, "Cat 2", "Cat_2")
        )
        val radiusPx = 200f

        val rightHandedLayout = RadialMenuGeometry.calculateLayout(
            categories = categories,
            radiusPx = radiusPx,
            style = ArcDesignStyle.ORBIT,
            handedness = Handedness.RIGHT_HANDED
        )

        val leftHandedLayout = RadialMenuGeometry.calculateLayout(
            categories = categories,
            radiusPx = radiusPx,
            style = ArcDesignStyle.ORBIT,
            handedness = Handedness.LEFT_HANDED
        )

        // In right handed, first category is leftward (x < 0)
        val rightX = rightHandedLayout.categoriesWithPos.first().second.x
        // In left handed, first category is rightward (x > 0)
        val leftX = leftHandedLayout.categoriesWithPos.first().second.x

        assertTrue("Right handed layout should start towards left", rightX < 0)
        assertTrue("Left handed layout should start towards right", leftX > 0)
    }

    @Test
    fun testHoverTargetHitTesting() {
        val categories = listOf(
            CategoryUiModel(10L, "Receipts", "Receipts"),
            CategoryUiModel(20L, "Designs", "Designs")
        )
        val radiusPx = 200f
        val layout = RadialMenuGeometry.calculateLayout(categories, radiusPx, ArcDesignStyle.ORBIT)
        val hitThreshold = 50f

        // 1. Dragging exactly onto Delete target
        val deleteHit = RadialMenuGeometry.findHoverTarget(layout.deletePos, layout, hitThreshold)
        assertEquals(HoverTarget.Delete, deleteHit)

        // 2. Dragging near Delete target (within hit threshold)
        val nearDelete = Offset(layout.deletePos.x + 20f, layout.deletePos.y - 15f)
        val nearDeleteHit = RadialMenuGeometry.findHoverTarget(nearDelete, layout, hitThreshold)
        assertEquals(HoverTarget.Delete, nearDeleteHit)

        // 3. Dragging near Share target
        val sharePos = layout.sharePos!!
        val nearShare = Offset(sharePos.x + 10f, sharePos.y + 10f)
        val shareHit = RadialMenuGeometry.findHoverTarget(nearShare, layout, hitThreshold)
        assertEquals(HoverTarget.Share, shareHit)

        // 4. Dragging onto Category target
        val (cat1, cat1Pos) = layout.categoriesWithPos[0]
        val cat1Hit = RadialMenuGeometry.findHoverTarget(cat1Pos, layout, hitThreshold)
        assertTrue(cat1Hit is HoverTarget.Category)
        assertEquals(cat1.id, (cat1Hit as HoverTarget.Category).category.id)

        // 5. Dragging in empty space (far away from any target)
        val emptyOffset = Offset(0f, 0f) // Center
        val noHit = RadialMenuGeometry.findHoverTarget(emptyOffset, layout, hitThreshold)
        assertNull(noHit)
    }

    @Test
    fun testCategorizedSubfolderDetectionLogic() {
        // Screenshots taken directly by Android system
        val rootPaths = listOf(
            "Pictures/Screenshots/",
            "Pictures/Screenshots",
            "DCIM/Screenshots/",
            "DCIM/Screenshots",
            "Pictures/Screenshots\\",
            "Pictures\\Screenshots"
        )

        rootPaths.forEach { path ->
            val normalized = path.replace('\\', '/').trim('/')
            val isSubfolder = normalized.startsWith("Pictures/Screenshots/", ignoreCase = true) &&
                    normalized.length > "Pictures/Screenshots/".length
            assertFalse("Root screenshot path '$path' should not be detected as subfolder", isSubfolder)
        }

        // Screenshots organized into category subfolders
        val categorizedPaths = listOf(
            "Pictures/Screenshots/Finance/",
            "Pictures/Screenshots/Work/ProjectA",
            "Pictures/Screenshots/Receipts",
            "Pictures\\Screenshots\\Travel"
        )

        categorizedPaths.forEach { path ->
            val normalized = path.replace('\\', '/').trim('/')
            val isSubfolder = normalized.startsWith("Pictures/Screenshots/", ignoreCase = true) &&
                    normalized.length > "Pictures/Screenshots/".length
            assertTrue("Categorized path '$path' must be detected as subfolder", isSubfolder)
        }
    }

    @Test
    fun testArcDesignStyleProperties() {
        assertEquals("Orbit", ArcDesignStyle.ORBIT.title)
        assertEquals("Fan Arc", ArcDesignStyle.FAN.title)
        assertEquals("Segmented Dial", ArcDesignStyle.DIAL.title)

        // Verify valueOf
        assertEquals(ArcDesignStyle.ORBIT, ArcDesignStyle.valueOf("ORBIT"))
        assertEquals(ArcDesignStyle.FAN, ArcDesignStyle.valueOf("FAN"))
        assertEquals(ArcDesignStyle.DIAL, ArcDesignStyle.valueOf("DIAL"))
    }

    @Test
    fun testBottomRightCornerRadialMenuGeometry() {
        val categories = listOf(
            CategoryUiModel(1L, "Work", "Work"),
            CategoryUiModel(2L, "Personal", "Personal"),
            CategoryUiModel(3L, "Finance", "Finance")
        )
        val radiusPx = 280f
        val layout = RadialMenuGeometry.calculateLayout(
            categories = categories,
            radiusPx = radiusPx,
            style = ArcDesignStyle.ORBIT,
            edgeAnchor = OverlayEdgeAnchor.BOTTOM_RIGHT
        )

        assertEquals(3, layout.categoriesWithPos.size)

        // All category targets must bloom into the upper-left quadrant (x < 0, y < 0)
        layout.categoriesWithPos.forEach { (cat, pos) ->
            assertTrue("Category ${cat.name} x should be negative (leftward into screen), was ${pos.x}", pos.x < 0)
            assertTrue("Category ${cat.name} y should be negative (upward into screen), was ${pos.y}", pos.y < 0)
        }

        // Delete target must be stationed on the left side of the bubble (negative x)
        assertNotNull(layout.deletePos)
        assertTrue("Delete target x should be negative for bottom-right corner", layout.deletePos.x < 0)

        // Share target must be stationed above the bubble (negative y)
        assertNotNull(layout.sharePos)
        assertTrue("Share target y should be negative (above bubble) for bottom-right corner", layout.sharePos!!.y < 0)

        // Share & Delete target must be stationed in inner arc between share and delete
        assertNotNull(layout.shareAndDeletePos)
        assertTrue("Share & Delete target x should be negative", layout.shareAndDeletePos!!.x < 0)
        assertTrue("Share & Delete target y should be negative", layout.shareAndDeletePos!!.y < 0)

        // Copy target must be stationed in inner arc
        assertNotNull(layout.copyPos)
        assertTrue("Copy target y should be negative", layout.copyPos!!.y < 0)

        // Inner and outer radii should reflect 2-layer geometry
        assertTrue("Outer radius should be larger than inner radius", layout.outerRadiusPx > layout.innerRadiusPx)
    }

    @Test
    fun testTwoLayerRadialMenuGeometryAndActions() {
        val categories = listOf(
            CategoryUiModel(1L, "Receipts", "Receipts"),
            CategoryUiModel(2L, "Work", "Work"),
            CategoryUiModel(3L, "Social", "Social")
        )
        val radiusPx = 300f
        val layout = RadialMenuGeometry.calculateLayout(
            categories = categories,
            radiusPx = radiusPx,
            edgeAnchor = OverlayEdgeAnchor.BOTTOM_RIGHT
        )

        // Layer 1 (Inner Arc): Actions
        assertNotNull("Copy position must be calculated", layout.copyPos)
        assertNotNull("Share position must be calculated", layout.sharePos)
        assertNotNull("Share & Delete position must be calculated", layout.shareAndDeletePos)
        assertNotNull("Delete position must be calculated", layout.deletePos)

        // Layer 2 (Outer Arc): Overlapping Category Capsules
        assertEquals(3, layout.categoriesWithPos.size)

        // Verify hit-testing for Layer 1 actions
        val copyHit = RadialMenuGeometry.findHoverTarget(layout.copyPos!!, layout, 40f)
        assertEquals(HoverTarget.Copy, copyHit)

        val shareAndDelHit = RadialMenuGeometry.findHoverTarget(layout.shareAndDeletePos!!, layout, 40f)
        assertEquals(HoverTarget.ShareAndDelete, shareAndDelHit)

        val shareHit = RadialMenuGeometry.findHoverTarget(layout.sharePos!!, layout, 40f)
        assertEquals(HoverTarget.Share, shareHit)

        val deleteHit = RadialMenuGeometry.findHoverTarget(layout.deletePos, layout, 40f)
        assertEquals(HoverTarget.Delete, deleteHit)
    }

    @Test
    fun testBottomLeftCornerRadialMenuGeometry() {
        val categories = listOf(
            CategoryUiModel(1L, "Work", "Work"),
            CategoryUiModel(2L, "Personal", "Personal")
        )
        val radiusPx = 280f
        val layout = RadialMenuGeometry.calculateLayout(
            categories = categories,
            radiusPx = radiusPx,
            style = ArcDesignStyle.ORBIT,
            edgeAnchor = OverlayEdgeAnchor.BOTTOM_LEFT
        )

        assertEquals(2, layout.categoriesWithPos.size)

        // All category targets must bloom into the upper-right quadrant (x > 0, y < 0)
        layout.categoriesWithPos.forEach { (cat, pos) ->
            assertTrue("Category ${cat.name} x should be positive (rightward into screen)", pos.x > 0)
            assertTrue("Category ${cat.name} y should be negative (upward into screen)", pos.y < 0)
        }

        // Delete target must be stationed on the right side of the bubble (positive x)
        assertNotNull(layout.deletePos)
        assertTrue("Delete target x should be positive for bottom-left corner", layout.deletePos.x > 0)

        // Share target must be stationed above the bubble (negative y)
        assertNotNull(layout.sharePos)
        assertTrue("Share target y should be negative for bottom-left corner", layout.sharePos!!.y < 0)
    }

    @Test
    fun testInternallyGeneratedScreenshotExclusion() {
        // Redacted screenshot files must be excluded
        assertTrue(com.screenarc.app.service.ScreenshotObserver.isExcluded(100L, null, "Screenshot_Redacted_1712345678.png"))
        assertTrue(com.screenarc.app.service.ScreenshotObserver.isExcluded(101L, null, "screenarc_export.png"))
        assertTrue(com.screenarc.app.service.ScreenshotObserver.isExcluded(102L, null, "image_edited_99.png"))
        assertTrue(com.screenarc.app.service.ScreenshotObserver.isExcluded(103L, null, "screenshot_redacted_test.jpg"))

        // Fresh normal screenshots must NOT be excluded
        assertFalse(com.screenarc.app.service.ScreenshotObserver.isExcluded(999999L, null, "Screenshot_20261010-035000.png"))
        assertFalse(com.screenarc.app.service.ScreenshotObserver.isExcluded(999998L, null, "capture_20261010.png"))
    }

    @Test
    fun testCompanionExclusionRegistryByUriAndId() {
        val testId = 777666L
        com.screenarc.app.service.ScreenshotObserver.excludeId(testId)
        assertTrue("Excluded ID must be recognized", com.screenarc.app.service.ScreenshotObserver.isExcluded(testId, null, "Screenshot_Normal.png"))

        val mockUri = org.mockito.Mockito.mock(android.net.Uri::class.java)
        org.mockito.Mockito.`when`(mockUri.toString()).thenReturn("content://media/external_primary/images/media/555444")
        org.mockito.Mockito.`when`(mockUri.lastPathSegment).thenReturn("555444")

        com.screenarc.app.service.ScreenshotObserver.excludeUri(mockUri)

        // Primary URI must be excluded
        assertTrue(com.screenarc.app.service.ScreenshotObserver.isExcluded(1L, mockUri, "Screenshot_Normal.png"))

        // Extracted ID must also be excluded
        assertTrue(com.screenarc.app.service.ScreenshotObserver.isExcluded(555444L, null, "Screenshot_Normal.png"))

        // Normalized external URI should also be excluded
        val normalizedUri = org.mockito.Mockito.mock(android.net.Uri::class.java)
        org.mockito.Mockito.`when`(normalizedUri.toString()).thenReturn("content://media/external/images/media/555444")
        assertTrue(com.screenarc.app.service.ScreenshotObserver.isExcluded(2L, normalizedUri, "Screenshot_Normal.png"))
    }
}

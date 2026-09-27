package com.screenarc.app

import androidx.compose.ui.geometry.Offset
import com.screenarc.app.model.ArcDesignStyle
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.ui.overlay.HoverTarget
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
}

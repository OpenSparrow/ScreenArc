package com.screenarc.app

import com.screenarc.app.model.AppThemePreset
import com.screenarc.app.model.ThemeCategory
import com.screenarc.app.ui.theme.ThemeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStateManagerTest {

    @Test
    fun testThemeCategoryPresetsClassification() {
        val allPresets = ThemeCategory.ALL.getPresets()
        val lightPresets = ThemeCategory.LIGHT.getPresets()
        val darkPresets = ThemeCategory.DARK.getPresets()
        val glassPresets = ThemeCategory.GLASS.getPresets()

        // Verify All contains all enum entries
        assertEquals(AppThemePreset.values().size, allPresets.size)

        // Verify Light contains only isLightOnly presets
        assertTrue("Light category must not be empty", lightPresets.isNotEmpty())
        lightPresets.forEach { preset ->
            assertTrue("${preset.name} should be light only", preset.isLightOnly)
        }
        assertTrue(lightPresets.contains(AppThemePreset.MATERIAL_LIGHT))
        assertTrue(lightPresets.contains(AppThemePreset.PASTEL_CREAM))
        assertTrue(lightPresets.contains(AppThemePreset.NORD_SNOW))

        // Verify Dark contains dark presets
        assertTrue("Dark category must not be empty", darkPresets.isNotEmpty())
        assertTrue(darkPresets.contains(AppThemePreset.MIDNIGHT_AMOLED))
        assertTrue(darkPresets.contains(AppThemePreset.DRACULA))
        assertTrue(darkPresets.contains(AppThemePreset.DYNAMIC_SYSTEM))

        // Verify Glass contains only glass presets
        assertTrue("Glass category must not be empty", glassPresets.isNotEmpty())
        glassPresets.forEach { preset ->
            assertTrue("${preset.name} should be glass", preset.isGlass)
        }
        assertTrue(glassPresets.contains(AppThemePreset.GLASS_FROST))
        assertTrue(glassPresets.contains(AppThemePreset.GLASS_AURORA))
    }

    @Test
    fun testTabHighlightingPreventsMismatch() {
        // Scenario 1: User selects LIGHT category tab, but active theme is MIDNIGHT_AMOLED (Dark)
        val mismatchedState = ThemeUiState(
            selectedCategory = ThemeCategory.LIGHT,
            activeTheme = AppThemePreset.MIDNIGHT_AMOLED,
            filteredPresets = ThemeCategory.LIGHT.getPresets(),
            isCategoryActive = false
        )
        // Tab must NOT be highlighted because the active theme does not match the tab!
        assertFalse(
            "Light tab must NOT be highlighted when active theme is dark",
            mismatchedState.isTabHighlighted(ThemeCategory.LIGHT)
        )

        // Scenario 2: User selects LIGHT category tab and active theme is MATERIAL_LIGHT
        val matchedLightState = ThemeUiState(
            selectedCategory = ThemeCategory.LIGHT,
            activeTheme = AppThemePreset.MATERIAL_LIGHT,
            filteredPresets = ThemeCategory.LIGHT.getPresets(),
            isCategoryActive = true
        )
        assertTrue(
            "Light tab should be highlighted when active theme is light",
            matchedLightState.isTabHighlighted(ThemeCategory.LIGHT)
        )
        assertFalse(matchedLightState.isTabHighlighted(ThemeCategory.DARK))
        assertFalse(matchedLightState.isTabHighlighted(ThemeCategory.ALL))

        // Scenario 3: User selects GLASS category tab and active theme is GLASS_AURORA
        val matchedGlassState = ThemeUiState(
            selectedCategory = ThemeCategory.GLASS,
            activeTheme = AppThemePreset.GLASS_AURORA,
            filteredPresets = ThemeCategory.GLASS.getPresets(),
            isCategoryActive = true
        )
        assertTrue(
            "Glass tab should be highlighted when active theme is glass",
            matchedGlassState.isTabHighlighted(ThemeCategory.GLASS)
        )
        assertFalse(matchedGlassState.isTabHighlighted(ThemeCategory.LIGHT))

        // Scenario 4: User selects ALL tab
        val allState = ThemeUiState(
            selectedCategory = ThemeCategory.ALL,
            activeTheme = AppThemePreset.DYNAMIC_SYSTEM,
            filteredPresets = ThemeCategory.ALL.getPresets(),
            isCategoryActive = true
        )
        assertTrue(
            "All tab should be highlighted when selectedCategory is ALL",
            allState.isTabHighlighted(ThemeCategory.ALL)
        )
    }

    @Test
    fun testPrimaryCategoryResolution() {
        assertEquals(ThemeCategory.GLASS, AppThemePreset.GLASS_AURORA.primaryCategory())
        assertEquals(ThemeCategory.GLASS, AppThemePreset.GLASS_FROST.primaryCategory())
        assertEquals(ThemeCategory.LIGHT, AppThemePreset.MATERIAL_LIGHT.primaryCategory())
        assertEquals(ThemeCategory.LIGHT, AppThemePreset.PASTEL_CREAM.primaryCategory())
        assertEquals(ThemeCategory.DARK, AppThemePreset.MIDNIGHT_AMOLED.primaryCategory())
        assertEquals(ThemeCategory.DARK, AppThemePreset.DYNAMIC_SYSTEM.primaryCategory())
    }

    @Test
    fun testCategoryDisplayNameAndFromName() {
        assertEquals("All", ThemeCategory.ALL.displayName)
        assertEquals("Light", ThemeCategory.LIGHT.displayName)
        assertEquals("Dark", ThemeCategory.DARK.displayName)
        assertEquals("Glassmorphism", ThemeCategory.GLASS.displayName)

        assertEquals(ThemeCategory.ALL, ThemeCategory.fromName("ALL"))
        assertEquals(ThemeCategory.LIGHT, ThemeCategory.fromName("light"))
        assertEquals(ThemeCategory.GLASS, ThemeCategory.fromName("GLASS"))
        assertEquals(ThemeCategory.ALL, ThemeCategory.fromName("non_existent"))
    }
}

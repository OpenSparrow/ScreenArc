package com.screenarc.app

import com.screenarc.app.model.AppThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeStateManagerTest {

    @Test
    fun testSimplifiedThemePresetsExist() {
        val presets = AppThemePreset.entries
        assertEquals("Should have 6 themes: system, light, dark, and 3 neutral accents", 6, presets.size)

        assertNotNull(AppThemePreset.fromId("dynamic_system"))
        assertNotNull(AppThemePreset.fromId("light"))
        assertNotNull(AppThemePreset.fromId("dark"))
        assertNotNull(AppThemePreset.fromId("indigo"))
        assertNotNull(AppThemePreset.fromId("teal"))
        assertNotNull(AppThemePreset.fromId("monochrome"))
    }

    @Test
    fun testThemeFallbackWhenPresetNotFound() {
        // Obsolete / removed preset IDs must fall back gracefully to DYNAMIC_SYSTEM
        assertEquals(AppThemePreset.DYNAMIC_SYSTEM, AppThemePreset.fromId("catppuccin_mocha"))
        assertEquals(AppThemePreset.DYNAMIC_SYSTEM, AppThemePreset.fromId("dracula"))
        assertEquals(AppThemePreset.DYNAMIC_SYSTEM, AppThemePreset.fromId("nord_snow"))
        assertEquals(AppThemePreset.DYNAMIC_SYSTEM, AppThemePreset.fromId("glass_aurora"))
        assertEquals(AppThemePreset.DYNAMIC_SYSTEM, AppThemePreset.fromId("non_existent_preset"))
        assertEquals(AppThemePreset.DYNAMIC_SYSTEM, AppThemePreset.fromId(null))
        assertEquals(AppThemePreset.DYNAMIC_SYSTEM, AppThemePreset.fromId(""))
    }

    @Test
    fun testCaseInsensitiveMatching() {
        assertEquals(AppThemePreset.DYNAMIC_SYSTEM, AppThemePreset.fromId("DYNAMIC_SYSTEM"))
        assertEquals(AppThemePreset.LIGHT, AppThemePreset.fromId("Light"))
        assertEquals(AppThemePreset.DARK, AppThemePreset.fromId("DARK"))
        assertEquals(AppThemePreset.INDIGO, AppThemePreset.fromId("Indigo"))
        assertEquals(AppThemePreset.TEAL, AppThemePreset.fromId("tEaL"))
        assertEquals(AppThemePreset.MONOCHROME, AppThemePreset.fromId("Monochrome"))
    }
}

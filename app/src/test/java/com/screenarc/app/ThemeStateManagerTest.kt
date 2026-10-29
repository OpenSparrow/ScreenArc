package com.screenarc.app

import com.screenarc.app.model.AppThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeStateManagerTest {

    @Test
    fun testAllSevenThemePresetsExist() {
        val presets = AppThemePreset.entries
        assertEquals("Should have all 7 presets: Glassmorphic, Dynamic System, AMOLED, Catppuccin, Neon Glow, Nordic Blue, Sunset Rose", 7, presets.size)

        assertNotNull(AppThemePreset.fromId("glassmorphic"))
        assertNotNull(AppThemePreset.fromId("dynamic_system"))
        assertNotNull(AppThemePreset.fromId("midnight_amoled"))
        assertNotNull(AppThemePreset.fromId("catppuccin"))
        assertNotNull(AppThemePreset.fromId("neon_glow"))
        assertNotNull(AppThemePreset.fromId("nordic_blue"))
        assertNotNull(AppThemePreset.fromId("sunset_rose"))
    }

    @Test
    fun testThemeFallbackWhenPresetNotFound() {
        // Unknown preset IDs fall back gracefully to default hero preset (GLASSMORPHIC)
        assertEquals(AppThemePreset.GLASSMORPHIC, AppThemePreset.fromId("non_existent_preset"))
        assertEquals(AppThemePreset.GLASSMORPHIC, AppThemePreset.fromId(null))
        assertEquals(AppThemePreset.GLASSMORPHIC, AppThemePreset.fromId(""))
    }

    @Test
    fun testBackwardsCompatibleAliases() {
        assertEquals(AppThemePreset.GLASSMORPHIC, AppThemePreset.fromId("glass_aurora"))
        assertEquals(AppThemePreset.GLASSMORPHIC, AppThemePreset.fromId("dark"))
        assertEquals(AppThemePreset.GLASSMORPHIC, AppThemePreset.fromId("indigo"))
        assertEquals(AppThemePreset.CATPPUCCIN, AppThemePreset.fromId("catppuccin_mocha"))
        assertEquals(AppThemePreset.NEON_GLOW, AppThemePreset.fromId("dracula"))
        assertEquals(AppThemePreset.NEON_GLOW, AppThemePreset.fromId("cyberpunk"))
        assertEquals(AppThemePreset.NORDIC_BLUE, AppThemePreset.fromId("nord_frost"))
        assertEquals(AppThemePreset.NORDIC_BLUE, AppThemePreset.fromId("nord_snow"))
        assertEquals(AppThemePreset.NORDIC_BLUE, AppThemePreset.fromId("light"))
        assertEquals(AppThemePreset.NORDIC_BLUE, AppThemePreset.fromId("teal"))
        assertEquals(AppThemePreset.SUNSET_ROSE, AppThemePreset.fromId("sakura"))
        assertEquals(AppThemePreset.MIDNIGHT_AMOLED, AppThemePreset.fromId("monochrome"))
    }

    @Test
    fun testCaseInsensitiveMatching() {
        assertEquals(AppThemePreset.GLASSMORPHIC, AppThemePreset.fromId("GLASSMORPHIC"))
        assertEquals(AppThemePreset.DYNAMIC_SYSTEM, AppThemePreset.fromId("DYNAMIC_SYSTEM"))
        assertEquals(AppThemePreset.MIDNIGHT_AMOLED, AppThemePreset.fromId("Midnight_Amoled"))
        assertEquals(AppThemePreset.CATPPUCCIN, AppThemePreset.fromId("Catppuccin"))
        assertEquals(AppThemePreset.NEON_GLOW, AppThemePreset.fromId("NEON_GLOW"))
        assertEquals(AppThemePreset.NORDIC_BLUE, AppThemePreset.fromId("Nordic_Blue"))
        assertEquals(AppThemePreset.SUNSET_ROSE, AppThemePreset.fromId("Sunset_Rose"))
    }
}

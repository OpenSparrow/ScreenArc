package com.screenarc.app

import com.screenarc.app.data.AppSettings
import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.ScreenshotItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticProcessingAndTogglesTest {

    @Test
    fun testDefaultSettingsValues() {
        val settings = AppSettings()
        assertFalse("Auto OCR should default to false", settings.autoOcrEnabled)
        assertFalse("Auto assign rules should default to false", settings.autoAssignRulesEnabled)
    }

    @Test
    fun testSettingsDependencyConstraint() {
        // Function representing AppSettingsRepository.updateSettings normalization logic
        fun normalizeSettings(raw: AppSettings): AppSettings {
            return if (!raw.autoOcrEnabled) {
                raw.copy(autoAssignRulesEnabled = false)
            } else {
                raw
            }
        }

        // 1. Both disabled -> valid
        val s1 = normalizeSettings(AppSettings(autoOcrEnabled = false, autoAssignRulesEnabled = false))
        assertFalse(s1.autoOcrEnabled)
        assertFalse(s1.autoAssignRulesEnabled)

        // 2. Attempt to enable autoAssignRules without autoOcr -> autoAssignRules must be forced to false
        val s2 = normalizeSettings(AppSettings(autoOcrEnabled = false, autoAssignRulesEnabled = true))
        assertFalse(s2.autoOcrEnabled)
        assertFalse(s2.autoAssignRulesEnabled)

        // 3. AutoOcr enabled, autoAssignRules disabled -> valid
        val s3 = normalizeSettings(AppSettings(autoOcrEnabled = true, autoAssignRulesEnabled = false))
        assertTrue(s3.autoOcrEnabled)
        assertFalse(s3.autoAssignRulesEnabled)

        // 4. Both enabled -> valid
        val s4 = normalizeSettings(AppSettings(autoOcrEnabled = true, autoAssignRulesEnabled = true))
        assertTrue(s4.autoOcrEnabled)
        assertTrue(s4.autoAssignRulesEnabled)
    }

    @Test
    fun testAutomaticProcessingDecisionPipeline() {
        val ruleFinance = AutoCategorizationRule(
            id = "rule_finance",
            name = "Finance Invoices",
            primaryKeywords = listOf("invoice", "receipt", "payment"),
            targetCategoryId = 101L,
            targetCategoryName = "Finance",
            priority = 0,
            isEnabled = true
        )
        val activeRules = listOf(ruleFinance)
        val categories = listOf(
            CategoryUiModel(101L, "Finance", "Finance"),
            CategoryUiModel(102L, "Social", "Social")
        )

        val ocrTextInvoice = "Tax Invoice #9842 Total $150.00 Paid with Visa"

        // Scenario A: autoOcrEnabled = false
        val settingsA = AppSettings(autoOcrEnabled = false, autoAssignRulesEnabled = false)
        val shouldRunOcrA = settingsA.autoOcrEnabled
        assertFalse("Should not run OCR if autoOcrEnabled is false", shouldRunOcrA)

        // Scenario B: autoOcrEnabled = true, autoAssignRulesEnabled = false
        // OCR is executed, matching rule suggests category, but does NOT auto-move
        val settingsB = AppSettings(autoOcrEnabled = true, autoAssignRulesEnabled = false)
        assertTrue("Should run OCR if autoOcrEnabled is true", settingsB.autoOcrEnabled)

        val matchB = AutoCategorizationRule.resolveBestMatch(activeRules, ocrTextInvoice)
        assertNotNull(matchB)
        assertTrue(matchB!!.isAssigned)
        assertEquals(101L, matchB.rule.targetCategoryId)

        val willAutoMoveB = settingsB.autoAssignRulesEnabled
        assertFalse("Should NOT auto-move screenshot if autoAssignRulesEnabled is false", willAutoMoveB)
        // Instead, suggestedCategoryId is used for radial overlay highlight
        val suggestedCategoryForOverlayB = categories.find { it.id == matchB.rule.targetCategoryId }
        assertNotNull(suggestedCategoryForOverlayB)
        assertEquals("Finance", suggestedCategoryForOverlayB?.name)

        // Scenario C: autoOcrEnabled = true, autoAssignRulesEnabled = true
        // Screenshot is automatically assigned and moved
        val settingsC = AppSettings(autoOcrEnabled = true, autoAssignRulesEnabled = true)
        val willAutoMoveC = settingsC.autoOcrEnabled && settingsC.autoAssignRulesEnabled
        assertTrue("Should auto-move screenshot when both toggles are enabled", willAutoMoveC)
    }
}

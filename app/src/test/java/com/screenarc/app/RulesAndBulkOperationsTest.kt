package com.screenarc.app

import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.model.OrganizedRecord
import com.screenarc.app.model.ScreenshotUiModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesAndBulkOperationsTest {

    @Test
    fun testPrimaryWinsOverSecondary() {
        // Rule A (Tech): has secondary keywords "netflix", "cinema" (not primary)
        val techRule = AutoCategorizationRule(
            id = "rule_tech",
            name = "Tech",
            primaryKeywords = listOf("terminal", "github", "docker"),
            secondaryKeywords = listOf("netflix", "cinema", "movie"),
            targetCategoryId = 1L,
            targetCategoryName = "Tech",
            priority = 0,
            secondaryThreshold = 2
        )

        // Rule B (Movie): has primary keyword "netflix"
        val movieRule = AutoCategorizationRule(
            id = "rule_movie",
            name = "Movie",
            primaryKeywords = listOf("netflix", "cinema", "movie"),
            secondaryKeywords = listOf("series", "streaming"),
            targetCategoryId = 5L,
            targetCategoryName = "Memes",
            priority = 1
        )

        val screenshot = ScreenshotUiModel(
            id = 101L,
            uri = "content://media/external/images/media/101",
            displayName = "movie_screenshot.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 2048L,
            categoryId = null,
            ocrStatus = "COMPLETED",
            recognizedText = "Watch new movie on Netflix tonight",
            normalizedText = "watch new movie on netflix tonight"
        )

        // Even though Tech rule has higher priority (priority 0 vs 1), Movie rule has PRIMARY match,
        // so Movie rule must win over Tech rule which only has secondary matches!
        val winningMatch = AutoCategorizationRule.resolveBestMatch(listOf(techRule, movieRule), screenshot)
        assertNotNull(winningMatch)
        assertEquals("Movie rule must win because it has a primary match", "rule_movie", winningMatch!!.rule.id)
        assertTrue(winningMatch.hasPrimaryMatch)
    }

    @Test
    fun testMultiplePrimaryConflictsResolvedByPriority() {
        val rule1 = AutoCategorizationRule(
            id = "rule_1",
            name = "High Priority Rule",
            primaryKeywords = listOf("order", "receipt"),
            targetCategoryId = 4L,
            targetCategoryName = "Receipts",
            priority = 0 // Higher priority
        )

        val rule2 = AutoCategorizationRule(
            id = "rule_2",
            name = "Lower Priority Rule",
            primaryKeywords = listOf("order", "confirmation"),
            targetCategoryId = 2L,
            targetCategoryName = "Docs",
            priority = 1 // Lower priority
        )

        val screenshot = ScreenshotUiModel(
            id = 102L,
            uri = "content://media/external/images/media/102",
            displayName = "order_receipt.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 2048L,
            categoryId = null,
            ocrStatus = "COMPLETED",
            recognizedText = "Your Order Receipt Confirmation",
            normalizedText = "your order receipt confirmation"
        )

        val winningMatch = AutoCategorizationRule.resolveBestMatch(listOf(rule2, rule1), screenshot)
        assertNotNull(winningMatch)
        assertEquals("Higher priority rule (priority 0) must win conflict", "rule_1", winningMatch!!.rule.id)
    }

    @Test
    fun testSecondaryOnlyThreshold() {
        val rule = AutoCategorizationRule(
            id = "rule_finance",
            name = "Finance Signals",
            primaryKeywords = listOf("tax invoice", "statement of account"),
            secondaryKeywords = listOf("total", "amount", "paid", "upi", "gst"),
            targetCategoryId = 4L,
            targetCategoryName = "Receipts",
            secondaryThreshold = 3 // Requires ≥ 3 secondary keywords
        )

        // Screenshot with only 2 secondary matches (below threshold 3)
        val screenshotBelowThreshold = ScreenshotUiModel(
            id = 201L,
            uri = "content://media/external/images/media/201",
            displayName = "bill_snippet.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 1024L,
            categoryId = null,
            ocrStatus = "COMPLETED",
            recognizedText = "Total Amount was processed",
            normalizedText = "total amount was processed"
        )

        val evalBelow = rule.evaluate(screenshotBelowThreshold)
        assertFalse("Should not match when secondary matches (2) is below threshold (3)", evalBelow.isAssigned)
        assertFalse(rule.matches(screenshotBelowThreshold))

        // Screenshot with 3 secondary matches ("total", "amount", "paid") (meets threshold 3)
        val screenshotMeetsThreshold = ScreenshotUiModel(
            id = 202L,
            uri = "content://media/external/images/media/202",
            displayName = "bill_snippet.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 1024L,
            categoryId = null,
            ocrStatus = "COMPLETED",
            recognizedText = "Total Amount has been Paid successfully via UPI",
            normalizedText = "total amount has been paid successfully via upi"
        )

        val evalMeets = rule.evaluate(screenshotMeetsThreshold)
        assertTrue("Should match when secondary matches (4) meets threshold (3)", evalMeets.isAssigned)
        assertTrue(rule.matches(screenshotMeetsThreshold))
    }

    @Test
    fun testNoMatchScenario() {
        val rule = AutoCategorizationRule(
            id = "rule_dev",
            name = "Dev Tools",
            primaryKeywords = listOf("kubernetes", "docker", "terraform"),
            secondaryKeywords = listOf("cluster", "ingress"),
            targetCategoryId = 1L,
            targetCategoryName = "Tech",
            secondaryThreshold = 2
        )

        val screenshot = ScreenshotUiModel(
            id = 301L,
            uri = "content://media/external/images/media/301",
            displayName = "recipe_pie.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 1024L,
            categoryId = null,
            ocrStatus = "COMPLETED",
            recognizedText = "Delicious apple pie recipe with cinnamon and sugar",
            normalizedText = "delicious apple pie recipe with cinnamon and sugar"
        )

        val eval = rule.evaluate(screenshot)
        assertFalse(eval.isAssigned)
        assertEquals("No primary or secondary keywords matched", eval.reason)

        val winning = AutoCategorizationRule.resolveBestMatch(listOf(rule), screenshot)
        assertNull("Should resolve to null when no rule matches", winning)
    }

    @Test
    fun testWholeWordAndUnicodeNormalizationMatching() {
        val ruleWholeWord = AutoCategorizationRule(
            id = "rule_whole",
            name = "Whole Word Test",
            primaryKeywords = listOf("cat"),
            targetCategoryId = 1L,
            targetCategoryName = "Tech",
            matchWholeWord = true
        )

        // "category" contains "cat" as a substring, but not as a whole word
        val substringScreenshot = ScreenshotUiModel(
            id = 401L,
            uri = "content://media/external/images/media/401",
            displayName = "category_list.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 1024L,
            categoryId = null,
            ocrStatus = "COMPLETED",
            recognizedText = "Choose a category from settings",
            normalizedText = "choose a category from settings"
        )
        assertFalse("Whole-word matching must not match 'category' for keyword 'cat'", ruleWholeWord.matches(substringScreenshot))

        // "The cat slept" has "cat" as a whole word
        val wholeWordScreenshot = substringScreenshot.copy(
            recognizedText = "The cat slept on the mat",
            normalizedText = "the cat slept on the mat"
        )
        assertTrue("Whole-word matching must match standalone word 'cat'", ruleWholeWord.matches(wholeWordScreenshot))

        // Unicode normalization (accented characters: "café" matches "cafe")
        val unicodeRule = AutoCategorizationRule(
            id = "rule_unicode",
            name = "Unicode Test",
            primaryKeywords = listOf("cafe"),
            targetCategoryId = 1L,
            targetCategoryName = "Food"
        )
        val accentedScreenshot = substringScreenshot.copy(
            recognizedText = "Welcome to our Café Central",
            normalizedText = "welcome to our cafe central"
        )
        assertTrue("Unicode normalization should match 'Café' with keyword 'cafe'", unicodeRule.matches(accentedScreenshot))
    }

    @Test
    fun testLegacyRuleJsonMigration() {
        // Old JSON format with single 'keywords' array and no primary/secondary keywords
        val legacyJson = """
            [
                {
                    "id": "legacy_rule_1",
                    "name": "Legacy Tech Rule",
                    "keywords": ["github", "terminal", "docker"],
                    "targetCategoryId": 1,
                    "targetCategoryName": "Tech",
                    "isEnabled": true,
                    "onlyNewScreenshots": true,
                    "matchCount": 5
                }
            ]
        """.trimIndent()

        val array = JSONArray(legacyJson)
        val obj = array.getJSONObject(0)

        // Parse using repository migration logic
        val primaryKeywords = mutableListOf<String>()
        if (obj.has("primaryKeywords")) {
            val pArray = obj.getJSONArray("primaryKeywords")
            for (j in 0 until pArray.length()) primaryKeywords.add(pArray.getString(j))
        } else if (obj.has("keywords")) {
            val kArray = obj.getJSONArray("keywords")
            for (j in 0 until kArray.length()) primaryKeywords.add(kArray.getString(j))
        }

        val secondaryKeywords = mutableListOf<String>()
        if (obj.has("secondaryKeywords")) {
            val sArray = obj.getJSONArray("secondaryKeywords")
            for (j in 0 until sArray.length()) secondaryKeywords.add(sArray.getString(j))
        }

        val migratedRule = AutoCategorizationRule(
            id = obj.getString("id"),
            name = obj.getString("name"),
            primaryKeywords = primaryKeywords,
            secondaryKeywords = secondaryKeywords,
            targetCategoryId = obj.getLong("targetCategoryId"),
            targetCategoryName = obj.getString("targetCategoryName"),
            priority = obj.optInt("priority", 0),
            isEnabled = obj.optBoolean("isEnabled", true),
            onlyNewScreenshots = obj.optBoolean("onlyNewScreenshots", true),
            matchCount = obj.optInt("matchCount", 0)
        )

        assertEquals("legacy_rule_1", migratedRule.id)
        assertEquals(3, migratedRule.primaryKeywords.size)
        assertTrue(migratedRule.primaryKeywords.contains("github"))
        assertTrue(migratedRule.primaryKeywords.contains("docker"))
        assertTrue("Secondary keywords should default to empty on migration", migratedRule.secondaryKeywords.isEmpty())
        assertEquals(0, migratedRule.priority)
    }

    @Test
    fun testOrganizedRecordMetadata() {
        val mockUri = org.mockito.Mockito.mock(android.net.Uri::class.java)
        val ruleRecord = OrganizedRecord(
            id = "rec_1",
            screenshotId = 101L,
            uri = mockUri,
            fileName = "movie_poster.png",
            categoryName = "Memes",
            targetFolder = "Memes",
            timestamp = 1700000000L,
            success = true,
            ruleName = "Movie & Cinema",
            triggerType = OrganizedRecord.TRIGGER_RULE
        )

        assertEquals("Movie & Cinema", ruleRecord.ruleName)
        assertEquals(OrganizedRecord.TRIGGER_RULE, ruleRecord.triggerType)

        val manualRecord = OrganizedRecord(
            id = "rec_2",
            screenshotId = 102L,
            uri = mockUri,
            fileName = "manual.png",
            categoryName = "Tech",
            targetFolder = "Tech",
            timestamp = 1700000000L,
            success = true,
            ruleName = null,
            triggerType = OrganizedRecord.TRIGGER_MANUAL
        )

        assertNull(manualRecord.ruleName)
        assertEquals(OrganizedRecord.TRIGGER_MANUAL, manualRecord.triggerType)
    }
}

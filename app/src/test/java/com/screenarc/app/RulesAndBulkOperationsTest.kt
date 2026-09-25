package com.screenarc.app

import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.model.OrganizedRecord
import com.screenarc.app.model.ScreenshotUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesAndBulkOperationsTest {

    @Test
    fun testRuleMatchingMovieKeywords() {
        val movieRule = AutoCategorizationRule(
            id = "rule_movie_1",
            name = "Movie & Cinema",
            keywords = listOf("movie", "netflix", "cinema", "imdb", "hotstar"),
            targetCategoryId = 5L,
            targetCategoryName = "Memes",
            isEnabled = true,
            onlyNewScreenshots = true
        )

        // Screenshot with OCR matching "Movie"
        val movieScreenshot = ScreenshotUiModel(
            id = 101L,
            uri = "content://media/external/images/media/101",
            displayName = "screenshot_2026.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 2048L,
            categoryId = null,
            ocrStatus = "COMPLETED",
            recognizedText = "Inception (2010) - IMDb Top Rated Movie directed by Christopher Nolan",
            normalizedText = "inception 2010 imdb top rated movie directed by christopher nolan"
        )

        assertTrue("Rule should match screenshot containing 'imdb' and 'movie'", movieRule.matches(movieScreenshot))

        // Screenshot without movie keywords
        val techScreenshot = ScreenshotUiModel(
            id = 102L,
            uri = "content://media/external/images/media/102",
            displayName = "terminal_debug.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 2048L,
            categoryId = null,
            ocrStatus = "COMPLETED",
            recognizedText = "git clone https://github.com/project/repo.git",
            normalizedText = "git clone https://github.com/project/repo.git"
        )

        assertFalse("Movie rule should not match git terminal screenshot", movieRule.matches(techScreenshot))
    }

    @Test
    fun testRuleMatchingTechAndOsintKeywords() {
        val techRule = AutoCategorizationRule(
            id = "rule_tech_1",
            name = "Tech, Repos & OSINT",
            keywords = listOf("repo", "tool", "osint", "github", "gitlab", "terminal"),
            targetCategoryId = 1L,
            targetCategoryName = "Tech",
            isEnabled = true,
            onlyNewScreenshots = true
        )

        val osintScreenshot = ScreenshotUiModel(
            id = 103L,
            uri = "content://media/external/images/media/103",
            displayName = "osint_framework_guide.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 1024L,
            categoryId = null,
            ocrStatus = "COMPLETED",
            recognizedText = "Awesome OSINT reconnaissance tool for investigating domains",
            normalizedText = "awesome osint reconnaissance tool for investigating domains"
        )

        assertTrue("Tech rule should match OSINT and tool keywords", techRule.matches(osintScreenshot))
    }

    @Test
    fun testRuleOnlyNewScreenshotsFlag() {
        val rule = AutoCategorizationRule(
            id = "rule_strict",
            name = "Only New Movie",
            keywords = listOf("movie"),
            targetCategoryId = 5L,
            targetCategoryName = "Movie",
            isEnabled = true,
            onlyNewScreenshots = true
        )

        val unorganizedScreenshot = ScreenshotUiModel(
            id = 201L,
            uri = "content://media/external/images/media/201",
            displayName = "new_movie.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 1024L,
            categoryId = null, // unorganized
            ocrStatus = "COMPLETED",
            recognizedText = "New Movie release",
            normalizedText = "new movie release"
        )

        val alreadyOrganizedScreenshot = unorganizedScreenshot.copy(
            id = 202L,
            categoryId = 1L, // already organized
            categoryName = "Tech"
        )

        assertTrue(rule.matches(unorganizedScreenshot))
        assertFalse("Should not match already organized screenshot when onlyNewScreenshots is true", rule.matches(alreadyOrganizedScreenshot))

        // If onlyNewScreenshots is false, it should match even if already categorized
        val ruleAllowAll = rule.copy(onlyNewScreenshots = false)
        assertTrue(ruleAllowAll.matches(alreadyOrganizedScreenshot))
    }

    @Test
    fun testRuleDisabledState() {
        val disabledRule = AutoCategorizationRule(
            id = "rule_disabled",
            name = "Disabled Rule",
            keywords = listOf("movie"),
            targetCategoryId = 5L,
            targetCategoryName = "Movie",
            isEnabled = false,
            onlyNewScreenshots = false
        )

        val screenshot = ScreenshotUiModel(
            id = 301L,
            uri = "content://media/external/images/media/301",
            displayName = "movie.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1700000000L,
            size = 1024L,
            categoryId = null,
            ocrStatus = "COMPLETED",
            recognizedText = "movie",
            normalizedText = "movie"
        )

        assertFalse("Disabled rule should not match any screenshot", disabledRule.matches(screenshot))
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

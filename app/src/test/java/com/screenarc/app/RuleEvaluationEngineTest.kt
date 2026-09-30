package com.screenarc.app

import com.screenarc.app.model.CategoryRule
import com.screenarc.app.model.RuleConditionOperator
import com.screenarc.app.model.ScreenOcrData
import com.screenarc.app.rules.RuleEvaluationEngine
import com.screenarc.app.rules.WebEnrichmentProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEvaluationEngineTest {
    private val engine = RuleEvaluationEngine()

    @Test
    fun containsAnyAndContainsAllMatchCaseInsensitively() {
        val anyRule = rule(operator = RuleConditionOperator.CONTAINS_ANY, values = listOf("receipt", "invoice"))
        val allRule = rule(operator = RuleConditionOperator.CONTAINS_ALL, values = listOf("paid", "visa"))

        assertEquals("Receipts", engine.evaluate(ScreenOcrData("Your INVOICE is ready"), listOf(anyRule)).categoryName)
        assertEquals("Receipts", engine.evaluate(ScreenOcrData("Paid with Visa"), listOf(allRule)).categoryName)
        assertEquals(RuleEvaluationEngine.UNSORTED_CATEGORY, engine.evaluate(ScreenOcrData("Paid only"), listOf(allRule)).categoryName)
    }

    @Test
    fun regexMatchesRecognizedTextAndInvalidRegexDoesNotCrash() {
        val regexRule = rule(operator = RuleConditionOperator.MATCHES_REGEX, values = listOf("PNR\\s*[:#]?\\s*\\d{6}"))
        val invalidRule = rule(operator = RuleConditionOperator.MATCHES_REGEX, values = listOf("["))

        assertEquals("Receipts", engine.evaluate(ScreenOcrData("PNR: 123456"), listOf(regexRule)).categoryName)
        assertEquals(RuleEvaluationEngine.UNSORTED_CATEGORY, engine.evaluate(ScreenOcrData("something"), listOf(invalidRule)).categoryName)
    }

    @Test
    fun domainsMatchSubdomainsButNotLookalikeDomains() {
        val domainRule = rule(operator = RuleConditionOperator.DOMAINS_MATCH, values = listOf("instagram.com"))

        assertEquals("Receipts", engine.evaluate(ScreenOcrData("Open https://m.instagram.com/reel/1"), listOf(domainRule)).categoryName)
        assertEquals(
            RuleEvaluationEngine.UNSORTED_CATEGORY,
            engine.evaluate(ScreenOcrData("Visit https://instagram.com.example.org"), listOf(domainRule)).categoryName
        )
    }

    @Test
    fun highestPriorityWinsAndInputOrderBreaksTies() {
        val first = rule(name = "First", priority = 5, values = listOf("receipt"))
        val higherPriority = rule(name = "Priority", priority = 10, values = listOf("receipt"))
        val samePriority = rule(name = "Same priority", priority = 10, values = listOf("receipt"))

        assertEquals("Priority", engine.evaluate(ScreenOcrData("receipt"), listOf(first, higherPriority, samePriority)).categoryName)
    }

    @Test
    fun unmatchedAndDisabledRulesRouteToUnsorted() {
        val disabled = rule(values = listOf("receipt"), enabled = false)
        val result = engine.evaluate(ScreenOcrData("unrelated text"), listOf(disabled))

        assertEquals(RuleEvaluationEngine.UNSORTED_FOLDER, result.destinationRelativeFolder)
        assertEquals(listOf("Unsorted"), result.tags)
        assertNull(result.matchedRule)
    }

    @Test
    fun sparseOcrUsesAvailableVisualAndBarcodeEnrichment() {
        val fallbackRule = rule(
            operator = RuleConditionOperator.CONTAINS_ANY,
            values = listOf("boarding pass"),
            useWebEnrichmentFallback = true
        )
        val result = engine.evaluate(
            ScreenOcrData(
                recognizedText = "PNR",
                visualLabels = listOf("boarding pass"),
                barcodeResults = listOf("AB123")
            ),
            listOf(fallbackRule)
        )

        assertEquals("Receipts", result.categoryName)
        assertTrue(result.usedEnrichment)
        assertEquals(listOf("Receipts", "boarding pass"), result.tags)
    }

    @Test
    fun sparseOcrCanInvokeOptionalWebEnrichmentProvider() = runBlocking {
        var providerCalled = false
        val fallbackRule = rule(
            operator = RuleConditionOperator.CONTAINS_ANY,
            values = listOf("walmart.com"),
            useWebEnrichmentFallback = true
        )
        val engineWithProvider = RuleEvaluationEngine(WebEnrichmentProvider {
            providerCalled = true
            it.copy(visualLabels = listOf("walmart.com"))
        })

        val result = engineWithProvider.evaluateWithEnrichment(ScreenOcrData("shop"), listOf(fallbackRule))

        assertTrue(providerCalled)
        assertEquals("Receipts", result.categoryName)
        assertTrue(result.usedEnrichment)
        assertFalse(result.enrichmentRecommended)
    }

    @Test
    fun defaultRulesCoverAllRequestedCategories() {
        assertEquals(
            setOf("Payments/Receipts", "Code/Dev", "Social Media", "Online Shopping", "Flight/Train Tickets"),
            RuleEvaluationEngine.DEFAULT_RULES.map { it.categoryName }.toSet()
        )
    }

    private fun rule(
        name: String = "Receipts",
        operator: RuleConditionOperator = RuleConditionOperator.CONTAINS_ANY,
        values: List<String> = listOf("receipt"),
        priority: Int = 0,
        useWebEnrichmentFallback: Boolean = false,
        enabled: Boolean = true
    ) = CategoryRule(
        categoryName = name,
        destinationRelativeFolder = "/Screenshots/Receipts",
        conditionOperator = operator,
        conditionValues = values,
        priority = priority,
        useWebEnrichmentFallback = useWebEnrichmentFallback,
        enabled = enabled
    )
}

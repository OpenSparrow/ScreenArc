package com.screenarc.app.rules

import com.screenarc.app.model.CategoryResult
import com.screenarc.app.model.CategoryRule
import com.screenarc.app.model.RuleConditionOperator
import com.screenarc.app.model.ScreenOcrData
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

fun interface WebEnrichmentProvider {
    suspend fun enrich(ocrResult: ScreenOcrData): ScreenOcrData
}

class RuleEvaluationEngine(
    private val webEnrichmentProvider: WebEnrichmentProvider? = null
) {
    fun evaluate(ocrResult: ScreenOcrData, rules: List<CategoryRule>): CategoryResult {
        val activeRules = rules.withIndex()
            .filter { it.value.enabled }
            .sortedWith(compareByDescending<IndexedValue<CategoryRule>> { it.value.priority }.thenBy { it.index })

        val shouldUseEnrichment = activeRules.any { it.value.useWebEnrichmentFallback } &&
            isSparse(ocrResult, activeRules.map { it.value }) &&
            (ocrResult.visualLabels.isNotEmpty() || ocrResult.barcodeResults.isNotEmpty())
        val textToMatch = buildList {
            add(ocrResult.recognizedText)
            if (shouldUseEnrichment) {
                addAll(ocrResult.visualLabels)
                addAll(ocrResult.barcodeResults)
            }
        }.joinToString(" ")

        for ((_, rule) in activeRules) {
            val matches = findMatches(rule, textToMatch, ocrResult.domains)
            if (matches.isNotEmpty()) {
                return CategoryResult(
                    categoryName = rule.categoryName,
                    destinationRelativeFolder = rule.destinationRelativeFolder,
                    tags = (listOf(rule.categoryName) + matches).distinct(),
                    matchedRule = rule,
                    usedEnrichment = shouldUseEnrichment
                )
            }
        }

        val enrichmentRecommended = isSparse(ocrResult, activeRules.map { it.value }) &&
            activeRules.any { it.value.useWebEnrichmentFallback }

        return CategoryResult(
            categoryName = UNSORTED_CATEGORY,
            destinationRelativeFolder = UNSORTED_FOLDER,
            tags = listOf(UNSORTED_CATEGORY),
            enrichmentRecommended = enrichmentRecommended
        )
    }

    suspend fun evaluateWithEnrichment(
        ocrResult: ScreenOcrData,
        rules: List<CategoryRule>
    ): CategoryResult {
        val initialResult = evaluate(ocrResult, rules)
        val provider = webEnrichmentProvider
        if (!initialResult.enrichmentRecommended || provider == null) return initialResult

        val enrichedResult = provider.enrich(ocrResult)
        val result = evaluate(enrichedResult, rules)
        return if (result.matchedRule != null) {
            result.copy(usedEnrichment = true)
        } else {
            result.copy(enrichmentRecommended = false)
        }
    }

    private fun isSparse(data: ScreenOcrData, rules: List<CategoryRule>): Boolean {
        val threshold = rules.filter { it.useWebEnrichmentFallback }
            .maxOfOrNull { it.sparseOcrCharacterThreshold }
            ?: DEFAULT_SPARSE_OCR_CHARACTER_THRESHOLD
        return data.recognizedText.trim().length < threshold
    }

    private fun findMatches(
        rule: CategoryRule,
        text: String,
        explicitDomains: List<String>
    ): List<String> {
        val nonBlankValues = rule.conditionValues.map(String::trim).filter(String::isNotEmpty)
        if (nonBlankValues.isEmpty()) return emptyList()
        val normalizedText = normalize(text)

        return when (rule.conditionOperator) {
            RuleConditionOperator.CONTAINS_ANY ->
                nonBlankValues.filter { normalize(it) in normalizedText }

            RuleConditionOperator.CONTAINS_ALL -> {
                if (nonBlankValues.all { normalize(it) in normalizedText }) nonBlankValues else emptyList()
            }

            RuleConditionOperator.MATCHES_REGEX -> nonBlankValues.filter { expression ->
                try {
                    Pattern.compile(expression, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                        .matcher(text)
                        .find()
                } catch (_: PatternSyntaxException) {
                    false
                }
            }

            RuleConditionOperator.DOMAINS_MATCH -> {
                val foundDomains = (explicitDomains + extractDomains(text)).map(::normalizeDomain).toSet()
                nonBlankValues.filter { expected ->
                    val expectedDomain = normalizeDomain(expected)
                    foundDomains.any { found ->
                        found == expectedDomain || found.endsWith(".$expectedDomain")
                    }
                }
            }
        }
    }

    private fun extractDomains(text: String): List<String> {
        val urlPattern = Regex("""(?i)\b(?:https?://)?(?:www\.)?([a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+)""")
        return urlPattern.findAll(text).map { match ->
            match.groupValues[1].trimEnd('.', ',', ';', ')', ']')
        }.toList()
    }

    private fun normalizeDomain(domain: String): String {
        val trimmed = domain.trim().lowercase(Locale.ROOT)
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore(':')
            .trimEnd('.')
        return trimmed
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    companion object {
        const val UNSORTED_CATEGORY = "Unsorted"
        const val UNSORTED_FOLDER = "/Screenshots/Unsorted"
        const val DEFAULT_SPARSE_OCR_CHARACTER_THRESHOLD = 20

        val DEFAULT_RULES = listOf(
            CategoryRule(
                categoryName = "Payments/Receipts",
                destinationRelativeFolder = "/Screenshots/Receipts",
                conditionOperator = RuleConditionOperator.CONTAINS_ANY,
                conditionValues = listOf("receipt", "invoice", "upi", "payment successful", "transaction id", "total paid"),
                priority = 100,
                useWebEnrichmentFallback = true
            ),
            CategoryRule(
                categoryName = "Code/Dev",
                destinationRelativeFolder = "/Screenshots/Code",
                conditionOperator = RuleConditionOperator.CONTAINS_ANY,
                conditionValues = listOf("github", "gitlab", "stack trace", "terminal", "pull request", "function ", "class "),
                priority = 90
            ),
            CategoryRule(
                categoryName = "Social Media",
                destinationRelativeFolder = "/Screenshots/Social",
                conditionOperator = RuleConditionOperator.DOMAINS_MATCH,
                conditionValues = listOf("instagram.com", "facebook.com", "tiktok.com", "x.com", "reddit.com"),
                priority = 80,
                useWebEnrichmentFallback = true
            ),
            CategoryRule(
                categoryName = "Online Shopping",
                destinationRelativeFolder = "/Screenshots/Shopping",
                conditionOperator = RuleConditionOperator.CONTAINS_ANY,
                conditionValues = listOf("order confirmed", "track order", "shopping cart", "add to cart", "delivery address"),
                priority = 70,
                useWebEnrichmentFallback = true
            ),
            CategoryRule(
                categoryName = "Flight/Train Tickets",
                destinationRelativeFolder = "/Screenshots/Tickets",
                conditionOperator = RuleConditionOperator.CONTAINS_ANY,
                conditionValues = listOf("boarding pass", "flight number", "departure", "pnr", "e-ticket", "train ticket"),
                priority = 60,
                useWebEnrichmentFallback = true
            )
        )
    }
}

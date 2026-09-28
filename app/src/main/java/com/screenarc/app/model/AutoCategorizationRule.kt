package com.screenarc.app.model

import java.text.Normalizer
import java.util.UUID
import java.util.regex.Pattern

data class RuleMatchDetails(
    val rule: AutoCategorizationRule,
    val hasPrimaryMatch: Boolean,
    val matchedPrimaryKeywords: List<String>,
    val matchedSecondaryKeywords: List<String>,
    val primaryMatchCount: Int,
    val secondaryMatchCount: Int,
    val firstMatchIndex: Int,
    val isAssigned: Boolean,
    val reason: String
)

/**
 * Model representing an automated categorization rule with primary & secondary keywords,
 * priority order, configurable thresholds, whole-word matching, and regex/phrase support.
 */
data class AutoCategorizationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val primaryKeywords: List<String> = emptyList(),
    val secondaryKeywords: List<String> = emptyList(),
    val targetCategoryId: Long,
    val targetCategoryName: String,
    val priority: Int = 0,
    val isEnabled: Boolean = true,
    val onlyNewScreenshots: Boolean = true,
    val matchWholeWord: Boolean = false,
    val isRegexEnabled: Boolean = false,
    val secondaryThreshold: Int = 2,
    val matchCount: Int = 0,
    val lastRunTimestamp: Long? = null
) {
    // Backward compatibility for legacy callers expecting `keywords`
    val keywords: List<String>
        get() = if (primaryKeywords.isNotEmpty() || secondaryKeywords.isNotEmpty()) {
            primaryKeywords + secondaryKeywords
        } else {
            emptyList()
        }

    /**
     * Checks if a screenshot matches this rule.
     */
    fun matches(screenshot: ScreenshotUiModel): Boolean {
        if (!isEnabled) return false
        if (onlyNewScreenshots && screenshot.categoryId != null) return false
        val evaluation = evaluate(screenshot)
        return evaluation.isAssigned
    }

    /**
     * Evaluates a ScreenshotUiModel in detail against this rule.
     */
    fun evaluate(screenshot: ScreenshotUiModel): RuleMatchDetails {
        val ocrText = buildString {
            append(screenshot.recognizedText.orEmpty())
            append(" ")
            append(screenshot.normalizedText.orEmpty())
        }
        return evaluateText(ocrText, screenshot.displayName)
    }

    /**
     * Evaluates arbitrary text and filename against this rule.
     */
    fun evaluateText(text: String, fileName: String = ""): RuleMatchDetails {
        if (!isEnabled || (primaryKeywords.isEmpty() && secondaryKeywords.isEmpty())) {
            return RuleMatchDetails(
                rule = this,
                hasPrimaryMatch = false,
                matchedPrimaryKeywords = emptyList(),
                matchedSecondaryKeywords = emptyList(),
                primaryMatchCount = 0,
                secondaryMatchCount = 0,
                firstMatchIndex = Int.MAX_VALUE,
                isAssigned = false,
                reason = if (!isEnabled) "Rule is disabled" else "No keywords configured"
            )
        }

        val combinedTarget = "$text $fileName"
        val normalizedCombined = normalizeForMatching(combinedTarget)

        val matchedPrimaries = mutableListOf<String>()
        val matchedSecondaries = mutableListOf<String>()
        var earliestMatchIndex = Int.MAX_VALUE
        var primaryOccurrences = 0
        var secondaryOccurrences = 0

        // 1. Evaluate Primary Keywords
        for (kw in primaryKeywords) {
            val (matched, count, index) = testKeywordMatch(kw, normalizedCombined, combinedTarget)
            if (matched) {
                matchedPrimaries.add(kw)
                primaryOccurrences += count
                if (index in 0 until earliestMatchIndex) {
                    earliestMatchIndex = index
                }
            }
        }

        // 2. Evaluate Secondary Keywords
        for (kw in secondaryKeywords) {
            val (matched, count, index) = testKeywordMatch(kw, normalizedCombined, combinedTarget)
            if (matched) {
                matchedSecondaries.add(kw)
                secondaryOccurrences += count
                if (index in 0 until earliestMatchIndex) {
                    earliestMatchIndex = index
                }
            }
        }

        val hasPrimary = matchedPrimaries.isNotEmpty()
        val hasSecondaryThreshold = matchedSecondaries.size >= secondaryThreshold

        val isAssigned = hasPrimary || hasSecondaryThreshold
        val reason = when {
            hasPrimary -> {
                "Matched primary keywords: [${matchedPrimaries.joinToString(", ")}]"
            }
            hasSecondaryThreshold -> {
                "Matched ${matchedSecondaries.size} secondary keywords (threshold: $secondaryThreshold): [${matchedSecondaries.joinToString(", ")}]"
            }
            matchedSecondaries.isNotEmpty() -> {
                "Found ${matchedSecondaries.size} secondary keywords, but below threshold of $secondaryThreshold: [${matchedSecondaries.joinToString(", ")}]"
            }
            else -> {
                "No primary or secondary keywords matched"
            }
        }

        return RuleMatchDetails(
            rule = this,
            hasPrimaryMatch = hasPrimary,
            matchedPrimaryKeywords = matchedPrimaries,
            matchedSecondaryKeywords = matchedSecondaries,
            primaryMatchCount = primaryOccurrences,
            secondaryMatchCount = secondaryOccurrences,
            firstMatchIndex = earliestMatchIndex,
            isAssigned = isAssigned,
            reason = reason
        )
    }

    private fun testKeywordMatch(
        rawKeyword: String,
        normalizedTarget: String,
        rawTarget: String
    ): Triple<Boolean, Int, Int> {
        val trimmed = rawKeyword.trim()
        if (trimmed.isEmpty()) return Triple(false, 0, -1)

        if (isRegexEnabled) {
            return try {
                val pattern = Pattern.compile(trimmed, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                val matcher = pattern.matcher(rawTarget)
                var count = 0
                var firstIdx = -1
                while (matcher.find()) {
                    if (count == 0) firstIdx = matcher.start()
                    count++
                }
                Triple(count > 0, count, firstIdx)
            } catch (e: Exception) {
                // If invalid regex, fall back to literal check
                testLiteralMatch(trimmed, normalizedTarget)
            }
        } else if (matchWholeWord) {
            val normalizedKw = normalizeForMatching(trimmed)
            val regexStr = "\\b" + Pattern.quote(normalizedKw) + "\\b"
            val pattern = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
            val matcher = pattern.matcher(normalizedTarget)
            var count = 0
            var firstIdx = -1
            while (matcher.find()) {
                if (count == 0) firstIdx = matcher.start()
                count++
            }
            return Triple(count > 0, count, firstIdx)
        } else {
            return testLiteralMatch(trimmed, normalizedTarget)
        }
    }

    private fun testLiteralMatch(keyword: String, normalizedTarget: String): Triple<Boolean, Int, Int> {
        val normalizedKw = normalizeForMatching(keyword)
        if (normalizedKw.isEmpty()) return Triple(false, 0, -1)

        var count = 0
        var firstIdx = -1
        var startIndex = 0
        while (startIndex < normalizedTarget.length) {
            val idx = normalizedTarget.indexOf(normalizedKw, startIndex, ignoreCase = true)
            if (idx == -1) break
            if (count == 0) firstIdx = idx
            count++
            startIndex = idx + normalizedKw.length
        }
        return Triple(count > 0, count, firstIdx)
    }

    companion object {
        fun normalizeForMatching(text: String): String {
            val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
            return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase().trim()
        }

        /**
         * Resolves the winning rule from a list of rules against a screenshot.
         * Resolution order:
         * 1. Primary match rules win over secondary-only rules.
         * 2. Between primary matches:
         *    a. Priority order (lower priority number wins).
         *    b. Primary keyword match count (higher wins).
         *    c. Earliest match index (earlier in text wins).
         *    d. Secondary keyword count (higher wins).
         * 3. Between secondary-only matches (meeting threshold):
         *    a. Priority order.
         *    b. Secondary keyword match count.
         *    c. Earliest match index.
         */
        fun resolveBestMatch(
            rules: List<AutoCategorizationRule>,
            screenshot: ScreenshotUiModel
        ): RuleMatchDetails? {
            val activeRules = rules.filter { it.isEnabled && (!it.onlyNewScreenshots || screenshot.categoryId == null) }
            val evaluations = activeRules.map { it.evaluate(screenshot) }.filter { it.isAssigned }

            if (evaluations.isEmpty()) return null

            return evaluations.minWithOrNull(
                compareBy<RuleMatchDetails> { if (it.hasPrimaryMatch) 0 else 1 }
                    .thenBy { it.rule.priority }
                    .thenByDescending { if (it.hasPrimaryMatch) it.primaryMatchCount else it.secondaryMatchCount }
                    .thenBy { it.firstMatchIndex }
                    .thenByDescending { it.secondaryMatchCount }
            )
        }

        fun resolveBestMatch(
            rules: List<AutoCategorizationRule>,
            text: String
        ): RuleMatchDetails? {
            val activeRules = rules.filter { it.isEnabled }
            val evaluations = activeRules.map { it.evaluateText(text) }.filter { it.isAssigned }

            if (evaluations.isEmpty()) return null

            return evaluations.minWithOrNull(
                compareBy<RuleMatchDetails> { if (it.hasPrimaryMatch) 0 else 1 }
                    .thenBy { it.rule.priority }
                    .thenByDescending { if (it.hasPrimaryMatch) it.primaryMatchCount else it.secondaryMatchCount }
                    .thenBy { it.firstMatchIndex }
                    .thenByDescending { it.secondaryMatchCount }
            )
        }
    }
}

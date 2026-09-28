package com.screenarc.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.screenarc.app.data.local.db.entity.OcrTextEntity
import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.OrganizedRecord
import com.screenarc.app.model.RuleMatchDetails
import com.screenarc.app.model.ScreenshotUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RuleExecutionResult(
    val executedRulesCount: Int,
    val matchedScreenshotsCount: Int,
    val movedScreenshotsCount: Int,
    val message: String
)

class RulesRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("screenarc_rules", Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow<List<AutoCategorizationRule>>(emptyList())
    val rules: StateFlow<List<AutoCategorizationRule>> = _rules.asStateFlow()

    init {
        loadRules()
    }

    private fun loadRules() {
        val jsonString = prefs.getString(KEY_RULES, null)
        if (jsonString.isNullOrBlank()) {
            val defaults = createDefaultRules()
            saveRules(defaults)
            return
        }

        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<AutoCategorizationRule>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                // Migration handling: If primaryKeywords exists, read it; otherwise migrate from legacy 'keywords'
                val primaryKeywords = mutableListOf<String>()
                if (obj.has("primaryKeywords")) {
                    val pArray = obj.getJSONArray("primaryKeywords")
                    for (j in 0 until pArray.length()) {
                        primaryKeywords.add(pArray.getString(j))
                    }
                } else if (obj.has("keywords")) {
                    val kArray = obj.getJSONArray("keywords")
                    for (j in 0 until kArray.length()) {
                        primaryKeywords.add(kArray.getString(j))
                    }
                }

                val secondaryKeywords = mutableListOf<String>()
                if (obj.has("secondaryKeywords")) {
                    val sArray = obj.getJSONArray("secondaryKeywords")
                    for (j in 0 until sArray.length()) {
                        secondaryKeywords.add(sArray.getString(j))
                    }
                }

                val priority = obj.optInt("priority", i)
                val matchWholeWord = obj.optBoolean("matchWholeWord", false)
                val isRegexEnabled = obj.optBoolean("isRegexEnabled", false)
                val secondaryThreshold = obj.optInt("secondaryThreshold", 2)

                list.add(
                    AutoCategorizationRule(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        primaryKeywords = primaryKeywords,
                        secondaryKeywords = secondaryKeywords,
                        targetCategoryId = obj.getLong("targetCategoryId"),
                        targetCategoryName = obj.getString("targetCategoryName"),
                        priority = priority,
                        isEnabled = obj.optBoolean("isEnabled", true),
                        onlyNewScreenshots = obj.optBoolean("onlyNewScreenshots", true),
                        matchWholeWord = matchWholeWord,
                        isRegexEnabled = isRegexEnabled,
                        secondaryThreshold = secondaryThreshold,
                        matchCount = obj.optInt("matchCount", 0),
                        lastRunTimestamp = if (obj.has("lastRunTimestamp")) obj.getLong("lastRunTimestamp") else null
                    )
                )
            }
            _rules.value = list.sortedBy { it.priority }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading rules: ${e.message}", e)
            val defaults = createDefaultRules()
            saveRules(defaults)
        }
    }

    private fun saveRules(list: List<AutoCategorizationRule>) {
        val sortedList = list.sortedBy { it.priority }.mapIndexed { index, rule ->
            rule.copy(priority = index)
        }
        val array = JSONArray()
        sortedList.forEach { rule ->
            val obj = JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)

                val pArray = JSONArray()
                rule.primaryKeywords.forEach { pArray.put(it) }
                put("primaryKeywords", pArray)

                val sArray = JSONArray()
                rule.secondaryKeywords.forEach { sArray.put(it) }
                put("secondaryKeywords", sArray)

                // Backward compatibility mirror
                val kwArray = JSONArray()
                rule.keywords.forEach { kwArray.put(it) }
                put("keywords", kwArray)

                put("targetCategoryId", rule.targetCategoryId)
                put("targetCategoryName", rule.targetCategoryName)
                put("priority", rule.priority)
                put("isEnabled", rule.isEnabled)
                put("onlyNewScreenshots", rule.onlyNewScreenshots)
                put("matchWholeWord", rule.matchWholeWord)
                put("isRegexEnabled", rule.isRegexEnabled)
                put("secondaryThreshold", rule.secondaryThreshold)
                put("matchCount", rule.matchCount)
                if (rule.lastRunTimestamp != null) {
                    put("lastRunTimestamp", rule.lastRunTimestamp)
                }
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
        _rules.value = sortedList
    }

    fun saveRule(rule: AutoCategorizationRule) {
        val current = _rules.value.toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            current[index] = rule
        } else {
            val nextPriority = current.size
            current.add(rule.copy(priority = if (rule.priority == 0 && current.isNotEmpty()) nextPriority else rule.priority))
        }
        saveRules(current)
    }

    fun deleteRule(ruleId: String) {
        val current = _rules.value.filterNot { it.id == ruleId }
        saveRules(current)
    }

    fun toggleRule(ruleId: String, isEnabled: Boolean) {
        val current = _rules.value.map {
            if (it.id == ruleId) it.copy(isEnabled = isEnabled) else it
        }
        saveRules(current)
    }

    fun moveRulePriority(ruleId: String, moveUp: Boolean) {
        val list = _rules.value.toMutableList()
        val index = list.indexOfFirst { it.id == ruleId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex in 0 until list.size) {
            val item = list.removeAt(index)
            list.add(targetIndex, item)
            saveRules(list)
        }
    }

    fun reorderRules(reorderedRules: List<AutoCategorizationRule>) {
        saveRules(reorderedRules)
    }

    fun importRules(importedRules: List<AutoCategorizationRule>, replace: Boolean = false) {
        val current = if (replace) mutableListOf() else _rules.value.toMutableList()
        for (rule in importedRules) {
            val index = current.indexOfFirst { it.name.equals(rule.name, ignoreCase = true) || it.id == rule.id }
            if (index >= 0) {
                current[index] = rule
            } else {
                current.add(rule.copy(priority = current.size))
            }
        }
        saveRules(current)
    }

    /**
     * Executes a single rule on demand.
     */
    suspend fun runRule(
        ruleId: String,
        screenshotRepository: ScreenshotRepository,
        categoryRepository: CategoryRepository,
        ocrRepository: OcrRepository
    ): RuleExecutionResult = withContext(Dispatchers.IO) {
        val rule = _rules.value.find { it.id == ruleId }
            ?: return@withContext RuleExecutionResult(0, 0, 0, "Rule not found")

        screenshotRepository.syncMediaStoreScreenshots()

        val allScreenshotsEntities = screenshotRepository.getAllScreenshotsFlow().first()
        val allScreenshots = allScreenshotsEntities.map { it.toUiModel() }
        val categories = categoryRepository.categories.value

        val targetCategory = categories.find { it.id == rule.targetCategoryId }
            ?: categories.find { it.name.equals(rule.targetCategoryName, ignoreCase = true) }
            ?: categories.firstOrNull()

        if (targetCategory == null) {
            return@withContext RuleExecutionResult(0, 0, 0, "Target category not found")
        }

        var matchedCount = 0
        var movedCount = 0

        for (screenshot in allScreenshots) {
            var currentItem = screenshot

            if (currentItem.ocrStatus != "COMPLETED" && currentItem.ocrStatus != "NO_TEXT") {
                val ocrResult = ocrRepository.recognizeText(currentItem.contentUri)
                if (ocrResult.success && ocrResult.recognizedText.isNotBlank()) {
                    currentItem = currentItem.copy(
                        ocrStatus = "COMPLETED",
                        recognizedText = ocrResult.recognizedText,
                        normalizedText = OcrTextEntity.normalizeText(ocrResult.recognizedText)
                    )
                }
            }

            val evaluation = rule.evaluate(currentItem)
            if (evaluation.isAssigned) {
                matchedCount++
                val matched = evaluation.matchedPrimaryKeywords + evaluation.matchedSecondaryKeywords
                val result = screenshotRepository.moveToCategory(
                    screenshot = currentItem,
                    category = targetCategory,
                    ruleName = rule.name,
                    triggerType = OrganizedRecord.TRIGGER_RULE,
                    matchedKeywords = matched,
                    matchReason = evaluation.reason
                )
                if (result.isSuccess) {
                    movedCount++
                }
            }
        }

        val updatedRule = rule.copy(
            lastRunTimestamp = System.currentTimeMillis(),
            matchCount = rule.matchCount + movedCount
        )
        saveRule(updatedRule)

        RuleExecutionResult(
            executedRulesCount = 1,
            matchedScreenshotsCount = matchedCount,
            movedScreenshotsCount = movedCount,
            message = "Rule '${rule.name}' matched $matchedCount screenshots and moved $movedCount to ${targetCategory.name}"
        )
    }

    /**
     * Executes all enabled rules with deterministic priority-based conflict resolution.
     */
    suspend fun runAllActiveRules(
        screenshotRepository: ScreenshotRepository,
        categoryRepository: CategoryRepository,
        ocrRepository: OcrRepository
    ): RuleExecutionResult = withContext(Dispatchers.IO) {
        val activeRules = _rules.value.filter { it.isEnabled }.sortedBy { it.priority }
        if (activeRules.isEmpty()) {
            return@withContext RuleExecutionResult(0, 0, 0, "No active rules found to run")
        }

        screenshotRepository.syncMediaStoreScreenshots()
        val categories = categoryRepository.categories.value
        val allScreenshots = screenshotRepository.getAllScreenshotsFlow().first().map { it.toUiModel() }

        var totalMatched = 0
        var totalMoved = 0
        val ruleMatchCounts = mutableMapOf<String, Int>()

        for (screenshot in allScreenshots) {
            var currentItem = screenshot

            if (currentItem.ocrStatus != "COMPLETED" && currentItem.ocrStatus != "NO_TEXT") {
                val ocrResult = ocrRepository.recognizeText(currentItem.contentUri)
                if (ocrResult.success && ocrResult.recognizedText.isNotBlank()) {
                    currentItem = currentItem.copy(
                        ocrStatus = "COMPLETED",
                        recognizedText = ocrResult.recognizedText,
                        normalizedText = OcrTextEntity.normalizeText(ocrResult.recognizedText)
                    )
                }
            }

            // Resolve winning rule among all active rules using priority & keyword weights
            val winningMatch = AutoCategorizationRule.resolveBestMatch(activeRules, currentItem)
            if (winningMatch != null && winningMatch.isAssigned) {
                val winningRule = winningMatch.rule
                val targetCategory = categories.find { it.id == winningRule.targetCategoryId }
                    ?: categories.find { it.name.equals(winningRule.targetCategoryName, ignoreCase = true) }

                if (targetCategory != null) {
                    totalMatched++
                    val matched = winningMatch.matchedPrimaryKeywords + winningMatch.matchedSecondaryKeywords
                    val res = screenshotRepository.moveToCategory(
                        screenshot = currentItem,
                        category = targetCategory,
                        ruleName = winningRule.name,
                        triggerType = OrganizedRecord.TRIGGER_RULE,
                        matchedKeywords = matched,
                        matchReason = winningMatch.reason
                    )
                    if (res.isSuccess) {
                        totalMoved++
                        ruleMatchCounts[winningRule.id] = (ruleMatchCounts[winningRule.id] ?: 0) + 1
                    }
                }
            }
        }

        // Update lastRunTimestamp and stats
        val updatedRules = _rules.value.map { r ->
            val additional = ruleMatchCounts[r.id] ?: 0
            if (r.isEnabled) {
                r.copy(
                    lastRunTimestamp = System.currentTimeMillis(),
                    matchCount = r.matchCount + additional
                )
            } else {
                r
            }
        }
        saveRules(updatedRules)

        RuleExecutionResult(
            executedRulesCount = activeRules.size,
            matchedScreenshotsCount = totalMatched,
            movedScreenshotsCount = totalMoved,
            message = "Executed ${activeRules.size} active rules: sorted $totalMoved screenshots"
        )
    }

    private fun createDefaultRules(): List<AutoCategorizationRule> {
        return listOf(
            AutoCategorizationRule(
                id = "rule_tech_osint",
                name = "Tech, Repos & OSINT",
                primaryKeywords = listOf("github", "gitlab", "terminal", "docker", "osint", "repo"),
                secondaryKeywords = listOf("tool", "code", "api", "python", "debug", "script"),
                targetCategoryId = 1L, // Tech
                targetCategoryName = "Tech",
                priority = 0,
                isEnabled = true,
                onlyNewScreenshots = true,
                secondaryThreshold = 2
            ),
            AutoCategorizationRule(
                id = "rule_movies_cinema",
                name = "Movie & Cinema",
                primaryKeywords = listOf("movie", "netflix", "cinema", "imdb", "film", "hotstar"),
                secondaryKeywords = listOf("trailer", "streaming", "prime video", "director", "actor", "series"),
                targetCategoryId = 5L, // Memes / Media
                targetCategoryName = "Memes",
                priority = 1,
                isEnabled = true,
                onlyNewScreenshots = true,
                secondaryThreshold = 2
            ),
            AutoCategorizationRule(
                id = "rule_receipts_invoices",
                name = "Receipts & Invoices",
                primaryKeywords = listOf("receipt", "invoice", "upi", "billing", "paid", "transaction"),
                secondaryKeywords = listOf("payment", "order", "total", "amount", "tax", "gst", "subtotal"),
                targetCategoryId = 4L, // Receipts
                targetCategoryName = "Receipts",
                priority = 2,
                isEnabled = true,
                onlyNewScreenshots = true,
                secondaryThreshold = 2
            ),
            AutoCategorizationRule(
                id = "rule_docs_notes",
                name = "Docs & Reading",
                primaryKeywords = listOf("document", "article", "pdf", "chapter", "guide", "tutorial"),
                secondaryKeywords = listOf("notes", "summary", "reference", "manual", "book", "read"),
                targetCategoryId = 2L, // Docs
                targetCategoryName = "Docs",
                priority = 3,
                isEnabled = true,
                onlyNewScreenshots = true,
                secondaryThreshold = 2
            )
        )
    }

    companion object {
        private const val TAG = "RulesRepository"
        private const val KEY_RULES = "category_allocation_rules_json"
    }
}

package com.screenarc.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.screenarc.app.data.local.db.entity.OcrTextEntity
import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.OrganizedRecord
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
                val keywordsArray = obj.getJSONArray("keywords")
                val keywords = mutableListOf<String>()
                for (j in 0 until keywordsArray.length()) {
                    keywords.add(keywordsArray.getString(j))
                }

                list.add(
                    AutoCategorizationRule(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        keywords = keywords,
                        targetCategoryId = obj.getLong("targetCategoryId"),
                        targetCategoryName = obj.getString("targetCategoryName"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        onlyNewScreenshots = obj.optBoolean("onlyNewScreenshots", true),
                        matchCount = obj.optInt("matchCount", 0),
                        lastRunTimestamp = if (obj.has("lastRunTimestamp")) obj.getLong("lastRunTimestamp") else null
                    )
                )
            }
            _rules.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading rules: ${e.message}", e)
            val defaults = createDefaultRules()
            saveRules(defaults)
        }
    }

    private fun saveRules(list: List<AutoCategorizationRule>) {
        val array = JSONArray()
        list.forEach { rule ->
            val obj = JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                val kwArray = JSONArray()
                rule.keywords.forEach { kwArray.put(it) }
                put("keywords", kwArray)
                put("targetCategoryId", rule.targetCategoryId)
                put("targetCategoryName", rule.targetCategoryName)
                put("isEnabled", rule.isEnabled)
                put("onlyNewScreenshots", rule.onlyNewScreenshots)
                put("matchCount", rule.matchCount)
                if (rule.lastRunTimestamp != null) {
                    put("lastRunTimestamp", rule.lastRunTimestamp)
                }
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
        _rules.value = list
    }

    fun saveRule(rule: AutoCategorizationRule) {
        val current = _rules.value.toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            current[index] = rule
        } else {
            current.add(rule)
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

            if (rule.matches(currentItem)) {
                matchedCount++
                val result = screenshotRepository.moveToCategory(
                    screenshot = currentItem,
                    category = targetCategory,
                    ruleName = rule.name,
                    triggerType = OrganizedRecord.TRIGGER_RULE
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
     * Executes all enabled rules sequentially.
     */
    suspend fun runAllActiveRules(
        screenshotRepository: ScreenshotRepository,
        categoryRepository: CategoryRepository,
        ocrRepository: OcrRepository
    ): RuleExecutionResult = withContext(Dispatchers.IO) {
        val activeRules = _rules.value.filter { it.isEnabled }
        if (activeRules.isEmpty()) {
            return@withContext RuleExecutionResult(0, 0, 0, "No active rules found to run")
        }

        screenshotRepository.syncMediaStoreScreenshots()
        val categories = categoryRepository.categories.value

        var totalMatched = 0
        var totalMoved = 0
        var rulesExecuted = 0

        for (rule in activeRules) {
            val allScreenshots = screenshotRepository.getAllScreenshotsFlow().first().map { it.toUiModel() }
            val targetCategory = categories.find { it.id == rule.targetCategoryId }
                ?: categories.find { it.name.equals(rule.targetCategoryName, ignoreCase = true) }
                ?: continue

            var matchedForRule = 0
            var movedForRule = 0

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

                if (rule.matches(currentItem)) {
                    matchedForRule++
                    val res = screenshotRepository.moveToCategory(
                        screenshot = currentItem,
                        category = targetCategory,
                        ruleName = rule.name,
                        triggerType = OrganizedRecord.TRIGGER_RULE
                    )
                    if (res.isSuccess) {
                        movedForRule++
                    }
                }
            }

            rulesExecuted++
            totalMatched += matchedForRule
            totalMoved += movedForRule

            val updatedRule = rule.copy(
                lastRunTimestamp = System.currentTimeMillis(),
                matchCount = rule.matchCount + movedForRule
            )
            saveRule(updatedRule)
        }

        RuleExecutionResult(
            executedRulesCount = rulesExecuted,
            matchedScreenshotsCount = totalMatched,
            movedScreenshotsCount = totalMoved,
            message = "Executed $rulesExecuted active rules: sorted $totalMoved screenshots"
        )
    }

    private fun createDefaultRules(): List<AutoCategorizationRule> {
        return listOf(
            AutoCategorizationRule(
                id = "rule_tech_osint",
                name = "Tech, Repos & OSINT",
                keywords = listOf("repo", "tool", "osint", "github", "gitlab", "terminal", "docker", "code", "api", "python"),
                targetCategoryId = 1L, // Tech
                targetCategoryName = "Tech",
                isEnabled = true,
                onlyNewScreenshots = true
            ),
            AutoCategorizationRule(
                id = "rule_movies_cinema",
                name = "Movie & Cinema",
                keywords = listOf("movie", "netflix", "cinema", "imdb", "film", "hotstar", "trailer", "streaming", "prime video"),
                targetCategoryId = 5L,
                targetCategoryName = "Memes",
                isEnabled = true,
                onlyNewScreenshots = true
            ),
            AutoCategorizationRule(
                id = "rule_receipts_invoices",
                name = "Receipts & Invoices",
                keywords = listOf("receipt", "invoice", "payment", "upi", "order", "total", "billing", "paid", "amount", "transaction"),
                targetCategoryId = 4L, // Receipts
                targetCategoryName = "Receipts",
                isEnabled = true,
                onlyNewScreenshots = true
            ),
            AutoCategorizationRule(
                id = "rule_docs_notes",
                name = "Docs & Reading",
                keywords = listOf("document", "article", "notes", "summary", "pdf", "chapter", "guide", "tutorial"),
                targetCategoryId = 2L, // Docs
                targetCategoryName = "Docs",
                isEnabled = true,
                onlyNewScreenshots = true
            )
        )
    }

    companion object {
        private const val TAG = "RulesRepository"
        private const val KEY_RULES = "category_allocation_rules_json"
    }
}

package com.screenarc.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.screenarc.app.data.local.db.AppDatabase
import com.screenarc.app.data.local.db.entity.OcrTextEntity
import com.screenarc.app.data.local.db.entity.RuleEntity
import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.OrganizedRecord
import com.screenarc.app.model.RuleMatchDetails
import com.screenarc.app.model.ScreenshotUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RuleExecutionResult(
    val executedRulesCount: Int,
    val matchedScreenshotsCount: Int,
    val movedScreenshotsCount: Int,
    val message: String
)

class RulesRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ruleDao = database.ruleDao()
    private val prefs: SharedPreferences = context.getSharedPreferences("screenarc_rules", Context.MODE_PRIVATE)

    val rules: StateFlow<List<AutoCategorizationRule>> = ruleDao.getAllRulesFlow()
        .map { entities ->
            entities.map { it.toDomainModel() }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    init {
        scope.launch {
            initRules()
        }
    }

    private suspend fun initRules() = withContext(Dispatchers.IO) {
        val count = ruleDao.getRuleCount()
        if (count == 0) {
            // Check for legacy rules in SharedPreferences
            val legacyJson = prefs.getString(KEY_RULES, null)
            if (!legacyJson.isNullOrBlank()) {
                try {
                    val migrated = parseLegacyRulesJson(legacyJson)
                    if (migrated.isNotEmpty()) {
                        val entities = migrated.mapIndexed { idx, rule ->
                            RuleEntity.fromDomainModel(rule.copy(priority = idx))
                        }
                        ruleDao.insertRules(entities)
                        Log.d(TAG, "Migrated ${entities.size} rules from SharedPreferences to Room")
                    } else {
                        seedDefaultRules()
                    }
                    prefs.edit().remove(KEY_RULES).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error migrating legacy rules: ${e.message}", e)
                    seedDefaultRules()
                }
            } else {
                seedDefaultRules()
            }
        }
    }

    private suspend fun seedDefaultRules() {
        val defaults = createDefaultRules()
        val entities = defaults.mapIndexed { idx, rule ->
            RuleEntity.fromDomainModel(rule.copy(priority = idx))
        }
        ruleDao.insertRules(entities)
        Log.d(TAG, "Seeded ${entities.size} default rules in Room")
    }

    private fun parseLegacyRulesJson(jsonString: String): List<AutoCategorizationRule> {
        val array = JSONArray(jsonString)
        val list = mutableListOf<AutoCategorizationRule>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
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
        return list
    }

    fun saveRule(rule: AutoCategorizationRule) {
        scope.launch(Dispatchers.IO) {
            val currentList = ruleDao.getAllRules()
            val existingIndex = currentList.indexOfFirst { it.id == rule.id }
            val priority = if (existingIndex >= 0) {
                currentList[existingIndex].priority
            } else {
                currentList.size
            }
            ruleDao.insertRule(RuleEntity.fromDomainModel(rule.copy(priority = priority)))
        }
    }

    fun deleteRule(ruleId: String) {
        scope.launch(Dispatchers.IO) {
            ruleDao.deleteRuleById(ruleId)
            normalizePriorities()
        }
    }

    fun toggleRule(ruleId: String, isEnabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            val existing = ruleDao.getRuleById(ruleId) ?: return@launch
            ruleDao.updateRule(existing.copy(isEnabled = isEnabled, updatedAt = System.currentTimeMillis()))
        }
    }

    fun moveRulePriority(ruleId: String, moveUp: Boolean) {
        scope.launch(Dispatchers.IO) {
            val list = ruleDao.getAllRules().toMutableList()
            val index = list.indexOfFirst { it.id == ruleId }
            if (index == -1) return@launch
            val targetIndex = if (moveUp) index - 1 else index + 1
            if (targetIndex in 0 until list.size) {
                val item = list.removeAt(index)
                list.add(targetIndex, item)
                val reindexed = list.mapIndexed { idx, entity -> entity.copy(priority = idx) }
                ruleDao.insertRules(reindexed)
            }
        }
    }

    fun reorderRules(reorderedRules: List<AutoCategorizationRule>) {
        scope.launch(Dispatchers.IO) {
            val entities = reorderedRules.mapIndexed { idx, rule ->
                RuleEntity.fromDomainModel(rule.copy(priority = idx))
            }
            ruleDao.insertRules(entities)
        }
    }

    fun importRules(importedRules: List<AutoCategorizationRule>, replace: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            if (replace) {
                ruleDao.deleteAllRules()
            }
            val existing = ruleDao.getAllRules().toMutableList()
            for (rule in importedRules) {
                val matchIdx = existing.indexOfFirst { it.name.equals(rule.name, ignoreCase = true) || it.id == rule.id }
                if (matchIdx >= 0) {
                    existing[matchIdx] = RuleEntity.fromDomainModel(rule.copy(priority = matchIdx))
                } else {
                    existing.add(RuleEntity.fromDomainModel(rule.copy(priority = existing.size)))
                }
            }
            val reindexed = existing.mapIndexed { idx, entity -> entity.copy(priority = idx) }
            ruleDao.insertRules(reindexed)
        }
    }

    suspend fun updateCategoryNameForRules(categoryId: Long, newName: String) = withContext(Dispatchers.IO) {
        ruleDao.updateCategoryNameForRules(categoryId, newName)
    }

    private suspend fun normalizePriorities() {
        val list = ruleDao.getAllRules()
        val reindexed = list.mapIndexed { idx, entity -> entity.copy(priority = idx) }
        ruleDao.insertRules(reindexed)
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
        val entity = ruleDao.getRuleById(ruleId)
            ?: return@withContext RuleExecutionResult(0, 0, 0, "Rule not found")
        val rule = entity.toDomainModel()

        screenshotRepository.syncMediaStoreScreenshots()

        val allScreenshotsEntities = screenshotRepository.getAllScreenshotsFlow().first()
        val allScreenshots = allScreenshotsEntities.map { it.toUiModel() }
        // Strictly apply rules only to uncategorized screenshots; already categorized items are preserved
        val uncategorizedScreenshots = allScreenshots.filter { it.categoryId == null }
        val categories = categoryRepository.categories.value

        val targetCategory = categories.find { it.id == rule.targetCategoryId }
            ?: categories.find { it.name.equals(rule.targetCategoryName, ignoreCase = true) }
            ?: categories.firstOrNull()

        if (targetCategory == null) {
            return@withContext RuleExecutionResult(0, 0, 0, "Target category not found")
        }

        if (uncategorizedScreenshots.isEmpty()) {
            return@withContext RuleExecutionResult(
                executedRulesCount = 1,
                matchedScreenshotsCount = 0,
                movedScreenshotsCount = 0,
                message = "All screenshots are already categorized. Rules only evaluate uncategorized items."
            )
        }

        var matchedCount = 0
        var movedCount = 0

        for (screenshot in uncategorizedScreenshots) {
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
        ruleDao.insertRule(RuleEntity.fromDomainModel(updatedRule))

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
        val activeRules = ruleDao.getAllRules()
            .map { it.toDomainModel() }
            .filter { it.isEnabled }
            .sortedBy { it.priority }

        if (activeRules.isEmpty()) {
            return@withContext RuleExecutionResult(0, 0, 0, "No active rules found to run")
        }

        screenshotRepository.syncMediaStoreScreenshots()
        val categories = categoryRepository.categories.value
        val allScreenshots = screenshotRepository.getAllScreenshotsFlow().first().map { it.toUiModel() }
        // Strictly apply rules only to uncategorized screenshots; already categorized items are preserved
        val uncategorizedScreenshots = allScreenshots.filter { it.categoryId == null }

        if (uncategorizedScreenshots.isEmpty()) {
            return@withContext RuleExecutionResult(
                executedRulesCount = activeRules.size,
                matchedScreenshotsCount = 0,
                movedScreenshotsCount = 0,
                message = "All screenshots are already categorized. Rules only evaluate uncategorized items."
            )
        }

        var totalMatched = 0
        var totalMoved = 0
        val ruleMatchCounts = mutableMapOf<String, Int>()

        for (screenshot in uncategorizedScreenshots) {
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

        // Update lastRunTimestamp and stats in database
        val updatedEntities = ruleDao.getAllRules().map { entity ->
            val additional = ruleMatchCounts[entity.id] ?: 0
            if (entity.isEnabled) {
                entity.copy(
                    lastRunTimestamp = System.currentTimeMillis(),
                    matchCount = entity.matchCount + additional,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                entity
            }
        }
        ruleDao.insertRules(updatedEntities)

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

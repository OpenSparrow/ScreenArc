package com.screenarc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.screenarc.app.model.AutoCategorizationRule
import org.json.JSONArray

@Entity(
    tableName = "rules",
    indices = [
        Index("priority"),
        Index("targetCategoryId"),
        Index("isEnabled")
    ]
)
data class RuleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val primaryKeywords: String, // Stored as comma-separated or JSON array string
    val secondaryKeywords: String = "",
    val regexPattern: String? = null,
    val targetCategoryId: Long,
    val targetCategoryName: String,
    val priority: Int = 0,
    val isEnabled: Boolean = true,
    val onlyNewScreenshots: Boolean = true,
    val matchWholeWord: Boolean = false,
    val isRegexEnabled: Boolean = false,
    val secondaryThreshold: Int = 2,
    val matchCount: Int = 0,
    val lastRunTimestamp: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomainModel(): AutoCategorizationRule {
        val pKeywords = parseKeywords(primaryKeywords)
        val sKeywords = parseKeywords(secondaryKeywords)
        return AutoCategorizationRule(
            id = id,
            name = name,
            primaryKeywords = pKeywords,
            secondaryKeywords = sKeywords,
            targetCategoryId = targetCategoryId,
            targetCategoryName = targetCategoryName,
            priority = priority,
            isEnabled = isEnabled,
            onlyNewScreenshots = onlyNewScreenshots,
            matchWholeWord = matchWholeWord,
            isRegexEnabled = isRegexEnabled,
            secondaryThreshold = secondaryThreshold,
            matchCount = matchCount,
            lastRunTimestamp = lastRunTimestamp
        )
    }

    companion object {
        fun fromDomainModel(rule: AutoCategorizationRule): RuleEntity {
            return RuleEntity(
                id = rule.id,
                name = rule.name,
                primaryKeywords = serializeKeywords(rule.primaryKeywords),
                secondaryKeywords = serializeKeywords(rule.secondaryKeywords),
                targetCategoryId = rule.targetCategoryId,
                targetCategoryName = rule.targetCategoryName,
                priority = rule.priority,
                isEnabled = rule.isEnabled,
                onlyNewScreenshots = rule.onlyNewScreenshots,
                matchWholeWord = rule.matchWholeWord,
                isRegexEnabled = rule.isRegexEnabled,
                secondaryThreshold = rule.secondaryThreshold,
                matchCount = rule.matchCount,
                lastRunTimestamp = rule.lastRunTimestamp
            )
        }

        private fun serializeKeywords(keywords: List<String>): String {
            val jsonArray = JSONArray()
            keywords.forEach { jsonArray.put(it) }
            return jsonArray.toString()
        }

        private fun parseKeywords(serialized: String): List<String> {
            if (serialized.isBlank()) return emptyList()
            return try {
                if (serialized.trim().startsWith("[")) {
                    val array = JSONArray(serialized)
                    val result = mutableListOf<String>()
                    for (i in 0 until array.length()) {
                        result.add(array.getString(i))
                    }
                    result
                } else {
                    serialized.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
            } catch (e: Exception) {
                serialized.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    }
}

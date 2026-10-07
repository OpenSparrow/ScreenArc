package com.screenarc.app.data.local.db.entity

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.screenarc.app.model.OrganizedRecord
import org.json.JSONArray

@Entity(
    tableName = "organized_history",
    indices = [
        Index("timestamp"),
        Index("categoryName"),
        Index("triggerType"),
        Index("ruleName")
    ]
)
data class OrganizedHistoryEntity(
    @PrimaryKey
    val id: String,
    val screenshotId: Long,
    val uri: String,
    val fileName: String,
    val categoryName: String,
    val targetFolder: String,
    val timestamp: Long,
    val success: Boolean = true,
    val ruleName: String? = null,
    val triggerType: String = OrganizedRecord.TRIGGER_MANUAL,
    val matchedKeywords: String = "",
    val matchReason: String? = null
) {
    fun toDomainModel(): OrganizedRecord {
        val keywords = parseKeywords(matchedKeywords)
        return OrganizedRecord(
            id = id,
            screenshotId = screenshotId,
            uri = Uri.parse(uri),
            fileName = fileName,
            categoryName = categoryName,
            targetFolder = targetFolder,
            timestamp = timestamp,
            success = success,
            ruleName = ruleName,
            triggerType = triggerType,
            matchedKeywords = keywords,
            matchReason = matchReason
        )
    }

    companion object {
        fun fromDomainModel(record: OrganizedRecord): OrganizedHistoryEntity {
            val jsonArray = JSONArray()
            record.matchedKeywords.forEach { jsonArray.put(it) }
            return OrganizedHistoryEntity(
                id = record.id,
                screenshotId = record.screenshotId,
                uri = record.uri.toString(),
                fileName = record.fileName,
                categoryName = record.categoryName,
                targetFolder = record.targetFolder,
                timestamp = record.timestamp,
                success = record.success,
                ruleName = record.ruleName,
                triggerType = record.triggerType,
                matchedKeywords = jsonArray.toString(),
                matchReason = record.matchReason
            )
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
                emptyList()
            }
        }
    }
}

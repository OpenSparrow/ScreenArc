package com.screenarc.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.screenarc.app.data.local.db.AppDatabase
import com.screenarc.app.data.local.db.entity.OrganizedHistoryEntity
import com.screenarc.app.model.OrganizedRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class OrganizedHistoryRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val historyDao = database.organizedHistoryDao()
    private val prefs: SharedPreferences = context.getSharedPreferences("screenarc_history", Context.MODE_PRIVATE)

    val history: StateFlow<List<OrganizedRecord>> = historyDao.getAllHistoryFlow()
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
            migrateLegacyHistoryIfPresent()
        }
    }

    private suspend fun migrateLegacyHistoryIfPresent() = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return@withContext
        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<OrganizedHistoryEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val ruleNameRaw = obj.optString("ruleName", "")
                val triggerTypeRaw = obj.optString("triggerType", OrganizedRecord.TRIGGER_MANUAL)
                val matchReasonRaw = obj.optString("matchReason", "")
                val keywordsList = mutableListOf<String>()
                if (obj.has("matchedKeywords")) {
                    val kwArray = obj.getJSONArray("matchedKeywords")
                    for (k in 0 until kwArray.length()) {
                        keywordsList.add(kwArray.getString(k))
                    }
                }
                val domain = OrganizedRecord(
                    id = obj.getString("id"),
                    screenshotId = obj.getLong("screenshotId"),
                    uri = Uri.parse(obj.getString("uri")),
                    fileName = obj.getString("fileName"),
                    categoryName = obj.getString("categoryName"),
                    targetFolder = obj.getString("targetFolder"),
                    timestamp = obj.getLong("timestamp"),
                    success = obj.optBoolean("success", true),
                    ruleName = if (ruleNameRaw.isNotBlank()) ruleNameRaw else null,
                    triggerType = if (triggerTypeRaw.isNotBlank()) triggerTypeRaw else OrganizedRecord.TRIGGER_MANUAL,
                    matchedKeywords = keywordsList,
                    matchReason = if (matchReasonRaw.isNotBlank()) matchReasonRaw else null
                )
                list.add(OrganizedHistoryEntity.fromDomainModel(domain))
            }
            if (list.isNotEmpty()) {
                historyDao.insertRecords(list)
                Log.d(TAG, "Migrated ${list.size} history records from SharedPreferences to Room")
            }
            // Clear legacy key to avoid re-migration
            prefs.edit().remove(KEY_HISTORY).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed migrating legacy history: ${e.message}", e)
        }
    }

    fun addRecord(record: OrganizedRecord) {
        scope.launch(Dispatchers.IO) {
            historyDao.insertRecord(OrganizedHistoryEntity.fromDomainModel(record))
            // Prune old records cleanly in database beyond 1,000 entries
            historyDao.pruneOldRecords(1000)
        }
    }

    fun addRecords(records: List<OrganizedRecord>) {
        scope.launch(Dispatchers.IO) {
            val entities = records.map { OrganizedHistoryEntity.fromDomainModel(it) }
            historyDao.insertRecords(entities)
            historyDao.pruneOldRecords(1000)
        }
    }

    fun clearHistory() {
        scope.launch(Dispatchers.IO) {
            historyDao.deleteAllHistory()
        }
    }

    suspend fun updateCategoryName(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        historyDao.updateCategoryName(oldName, newName)
    }

    companion object {
        private const val TAG = "OrganizedHistoryRepo"
        private const val KEY_HISTORY = "history_records_json"
    }
}

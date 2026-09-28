package com.screenarc.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.screenarc.app.model.OrganizedRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class OrganizedHistoryRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("screenarc_history", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow<List<OrganizedRecord>>(emptyList())
    val history: StateFlow<List<OrganizedRecord>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return
        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<OrganizedRecord>()
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
                list.add(
                    OrganizedRecord(
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
                )
            }
            _history.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveHistory(list: List<OrganizedRecord>) {
        val array = JSONArray()
        list.take(200).forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("screenshotId", item.screenshotId)
                put("uri", item.uri.toString())
                put("fileName", item.fileName)
                put("categoryName", item.categoryName)
                put("targetFolder", item.targetFolder)
                put("timestamp", item.timestamp)
                put("success", item.success)
                put("ruleName", item.ruleName ?: "")
                put("triggerType", item.triggerType)
                val kwArray = JSONArray()
                item.matchedKeywords.forEach { kwArray.put(it) }
                put("matchedKeywords", kwArray)
                put("matchReason", item.matchReason ?: "")
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
        _history.value = list
    }

    fun addRecord(record: OrganizedRecord) {
        val current = _history.value.toMutableList()
        current.add(0, record)
        saveHistory(current)
    }

    fun addRecords(records: List<OrganizedRecord>) {
        val current = _history.value.toMutableList()
        current.addAll(0, records)
        saveHistory(current)
    }

    fun clearHistory() {
        saveHistory(emptyList())
    }

    companion object {
        private const val KEY_HISTORY = "history_records_json"
    }
}

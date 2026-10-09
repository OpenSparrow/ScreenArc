package com.screenarc.app.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.screenarc.app.model.AppThemePreset
import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.model.CategoryUiModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class BackupImportResult(
    val rules: List<AutoCategorizationRule>,
    val categories: List<CategoryUiModel>,
    val settingsMap: Map<String, Any?>,
    val success: Boolean,
    val message: String
)

object ConfigurationBackupManager {
    private const val TAG = "ConfigBackupManager"
    private const val BACKUP_VERSION = 1

    /**
     * Serializes rules, categories, and settings into a formatted JSON string.
     */
    fun createBackupJson(
        rules: List<AutoCategorizationRule>,
        categories: List<CategoryUiModel>,
        settings: AppSettings
    ): String {
        val root = JSONObject().apply {
            put("app", "ScreenArc")
            put("version", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())

            // Categories
            val catArray = JSONArray()
            categories.forEach { cat ->
                val catObj = JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("subfolderName", cat.subfolderName)
                    put("iconName", cat.iconName)
                    put("colorHex", cat.colorHex)
                    put("isDefault", cat.isDefault)
                    put("orderIndex", cat.orderIndex)
                }
                catArray.put(catObj)
            }
            put("categories", catArray)

            // Rules
            val rulesArray = JSONArray()
            rules.forEach { rule ->
                val ruleObj = JSONObject().apply {
                    put("id", rule.id)
                    put("name", rule.name)
                    val pArray = JSONArray()
                    rule.primaryKeywords.forEach { pArray.put(it) }
                    put("primaryKeywords", pArray)

                    val sArray = JSONArray()
                    rule.secondaryKeywords.forEach { sArray.put(it) }
                    put("secondaryKeywords", sArray)

                    put("targetCategoryId", rule.targetCategoryId)
                    put("targetCategoryName", rule.targetCategoryName)
                    put("priority", rule.priority)
                    put("isEnabled", rule.isEnabled)
                    put("onlyNewScreenshots", rule.onlyNewScreenshots)
                    put("matchWholeWord", rule.matchWholeWord)
                    put("isRegexEnabled", rule.isRegexEnabled)
                    put("secondaryThreshold", rule.secondaryThreshold)
                    put("matchCount", rule.matchCount)
                }
                rulesArray.put(ruleObj)
            }
            put("rules", rulesArray)

            // Settings
            val settingsObj = JSONObject().apply {
                put("bubbleSizeDp", settings.bubbleSizeDp)
                put("arcRadiusDp", settings.arcRadiusDp)
                put("autoDismissSeconds", settings.autoDismissSeconds)
                put("hapticFeedbackEnabled", settings.hapticFeedbackEnabled)
                put("dynamicPaletteEnabled", settings.dynamicPaletteEnabled)
                put("themePreset", settings.themePreset.id)
                put("uiDesignStyle", settings.uiDesignStyle.name)
                put("ocrLanguage", settings.ocrLanguage.id)
            }
            put("settings", settingsObj)
        }
        return root.toString(2)
    }

    /**
     * Parses a backup JSON string and extracts rules and categories.
     */
    fun parseBackupJson(jsonString: String): BackupImportResult {
        return try {
            val root = JSONObject(jsonString)

            // Extract categories
            val parsedCategories = mutableListOf<CategoryUiModel>()
            if (root.has("categories")) {
                val catArray = root.getJSONArray("categories")
                for (i in 0 until catArray.length()) {
                    val obj = catArray.getJSONObject(i)
                    parsedCategories.add(
                        CategoryUiModel(
                            id = obj.optLong("id", (i + 1).toLong()),
                            name = obj.getString("name"),
                            subfolderName = obj.optString("subfolderName", obj.getString("name")),
                            iconName = obj.optString("iconName", "Folder"),
                            colorHex = obj.optLong("colorHex", 0xFF6750A4),
                            isDefault = obj.optBoolean("isDefault", false),
                            orderIndex = obj.optInt("orderIndex", i)
                        )
                    )
                }
            }

            // Extract rules
            val parsedRules = mutableListOf<AutoCategorizationRule>()
            if (root.has("rules")) {
                val rulesArray = root.getJSONArray("rules")
                for (i in 0 until rulesArray.length()) {
                    val obj = rulesArray.getJSONObject(i)
                    val pList = mutableListOf<String>()
                    if (obj.has("primaryKeywords")) {
                        val arr = obj.getJSONArray("primaryKeywords")
                        for (j in 0 until arr.length()) pList.add(arr.getString(j))
                    } else if (obj.has("keywords")) {
                        val arr = obj.getJSONArray("keywords")
                        for (j in 0 until arr.length()) pList.add(arr.getString(j))
                    }

                    val sList = mutableListOf<String>()
                    if (obj.has("secondaryKeywords")) {
                        val arr = obj.getJSONArray("secondaryKeywords")
                        for (j in 0 until arr.length()) sList.add(arr.getString(j))
                    }

                    parsedRules.add(
                        AutoCategorizationRule(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            name = obj.getString("name"),
                            primaryKeywords = pList,
                            secondaryKeywords = sList,
                            targetCategoryId = obj.optLong("targetCategoryId", 1L),
                            targetCategoryName = obj.optString("targetCategoryName", "Tech"),
                            priority = obj.optInt("priority", i),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            onlyNewScreenshots = obj.optBoolean("onlyNewScreenshots", true),
                            matchWholeWord = obj.optBoolean("matchWholeWord", false),
                            isRegexEnabled = obj.optBoolean("isRegexEnabled", false),
                            secondaryThreshold = obj.optInt("secondaryThreshold", 2),
                            matchCount = obj.optInt("matchCount", 0)
                        )
                    )
                }
            }

            val settingsMap = mutableMapOf<String, Any?>()
            if (root.has("settings")) {
                val sObj = root.getJSONObject("settings")
                sObj.keys().forEach { key ->
                    settingsMap[key] = sObj.opt(key)
                }
            }

            BackupImportResult(
                rules = parsedRules,
                categories = parsedCategories,
                settingsMap = settingsMap,
                success = true,
                message = "Parsed ${parsedRules.size} rules and ${parsedCategories.size} categories successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing backup JSON: ${e.message}", e)
            BackupImportResult(
                rules = emptyList(),
                categories = emptyList(),
                settingsMap = emptyMap(),
                success = false,
                message = "Invalid backup format: ${e.message}"
            )
        }
    }

    /**
     * Reads text from a content URI.
     */
    fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read URI: ${e.message}", e)
            null
        }
    }

    /**
     * Shares backup JSON via Android share sheet.
     */
    fun shareBackup(context: Context, jsonString: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ScreenArc_Configuration_Backup.json")
            putExtra(Intent.EXTRA_TEXT, jsonString)
        }
        context.startActivity(Intent.createChooser(intent, "Export ScreenArc Backup"))
    }

    /**
     * Copies backup JSON to system clipboard.
     */
    fun copyToClipboard(context: Context, jsonString: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ScreenArc Backup", jsonString))
    }
}

package com.screenarc.app.model

import java.util.UUID

/**
 * Model representing an automated categorization rule based on OCR text & filename keywords.
 */
data class AutoCategorizationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val keywords: List<String>, // e.g., ["Movie", "Netflix", "Cinema"] or ["Repo", "Tool", "OSINT"]
    val targetCategoryId: Long,
    val targetCategoryName: String,
    val isEnabled: Boolean = true,
    val onlyNewScreenshots: Boolean = true, // When true, only processes unorganized/uncategorized screenshots
    val matchCount: Int = 0,
    val lastRunTimestamp: Long? = null
) {
    /**
     * Checks if a screenshot matches this rule's keywords.
     * Evaluates normalized recognized OCR text, raw recognized text, and filename.
     */
    fun matches(screenshot: ScreenshotUiModel): Boolean {
        if (!isEnabled) return false
        if (onlyNewScreenshots && screenshot.categoryId != null) return false
        if (keywords.isEmpty()) return false

        val normalizedOcr = screenshot.normalizedText.orEmpty()
        val rawOcr = screenshot.recognizedText.orEmpty().lowercase()
        val fileName = screenshot.displayName.lowercase()

        return keywords.any { kw ->
            val cleanKw = kw.trim().lowercase()
            if (cleanKw.isEmpty()) false
            else {
                normalizedOcr.contains(cleanKw) ||
                rawOcr.contains(cleanKw) ||
                fileName.contains(cleanKw)
            }
        }
    }
}

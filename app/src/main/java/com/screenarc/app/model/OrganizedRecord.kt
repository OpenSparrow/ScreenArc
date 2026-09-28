package com.screenarc.app.model

import android.net.Uri

data class OrganizedRecord(
    val id: String,
    val screenshotId: Long,
    val uri: Uri,
    val fileName: String,
    val categoryName: String,
    val targetFolder: String,
    val timestamp: Long,
    val success: Boolean = true,
    val ruleName: String? = null,
    val triggerType: String = TRIGGER_MANUAL,
    val matchedKeywords: List<String> = emptyList(),
    val matchReason: String? = null
) {
    companion object {
        const val TRIGGER_MANUAL = "MANUAL"
        const val TRIGGER_RULE = "RULE"
        const val TRIGGER_BULK_MANUAL = "BULK_MANUAL"
        const val TRIGGER_OVERLAY = "OVERLAY"
    }
}

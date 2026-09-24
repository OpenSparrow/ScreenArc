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
    val success: Boolean = true
)

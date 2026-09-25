package com.screenarc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "screenshots",
    indices = [
        Index("uri", unique = true),
        Index("dateAdded"),
        Index("categoryId"),
        Index("displayName")
    ]
)
data class ScreenshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val dateAdded: Long,
    val size: Long,
    val relativePath: String,
    val mimeType: String = "image/png",
    val categoryId: Long? = null,
    val ocrStatus: String = "NOT_STARTED", // "NOT_STARTED", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED", "NO_TEXT"
    val updatedAt: Long = System.currentTimeMillis()
)

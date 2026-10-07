package com.screenarc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.screenarc.app.data.local.db.entity.OcrTextEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrDao {

    @Query("SELECT * FROM ocr_texts WHERE screenshotUri = :uri LIMIT 1")
    fun getOcrForUriFlow(uri: String): Flow<OcrTextEntity?>

    @Query("SELECT * FROM ocr_texts WHERE screenshotUri = :uri LIMIT 1")
    suspend fun getOcrForUri(uri: String): OcrTextEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(ocrText: OcrTextEntity)

    @Update
    suspend fun updateOcr(ocrText: OcrTextEntity)

    @Query("DELETE FROM ocr_texts WHERE screenshotUri = :uri")
    suspend fun deleteOcrForUri(uri: String)

    @Query("UPDATE OR REPLACE ocr_texts SET screenshotUri = :newUri WHERE screenshotUri = :oldUri")
    suspend fun updateScreenshotUri(oldUri: String, newUri: String): Int

    @androidx.room.Transaction
    suspend fun migrateScreenshotUri(oldUri: String, newUri: String) {
        if (oldUri == newUri) return
        val existing = getOcrForUri(oldUri) ?: return
        deleteOcrForUri(newUri)
        deleteOcrForUri(oldUri)
        insertOrUpdate(existing.copy(screenshotUri = newUri, updatedAt = System.currentTimeMillis()))
    }

    @Query("DELETE FROM ocr_texts WHERE screenshotUri NOT IN (SELECT uri FROM screenshots)")
    suspend fun deleteOrphanedOcr(): Int

    @Query("SELECT * FROM ocr_texts WHERE processingStatus = 'FAILED' OR processingStatus = 'NOT_STARTED'")
    suspend fun getPendingOrFailedOcr(): List<OcrTextEntity>
}

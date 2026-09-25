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

    @Query("SELECT * FROM ocr_texts WHERE processingStatus = 'FAILED' OR processingStatus = 'NOT_STARTED'")
    suspend fun getPendingOrFailedOcr(): List<OcrTextEntity>
}

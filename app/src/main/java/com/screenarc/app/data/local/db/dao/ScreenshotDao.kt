package com.screenarc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.screenarc.app.data.local.db.entity.ScreenshotEntity
import com.screenarc.app.data.local.db.relation.ScreenshotWithOcrAndCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {

    @Transaction
    @Query("SELECT * FROM screenshots ORDER BY dateAdded DESC")
    fun getAllScreenshotsFlow(): Flow<List<ScreenshotWithOcrAndCategory>>

    @Transaction
    @Query("SELECT * FROM screenshots ORDER BY dateAdded DESC")
    suspend fun getAllScreenshots(): List<ScreenshotWithOcrAndCategory>

    @Query("SELECT * FROM screenshots WHERE uri = :uri LIMIT 1")
    suspend fun getScreenshotEntityByUri(uri: String): ScreenshotEntity?

    @Transaction
    @Query("SELECT * FROM screenshots WHERE uri = :uri LIMIT 1")
    suspend fun getScreenshotWithOcrByUri(uri: String): ScreenshotWithOcrAndCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(screenshot: ScreenshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(screenshots: List<ScreenshotEntity>)

    @Update
    suspend fun updateScreenshot(screenshot: ScreenshotEntity)

    @Query("UPDATE screenshots SET categoryId = :categoryId, relativePath = :relativePath, updatedAt = :updatedAt WHERE uri = :uri")
    suspend fun updateCategory(uri: String, categoryId: Long?, relativePath: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE screenshots SET ocrStatus = :ocrStatus, updatedAt = :updatedAt WHERE uri = :uri")
    suspend fun updateOcrStatus(uri: String, ocrStatus: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM screenshots WHERE uri = :uri")
    suspend fun deleteScreenshotByUri(uri: String)

    @Delete
    suspend fun deleteScreenshot(screenshot: ScreenshotEntity)

    @Query("DELETE FROM screenshots WHERE uri NOT IN (:activeUris)")
    suspend fun deleteNonExistentUris(activeUris: List<String>)

    @Transaction
    @Query("""
        SELECT s.* FROM screenshots s
        LEFT JOIN ocr_texts o ON s.uri = o.screenshotUri
        LEFT JOIN categories c ON s.categoryId = c.id
        WHERE (
            (:textQuery = '' AND :normalizedQuery = '')
            OR s.displayName LIKE '%' || :textQuery || '%'
            OR (:includeOcr = 1 AND o.normalizedText LIKE '%' || :normalizedQuery || '%')
            OR c.name LIKE '%' || :textQuery || '%'
        )
        AND (:categoryId IS NULL OR s.categoryId = :categoryId)
        AND (:dateFrom IS NULL OR s.dateAdded >= :dateFrom)
        AND (:dateTo IS NULL OR s.dateAdded <= :dateTo)
        AND (:ocrStatus IS NULL OR s.ocrStatus = :ocrStatus)
        ORDER BY s.dateAdded DESC
    """)
    fun searchScreenshotsFlow(
        textQuery: String,
        normalizedQuery: String,
        includeOcr: Boolean,
        categoryId: Long?,
        dateFrom: Long?,
        dateTo: Long?,
        ocrStatus: String?
    ): Flow<List<ScreenshotWithOcrAndCategory>>

    @Query("SELECT * FROM screenshots WHERE ocrStatus = 'NOT_STARTED' OR ocrStatus = 'FAILED' ORDER BY dateAdded DESC")
    suspend fun getUnprocessedScreenshots(): List<ScreenshotEntity>
}

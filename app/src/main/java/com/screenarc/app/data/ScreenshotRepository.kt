package com.screenarc.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.screenarc.app.data.local.db.AppDatabase
import com.screenarc.app.data.local.db.entity.ScreenshotEntity
import com.screenarc.app.data.local.db.relation.ScreenshotWithOcrAndCategory
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.OrganizedRecord
import com.screenarc.app.model.OverlayEvent
import com.screenarc.app.model.ScreenshotItem
import com.screenarc.app.model.ScreenshotSearchQuery
import com.screenarc.app.model.ScreenshotUiModel
import com.screenarc.app.service.OverlayEventBus
import com.screenarc.app.storage.MediaStoreOrganizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenshotRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val historyRepository: OrganizedHistoryRepository = OrganizedHistoryRepository(context)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val screenshotDao = database.screenshotDao()

    init {
        scope.launch {
            syncMediaStoreScreenshots()
        }
    }

    /**
     * Search screenshots reactively using Room flow
     */
    fun searchScreenshots(query: ScreenshotSearchQuery): Flow<List<ScreenshotWithOcrAndCategory>> {
        val cleanText = query.text.trim()
        val normalized = com.screenarc.app.data.local.db.entity.OcrTextEntity.normalizeText(cleanText)
        return screenshotDao.searchScreenshotsFlow(
            textQuery = cleanText,
            normalizedQuery = normalized,
            includeOcr = query.includeOcrText,
            categoryId = query.categoryId,
            dateFrom = query.dateFrom,
            dateTo = query.dateTo,
            ocrStatus = query.ocrStatus
        ).flowOn(Dispatchers.IO)
    }

    fun getAllScreenshotsFlow(): Flow<List<ScreenshotWithOcrAndCategory>> {
        return screenshotDao.getAllScreenshotsFlow().flowOn(Dispatchers.IO)
    }

    suspend fun syncMediaStoreScreenshots() = withContext(Dispatchers.IO) {
        try {
            val mediaStoreItems = MediaStoreOrganizer.getRecentScreenshots(context, limit = 100)
            val entities = mediaStoreItems.map { item ->
                ScreenshotEntity(
                    id = item.id,
                    uri = item.uri.toString(),
                    displayName = item.displayName,
                    dateAdded = item.dateAdded,
                    size = item.size,
                    relativePath = item.relativePath,
                    mimeType = item.mimeType
                )
            }
            if (entities.isNotEmpty()) {
                screenshotDao.insertAll(entities)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing MediaStore screenshots: ${e.message}", e)
        }
    }

    /**
     * Discover and synchronize all available screenshots from device storage into Room database.
     */
    suspend fun syncAllScreenshots() = withContext(Dispatchers.IO) {
        try {
            val mediaStoreItems = MediaStoreOrganizer.getAllScreenshots(context)
            val entities = mediaStoreItems.map { item ->
                ScreenshotEntity(
                    id = item.id,
                    uri = item.uri.toString(),
                    displayName = item.displayName,
                    dateAdded = item.dateAdded,
                    size = item.size,
                    relativePath = item.relativePath,
                    mimeType = item.mimeType
                )
            }
            if (entities.isNotEmpty()) {
                screenshotDao.insertAll(entities)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing all MediaStore screenshots: ${e.message}", e)
        }
    }

    suspend fun registerScreenshot(item: ScreenshotItem, categoryId: Long? = null): ScreenshotEntity = withContext(Dispatchers.IO) {
        val entity = ScreenshotEntity(
            id = item.id,
            uri = item.uri.toString(),
            displayName = item.displayName,
            dateAdded = item.dateAdded,
            size = item.size,
            relativePath = item.relativePath,
            mimeType = item.mimeType,
            categoryId = categoryId
        )
        screenshotDao.insertOrUpdate(entity)
        entity
    }

    suspend fun moveToCategory(
        screenshot: ScreenshotUiModel,
        category: CategoryUiModel,
        ruleName: String? = null,
        triggerType: String = OrganizedRecord.TRIGGER_MANUAL
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val legacyCategory = com.screenarc.app.model.Category(
            id = category.id.toString(),
            name = category.name,
            subfolderName = category.subfolderName,
            iconName = category.iconName,
            colorHex = category.colorHex,
            isDefault = category.isDefault,
            orderIndex = category.orderIndex
        )
        val legacyItem = ScreenshotItem(
            id = screenshot.id,
            uri = screenshot.contentUri,
            displayName = screenshot.displayName,
            dateAdded = screenshot.dateAdded,
            size = screenshot.size,
            relativePath = screenshot.relativePath,
            mimeType = screenshot.mimeType
        )

        val result = MediaStoreOrganizer.moveToCategory(context, legacyItem, legacyCategory)
        result.onSuccess { newUri ->
            // Update database record
            val relativeSubfolder = "Pictures/Screenshots/${category.subfolderName}/"
            screenshotDao.deleteScreenshotByUri(screenshot.uri)
            val updatedEntity = ScreenshotEntity(
                id = screenshot.id,
                uri = newUri.toString(),
                displayName = screenshot.displayName,
                dateAdded = screenshot.dateAdded,
                size = screenshot.size,
                relativePath = relativeSubfolder,
                mimeType = screenshot.mimeType,
                categoryId = category.id
            )
            screenshotDao.insertOrUpdate(updatedEntity)

            // Add to history
            historyRepository.addRecord(
                OrganizedRecord(
                    id = "rec_${System.currentTimeMillis()}_${screenshot.id}",
                    screenshotId = screenshot.id,
                    uri = newUri,
                    fileName = screenshot.displayName,
                    categoryName = category.name,
                    targetFolder = category.subfolderName,
                    timestamp = System.currentTimeMillis(),
                    success = true,
                    ruleName = ruleName,
                    triggerType = triggerType
                )
            )

            OverlayEventBus.postEvent(OverlayEvent.RefreshRequested)
        }
        result
    }

    suspend fun moveBatchToCategory(
        screenshots: List<ScreenshotUiModel>,
        category: CategoryUiModel,
        ruleName: String? = null,
        triggerType: String = OrganizedRecord.TRIGGER_BULK_MANUAL
    ): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        val historyRecords = mutableListOf<OrganizedRecord>()

        for (screenshot in screenshots) {
            val res = moveToCategory(screenshot, category, ruleName = ruleName, triggerType = triggerType)
            if (res.isSuccess) {
                successCount++
            }
        }
        successCount
    }

    suspend fun copyToCategory(
        screenshot: ScreenshotUiModel,
        category: CategoryUiModel,
        ruleName: String? = null,
        triggerType: String = OrganizedRecord.TRIGGER_MANUAL
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val relativeSubfolder = "Pictures/Screenshots/${category.subfolderName}/"
        val legacyItem = ScreenshotItem(
            id = screenshot.id,
            uri = screenshot.contentUri,
            displayName = screenshot.displayName,
            dateAdded = screenshot.dateAdded,
            size = screenshot.size,
            relativePath = screenshot.relativePath,
            mimeType = screenshot.mimeType
        )

        val newUri = MediaStoreOrganizer.copyToNewFolderPublic(context, legacyItem, relativeSubfolder)
        if (newUri != null) {
            val copyEntity = ScreenshotEntity(
                id = System.currentTimeMillis(),
                uri = newUri.toString(),
                displayName = screenshot.displayName,
                dateAdded = System.currentTimeMillis() / 1000,
                size = screenshot.size,
                relativePath = relativeSubfolder,
                mimeType = screenshot.mimeType,
                categoryId = category.id
            )
            screenshotDao.insertOrUpdate(copyEntity)

            historyRepository.addRecord(
                OrganizedRecord(
                    id = "rec_${System.currentTimeMillis()}_${screenshot.id}",
                    screenshotId = screenshot.id,
                    uri = newUri,
                    fileName = "[Copy] ${screenshot.displayName}",
                    categoryName = category.name,
                    targetFolder = category.subfolderName,
                    timestamp = System.currentTimeMillis(),
                    success = true,
                    ruleName = ruleName,
                    triggerType = triggerType
                )
            )
            OverlayEventBus.postEvent(OverlayEvent.RefreshRequested)
            Result.success(newUri)
        } else {
            Result.failure(Exception("Failed to copy screenshot to ${category.name}"))
        }
    }

    suspend fun copyBatchToCategory(
        screenshots: List<ScreenshotUiModel>,
        category: CategoryUiModel,
        ruleName: String? = null,
        triggerType: String = OrganizedRecord.TRIGGER_BULK_MANUAL
    ): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        for (screenshot in screenshots) {
            val res = copyToCategory(screenshot, category, ruleName = ruleName, triggerType = triggerType)
            if (res.isSuccess) {
                successCount++
            }
        }
        successCount
    }

    suspend fun deleteScreenshot(screenshot: ScreenshotUiModel): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(screenshot.contentUri, null, null)
            screenshotDao.deleteScreenshotByUri(screenshot.uri)
            database.ocrDao().deleteOcrForUri(screenshot.uri)
            OverlayEventBus.postEvent(OverlayEvent.RefreshRequested)
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting screenshot: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteBatch(screenshots: List<ScreenshotUiModel>): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        for (screenshot in screenshots) {
            val res = deleteScreenshot(screenshot)
            if (res.isSuccess) deletedCount++
        }
        deletedCount
    }

    companion object {
        private const val TAG = "ScreenshotRepository"
    }
}

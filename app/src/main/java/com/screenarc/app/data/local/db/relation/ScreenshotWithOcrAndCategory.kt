package com.screenarc.app.data.local.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.screenarc.app.data.local.db.entity.CategoryEntity
import com.screenarc.app.data.local.db.entity.OcrTextEntity
import com.screenarc.app.data.local.db.entity.ScreenshotEntity
import com.screenarc.app.model.ScreenshotUiModel

data class ScreenshotWithOcrAndCategory(
    @Embedded
    val screenshot: ScreenshotEntity,

    @Relation(
        parentColumn = "uri",
        entityColumn = "screenshotUri"
    )
    val ocrText: OcrTextEntity?,

    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoryEntity?
) {
    fun toUiModel(): ScreenshotUiModel {
        return ScreenshotUiModel(
            id = screenshot.id,
            uri = screenshot.uri,
            displayName = screenshot.displayName,
            dateAdded = screenshot.dateAdded,
            size = screenshot.size,
            relativePath = screenshot.relativePath,
            mimeType = screenshot.mimeType,
            categoryId = screenshot.categoryId,
            categoryName = category?.name,
            ocrStatus = ocrText?.processingStatus ?: screenshot.ocrStatus,
            recognizedText = ocrText?.recognizedText,
            normalizedText = ocrText?.normalizedText,
            blocks = com.screenarc.app.data.OcrRepositoryImpl.parseBlocksFromJson(ocrText?.blocksJson)
        )
    }
}

package com.screenarc.app.rules

import android.content.Context
import android.net.Uri
import com.screenarc.app.model.CategoryRule
import com.screenarc.app.model.CategoryResult
import com.screenarc.app.model.ScreenOcrData
import com.screenarc.app.model.ScreenshotItem
import com.screenarc.app.storage.MediaStoreOrganizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RoutedScreenshot(
    val uri: Uri,
    val categoryResult: CategoryResult
)

class ScreenshotRoutingService(
    private val context: Context,
    private val ruleEvaluationEngine: RuleEvaluationEngine = RuleEvaluationEngine()
) {
    suspend fun route(
        screenshot: ScreenshotItem,
        ocrResult: ScreenOcrData,
        rules: List<CategoryRule>
    ): Result<RoutedScreenshot> = withContext(Dispatchers.IO) {
        val categoryResult = ruleEvaluationEngine.evaluateWithEnrichment(ocrResult, rules)
        MediaStoreOrganizer.moveToRelativeFolder(
            context = context,
            screenshot = screenshot,
            targetRelativePath = categoryResult.destinationRelativeFolder
        ).map { movedUri ->
            RoutedScreenshot(
                uri = movedUri,
                categoryResult = categoryResult
            )
        }
    }
}

package com.screenarc.app.model

import android.net.Uri

sealed class OverlayEvent {
    data class CategoriesChanged(val categories: List<CategoryUiModel>) : OverlayEvent()
    data class ScreenshotsChanged(val pendingScreenshots: List<ScreenshotUiModel>) : OverlayEvent()
    data object RefreshRequested : OverlayEvent()
    data object OverlayClosed : OverlayEvent()

    /** A screenshot (e.g. a freshly saved redacted copy) was handled by the app
     *  and must be ignored by the MediaStore screenshot observer. */
    data class ScreenshotHandled(val uri: Uri) : OverlayEvent()
}

sealed class OverlayCommand {
    data class ShowScreenshot(val screenshot: ScreenshotUiModel) : OverlayCommand()
    data object DismissOverlay : OverlayCommand()
    data object RefreshCategories : OverlayCommand()
    data class HighlightCategory(val categoryId: Long?) : OverlayCommand()
}

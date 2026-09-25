package com.screenarc.app.model

sealed class OverlayEvent {
    data class CategoriesChanged(val categories: List<CategoryUiModel>) : OverlayEvent()
    data class ScreenshotsChanged(val pendingScreenshots: List<ScreenshotUiModel>) : OverlayEvent()
    data object RefreshRequested : OverlayEvent()
    data object OverlayClosed : OverlayEvent()
}

sealed class OverlayCommand {
    data class ShowScreenshot(val screenshot: ScreenshotUiModel) : OverlayCommand()
    data object DismissOverlay : OverlayCommand()
    data object RefreshCategories : OverlayCommand()
    data class HighlightCategory(val categoryId: Long?) : OverlayCommand()
}

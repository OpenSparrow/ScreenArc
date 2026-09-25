package com.screenarc.app.model

data class OverlayUiState(
    val categories: List<CategoryUiModel> = emptyList(),
    val pendingScreenshots: List<ScreenshotUiModel> = emptyList(),
    val isExpanded: Boolean = true,
    val isDragging: Boolean = false,
    val activeDragItem: ScreenshotUiModel? = null,
    val highlightedCategoryId: Long? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

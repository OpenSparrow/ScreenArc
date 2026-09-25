package com.screenarc.app.model

data class ScreenshotSearchQuery(
    val text: String = "",
    val categoryId: Long? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val includeOcrText: Boolean = true,
    val includeFilename: Boolean = true,
    val ocrStatus: String? = null
)

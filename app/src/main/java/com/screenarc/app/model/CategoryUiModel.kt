package com.screenarc.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryUiModel(
    val id: Long,
    val name: String,
    val subfolderName: String,
    val iconName: String = "Description",
    val colorHex: Long = 0xFF6750A4,
    val isDefault: Boolean = false,
    val orderIndex: Int = 0
) {
    val composeColor: Color
        get() = Color(colorHex)

    fun getIcon(): ImageVector {
        return when (iconName) {
            "Code", "Tech" -> Icons.Default.Code
            "Docs", "Description" -> Icons.Default.Description
            "Quotes", "FormatQuote" -> Icons.Default.FormatQuote
            "Receipts", "Receipt" -> Icons.Default.Receipt
            "Design", "Palette" -> Icons.Default.Palette
            "Memes", "Mood" -> Icons.Default.Mood
            "Work" -> Icons.Default.Work
            else -> Icons.Default.Bookmark
        }
    }
}

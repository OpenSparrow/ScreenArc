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

data class Category(
    val id: String,
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

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            Category(
                id = "cat_tech",
                name = "Tech",
                subfolderName = "Tech",
                iconName = "Tech",
                colorHex = 0xFF1976D2, // Material Blue
                isDefault = true,
                orderIndex = 0
            ),
            Category(
                id = "cat_docs",
                name = "Docs",
                subfolderName = "Docs",
                iconName = "Docs",
                colorHex = 0xFF388E3C, // Material Green
                isDefault = true,
                orderIndex = 1
            ),
            Category(
                id = "cat_quotes",
                name = "Quotes",
                subfolderName = "Quotes",
                iconName = "Quotes",
                colorHex = 0xFF7B1FA2, // Material Purple
                isDefault = true,
                orderIndex = 2
            ),
            Category(
                id = "cat_receipts",
                name = "Receipts",
                subfolderName = "Receipts",
                iconName = "Receipts",
                colorHex = 0xFFE65100, // Material Deep Orange
                isDefault = true,
                orderIndex = 3
            ),
            Category(
                id = "cat_design",
                name = "Design",
                subfolderName = "Design",
                iconName = "Design",
                colorHex = 0xFFC2185B, // Material Pink
                isDefault = true,
                orderIndex = 4
            ),
            Category(
                id = "cat_memes",
                name = "Memes",
                subfolderName = "Memes",
                iconName = "Memes",
                colorHex = 0xFF0097A7, // Material Cyan
                isDefault = true,
                orderIndex = 5
            )
        )
    }
}

package com.screenarc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.screenarc.app.model.CategoryUiModel

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val subfolderName: String,
    val iconName: String = "Description",
    val colorHex: Long = 0xFF6750A4,
    val isDefault: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toUiModel(): CategoryUiModel {
        return CategoryUiModel(
            id = id,
            name = name,
            subfolderName = subfolderName,
            iconName = iconName,
            colorHex = colorHex,
            isDefault = isDefault,
            orderIndex = orderIndex
        )
    }

    companion object {
        val DEFAULT_ENTITIES = listOf(
            CategoryEntity(
                id = 1,
                name = "Tech",
                subfolderName = "Tech",
                iconName = "Tech",
                colorHex = 0xFF1976D2,
                isDefault = true,
                orderIndex = 0
            ),
            CategoryEntity(
                id = 2,
                name = "Docs",
                subfolderName = "Docs",
                iconName = "Docs",
                colorHex = 0xFF388E3C,
                isDefault = true,
                orderIndex = 1
            ),
            CategoryEntity(
                id = 3,
                name = "Quotes",
                subfolderName = "Quotes",
                iconName = "Quotes",
                colorHex = 0xFF7B1FA2,
                isDefault = true,
                orderIndex = 2
            ),
            CategoryEntity(
                id = 4,
                name = "Receipts",
                subfolderName = "Receipts",
                iconName = "Receipts",
                colorHex = 0xFFE65100,
                isDefault = true,
                orderIndex = 3
            ),
            CategoryEntity(
                id = 5,
                name = "Design",
                subfolderName = "Design",
                iconName = "Design",
                colorHex = 0xFFC2185B,
                isDefault = true,
                orderIndex = 4
            ),
            CategoryEntity(
                id = 6,
                name = "Memes",
                subfolderName = "Memes",
                iconName = "Memes",
                colorHex = 0xFF0097A7,
                isDefault = true,
                orderIndex = 5
            )
        )
    }
}

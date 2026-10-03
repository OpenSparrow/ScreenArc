package com.screenarc.app.data

import android.content.Context
import android.os.Environment
import android.util.Log
import com.screenarc.app.data.local.db.AppDatabase
import com.screenarc.app.data.local.db.entity.CategoryEntity
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.OverlayEvent
import com.screenarc.app.service.OverlayEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CategoryRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = AppDatabase.getInstance(context)
    private val categoryDao = database.categoryDao()

    val categoriesFlow: Flow<List<CategoryUiModel>> = categoryDao.getAllCategoriesFlow()
        .map { entities ->
            if (entities.isEmpty()) {
                // Ensure default categories exist
                categoryDao.insertCategories(CategoryEntity.DEFAULT_ENTITIES)
                CategoryEntity.DEFAULT_ENTITIES.map { it.toUiModel() }
            } else {
                entities.map { it.toUiModel() }
            }
        }

    val categories: StateFlow<List<CategoryUiModel>> = categoriesFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = CategoryEntity.DEFAULT_ENTITIES.map { it.toUiModel() }
        )

    init {
        // Initial setup check
        scope.launch {
            val count = categoryDao.getCategoryCount()
            if (count == 0) {
                categoryDao.insertCategories(CategoryEntity.DEFAULT_ENTITIES)
            }
            refreshOverlayCategories()
        }
    }

    suspend fun refreshOverlayCategories() = withContext(Dispatchers.IO) {
        val currentCategories = categoryDao.getAllCategories().map { it.toUiModel() }
        OverlayEventBus.postEvent(OverlayEvent.CategoriesChanged(currentCategories))
    }

    /**
     * Sanitizes a folder name to prevent path traversal and invalid characters.
     */
    private fun sanitizeFolderName(name: String): String {
        // Remove path separators and common invalid filename characters
        val sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "")
        // Prevent pure relative paths like ".", ".."
        return if (sanitized.all { it == '.' }) {
            "CustomCategory"
        } else {
            sanitized
        }
    }

    suspend fun addCategory(
        name: String,
        subfolder: String,
        iconName: String,
        colorHex: Long
    ): Long = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val rawSubfolder = if (subfolder.isBlank()) cleanName else subfolder.trim()
        val cleanSubfolder = sanitizeFolderName(rawSubfolder)

        val currentCount = categoryDao.getCategoryCount()

        val newEntity = CategoryEntity(
            name = cleanName,
            subfolderName = cleanSubfolder,
            iconName = iconName,
            colorHex = colorHex,
            isDefault = false,
            orderIndex = currentCount
        )

        val id = categoryDao.insertCategory(newEntity)

        // Ensure target folder exists on disk if possible
        createCategoryDirectory(cleanSubfolder)

        refreshOverlayCategories()
        id
    }

    suspend fun updateCategory(category: CategoryUiModel) = withContext(Dispatchers.IO) {
        val cleanSubfolder = sanitizeFolderName(category.subfolderName.trim())
        val entity = CategoryEntity(
            id = category.id,
            name = category.name.trim(),
            subfolderName = cleanSubfolder,
            iconName = category.iconName,
            colorHex = category.colorHex,
            isDefault = category.isDefault,
            orderIndex = category.orderIndex
        )
        categoryDao.updateCategory(entity)
        createCategoryDirectory(cleanSubfolder)
        refreshOverlayCategories()
    }

    suspend fun deleteCategory(id: Long) = withContext(Dispatchers.IO) {
        categoryDao.deleteCategoryById(id)
        refreshOverlayCategories()
    }

    suspend fun importCategories(importedCategories: List<CategoryUiModel>) = withContext(Dispatchers.IO) {
        val existing = categoryDao.getAllCategories()
        for (imported in importedCategories) {
            val match = existing.find { it.name.equals(imported.name, ignoreCase = true) }
            if (match == null) {
                val cleanSubfolder = sanitizeFolderName(imported.subfolderName.trim())
                val entity = CategoryEntity(
                    name = imported.name.trim(),
                    subfolderName = cleanSubfolder,
                    iconName = imported.iconName,
                    colorHex = imported.colorHex,
                    isDefault = false,
                    orderIndex = existing.size
                )
                categoryDao.insertCategory(entity)
                createCategoryDirectory(cleanSubfolder)
            }
        }
        refreshOverlayCategories()
    }

    suspend fun resetToDefaults() = withContext(Dispatchers.IO) {
        categoryDao.deleteAllCategories()
        categoryDao.insertCategories(CategoryEntity.DEFAULT_ENTITIES)
        refreshOverlayCategories()
    }

    private fun createCategoryDirectory(subfolderName: String) {
        try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val screenshotsDir = File(picturesDir, "Screenshots")
            val targetDir = File(screenshotsDir, subfolderName)

            // Defense in depth: Ensure canonical path stays within the intended directory
            if (targetDir.canonicalPath.startsWith(screenshotsDir.canonicalPath + File.separator) || targetDir.canonicalPath == screenshotsDir.canonicalPath) {
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
            } else {
                Log.w("CategoryRepository", "Path traversal detected and blocked: ${targetDir.canonicalPath}")
            }
        } catch (e: Exception) {
            Log.w("CategoryRepository", "Could not directly create directory on storage: ${e.message}")
        }
    }
}

package com.screenarc.app.data

import android.content.Context
import android.content.SharedPreferences
import com.screenarc.app.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class CategoryRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("screenarc_categories", Context.MODE_PRIVATE)

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        val jsonString = prefs.getString(KEY_CATEGORIES, null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<Category>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        Category(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            subfolderName = obj.getString("subfolderName"),
                            iconName = obj.optString("iconName", "Description"),
                            colorHex = obj.optLong("colorHex", 0xFF6750A4),
                            isDefault = obj.optBoolean("isDefault", false),
                            orderIndex = obj.optInt("orderIndex", i)
                        )
                    )
                }
                _categories.value = list.sortedBy { it.orderIndex }
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Initial default setup
        _categories.value = Category.DEFAULT_CATEGORIES
        saveCategories(_categories.value)
    }

    private fun saveCategories(list: List<Category>) {
        val jsonArray = JSONArray()
        list.forEach { cat ->
            val obj = JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("subfolderName", cat.subfolderName)
                put("iconName", cat.iconName)
                put("colorHex", cat.colorHex)
                put("isDefault", cat.isDefault)
                put("orderIndex", cat.orderIndex)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CATEGORIES, jsonArray.toString()).apply()
        _categories.value = list.sortedBy { it.orderIndex }
    }

    fun addCategory(name: String, subfolder: String, iconName: String, colorHex: Long) {
        val current = _categories.value.toMutableList()
        val id = "cat_${System.currentTimeMillis()}"
        val cleanSubfolder = if (subfolder.isBlank()) name.trim() else subfolder.trim()
        val newCategory = Category(
            id = id,
            name = name.trim(),
            subfolderName = cleanSubfolder,
            iconName = iconName,
            colorHex = colorHex,
            isDefault = false,
            orderIndex = current.size
        )
        current.add(newCategory)
        saveCategories(current)
    }

    fun updateCategory(category: Category) {
        val current = _categories.value.map {
            if (it.id == category.id) category else it
        }
        saveCategories(current)
    }

    fun deleteCategory(id: String) {
        val current = _categories.value.filterNot { it.id == id }
        saveCategories(current)
    }

    fun resetToDefaults() {
        saveCategories(Category.DEFAULT_CATEGORIES)
    }

    companion object {
        private const val KEY_CATEGORIES = "saved_categories_json"
    }
}

package com.screenarc.app.ui.theme

import com.screenarc.app.data.AppSettingsRepository
import com.screenarc.app.model.AppThemePreset
import com.screenarc.app.model.ThemeCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * State representation for the Theme Selection System.
 * Cleanly separates the selected category tab from the active theme preset.
 */
data class ThemeUiState(
    val selectedCategory: ThemeCategory = ThemeCategory.ALL,
    val activeTheme: AppThemePreset = AppThemePreset.DYNAMIC_SYSTEM,
    val filteredPresets: List<AppThemePreset> = emptyList(),
    val isCategoryActive: Boolean = true
) {
    /**
     * Determines whether a given category tab should be highlighted in the UI.
     * The tab is highlighted ONLY if:
     * 1. It is the selected category
     * 2. It contains at least one theme
     * 3. The currently active theme actually matches the category (or category is ALL)
     */
    fun isTabHighlighted(category: ThemeCategory): Boolean {
        if (selectedCategory != category) return false
        val presetsForCat = category.getPresets()
        if (presetsForCat.isEmpty()) return false
        return category == ThemeCategory.ALL || activeTheme.matchesCategory(category)
    }
}

/**
 * State manager coordinating theme categories and applied theme presets.
 * Exposes reactive StateFlows, persists category & theme selections across restarts,
 * and ensures UI tabs only highlight when valid themes exist and are applied.
 */
class ThemeStateManager(
    private val settingsRepo: AppSettingsRepository,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    private val _selectedCategory = MutableStateFlow(settingsRepo.getSelectedThemeCategory())
    val selectedCategory: StateFlow<ThemeCategory> = _selectedCategory

    val themeState: StateFlow<ThemeUiState> = combine(
        _selectedCategory,
        settingsRepo.settings
    ) { category, settings ->
        val activeTheme = settings.themePreset
        val categoryThemes = category.getPresets()
        val themesExist = categoryThemes.isNotEmpty()

        // Tab is highlighted only when themes exist and active theme matches
        val isCategoryActive = themesExist && (category == ThemeCategory.ALL || activeTheme.matchesCategory(category))

        ThemeUiState(
            selectedCategory = category,
            activeTheme = activeTheme,
            filteredPresets = if (themesExist) categoryThemes else AppThemePreset.values().toList(),
            isCategoryActive = isCategoryActive
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeUiState(
            selectedCategory = _selectedCategory.value,
            activeTheme = settingsRepo.settings.value.themePreset,
            filteredPresets = _selectedCategory.value.getPresets(),
            isCategoryActive = true
        )
    )

    /**
     * Called when a user taps a category tab (All, Light, Dark, Glassmorphism).
     *
     * Rule:
     * - Check if themes exist in that category.
     * - If themes exist:
     *     - Update selectedCategory and persist preference.
     *     - If current active theme is not in that category, auto-apply the first theme.
     *     - Returns true (tab can be selected/highlighted).
     * - If NO themes exist:
     *     - Keep current theme applied.
     *     - Do NOT highlight the tab as selected.
     *     - Returns false.
     */
    fun selectCategory(category: ThemeCategory): Boolean {
        val themes = category.getPresets()
        if (themes.isEmpty()) {
            // Keep current theme, do not highlight tab
            return false
        }

        _selectedCategory.value = category
        settingsRepo.setSelectedThemeCategory(category)

        val currentTheme = settingsRepo.settings.value.themePreset
        if (!themes.contains(currentTheme)) {
            // Auto-apply the first theme from the selected category
            val firstTheme = themes.first()
            settingsRepo.updateSettings { it.copy(themePreset = firstTheme) }
        }
        return true
    }

    /**
     * Called when a user selects a specific theme preset from the list.
     * Applies the theme and synchronizes selectedCategory if necessary.
     */
    fun selectTheme(preset: AppThemePreset) {
        settingsRepo.updateSettings { it.copy(themePreset = preset) }

        // If the current tab does not match the chosen preset, synchronize category
        val currentCategory = _selectedCategory.value
        if (currentCategory != ThemeCategory.ALL && !preset.matchesCategory(currentCategory)) {
            val matchingCategory = preset.primaryCategory()
            _selectedCategory.value = matchingCategory
            settingsRepo.setSelectedThemeCategory(matchingCategory)
        }
    }
}

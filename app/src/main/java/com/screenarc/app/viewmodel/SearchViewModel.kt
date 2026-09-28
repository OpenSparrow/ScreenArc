package com.screenarc.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenarc.app.data.CategoryRepository
import com.screenarc.app.data.OcrRepository
import com.screenarc.app.data.OcrRepositoryImpl
import com.screenarc.app.data.RuleExecutionResult
import com.screenarc.app.data.RulesRepository
import com.screenarc.app.data.ScreenshotRepository
import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.OcrResult
import com.screenarc.app.model.ScreenshotSearchQuery
import com.screenarc.app.model.ScreenshotUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: ScreenshotSearchQuery = ScreenshotSearchQuery(),
    val isSearching: Boolean = false,
    val isOcrRunning: Boolean = false,
    val activeOcrUri: String? = null,
    val activeOcrResult: OcrResult? = null,
    val selectedScreenshot: ScreenshotUiModel? = null,
    val isSelectionMode: Boolean = false,
    val selectedUris: Set<String> = emptySet(),
    val isRuleRunning: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel @JvmOverloads constructor(
    application: Application,
    private val screenshotRepository: ScreenshotRepository = ScreenshotRepository(application),
    private val categoryRepository: CategoryRepository = CategoryRepository(application),
    private val ocrRepository: OcrRepository = OcrRepositoryImpl(application),
    private val rulesRepository: RulesRepository = RulesRepository(application)
) : AndroidViewModel(application) {

    private val _queryFlow = MutableStateFlow(ScreenshotSearchQuery())
    val queryFlow: StateFlow<ScreenshotSearchQuery> = _queryFlow.asStateFlow()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<CategoryUiModel>> = categoryRepository.categories
    val rules: StateFlow<List<AutoCategorizationRule>> = rulesRepository.rules
    val batchProgress = ocrRepository.batchProgress

    // Search results reactive flow with debouncing
    val searchResults: StateFlow<List<ScreenshotUiModel>> = _queryFlow
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            screenshotRepository.searchScreenshots(query).map { list ->
                list.map { it.toUiModel() }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSearchText(text: String) {
        _queryFlow.value = _queryFlow.value.copy(text = text)
        _uiState.value = _uiState.value.copy(query = _queryFlow.value)
    }

    fun setCategoryFilter(categoryId: Long?) {
        _queryFlow.value = _queryFlow.value.copy(categoryId = categoryId)
        _uiState.value = _uiState.value.copy(query = _queryFlow.value)
    }

    fun setOcrStatusFilter(status: String?) {
        _queryFlow.value = _queryFlow.value.copy(ocrStatus = status)
        _uiState.value = _uiState.value.copy(query = _queryFlow.value)
    }

    fun toggleIncludeOcr(include: Boolean) {
        _queryFlow.value = _queryFlow.value.copy(includeOcrText = include)
        _uiState.value = _uiState.value.copy(query = _queryFlow.value)
    }

    fun clearSearch() {
        _queryFlow.value = ScreenshotSearchQuery()
        _uiState.value = _uiState.value.copy(query = _queryFlow.value)
    }

    // Multi-Selection Management
    fun setSelectionMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = enabled,
            selectedUris = if (enabled) _uiState.value.selectedUris else emptySet()
        )
    }

    fun toggleSelection(uri: String) {
        val currentSelected = _uiState.value.selectedUris.toMutableSet()
        if (currentSelected.contains(uri)) {
            currentSelected.remove(uri)
        } else {
            currentSelected.add(uri)
        }
        _uiState.value = _uiState.value.copy(
            selectedUris = currentSelected,
            isSelectionMode = currentSelected.isNotEmpty() || _uiState.value.isSelectionMode
        )
    }

    fun selectAll() {
        val allUris = searchResults.value.map { it.uri }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedUris = allUris,
            isSelectionMode = true
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedUris = emptySet(),
            isSelectionMode = false
        )
    }

    // Bulk Actions
    fun moveSelectedToCategory(category: CategoryUiModel) {
        viewModelScope.launch {
            val selected = searchResults.value.filter { _uiState.value.selectedUris.contains(it.uri) }
            if (selected.isEmpty()) return@launch

            _uiState.value = _uiState.value.copy(isOcrRunning = true)
            val movedCount = screenshotRepository.moveBatchToCategory(
                screenshots = selected,
                category = category,
                ruleName = null,
                triggerType = com.screenarc.app.model.OrganizedRecord.TRIGGER_BULK_MANUAL
            )

            clearSelection()
            _uiState.value = _uiState.value.copy(
                isOcrRunning = false,
                infoMessage = "Bulk moved $movedCount screenshot(s) to ${category.name}"
            )
        }
    }

    fun copySelectedToCategory(category: CategoryUiModel) {
        viewModelScope.launch {
            val selected = searchResults.value.filter { _uiState.value.selectedUris.contains(it.uri) }
            if (selected.isEmpty()) return@launch

            _uiState.value = _uiState.value.copy(isOcrRunning = true)
            val copiedCount = screenshotRepository.copyBatchToCategory(
                screenshots = selected,
                category = category,
                ruleName = null,
                triggerType = com.screenarc.app.model.OrganizedRecord.TRIGGER_BULK_MANUAL
            )

            clearSelection()
            _uiState.value = _uiState.value.copy(
                isOcrRunning = false,
                infoMessage = "Bulk copied $copiedCount screenshot(s) to ${category.name}"
            )
        }
    }

    fun deleteSelectedScreenshots() {
        viewModelScope.launch {
            val selected = searchResults.value.filter { _uiState.value.selectedUris.contains(it.uri) }
            if (selected.isEmpty()) return@launch

            val deletedCount = screenshotRepository.deleteBatch(selected)
            clearSelection()
            _uiState.value = _uiState.value.copy(
                infoMessage = "Deleted $deletedCount screenshot(s)"
            )
        }
    }

    fun runOcrForSelected() {
        viewModelScope.launch {
            val selected = searchResults.value.filter { _uiState.value.selectedUris.contains(it.uri) }
            if (selected.isEmpty()) return@launch

            _uiState.value = _uiState.value.copy(isOcrRunning = true)
            var count = 0
            for (item in selected) {
                val res = ocrRepository.recognizeText(item.contentUri)
                if (res.success) count++
            }
            clearSelection()
            _uiState.value = _uiState.value.copy(
                isOcrRunning = false,
                infoMessage = "OCR completed for $count selected screenshot(s)"
            )
        }
    }

    // Rules Management & Execution
    fun saveRule(rule: AutoCategorizationRule) {
        rulesRepository.saveRule(rule)
        _uiState.value = _uiState.value.copy(infoMessage = "Rule '${rule.name}' saved")
    }

    fun deleteRule(ruleId: String) {
        rulesRepository.deleteRule(ruleId)
        _uiState.value = _uiState.value.copy(infoMessage = "Rule deleted")
    }

    fun toggleRule(ruleId: String, isEnabled: Boolean) {
        rulesRepository.toggleRule(ruleId, isEnabled)
    }

    fun moveRulePriority(ruleId: String, moveUp: Boolean) {
        rulesRepository.moveRulePriority(ruleId, moveUp)
    }

    fun reorderRules(reorderedRules: List<AutoCategorizationRule>) {
        rulesRepository.reorderRules(reorderedRules)
    }

    fun runRule(ruleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isRuleRunning = true)
            val result = rulesRepository.runRule(
                ruleId = ruleId,
                screenshotRepository = screenshotRepository,
                categoryRepository = categoryRepository,
                ocrRepository = ocrRepository
            )
            _uiState.value = _uiState.value.copy(
                isRuleRunning = false,
                infoMessage = result.message
            )
        }
    }

    fun runAllActiveRules() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isRuleRunning = true)
            val result = rulesRepository.runAllActiveRules(
                screenshotRepository = screenshotRepository,
                categoryRepository = categoryRepository,
                ocrRepository = ocrRepository
            )
            _uiState.value = _uiState.value.copy(
                isRuleRunning = false,
                infoMessage = result.message
            )
        }
    }

    // Standard OCR & Single-Item Actions
    fun runOcrForScreenshot(screenshot: ScreenshotUiModel) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOcrRunning = true, activeOcrUri = screenshot.uri)
            val result = ocrRepository.recognizeText(screenshot.contentUri)
            _uiState.value = _uiState.value.copy(
                isOcrRunning = false,
                activeOcrUri = null,
                activeOcrResult = result
            )
        }
    }

    fun runOcrForSearchResults() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isOcrRunning = true)
            val results = searchResults.value
            var successCount = 0
            for (item in results) {
                if (item.ocrStatus != "COMPLETED") {
                    val res = ocrRepository.recognizeText(item.contentUri)
                    if (res.success) successCount++
                }
            }
            _uiState.value = _uiState.value.copy(
                isOcrRunning = false,
                infoMessage = "OCR completed for $successCount screenshots"
            )
        }
    }

    fun runOcrForUnprocessed() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isOcrRunning = true)
            val batch = ocrRepository.recognizePendingScreenshots()
            _uiState.value = _uiState.value.copy(
                isOcrRunning = false,
                infoMessage = "OCR processed: ${batch.processedCount} (Success: ${batch.successCount}, Failed: ${batch.failureCount})"
            )
        }
    }

    fun runAutoRulesForUncategorized() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isOcrRunning = true)
            val result = rulesRepository.runAllActiveRules(screenshotRepository, categoryRepository, ocrRepository)
            _uiState.value = _uiState.value.copy(
                isOcrRunning = false,
                infoMessage = result.message
            )
        }
    }

    fun processAndIndexAllImages(forceReindex: Boolean = false, language: com.screenarc.app.ocr.OcrLanguage? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isOcrRunning = true)
            val progress = ocrRepository.processAndIndexAllImages(forceReindex, language)
            _uiState.value = _uiState.value.copy(
                isOcrRunning = false,
                infoMessage = if (progress.isCancelled) {
                    "OCR indexing cancelled (Indexed: ${progress.indexedCount}, Failed: ${progress.failedCount})"
                } else {
                    "OCR indexing completed: ${progress.indexedCount} indexed, ${progress.skippedCount} skipped, ${progress.failedCount} failed"
                }
            )
        }
    }

    fun cancelOcr() {
        viewModelScope.launch {
            ocrRepository.cancelPendingOcr()
            _uiState.value = _uiState.value.copy(isOcrRunning = false, activeOcrUri = null)
        }
    }

    fun moveToCategory(screenshot: ScreenshotUiModel, category: CategoryUiModel) {
        viewModelScope.launch {
            val result = screenshotRepository.moveToCategory(screenshot, category)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(infoMessage = "Moved to ${category.name}")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(errorMessage = "Move failed: ${e.message}")
            }
        }
    }

    fun copyToCategory(screenshot: ScreenshotUiModel, category: CategoryUiModel) {
        viewModelScope.launch {
            val result = screenshotRepository.copyToCategory(screenshot, category)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(infoMessage = "Copied to ${category.name}")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(errorMessage = "Copy failed: ${e.message}")
            }
        }
    }

    fun deleteScreenshot(screenshot: ScreenshotUiModel) {
        viewModelScope.launch {
            val result = screenshotRepository.deleteScreenshot(screenshot)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(infoMessage = "Screenshot deleted")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(errorMessage = "Delete failed: ${e.message}")
            }
        }
    }

    fun dismissOcrModal() {
        _uiState.value = _uiState.value.copy(activeOcrResult = null)
    }

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null, errorMessage = null)
    }

    fun syncScreenshots() {
        viewModelScope.launch {
            screenshotRepository.syncMediaStoreScreenshots()
        }
    }
}

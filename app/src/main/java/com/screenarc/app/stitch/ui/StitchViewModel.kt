package com.screenarc.app.stitch.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.screenarc.app.ScreenArcApplication
import com.screenarc.app.data.AppSettingsRepository
import com.screenarc.app.data.CategoryRepository
import com.screenarc.app.data.OcrRepository
import com.screenarc.app.data.RulesRepository
import com.screenarc.app.data.ScreenshotRepository
import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.service.ScreenshotObserver
import com.screenarc.app.stitch.StitchEngine
import com.screenarc.app.stitch.model.StitchConfig
import com.screenarc.app.stitch.model.StitchFormat
import com.screenarc.app.stitch.model.StitchImageItem
import com.screenarc.app.stitch.model.StitchMode
import com.screenarc.app.stitch.model.StitchSeam
import com.screenarc.app.stitch.model.StitchUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

class StitchViewModel(
    application: Application,
    private val initialUris: List<Uri>,
    private val screenshotRepository: ScreenshotRepository,
    private val categoryRepository: CategoryRepository,
    private val ocrRepository: OcrRepository,
    private val rulesRepository: RulesRepository,
    private val settingsRepository: AppSettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<StitchUiState>(StitchUiState.Idle)
    val uiState: StateFlow<StitchUiState> = _uiState.asStateFlow()

    private var currentImages: MutableList<StitchImageItem> = mutableListOf()
    private var currentSeams: MutableList<StitchSeam> = mutableListOf()
    private var currentConfig = StitchConfig()
    private var analysisJob: Job? = null
    private var previewFile: File? = null

    init {
        loadAndAnalyze(initialUris)
    }

    /**
     * Inspects URIs and runs initial overlap analysis.
     */
    fun loadAndAnalyze(uris: List<Uri>) {
        if (uris.size < 2) {
            _uiState.value = StitchUiState.Failure("At least 2 screenshots are required to stitch.", canRetry = false)
            return
        }

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.value = StitchUiState.Analyzing(progressPercent = 0.1f, message = "Inspecting screenshots…")

            val items = StitchEngine.inspectImages(getApplication(), uris)
            if (items.size < 2) {
                _uiState.value = StitchUiState.Failure("Unable to read at least 2 screenshots.", canRetry = true)
                return@launch
            }

            currentImages = items.toMutableList()

            runSeamAnalysis()
        }
    }

    private suspend fun runSeamAnalysis() {
        _uiState.value = StitchUiState.Analyzing(progressPercent = 0.3f, message = "Detecting vertical overlap…")

        val seams = StitchEngine.analyzeSeams(
            context = getApplication(),
            images = currentImages,
            config = currentConfig,
            onProgress = { cur, tot ->
                val p = 0.3f + 0.4f * (cur.toFloat() / tot)
                _uiState.value = StitchUiState.Analyzing(progressPercent = p, message = "Analyzing seam $cur of $tot…")
            }
        )

        currentSeams = seams.toMutableList()

        refreshPreview()
    }

    /**
     * Re-renders the low-memory preview bitmap and emits the Preview state.
     */
    private suspend fun refreshPreview() {
        _uiState.value = StitchUiState.Analyzing(progressPercent = 0.8f, message = "Generating preview…")

        val previewBitmap = StitchEngine.renderPreview(
            context = getApplication(),
            images = currentImages,
            seams = currentSeams,
            config = currentConfig,
            previewWidth = 720
        )

        val targetW = StitchEngine.calculateTargetWidth(currentImages)
        val estimatedH = StitchEngine.calculateTotalHeight(currentImages, currentSeams, currentConfig, targetW)

        val previewUri = if (previewBitmap != null) {
            savePreviewToCache(previewBitmap)
        } else null

        _uiState.value = StitchUiState.Preview(
            images = currentImages.toList(),
            seams = currentSeams.toList(),
            config = currentConfig,
            previewUri = previewUri,
            estimatedHeight = estimatedH
        )
    }

    private fun savePreviewToCache(bitmap: Bitmap): Uri? {
        return try {
            val cacheDir = getApplication<Application>().cacheDir
            val file = File(cacheDir, "stitch_preview_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            previewFile?.delete()
            previewFile = file
            bitmap.recycle()
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.w("StitchViewModel", "Failed saving preview to cache: ${e.message}")
            bitmap.recycle()
            null
        }
    }

    fun setMode(mode: StitchMode) {
        if (currentConfig.mode == mode) return
        currentConfig = currentConfig.copy(mode = mode)
        viewModelScope.launch {
            runSeamAnalysis()
        }
    }

    fun toggleCropStatusBar() {
        currentConfig = currentConfig.copy(cropStatusBar = !currentConfig.cropStatusBar)
        viewModelScope.launch {
            runSeamAnalysis()
        }
    }

    fun toggleCropNavigationBar() {
        currentConfig = currentConfig.copy(cropNavigationBar = !currentConfig.cropNavigationBar)
        viewModelScope.launch {
            runSeamAnalysis()
        }
    }

    fun setFormat(format: StitchFormat, quality: Int = currentConfig.jpegQuality) {
        currentConfig = currentConfig.copy(format = format, jpegQuality = quality)
        val state = _uiState.value
        if (state is StitchUiState.Preview) {
            _uiState.value = state.copy(config = currentConfig)
        }
    }

    fun setManualGap(gapPx: Int) {
        currentConfig = currentConfig.copy(manualGapPx = gapPx.coerceIn(0, 100))
        viewModelScope.launch {
            refreshPreview()
        }
    }

    /**
     * Nudge seam offset by delta (+/- pixels).
     */
    fun adjustSeam(seamIndex: Int, delta: Int) {
        if (seamIndex in currentSeams.indices) {
            val oldSeam = currentSeams[seamIndex]
            val newAdj = oldSeam.manualOffsetAdjustment + delta
            currentSeams[seamIndex] = oldSeam.copy(
                manualOffsetAdjustment = newAdj,
                statusMessage = "Adjusted: ${oldSeam.overlapHeight + newAdj}px"
            )
            viewModelScope.launch {
                refreshPreview()
            }
        }
    }

    /**
     * Reorders an image in the sequence.
     */
    fun moveImage(fromIndex: Int, toIndex: Int) {
        if (fromIndex in currentImages.indices && toIndex in currentImages.indices && fromIndex != toIndex) {
            Collections.swap(currentImages, fromIndex, toIndex)
            viewModelScope.launch {
                runSeamAnalysis()
            }
        }
    }

    /**
     * Removes an image from the sequence.
     */
    fun removeImage(index: Int) {
        if (index in currentImages.indices) {
            currentImages.removeAt(index)
            if (currentImages.size < 2) {
                _uiState.value = StitchUiState.Failure("Cannot stitch fewer than 2 screenshots.", canRetry = false)
            } else {
                viewModelScope.launch {
                    runSeamAnalysis()
                }
            }
        }
    }

    /**
     * Renders high-resolution image to MediaStore, indexes via OCR, and auto-categorizes.
     */
    fun saveStitchedImage() {
        if (currentImages.size < 2) return

        viewModelScope.launch {
            _uiState.value = StitchUiState.Saving(progressPercent = 0.05f, message = "Preparing high-resolution stitch…")

            try {
                val context = getApplication<Application>()
                val resolver = context.contentResolver
                val timestamp = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
                val ext = currentConfig.format.extension
                val fileName = "Screenshot_Stitched_$dateFormat.$ext"

                // Exclude fileName immediately so ScreenshotObserver never picks it up as an external capture
                ScreenshotObserver.excludeFileName(fileName)

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, currentConfig.format.mimeType)
                    put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
                    put(MediaStore.Images.Media.DATE_MODIFIED, timestamp / 1000)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots/")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val savedUri = resolver.insert(collection, contentValues)
                    ?: throw IllegalStateException("Could not create MediaStore entry for stitched image.")

                ScreenshotObserver.excludeUri(savedUri)

                // Render and stream to output stream
                val (width, height) = resolver.openOutputStream(savedUri)?.use { outStream ->
                    StitchEngine.renderAndStreamTo(
                        context = context,
                        images = currentImages,
                        seams = currentSeams,
                        config = currentConfig,
                        outputStream = outStream,
                        onProgress = { p, msg ->
                            _uiState.value = StitchUiState.Saving(progressPercent = p, message = msg)
                        }
                    )
                } ?: throw IllegalStateException("Failed opening stream to write stitched image.")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val finishValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(savedUri, finishValues, null, null)
                }

                _uiState.value = StitchUiState.Saving(progressPercent = 0.95f, message = "Indexing text & categorizing…")

                // Run OCR & auto-categorization asynchronously so it's instantly searchable
                withContext(Dispatchers.IO) {
                    try {
                        val settings = settingsRepository.settings.value
                        val ocrResult = ocrRepository.recognizeText(savedUri, settings.ocrLanguage)
                        if (ocrResult.success && ocrResult.recognizedText.isNotBlank()) {
                            val activeRules = rulesRepository.rules.value.filter { it.isEnabled }.sortedBy { it.priority }
                            val bestMatch = AutoCategorizationRule.resolveBestMatch(activeRules, ocrResult.recognizedText)
                            if (bestMatch != null && bestMatch.isAssigned) {
                                val categories = categoryRepository.categories.value
                                val target = categories.find { it.id == bestMatch.rule.targetCategoryId }
                                    ?: categories.find { it.name.equals(bestMatch.rule.targetCategoryName, ignoreCase = true) }
                                if (target != null && settings.autoAssignRulesEnabled) {
                                    val dummyUi = com.screenarc.app.model.ScreenshotUiModel(
                                        id = timestamp,
                                        uri = savedUri.toString(),
                                        displayName = fileName,
                                        dateAdded = timestamp / 1000,
                                        size = 0L,
                                        relativePath = "Pictures/Screenshots/"
                                    )
                                    screenshotRepository.moveToCategory(
                                        screenshot = dummyUi,
                                        category = target,
                                        ruleName = bestMatch.rule.name,
                                        triggerType = com.screenarc.app.model.OrganizedRecord.TRIGGER_AUTO
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("StitchViewModel", "Background OCR/categorization for stitched image non-fatal error: ${e.message}")
                    }
                }

                val sourceUris = currentImages.map { it.uri }
                _uiState.value = StitchUiState.Saved(
                    outputUri = savedUri,
                    sourceUris = sourceUris,
                    width = width,
                    height = height
                )

            } catch (e: Exception) {
                Log.e("StitchViewModel", "Error saving stitched screenshot: ${e.message}", e)
                _uiState.value = StitchUiState.Failure(e.message ?: "Failed to save stitched screenshot", canRetry = true)
            }
        }
    }

    /**
     * Deletes the original source screenshots that were combined.
     */
    suspend fun deleteOriginalScreenshots(): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (item in currentImages) {
            try {
                val res = screenshotRepository.deleteScreenshotByUri(item.uri.toString())
                if (res.isSuccess) count++
            } catch (e: Exception) {
                Log.w("StitchViewModel", "Failed deleting source screenshot ${item.uri}: ${e.message}")
            }
        }
        count
    }

    /**
     * Deletes the generated stitched screenshot.
     */
    suspend fun deleteStitchedImage(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            screenshotRepository.deleteScreenshotByUri(uri.toString()).isSuccess
        } catch (e: Exception) {
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        previewFile?.delete()
    }

    companion object {
        fun provideFactory(application: Application, uris: List<Uri>): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (application as ScreenArcApplication).appContainer
                    return StitchViewModel(
                        application = application,
                        initialUris = uris,
                        screenshotRepository = container.screenshotRepository,
                        categoryRepository = container.categoryRepository,
                        ocrRepository = container.ocrRepository,
                        rulesRepository = container.rulesRepository,
                        settingsRepository = container.settingsRepository
                    ) as T
                }
            }
    }
}

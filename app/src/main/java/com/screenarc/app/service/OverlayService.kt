package com.screenarc.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.screenarc.app.MainActivity
import com.screenarc.app.R
import com.screenarc.app.ScreenArcApplication
import com.screenarc.app.data.AppSettingsRepository
import com.screenarc.app.data.CategoryRepository
import com.screenarc.app.data.OrganizedHistoryRepository
import com.screenarc.app.data.ScreenshotRepository
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.OverlayEvent
import com.screenarc.app.model.ScreenshotItem
import com.screenarc.app.palette.PaletteExtractor
import com.screenarc.app.storage.MediaStoreOrganizer
import com.screenarc.app.ui.overlay.FloatingOverlayView
import com.screenarc.app.ui.theme.ScreenArcTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var overlayComposeView: ComposeView? = null
    private var screenshotObserver: ScreenshotObserver? = null

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var historyRepository: OrganizedHistoryRepository
    private lateinit var screenshotRepository: ScreenshotRepository
    private lateinit var ocrRepository: com.screenarc.app.data.OcrRepository
    private lateinit var rulesRepository: com.screenarc.app.data.RulesRepository

    private var currentOverlayScreenshot: ScreenshotItem? = null
    private val overlayLifecycleOwner = OverlayServiceLifecycleOwner()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val container = (application as com.screenarc.app.ScreenArcApplication).appContainer
        categoryRepository = container.categoryRepository
        settingsRepository = container.settingsRepository
        historyRepository = container.historyRepository
        screenshotRepository = container.screenshotRepository
        ocrRepository = container.ocrRepository
        rulesRepository = container.rulesRepository

        overlayLifecycleOwner.performRestore(null)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        registerScreenshotObserver()
        observeCategoriesAndEvents()
    }

    private fun observeCategoriesAndEvents() {
        // Collect Room Flow of Categories reactively: re-render overlay if visible
        serviceScope.launch {
            categoryRepository.categoriesFlow.collectLatest { updatedCategories ->
                currentOverlayScreenshot?.let { item ->
                    showFloatingOverlay(item)
                }
            }
        }

        // Collect events from OverlayEventBus
        serviceScope.launch {
            OverlayEventBus.events.collectLatest { event ->
                when (event) {
                    is OverlayEvent.CategoriesChanged -> {
                        currentOverlayScreenshot?.let { item ->
                            showFloatingOverlay(item)
                        }
                    }
                    is OverlayEvent.RefreshRequested -> {
                        // RefreshRequested is for in-app data screens (HomeScreen/HistoryScreen).
                        // Do not re-display overlay here.
                    }
                    is OverlayEvent.OverlayClosed -> {
                        removeOverlayView(clearCurrentScreenshot = true)
                    }
                    is OverlayEvent.ScreenshotsChanged -> {
                        // handle screenshots change
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "Stopping OverlayService")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_DEMO -> {
                val label = intent.getStringExtra(EXTRA_DEMO_LABEL) ?: "Test Screenshot"
                val colorHex = intent.getIntExtra(EXTRA_DEMO_COLOR, 0xFF6750A4.toInt())
                serviceScope.launch {
                    val item = MediaStoreOrganizer.createSimulatedScreenshot(this@OverlayService, label, colorHex)
                    screenshotObserver?.markProcessed(item.id, item.uri)
                    showFloatingOverlay(item)
                }
            }
            ACTION_SHOW_SCREENSHOT_URI -> {
                val uriString = intent.getStringExtra(EXTRA_URI)
                if (uriString != null) {
                    val uri = Uri.parse(uriString)
                    serviceScope.launch {
                        val bitmap = MediaStoreOrganizer.loadThumbnail(this@OverlayService, uri, 350)
                        val palette = bitmap?.let { PaletteExtractor.extractColors(it) }
                            ?: com.screenarc.app.model.DynamicPaletteColors()
                        val item = ScreenshotItem(
                            id = System.currentTimeMillis(),
                            uri = uri,
                            displayName = "Selected_Image.png",
                            dateAdded = System.currentTimeMillis() / 1000,
                            size = 0,
                            relativePath = "Pictures/Screenshots/",
                            bitmapPreview = bitmap,
                            paletteColors = palette
                        )
                        screenshotObserver?.markProcessed(item.id, item.uri)
                        showFloatingOverlay(item)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun registerScreenshotObserver() {
        if (screenshotObserver == null) {
            screenshotObserver = ScreenshotObserver(this, serviceScope) { detectedItem ->
                Log.d(TAG, "Screenshot detected: ${detectedItem.displayName}")
                handleDetectedScreenshot(detectedItem)
            }
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver!!
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    contentResolver.registerContentObserver(
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        true,
                        screenshotObserver!!
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not register VOLUME_EXTERNAL observer: ${e.message}")
                }
            }
            Log.d(TAG, "Registered MediaStore content observer successfully")
        }
    }

    private fun handleDetectedScreenshot(screenshot: ScreenshotItem) {
        val settings = settingsRepository.settings.value
        val activeRules = rulesRepository.rules.value.filter { it.isEnabled }.sortedBy { it.priority }

        if (settings.autoOcrEnabled || activeRules.isNotEmpty()) {
            serviceScope.launch {
                try {
                    val ocrResult = ocrRepository.recognizeText(screenshot.uri, settings.ocrLanguage)
                    if (ocrResult.success && ocrResult.recognizedText.isNotBlank()) {
                        val winningMatch = com.screenarc.app.model.AutoCategorizationRule.resolveBestMatch(activeRules, ocrResult.recognizedText)
                        if (winningMatch != null && winningMatch.isAssigned) {
                            val categories = categoryRepository.categories.value
                            val targetCat = categories.find { it.id == winningMatch.rule.targetCategoryId }
                                ?: categories.find { it.name.equals(winningMatch.rule.targetCategoryName, ignoreCase = true) }

                            if (targetCat != null) {
                                if (settings.autoAssignRulesEnabled) {
                                    Log.d(TAG, "Auto-categorizing screenshot ${screenshot.displayName} -> ${targetCat.name} via rule '${winningMatch.rule.name}'")
                                    Toast.makeText(
                                        this@OverlayService,
                                        "Auto-categorized into ${targetCat.name} ('${winningMatch.rule.name}')",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    handleMoveToCategory(screenshot, targetCat)
                                    return@launch
                                } else {
                                    // Highlight & prioritize suggested category on radial overlay
                                    showFloatingOverlay(screenshot, suggestedCategoryId = targetCat.id)
                                    return@launch
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background auto-processing error: ${e.message}", e)
                }
                showFloatingOverlay(screenshot)
            }
        } else {
            showFloatingOverlay(screenshot)
        }
    }

    private fun unregisterScreenshotObserver() {
        screenshotObserver?.let {
            contentResolver.unregisterContentObserver(it)
            screenshotObserver = null
            Log.d(TAG, "Unregistered MediaStore content observer")
        }
    }

    private fun postFallbackNotification(screenshot: ScreenshotItem, reason: String) {
        Log.d(TAG, "Posting fallback notification for screenshot ${screenshot.displayName}: $reason")
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TAB, MainActivity.TAB_SEARCH)
            putExtra(EXTRA_URI, screenshot.uri.toString())
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            (screenshot.id % 10000).toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ScreenArcApplication.CHANNEL_ID)
            .setContentTitle("New Screenshot Detected")
            .setContentText("Tap to organize ${screenshot.displayName}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((FALLBACK_NOTIFICATION_ID + (screenshot.id % 1000)).toInt(), notification)
    }

    fun showFloatingOverlay(screenshot: ScreenshotItem, suggestedCategoryId: Long? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                showFloatingOverlay(screenshot, suggestedCategoryId)
            }
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlay: SYSTEM_ALERT_WINDOW permission not granted")
            Toast.makeText(this, "ScreenArc: Overlay permission required", Toast.LENGTH_SHORT).show()
            postFallbackNotification(screenshot, "Overlay permission not granted")
            return
        }

        // Prevent showing duplicate overlay if already active for this screenshot
        val current = currentOverlayScreenshot
        if (overlayComposeView != null && current != null && (current.id == screenshot.id || current.uri == screenshot.uri)) {
            Log.d(TAG, "Overlay is already active for screenshot ${screenshot.id}, ignoring duplicate show")
            return
        }

        // Remove existing overlay view if present without clearing the upcoming screenshot
        removeOverlayView(clearCurrentScreenshot = false)
        currentOverlayScreenshot = screenshot

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    removeOverlayView(clearCurrentScreenshot = true)
                    true
                } else {
                    false
                }
            }

            setContent {
                val categories by categoryRepository.categories.collectAsState()
                val settings by settingsRepository.settings.collectAsState()
                val history by historyRepository.history.collectAsState()

                // Compute usage counts for each category
                val usageMap = remember(history) {
                    history.groupBy { it.categoryName }
                        .mapNotNull { entry ->
                            val cat = categories.find { it.name.equals(entry.key, ignoreCase = true) }
                            cat?.let { it.id to entry.value.size }
                        }.toMap()
                }

                ScreenArcTheme(
                    themePreset = settings.themePreset,
                    uiDesignStyle = settings.uiDesignStyle
                ) {
                    FloatingOverlayView(
                        screenshot = screenshot,
                        categories = categories,
                        settings = settings,
                        suggestedCategoryId = suggestedCategoryId,
                        categoryUsageMap = usageMap,
                        onMoveToCategory = { category ->
                            handleMoveToCategory(screenshot, category)
                        },
                        onUndoMove = { category ->
                            handleUndoMove(screenshot, category)
                        },
                        onDeleteScreenshot = {
                            handleDeleteScreenshot(screenshot)
                        },
                        onShareScreenshot = {
                            handleShareScreenshot(screenshot)
                        },
                        onDismiss = {
                            removeOverlayView(clearCurrentScreenshot = true)
                        },
                        onOpenSearch = {
                            val openAppIntent = Intent(this@OverlayService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(MainActivity.EXTRA_NAVIGATE_TAB, MainActivity.TAB_SEARCH)
                            }
                            startActivity(openAppIntent)
                        },
                        onStyleChanged = { newStyle ->
                            settingsRepository.updateSettings { it.copy(arcDesignStyle = newStyle) }
                        }
                    )
                }
            }
        }

        try {
            windowManager?.addView(composeView, layoutParams)
            overlayComposeView = composeView
            Log.d(TAG, "Added FloatingOverlayView to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding overlay to WindowManager: ${e.message}", e)
            postFallbackNotification(screenshot, "WindowManager error: ${e.message}")
        }
    }

    private var lastMovedScreenshotUi: com.screenarc.app.model.ScreenshotUiModel? = null

    private fun handleMoveToCategory(screenshot: ScreenshotItem, category: CategoryUiModel) {
        screenshotObserver?.markProcessed(screenshot.id, screenshot.uri)

        val screenshotUi = com.screenarc.app.model.ScreenshotUiModel(
            id = screenshot.id,
            uri = screenshot.uri.toString(),
            displayName = screenshot.displayName,
            dateAdded = screenshot.dateAdded,
            size = screenshot.size,
            relativePath = screenshot.relativePath,
            mimeType = screenshot.mimeType
        )
        lastMovedScreenshotUi = screenshotUi

        serviceScope.launch {
            val result = screenshotRepository.moveToCategory(screenshotUi, category)
            result.onSuccess { newUri ->
                screenshotObserver?.markProcessed(screenshot.id, newUri)
                lastMovedScreenshotUi = screenshotUi.copy(uri = newUri.toString(), relativePath = "Pictures/Screenshots/${category.subfolderName}/", categoryId = category.id)
                Log.d(TAG, "Successfully moved screenshot to ${category.name}")
            }.onFailure { err ->
                Log.e(TAG, "Failed moving screenshot: ${err.message}", err)
                Toast.makeText(this@OverlayService, "Error moving to ${category.name}: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleUndoMove(screenshot: ScreenshotItem, category: CategoryUiModel) {
        val itemToUndo = lastMovedScreenshotUi ?: com.screenarc.app.model.ScreenshotUiModel(
            id = screenshot.id,
            uri = screenshot.uri.toString(),
            displayName = screenshot.displayName,
            dateAdded = screenshot.dateAdded,
            size = screenshot.size,
            relativePath = "Pictures/Screenshots/${category.subfolderName}/",
            mimeType = screenshot.mimeType
        )

        serviceScope.launch {
            val result = screenshotRepository.undoMoveToCategory(itemToUndo)
            result.onSuccess { restoredUri ->
                screenshotObserver?.markProcessed(screenshot.id, restoredUri)
                Toast.makeText(this@OverlayService, "Restored to Screenshots", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Successfully undid move for screenshot ${screenshot.displayName}")
            }.onFailure { err ->
                Log.e(TAG, "Failed to undo move: ${err.message}", err)
                Toast.makeText(this@OverlayService, "Could not undo: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleDeleteScreenshot(screenshot: ScreenshotItem) {
        screenshotObserver?.markProcessed(screenshot.id, screenshot.uri)
        currentOverlayScreenshot = null
        removeOverlayView(clearCurrentScreenshot = true)

        serviceScope.launch {
            val screenshotUi = com.screenarc.app.model.ScreenshotUiModel(
                id = screenshot.id,
                uri = screenshot.uri.toString(),
                displayName = screenshot.displayName,
                dateAdded = screenshot.dateAdded,
                size = screenshot.size,
                relativePath = screenshot.relativePath,
                mimeType = screenshot.mimeType
            )
            val result = screenshotRepository.deleteScreenshot(screenshotUi)
            result.onSuccess {
                Toast.makeText(this@OverlayService, "Screenshot deleted", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Log.e(TAG, "Failed deleting screenshot: ${err.message}", err)
                Toast.makeText(this@OverlayService, "Error deleting screenshot: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleShareScreenshot(screenshot: ScreenshotItem) {
        removeOverlayView(clearCurrentScreenshot = true)
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = screenshot.mimeType.ifEmpty { "image/png" }
                putExtra(Intent.EXTRA_STREAM, screenshot.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Screenshot").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed sharing screenshot: ${e.message}", e)
            Toast.makeText(this@OverlayService, "Could not share screenshot: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeOverlayView(clearCurrentScreenshot: Boolean = true) {
        val removeAction = {
            overlayComposeView?.let { view ->
                try {
                    windowManager?.removeView(view)
                    Log.d(TAG, "Removed FloatingOverlayView from WindowManager")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing overlay view: ${e.message}", e)
                }
                overlayComposeView = null
            }
            if (clearCurrentScreenshot) {
                currentOverlayScreenshot = null
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeAction()
        } else {
            Handler(Looper.getMainLooper()).post(removeAction)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val demoIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_TRIGGER_DEMO
            putExtra(EXTRA_DEMO_LABEL, "Quick Test Screenshot")
            putExtra(EXTRA_DEMO_COLOR, 0xFF1976D2.toInt())
        }
        val demoPendingIntent = PendingIntent.getService(
            this,
            2,
            demoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ScreenArcApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.action_test_screenshot),
                demoPendingIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.action_stop),
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService onDestroy")
        removeOverlayView()
        unregisterScreenshotObserver()
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val FALLBACK_NOTIFICATION_ID = 2002

        const val ACTION_START_SERVICE = "com.screenarc.app.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.screenarc.app.action.STOP_SERVICE"
        const val ACTION_TRIGGER_DEMO = "com.screenarc.app.action.TRIGGER_DEMO"
        const val ACTION_SHOW_SCREENSHOT_URI = "com.screenarc.app.action.SHOW_SCREENSHOT_URI"

        const val EXTRA_DEMO_LABEL = "extra_demo_label"
        const val EXTRA_DEMO_COLOR = "extra_demo_color"
        const val EXTRA_URI = "extra_uri"

        fun startService(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun triggerDemo(context: Context, label: String = "Test Screenshot", colorHex: Int = 0xFF6750A4.toInt()) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_TRIGGER_DEMO
                putExtra(EXTRA_DEMO_LABEL, label)
                putExtra(EXTRA_DEMO_COLOR, colorHex)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

private class OverlayServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val appViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = appViewModelStore
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun performRestore(savedState: android.os.Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }
}

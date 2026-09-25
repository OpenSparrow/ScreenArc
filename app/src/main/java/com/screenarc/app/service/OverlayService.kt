package com.screenarc.app.service

import android.app.Notification
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
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
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

    private var currentOverlayScreenshot: ScreenshotItem? = null
    private val overlayLifecycleOwner = OverlayServiceLifecycleOwner()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        categoryRepository = CategoryRepository(this)
        settingsRepository = AppSettingsRepository(this)
        historyRepository = OrganizedHistoryRepository(this)
        screenshotRepository = ScreenshotRepository(this)

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
                        currentOverlayScreenshot?.let { item ->
                            showFloatingOverlay(item)
                        }
                    }
                    is OverlayEvent.OverlayClosed -> {
                        removeOverlayView()
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
                Log.d(TAG, "Displaying overlay for new screenshot: ${detectedItem.displayName}")
                showFloatingOverlay(detectedItem)
            }
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver!!
            )
            Log.d(TAG, "Registered MediaStore content observer successfully")
        }
    }

    private fun unregisterScreenshotObserver() {
        screenshotObserver?.let {
            contentResolver.unregisterContentObserver(it)
            screenshotObserver = null
            Log.d(TAG, "Unregistered MediaStore content observer")
        }
    }

    fun showFloatingOverlay(screenshot: ScreenshotItem) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlay: SYSTEM_ALERT_WINDOW permission not granted")
            Toast.makeText(this, "ScreenArc: Overlay permission required", Toast.LENGTH_SHORT).show()
            return
        }

        currentOverlayScreenshot = screenshot

        // Remove existing overlay view if present
        removeOverlayView()

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

            setContent {
                val categories = categoryRepository.categories.value
                val settings = settingsRepository.settings.value

                FloatingOverlayView(
                    screenshot = screenshot,
                    categories = categories,
                    settings = settings,
                    onMoveToCategory = { category ->
                        handleMoveToCategory(screenshot, category)
                    },
                    onDismiss = {
                        removeOverlayView()
                    },
                    onOpenSearch = {
                        val openAppIntent = Intent(this@OverlayService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(MainActivity.EXTRA_NAVIGATE_TAB, MainActivity.TAB_SEARCH)
                        }
                        startActivity(openAppIntent)
                    }
                )
            }
        }

        try {
            windowManager?.addView(composeView, layoutParams)
            overlayComposeView = composeView
            Log.d(TAG, "Added FloatingOverlayView to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding overlay to WindowManager: ${e.message}", e)
        }
    }

    private fun handleMoveToCategory(screenshot: ScreenshotItem, category: CategoryUiModel) {
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
            val result = screenshotRepository.moveToCategory(screenshotUi, category)
            result.onSuccess {
                Log.d(TAG, "Successfully moved screenshot to ${category.name}")
            }.onFailure { err ->
                Log.e(TAG, "Failed moving screenshot: ${err.message}", err)
                Toast.makeText(this@OverlayService, "Error moving to ${category.name}: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeOverlayView() {
        overlayComposeView?.let { view ->
            try {
                windowManager?.removeView(view)
                Log.d(TAG, "Removed FloatingOverlayView from WindowManager")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view: ${e.message}", e)
            }
            overlayComposeView = null
        }
        currentOverlayScreenshot = null
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

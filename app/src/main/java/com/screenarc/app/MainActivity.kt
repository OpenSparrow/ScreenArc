package com.screenarc.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.screenarc.app.data.AppSettingsRepository
import com.screenarc.app.data.CategoryRepository
import com.screenarc.app.data.OrganizedHistoryRepository
import com.screenarc.app.service.OverlayService
import com.screenarc.app.ui.screens.CategoriesScreen
import com.screenarc.app.ui.screens.HistoryScreen
import com.screenarc.app.ui.screens.HomeScreen
import com.screenarc.app.ui.screens.ScreenshotsScreen
import com.screenarc.app.ui.screens.SettingsScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.screenarc.app.ui.theme.GlassmorphicBackdrop
import com.screenarc.app.ui.theme.LocalIsGlassmorphic
import com.screenarc.app.ui.theme.ScreenArcTheme
import com.screenarc.app.viewmodel.SearchViewModel

enum class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search),
    CATEGORIES("Categories", Icons.Filled.Category, Icons.Outlined.Category),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var historyRepository: OrganizedHistoryRepository
    private val searchViewModel: SearchViewModel by viewModels()

    private var selectedTabState = mutableStateOf(NavigationTab.HOME)

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Granted permissions handled
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        categoryRepository = CategoryRepository(this)
        settingsRepository = AppSettingsRepository(this)
        historyRepository = OrganizedHistoryRepository(this)

        handleIncomingIntent(intent)
        requestRequiredPermissions()

        setContent {
            val settings by settingsRepository.settings.collectAsState()
            ScreenArcTheme(
                themePreset = settings.themePreset,
                uiDesignStyle = settings.uiDesignStyle
            ) {
                val isGlass = LocalIsGlassmorphic.current
                val isDark = isSystemInDarkTheme() || settings.themePreset.isDarkOnly
                var selectedTab by remember { selectedTabState }

                GlassmorphicBackdrop(
                    isGlassmorphic = isGlass,
                    isDark = isDark
                ) {
                    Scaffold(
                        containerColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.background,
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = "ScreenArc",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            if (Settings.canDrawOverlays(this@MainActivity)) {
                                                OverlayService.triggerDemo(
                                                    this@MainActivity,
                                                    "Quick Mockup Arc",
                                                    0xFF6750A4.toInt()
                                                )
                                            } else {
                                                val intent = Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:$packageName")
                                                )
                                                startActivity(intent)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Test Overlay",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = if (isGlass) {
                                        if (isDark) Color(0x35120E22) else Color(0x75FFFFFF)
                                    } else MaterialTheme.colorScheme.surface
                                ),
                                modifier = if (isGlass) Modifier.drawBehind {
                                    drawLine(
                                        color = Color.White.copy(alpha = if (isDark) 0.18f else 0.45f),
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width, size.height),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                } else Modifier
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = if (isGlass) {
                                    if (isDark) Color(0x40120E22) else Color(0x85FFFFFF)
                                } else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = if (isGlass) Modifier.drawBehind {
                                    drawLine(
                                        color = Color.White.copy(alpha = if (isDark) 0.22f else 0.50f),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, 0f),
                                        strokeWidth = 1.2.dp.toPx()
                                    )
                                } else Modifier
                            ) {
                                NavigationTab.values().forEach { tab ->
                                    val isSelected = selectedTab == tab
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { selectedTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.title,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        },
                                        label = { Text(tab.title) }
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "TabContent"
                        ) { tab ->
                            when (tab) {
                                NavigationTab.HOME -> HomeScreen(
                                    categoryRepo = categoryRepository,
                                    settingsRepo = settingsRepository,
                                    historyRepo = historyRepository,
                                    onNavigateToCategories = { selectedTab = NavigationTab.CATEGORIES },
                                    onNavigateToSearch = { selectedTab = NavigationTab.SEARCH }
                                )
                                NavigationTab.SEARCH -> ScreenshotsScreen(
                                    searchViewModel = searchViewModel
                                )
                                NavigationTab.CATEGORIES -> CategoriesScreen(
                                    categoryRepo = categoryRepository
                                )
                                NavigationTab.HISTORY -> HistoryScreen(
                                    historyRepo = historyRepository,
                                    categoryRepo = categoryRepository
                                )
                                NavigationTab.SETTINGS -> SettingsScreen(
                                    settingsRepo = settingsRepository,
                                    searchViewModel = searchViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::settingsRepository.isInitialized &&
            settingsRepository.settings.value.isServiceEnabled &&
            Settings.canDrawOverlays(this)
        ) {
            OverlayService.startService(this)
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val tabTarget = intent?.getStringExtra(EXTRA_NAVIGATE_TAB)
        if (tabTarget == TAB_SEARCH) {
            selectedTabState.value = NavigationTab.SEARCH
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }

        permissionRequestLauncher.launch(permissions.toTypedArray())
    }

    companion object {
        const val EXTRA_NAVIGATE_TAB = "extra_navigate_tab"
        const val TAB_SEARCH = "tab_search"
    }
}

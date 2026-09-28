package com.screenarc.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.rotate
import com.screenarc.app.data.ConfigurationBackupManager
import com.screenarc.app.data.UiDesignStyle
import com.screenarc.app.model.ThemeCategory
import com.screenarc.app.ui.theme.GlassElevatedCard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Vibration
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.screenarc.app.service.OverlayService
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenarc.app.data.AppSettingsRepository
import com.screenarc.app.data.OcrProcessingMode
import com.screenarc.app.model.AppThemePreset
import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.ocr.OcrLanguage
import com.screenarc.app.viewmodel.SearchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepo: AppSettingsRepository,
    searchViewModel: SearchViewModel? = null,
    modifier: Modifier = Modifier
) {
    val settings by settingsRepo.settings.collectAsState()
    val batchProgress = searchViewModel?.batchProgress?.collectAsState()?.value
    val uiState = searchViewModel?.uiState?.collectAsState()?.value
    val rules = searchViewModel?.rules?.collectAsState()?.value ?: emptyList()
    val categories = searchViewModel?.categories?.collectAsState()?.value ?: emptyList()

    var forceReindex by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<AutoCategorizationRule?>(null) }
    var showCreateRuleDialog by remember { mutableStateOf(false) }
    var showPasteJsonDialog by remember { mutableStateOf(false) }
    var pastedJsonText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Text(
                text = "Preferences & Rules",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Configure auto-categorization rules, OCR language models, overlay behavior, and styling",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Rules-Based Category Allocation Card
        searchViewModel?.let { vm ->
            item {
                var rulesExpanded by remember { mutableStateOf(true) }
                val rulesArrowRotation by animateFloatAsState(
                    targetValue = if (rulesExpanded) 180f else 0f,
                    animationSpec = tween(300),
                    label = "RulesArrow"
                )
                GlassElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { rulesExpanded = !rulesExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Auto-Categorization Rules",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (rulesExpanded) {
                                    FilledTonalButton(
                                        onClick = { showCreateRuleDialog = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New Rule", fontSize = 11.sp)
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (rulesExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp).rotate(rulesArrowRotation)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = rulesExpanded,
                            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
                            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(140))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Automatically route screenshots to categories based on matching OCR text keywords (e.g., Movie names -> Movie; Repo, Tool, OSINT -> Tech).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Header action: Run All Active Rules Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${rules.count { it.isEnabled }} active / ${rules.size} total rules",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Button(
                                        onClick = { vm.runAllActiveRules() },
                                        enabled = uiState?.isRuleRunning != true && rules.any { it.isEnabled },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        if (uiState?.isRuleRunning == true) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Running...", fontSize = 11.sp)
                                        } else {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Run All Active Rules", fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // List of Rules
                                if (rules.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "No categorization rules configured. Tap '+ New Rule' to create one.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        rules.forEachIndexed { index, rule ->
                                            RuleItemCard(
                                                rule = rule,
                                                categories = categories,
                                                isRuleRunning = uiState?.isRuleRunning == true,
                                                canMoveUp = index > 0,
                                                canMoveDown = index < rules.size - 1,
                                                onMoveUp = { vm.moveRulePriority(rule.id, true) },
                                                onMoveDown = { vm.moveRulePriority(rule.id, false) },
                                                onToggle = { isEnabled -> vm.toggleRule(rule.id, isEnabled) },
                                                onRun = { vm.runRule(rule.id) },
                                                onEdit = { ruleToEdit = rule },
                                                onDelete = { vm.deleteRule(rule.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Backup & Restore Configurations Card (Rules & Categories)
        item {
            val context = LocalContext.current
            var backupExpanded by remember { mutableStateOf(false) }
            val backupArrowRotation by animateFloatAsState(
                targetValue = if (backupExpanded) 180f else 0f,
                animationSpec = tween(300),
                label = "BackupArrow"
            )

            val importFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    val content = ConfigurationBackupManager.readTextFromUri(context, uri)
                    if (content != null) {
                        searchViewModel?.importConfigurationBackup(content, settingsRepo)
                    }
                }
            }

            GlassElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { backupExpanded = !backupExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.Backup,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Backup & Restore Configurations",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (backupExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp).rotate(backupArrowRotation)
                        )
                    }

                    AnimatedVisibility(
                        visible = backupExpanded,
                        enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
                        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(140))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Export your custom rules, keywords, and category folder structure to a portable backup file, or restore them across devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val json = ConfigurationBackupManager.createBackupJson(
                                            rules = rules,
                                            categories = categories,
                                            settings = settings
                                        )
                                        ConfigurationBackupManager.shareBackup(context, json)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        importFileLauncher.launch("*/*")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import File", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val json = ConfigurationBackupManager.createBackupJson(
                                            rules = rules,
                                            categories = categories,
                                            settings = settings
                                        )
                                        ConfigurationBackupManager.copyToClipboard(context, json)
                                        android.widget.Toast.makeText(context, "Backup copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy JSON", fontSize = 11.sp)
                                }

                                FilledTonalButton(
                                    onClick = { showPasteJsonDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Paste JSON", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. On-Device OCR Settings Card (Multilingual & Script Support)
        item {
            var ocrExpanded by remember { mutableStateOf(true) }
            val ocrArrowRotation by animateFloatAsState(
                targetValue = if (ocrExpanded) 180f else 0f,
                animationSpec = tween(300),
                label = "OcrArrow"
            )
            GlassElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { ocrExpanded = !ocrExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "On-Device OCR & Script Models",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (ocrExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp).rotate(ocrArrowRotation)
                        )
                    }

                    AnimatedVisibility(
                        visible = ocrExpanded,
                        enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
                        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(140))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Configure OCR recognition models and language scripts. Google ML Kit models run 100% locally on-device with zero network latency.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Active OCR Script & Language",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Language Option: Devanagari + English (Default)
                            val isDevanagari = settings.ocrLanguage == OcrLanguage.DEVANAGARI
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDevanagari) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isDevanagari) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        settingsRepo.updateSettings { it.copy(ocrLanguage = OcrLanguage.DEVANAGARI) }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isDevanagari,
                                        onClick = {
                                            settingsRepo.updateSettings { it.copy(ocrLanguage = OcrLanguage.DEVANAGARI) }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Hindi / Devanagari + English",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isDevanagari) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Recommended",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Recognizes Hindi, Marathi, Sanskrit, Nepali, Konkani, etc., and bilingual English text seamlessly.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Language Option: English / Latin
                            val isLatin = settings.ocrLanguage == OcrLanguage.LATIN
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isLatin) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isLatin) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        settingsRepo.updateSettings { it.copy(ocrLanguage = OcrLanguage.LATIN) }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isLatin,
                                        onClick = {
                                            settingsRepo.updateSettings { it.copy(ocrLanguage = OcrLanguage.LATIN) }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "English / Latin",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isLatin) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                        Text(
                                            text = "Optimized for Latin script English and European text.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Informational note for other Indian scripts
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Note on Tamil, Telugu, Kannada, Malayalam, Bengali: On-device ML Kit v2 currently provides bundled models for Devanagari and Latin. Additional Indic scripts can be loaded via custom OCR provider plug-ins.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Automatic Processing & Rules",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // 1. Auto Run OCR Switch
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto run OCR",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "Run on-device OCR in the background whenever a new screenshot is captured.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = settings.autoOcrEnabled,
                                        onCheckedChange = { isChecked ->
                                            settingsRepo.updateSettings { it.copy(autoOcrEnabled = isChecked) }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 2. Auto Assign Categories using Rules Switch (Dependent on Auto Run OCR)
                            val isAutoRulesEnabled = settings.autoAssignRulesEnabled
                            val canToggleRules = settings.autoOcrEnabled
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (canToggleRules) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto assign categories using rules",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (canToggleRules) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        )
                                        Text(
                                            text = if (canToggleRules) {
                                                "Automatically move matching screenshots into their category folder without user intervention."
                                            } else {
                                                "Requires 'Auto run OCR' to be enabled."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (canToggleRules) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = isAutoRulesEnabled,
                                        enabled = canToggleRules,
                                        onCheckedChange = { isChecked ->
                                            settingsRepo.updateSettings { it.copy(autoAssignRulesEnabled = isChecked) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Bulk OCR Processing Card
        searchViewModel?.let { vm ->
            item {
                var bulkOcrExpanded by remember { mutableStateOf(true) }
                val bulkOcrArrowRotation by animateFloatAsState(
                    targetValue = if (bulkOcrExpanded) 180f else 0f,
                    animationSpec = tween(300),
                    label = "BulkOcrArrow"
                )
                GlassElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { bulkOcrExpanded = !bulkOcrExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Process & Index All Images",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (bulkOcrExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp).rotate(bulkOcrArrowRotation)
                            )
                        }

                        AnimatedVisibility(
                            visible = bulkOcrExpanded,
                            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
                            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(140))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Discover all screenshots from your device, run OCR with the selected language model, normalize Unicode text, and populate the local search index.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { forceReindex = !forceReindex }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = forceReindex,
                                        onCheckedChange = { forceReindex = it }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Force re-process already indexed images",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Active batch progress display
                                if (batchProgress != null && batchProgress.isRunning) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Processing images...",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${batchProgress.current} / ${batchProgress.total}",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            LinearProgressIndicator(
                                                progress = { batchProgress.progressFraction },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            if (batchProgress.currentFileName.isNotEmpty()) {
                                                Text(
                                                    text = "Current: ${batchProgress.currentFileName}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    maxLines = 1
                                                )
                                            }

                                            Text(
                                                text = "OCR: ${batchProgress.currentLanguage}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Indexed: ${batchProgress.indexedCount}  |  Skipped: ${batchProgress.skippedCount}  |  Failed: ${batchProgress.failedCount}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                Button(
                                                    onClick = { vm.cancelOcr() },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.error
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Cancel", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            vm.processAndIndexAllImages(forceReindex = forceReindex, language = settings.ocrLanguage)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Process & Index All Images")
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedButton(
                                        onClick = {
                                            vm.runAutoRulesForUncategorized()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.Rule, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Run Active Rules on Existing Screenshots")
                                    }
                                }

                                // Completed banner summary
                                if (batchProgress != null && batchProgress.isCompleted) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "OCR Indexing Completed",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Processed: ${batchProgress.total}  •  Indexed: ${batchProgress.indexedCount}  •  Skipped: ${batchProgress.skippedCount}  •  Failed: ${batchProgress.failedCount}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Overlay Appearance Settings Card
        item {
            var overlayExpanded by remember { mutableStateOf(false) }
            val overlayArrowRotation by animateFloatAsState(
                targetValue = if (overlayExpanded) 180f else 0f,
                animationSpec = tween(300),
                label = "OverlayArrow"
            )
            GlassElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { overlayExpanded = !overlayExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Overlay Sizing & Layout",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (overlayExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp).rotate(overlayArrowRotation)
                        )
                    }

                    AnimatedVisibility(
                        visible = overlayExpanded,
                        enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
                        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(140))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Bubble Size Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Bubble Diameter", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text("${settings.bubbleSizeDp} dp", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Slider(
                                value = settings.bubbleSizeDp.toFloat(),
                                onValueChange = { size ->
                                    settingsRepo.updateSettings { it.copy(bubbleSizeDp = size.toInt()) }
                                },
                                valueRange = 48f..96f,
                                steps = 5
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Arc Radius Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Animation, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Radial Arc Radius", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text("${settings.arcRadiusDp} dp", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Slider(
                                value = settings.arcRadiusDp.toFloat(),
                                onValueChange = { radius ->
                                    settingsRepo.updateSettings { it.copy(arcRadiusDp = radius.toInt()) }
                                },
                                valueRange = 100f..180f,
                                steps = 7
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Auto Dismiss Seconds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Auto-Dismiss Timeout", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text("${settings.autoDismissSeconds} sec", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Slider(
                                value = settings.autoDismissSeconds.toFloat(),
                                onValueChange = { sec ->
                                    settingsRepo.updateSettings { it.copy(autoDismissSeconds = sec.toInt()) }
                                },
                                valueRange = 4f..25f,
                                steps = 6
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Arc Design Pattern Selector
                            Text(
                                text = "Radial Arc Design Pattern",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Choose your preferred layout geometry, guide visuals, and ergonomics",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                com.screenarc.app.model.ArcDesignStyle.values().forEach { style ->
                                    val isSelected = settings.arcDesignStyle == style
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                settingsRepo.updateSettings { it.copy(arcDesignStyle = style) }
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = when (style) {
                                                    com.screenarc.app.model.ArcDesignStyle.ORBIT -> "✦ Orbit"
                                                    com.screenarc.app.model.ArcDesignStyle.FAN -> "⌓ Fan"
                                                    com.screenarc.app.model.ArcDesignStyle.DIAL -> "🝯 Dial"
                                                },
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = when (style) {
                                                    com.screenarc.app.model.ArcDesignStyle.ORBIT -> "360° Ring"
                                                    com.screenarc.app.model.ArcDesignStyle.FAN -> "180° Fan"
                                                    com.screenarc.app.model.ArcDesignStyle.DIAL -> "Sectors"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Visual Themes & Expressive Palettes Card (DropSauce Inspired)
        item {
            var themesExpanded by remember { mutableStateOf(false) }
            var selectedThemeCategory by remember { mutableStateOf(ThemeCategory.ALL) }
            val themesArrowRotation by animateFloatAsState(
                targetValue = if (themesExpanded) 180f else 0f,
                animationSpec = tween(300),
                label = "ThemesArrow"
            )
            GlassElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { themesExpanded = !themesExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Visual Themes & Palettes",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (themesExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp).rotate(themesArrowRotation)
                        )
                    }

                    AnimatedVisibility(
                        visible = themesExpanded,
                        enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Choose from Material 3 Expressive theme color schemes, Glassmorphism styles, and light/dark palettes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // UI Design Style Toggle: Material UI vs Glassmorphism
                            Text(
                                text = "Design Style",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (settings.uiDesignStyle == UiDesignStyle.MATERIAL) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = if (settings.uiDesignStyle == UiDesignStyle.MATERIAL) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            settingsRepo.updateSettings { it.copy(uiDesignStyle = UiDesignStyle.MATERIAL) }
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Material 3",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (settings.uiDesignStyle == UiDesignStyle.MATERIAL) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Expressive & Clean",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (settings.uiDesignStyle == UiDesignStyle.GLASSMORPHISM) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = if (settings.uiDesignStyle == UiDesignStyle.GLASSMORPHISM) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            settingsRepo.updateSettings { it.copy(uiDesignStyle = UiDesignStyle.GLASSMORPHISM) }
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Glassmorphism",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (settings.uiDesignStyle == UiDesignStyle.GLASSMORPHISM) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Frosted Translucent",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Theme Category Filter Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    ThemeCategory.ALL to "All",
                                    ThemeCategory.LIGHT to "Light",
                                    ThemeCategory.DARK to "Dark",
                                    ThemeCategory.GLASS to "Glass"
                                ).forEach { (cat, label) ->
                                    val isCatSelected = selectedThemeCategory == cat
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCatSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedThemeCategory = cat }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isCatSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val filteredPresets = remember(selectedThemeCategory) {
                                AppThemePreset.values().filter { preset ->
                                    when (selectedThemeCategory) {
                                        ThemeCategory.ALL -> true
                                        ThemeCategory.LIGHT -> preset.isLightOnly
                                        ThemeCategory.DARK -> preset.isDarkOnly || (!preset.isLightOnly && !preset.isGlass)
                                        ThemeCategory.GLASS -> preset.isGlass
                                    }
                                }
                            }

                            // Vertically scrollable preset list with max height
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                filteredPresets.forEach { preset ->
                                    val isSelected = settings.themePreset == preset
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                settingsRepo.updateSettings { it.copy(themePreset = preset) }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = {
                                                        settingsRepo.updateSettings { it.copy(themePreset = preset) }
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = preset.displayName,
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                            ),
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (preset.isGlass) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = MaterialTheme.colorScheme.secondaryContainer
                                                            ) {
                                                                Text(
                                                                    text = "Glass",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        } else if (preset.isLightOnly) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                                            ) {
                                                                Text(
                                                                    text = "Light",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        } else if (preset.isDarkOnly) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                                            ) {
                                                                Text(
                                                                    text = "Dark",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = preset.subtitle,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Color preview swatches
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(preset.primaryColorHex))
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(preset.secondaryColorHex))
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(preset.surfaceColorHex))
                                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Behavior & Motion Card
        item {
            var motionExpanded by remember { mutableStateOf(false) }
            val motionArrowRotation by animateFloatAsState(
                targetValue = if (motionExpanded) 180f else 0f,
                animationSpec = tween(300),
                label = "MotionArrow"
            )
            GlassElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { motionExpanded = !motionExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Animation, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Motion & Color Extraction",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (motionExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp).rotate(motionArrowRotation)
                        )
                    }

                    AnimatedVisibility(
                        visible = motionExpanded,
                        enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Dynamic Palette Extraction
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Dynamic Palette Theming", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    }
                                    Text(
                                        text = "Tint arc category chips dynamically using colors extracted from the screenshot preview",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.dynamicPaletteEnabled,
                                    onCheckedChange = { isChecked ->
                                        settingsRepo.updateSettings { it.copy(dynamicPaletteEnabled = isChecked) }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Haptic Feedback
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Haptic Feedback on Drag & Snap", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    }
                                    Text(
                                        text = "Vibrate subtly when hovering over or snapping to a category target chip",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.hapticFeedbackEnabled,
                                    onCheckedChange = { isChecked ->
                                        settingsRepo.updateSettings { it.copy(hapticFeedbackEnabled = isChecked) }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Auto Expand Arc
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Expand Arc on Detection", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        text = "Immediately unfold the radial menu when a new screenshot is detected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.autoExpandOnCapture,
                                    onCheckedChange = { isChecked ->
                                        settingsRepo.updateSettings { it.copy(autoExpandOnCapture = isChecked) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Reliability & System Health Diagnostics Card
        item {
            val context = LocalContext.current
            val hasOverlayPermission = remember(settings) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
            }
            val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
            val isBatteryIgnoring = remember(settings) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                } else true
            }
            val hasNotificationPermission = remember(settings) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true
            }

            var reliabilityExpanded by remember { mutableStateOf(false) }
            val reliabilityArrowRotation by animateFloatAsState(
                targetValue = if (reliabilityExpanded) 180f else 0f,
                animationSpec = tween(300),
                label = "ReliabilityArrow"
            )

            GlassElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { reliabilityExpanded = !reliabilityExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reliability & Diagnostics",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (reliabilityExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp).rotate(reliabilityArrowRotation)
                        )
                    }

                    AnimatedVisibility(
                        visible = reliabilityExpanded,
                        enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Ensure background service and listeners have necessary permissions to detect and organize screenshots seamlessly across reboots and battery saver modes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Background Service Toggle
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Overlay Background Service", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        }
                                        Text(
                                            text = if (settings.isServiceEnabled) "Active • Listening for new screenshots" else "Inactive • Screenshots will not show overlay",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (settings.isServiceEnabled) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = settings.isServiceEnabled,
                                        onCheckedChange = { isChecked ->
                                            settingsRepo.updateSettings { it.copy(isServiceEnabled = isChecked) }
                                            if (isChecked) {
                                                OverlayService.startService(context)
                                            } else {
                                                val stopIntent = Intent(context, OverlayService::class.java).apply {
                                                    action = OverlayService.ACTION_STOP_SERVICE
                                                }
                                                context.startService(stopIntent)
                                            }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 1. Overlay Permission Check
                            DiagnosticItemRow(
                                title = "Draw Over Apps (Overlay)",
                                subtitle = if (hasOverlayPermission) "Granted • Radial arc can float over any app" else "Missing • Required to show radial overlay",
                                isOk = hasOverlayPermission,
                                actionLabel = "Grant",
                                onAction = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                        context.startActivity(intent)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 2. Battery Optimization Check
                            DiagnosticItemRow(
                                title = "Battery Optimization",
                                subtitle = if (isBatteryIgnoring) "Unrestricted • System will not kill background listener" else "Optimized • System may delay detection when sleeping",
                                isOk = isBatteryIgnoring,
                                actionLabel = "Exempt",
                                onAction = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        try {
                                            val intent = Intent(
                                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                Uri.parse("package:${context.packageName}")
                                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(fallback)
                                        }
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 3. Notification Permission Check
                            DiagnosticItemRow(
                                title = "Foreground Notifications",
                                subtitle = if (hasNotificationPermission) "Granted • Keeps service persistent and stable" else "Disabled • Required for foreground reliability",
                                isOk = hasNotificationPermission,
                                actionLabel = "Settings",
                                onAction = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(details)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Quick Test / Demo Trigger Button
                            OutlinedButton(
                                onClick = {
                                    val demoIntent = Intent(context, OverlayService::class.java).apply {
                                        action = OverlayService.ACTION_TRIGGER_DEMO
                                        putExtra(OverlayService.EXTRA_DEMO_LABEL, "Live Test Screenshot")
                                        putExtra(OverlayService.EXTRA_DEMO_COLOR, 0xFF6750A4.toInt())
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(demoIntent)
                                    } else {
                                        context.startService(demoIntent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Trigger Simulated Screenshot (Test Overlay)")
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Rule Dialog
    if (showCreateRuleDialog || ruleToEdit != null) {
        val existing = ruleToEdit
        RuleEditorDialog(
            initialRule = existing,
            categories = categories,
            onDismiss = {
                showCreateRuleDialog = false
                ruleToEdit = null
            },
            onSave = { updatedRule ->
                searchViewModel?.saveRule(updatedRule)
                showCreateRuleDialog = false
                ruleToEdit = null
            }
        )
    }

    // Paste Backup JSON Dialog
    if (showPasteJsonDialog) {
        AlertDialog(
            onDismissRequest = { showPasteJsonDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Backup JSON")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Paste your exported configuration JSON below to restore categories, rules, and preferences:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pastedJsonText,
                        onValueChange = { pastedJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        label = { Text("Backup JSON") },
                        placeholder = { Text("{\n  \"version\": 1,\n  \"rules\": [...]\n}") },
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pastedJsonText.isNotBlank()) {
                            searchViewModel?.importConfigurationBackup(pastedJsonText, settingsRepo)
                            showPasteJsonDialog = false
                            pastedJsonText = ""
                        }
                    },
                    enabled = pastedJsonText.isNotBlank()
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteJsonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleItemCard(
    rule: AutoCategorizationRule,
    categories: List<CategoryUiModel>,
    isRuleRunning: Boolean,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val targetCat = categories.find { it.id == rule.targetCategoryId }
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    var isRuleExpanded by remember { mutableStateOf(false) }
    val ruleArrowRotation by animateFloatAsState(
        targetValue = if (isRuleExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "RuleArrow"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = if (rule.isEnabled) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Priority Badge + Rule Name + Switch + Expand Arrow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isRuleExpanded = !isRuleExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Priority Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "#${rule.priority + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (rule.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = onToggle
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isRuleExpanded) "Collapse rule" else "Expand rule",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(ruleArrowRotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = isRuleExpanded,
                enter = expandVertically(tween(200)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 2: Target Category Pill & Filter Flags
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Target Category Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = targetCat?.composeColor?.copy(alpha = 0.2f) ?: MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(targetCat?.composeColor ?: MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = targetCat?.name ?: rule.targetCategoryName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (rule.matchWholeWord) {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                Text("Whole-word", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                        if (rule.isRegexEnabled) {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text("Regex", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                        if (rule.onlyNewScreenshots) {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("Unorganized Only", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Primary Keywords chips
                    if (rule.primaryKeywords.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                text = "PRIMARY:",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                rule.primaryKeywords.forEach { kw ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "#$kw",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Secondary Keywords chips
                    if (rule.secondaryKeywords.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                text = "SECONDARY (≥${rule.secondaryThreshold}):",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                rule.secondaryKeywords.forEach { kw ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = "#$kw",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 4: Rule Metadata (Left) + Actions (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (rule.lastRunTimestamp != null)
                                "Matched: ${rule.matchCount} • ${dateFormat.format(Date(rule.lastRunTimestamp))}"
                            else
                                "Matched: ${rule.matchCount} • Not run yet",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = onMoveUp,
                                enabled = canMoveUp,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("▲", fontSize = 11.sp, color = if (canMoveUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            }

                            IconButton(
                                onClick = onMoveDown,
                                enabled = canMoveDown,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("▼", fontSize = 11.sp, color = if (canMoveDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            }

                            FilledTonalIconButton(
                                onClick = onRun,
                                enabled = !isRuleRunning && rule.isEnabled,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Run Rule",
                                    modifier = Modifier.size(15.dp),
                                    tint = if (rule.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit rule", modifier = Modifier.size(15.dp))
                            }

                            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete rule", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorDialog(
    initialRule: AutoCategorizationRule?,
    categories: List<CategoryUiModel>,
    onDismiss: () -> Unit,
    onSave: (AutoCategorizationRule) -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var primaryKeywordsString by remember { mutableStateOf(initialRule?.primaryKeywords?.joinToString(", ") ?: "") }
    var secondaryKeywordsString by remember { mutableStateOf(initialRule?.secondaryKeywords?.joinToString(", ") ?: "") }
    var selectedCategoryId by remember {
        mutableStateOf(initialRule?.targetCategoryId ?: categories.firstOrNull()?.id ?: 1L)
    }
    var priority by remember { mutableStateOf(initialRule?.priority ?: 0) }
    var secondaryThreshold by remember { mutableStateOf(initialRule?.secondaryThreshold ?: 2) }
    var matchWholeWord by remember { mutableStateOf(initialRule?.matchWholeWord ?: false) }
    var isRegexEnabled by remember { mutableStateOf(initialRule?.isRegexEnabled ?: false) }
    var onlyNewScreenshots by remember { mutableStateOf(initialRule?.onlyNewScreenshots ?: true) }
    var isEnabled by remember { mutableStateOf(initialRule?.isEnabled ?: true) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    // Test this rule sample text
    var sampleTestText by remember { mutableStateOf("") }

    val selectedCategory = categories.find { it.id == selectedCategoryId } ?: categories.firstOrNull()

    // Transient rule instance for live testing
    val currentPreviewRule = remember(
        name, primaryKeywordsString, secondaryKeywordsString, selectedCategoryId,
        priority, secondaryThreshold, matchWholeWord, isRegexEnabled, onlyNewScreenshots, isEnabled
    ) {
        val pList = primaryKeywordsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val sList = secondaryKeywordsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        AutoCategorizationRule(
            id = initialRule?.id ?: "preview",
            name = name,
            primaryKeywords = pList,
            secondaryKeywords = sList,
            targetCategoryId = selectedCategory?.id ?: 1L,
            targetCategoryName = selectedCategory?.name ?: "Category",
            priority = priority,
            isEnabled = isEnabled,
            onlyNewScreenshots = onlyNewScreenshots,
            matchWholeWord = matchWholeWord,
            isRegexEnabled = isRegexEnabled,
            secondaryThreshold = secondaryThreshold
        )
    }

    val testEvaluation = remember(currentPreviewRule, sampleTestText) {
        if (sampleTestText.isNotBlank()) {
            currentPreviewRule.evaluateText(sampleTestText)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialRule == null) "Create Priority Categorization Rule" else "Edit Categorization Rule")
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Rule Name") },
                        placeholder = { Text("e.g. Movie & Cinema, Tech Repos") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Category Selector
                item {
                    Text("Target Category", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))

                    ExposedDropdownMenuBox(
                        expanded = categoryDropdownExpanded,
                        onExpandedChange = { categoryDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "Select Category",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )

                        ExposedDropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(cat.composeColor)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(cat.name)
                                        }
                                    },
                                    onClick = {
                                        selectedCategoryId = cat.id
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Primary Keywords (High Priority)
                item {
                    OutlinedTextField(
                        value = primaryKeywordsString,
                        onValueChange = { primaryKeywordsString = it },
                        label = { Text("Primary Keywords (High Priority)") },
                        placeholder = { Text("e.g. movie, netflix, cinema, imdb") },
                        supportingText = { Text("Any match routes to this category immediately", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Secondary Keywords (Supporting signals)
                item {
                    OutlinedTextField(
                        value = secondaryKeywordsString,
                        onValueChange = { secondaryKeywordsString = it },
                        label = { Text("Secondary Keywords (Supporting Signals)") },
                        placeholder = { Text("e.g. trailer, director, cast, streaming") },
                        supportingText = { Text("Supporting matches break ties and route if threshold met", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Secondary Threshold Slider
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Secondary Match Threshold: $secondaryThreshold", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        Text("Requires ≥$secondaryThreshold matches", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = secondaryThreshold.toFloat(),
                        onValueChange = { secondaryThreshold = it.toInt() },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                }

                // Options switches
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Whole-word matching", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                            Text("Matches exact word boundaries (avoids partial substrings)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = matchWholeWord, onCheckedChange = { matchWholeWord = it })
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Regex / pattern mode", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                            Text("Evaluate keywords as regular expressions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isRegexEnabled, onCheckedChange = { isRegexEnabled = it })
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Process only new screenshots", style = MaterialTheme.typography.bodySmall)
                        Checkbox(checked = onlyNewScreenshots, onCheckedChange = { onlyNewScreenshots = it })
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Rule Enabled", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                    }
                }

                // -------------------------------------------------------------
                // Interactive "Test this rule" Sandbox Box
                // -------------------------------------------------------------
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Test this rule (Live Sandbox)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Type or paste sample OCR text to see if and why this rule matches.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = sampleTestText,
                                onValueChange = { sampleTestText = it },
                                placeholder = { Text("Paste sample text here, e.g., 'Watch Inception on Netflix tonight'...", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )

                            if (testEvaluation != null) {
                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (testEvaluation.isAssigned) Color(0x224CAF50) else Color(0x22E53935),
                                    border = BorderStroke(1.dp, if (testEvaluation.isAssigned) Color(0xFF4CAF50) else Color(0xFFE53935)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (testEvaluation.isAssigned) "✔ MATCH -> Assigned to ${selectedCategory?.name}" else "✖ NO MATCH",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (testEvaluation.isAssigned) Color(0xFF2E7D32) else Color(0xFFC62828)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = testEvaluation.reason,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (testEvaluation.matchedPrimaryKeywords.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Matched Primaries: ${testEvaluation.matchedPrimaryKeywords.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (testEvaluation.matchedSecondaryKeywords.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Matched Secondaries: ${testEvaluation.matchedSecondaryKeywords.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pList = primaryKeywordsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val sList = secondaryKeywordsString.split(",").map { it.trim() }.filter { it.isNotBlank() }

                    val finalRule = AutoCategorizationRule(
                        id = initialRule?.id ?: java.util.UUID.randomUUID().toString(),
                        name = if (name.isBlank()) "Categorization Rule" else name.trim(),
                        primaryKeywords = pList,
                        secondaryKeywords = sList,
                        targetCategoryId = selectedCategory?.id ?: 1L,
                        targetCategoryName = selectedCategory?.name ?: "Tech",
                        priority = priority,
                        isEnabled = isEnabled,
                        onlyNewScreenshots = onlyNewScreenshots,
                        matchWholeWord = matchWholeWord,
                        isRegexEnabled = isRegexEnabled,
                        secondaryThreshold = secondaryThreshold,
                        matchCount = initialRule?.matchCount ?: 0,
                        lastRunTimestamp = initialRule?.lastRunTimestamp
                    )
                    onSave(finalRule)
                },
                enabled = name.isNotBlank() && (primaryKeywordsString.isNotBlank() || secondaryKeywordsString.isNotBlank())
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DiagnosticItemRow(
    title: String,
    subtitle: String,
    isOk: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isOk) Color(0xFF00C853) else Color(0xFFFFB300),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isOk) {
                Spacer(modifier = Modifier.width(10.dp))
                FilledTonalButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


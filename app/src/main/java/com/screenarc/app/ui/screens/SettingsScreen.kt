package com.screenarc.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenarc.app.data.AppSettingsRepository
import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.ui.screens.settings.BackupRestoreCard
import com.screenarc.app.ui.screens.settings.BulkOcrSection
import com.screenarc.app.ui.screens.settings.DiagnosticsSection
import com.screenarc.app.ui.screens.settings.OcrSettingsSection
import com.screenarc.app.ui.screens.settings.OverlayControlsSection
import com.screenarc.app.ui.screens.settings.RuleEditorDialog
import com.screenarc.app.ui.screens.settings.RulesSettingsSection
import com.screenarc.app.ui.screens.settings.ThemePickerSection
import com.screenarc.app.ui.theme.GlassElevatedCard
import com.screenarc.app.viewmodel.SearchViewModel

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

    var ruleToEdit by remember { mutableStateOf<AutoCategorizationRule?>(null) }
    var showCreateRuleDialog by remember { mutableStateOf(false) }
    var showPasteJsonDialog by remember { mutableStateOf(false) }
    var pastedJsonText by remember { mutableStateOf("") }

    // Persistent Tab & Section Expanded States (survives re-renders, navigation, and page refreshes)
    var rulesExpanded by rememberSaveable { mutableStateOf(settingsRepo.isSectionExpanded("rules", true)) }
    var backupExpanded by rememberSaveable { mutableStateOf(settingsRepo.isSectionExpanded("backup", false)) }
    var ocrExpanded by rememberSaveable { mutableStateOf(settingsRepo.isSectionExpanded("ocr", true)) }
    var bulkOcrExpanded by rememberSaveable { mutableStateOf(settingsRepo.isSectionExpanded("bulk_ocr", true)) }
    var overlayExpanded by rememberSaveable { mutableStateOf(settingsRepo.isSectionExpanded("overlay", false)) }
    var themesExpanded by rememberSaveable { mutableStateOf(settingsRepo.isSectionExpanded("themes", false)) }
    var motionExpanded by rememberSaveable { mutableStateOf(settingsRepo.isSectionExpanded("motion", false)) }
    var reliabilityExpanded by rememberSaveable { mutableStateOf(settingsRepo.isSectionExpanded("reliability", false)) }

    val allExpanded = rulesExpanded && backupExpanded && ocrExpanded && bulkOcrExpanded &&
            overlayExpanded && themesExpanded && motionExpanded && reliabilityExpanded

    val toggleAllTabs: () -> Unit = {
        val targetState = !allExpanded
        rulesExpanded = targetState
        backupExpanded = targetState
        ocrExpanded = targetState
        bulkOcrExpanded = targetState
        overlayExpanded = targetState
        themesExpanded = targetState
        motionExpanded = targetState
        reliabilityExpanded = targetState

        settingsRepo.setAllSectionsExpanded(
            listOf("rules", "backup", "ocr", "bulk_ocr", "overlay", "themes", "motion", "reliability"),
            targetState
        )
    }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Preferences & Rules",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Configure auto-categorization rules, OCR language models, overlay behavior, and styling",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = toggleAllTabs,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (allExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                        contentDescription = if (allExpanded) "Collapse All Configurations" else "Expand All Configurations",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (allExpanded) "Collapse All" else "Expand All",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 1. Rules-Based Category Allocation Card
        searchViewModel?.let { vm ->
            item {
                RulesSettingsSection(
                    expanded = rulesExpanded,
                    onToggleExpanded = {
                        rulesExpanded = !rulesExpanded
                        settingsRepo.setSectionExpanded("rules", rulesExpanded)
                    },
                    rules = rules,
                    categories = categories,
                    isRuleRunning = uiState?.isRuleRunning == true,
                    allExpanded = allExpanded,
                    onToggleAllTabs = toggleAllTabs,
                    onCreateRule = { showCreateRuleDialog = true },
                    onEditRule = { ruleToEdit = it },
                    searchViewModel = vm,
                    settings = settings,
                    onUpdateSettings = { settingsRepo.updateSettings(it) }
                )
            }
        }

        // 2. Backup & Restore Configurations Card (Rules & Categories)
        item {
            BackupRestoreCard(
                isExpanded = backupExpanded,
                onToggleExpand = {
                    backupExpanded = !backupExpanded
                    settingsRepo.setSectionExpanded("backup", backupExpanded)
                },
                rules = rules,
                categories = categories,
                settings = settings,
                onImportBackupJson = { json ->
                    searchViewModel?.importConfigurationBackup(json, settingsRepo)
                },
                onOpenPasteJsonDialog = { showPasteJsonDialog = true }
            )
        }

        // 3. On-Device OCR Settings Card (Multilingual & Script Support)
        item {
            OcrSettingsSection(
                expanded = ocrExpanded,
                onToggleExpanded = {
                    ocrExpanded = !ocrExpanded
                    settingsRepo.setSectionExpanded("ocr", ocrExpanded)
                },
                settings = settings,
                onUpdateSettings = { transform -> settingsRepo.updateSettings(transform) }
            )
        }

        // 4. Bulk OCR Processing Card
        searchViewModel?.let { vm ->
            item {
                BulkOcrSection(
                    expanded = bulkOcrExpanded,
                    onToggleExpanded = {
                        bulkOcrExpanded = !bulkOcrExpanded
                        settingsRepo.setSectionExpanded("bulk_ocr", bulkOcrExpanded)
                    },
                    settings = settings,
                    batchProgress = batchProgress,
                    searchViewModel = vm
                )
            }
        }

        // 5. Overlay Appearance & Motion Settings Card
        item {
            OverlayControlsSection(
                overlayExpanded = overlayExpanded,
                onToggleOverlayExpand = {
                    overlayExpanded = !overlayExpanded
                    settingsRepo.setSectionExpanded("overlay", overlayExpanded)
                },
                motionExpanded = motionExpanded,
                onToggleMotionExpand = {
                    motionExpanded = !motionExpanded
                    settingsRepo.setSectionExpanded("motion", motionExpanded)
                },
                settings = settings,
                settingsRepo = settingsRepo
            )
        }

        // 6. Visual Themes & Palettes
        item {
            ThemePickerSection(
                isExpanded = themesExpanded,
                onToggleExpand = {
                    themesExpanded = !themesExpanded
                    settingsRepo.setSectionExpanded("themes", themesExpanded)
                },
                settings = settings,
                settingsRepo = settingsRepo
            )
        }

        // 7. Duplicate Screenshot Finder Configuration Card
        item {
            GlassElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Duplicate Screenshot Finder",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Detect and clean near-duplicate screenshots on Home",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.duplicateFinderEnabled,
                            onCheckedChange = { isEnabled ->
                                settingsRepo.updateSettings { it.copy(duplicateFinderEnabled = isEnabled) }
                            }
                        )
                    }

                    if (settings.duplicateFinderEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "When enabled, scans for visual difference hashes (dHash) and OCR text similarity within 5-minute capture windows to find redundant shots.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // 8. Reliability & System Health Diagnostics Card
        item {
            DiagnosticsSection(
                expanded = reliabilityExpanded,
                onToggleExpanded = {
                    reliabilityExpanded = !reliabilityExpanded
                    settingsRepo.setSectionExpanded("reliability", reliabilityExpanded)
                },
                settings = settings,
                onUpdateSettings = { transform -> settingsRepo.updateSettings(transform) }
            )
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

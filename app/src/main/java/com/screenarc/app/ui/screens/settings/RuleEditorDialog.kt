package com.screenarc.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenarc.app.model.AutoCategorizationRule
import com.screenarc.app.model.CategoryUiModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorDialog(
    initialRule: AutoCategorizationRule?,
    categories: List<CategoryUiModel>,
    onDismiss: () -> Unit,
    onSave: (AutoCategorizationRule) -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }

    // Combine legacy secondary keywords into keywords chips
    val initialKeywords = remember(initialRule) {
        val list = mutableListOf<String>()
        if (initialRule != null) {
            list.addAll(initialRule.primaryKeywords)
            // Fold secondary keywords into keywords list if present
            initialRule.secondaryKeywords.forEach { kw ->
                if (!list.contains(kw)) list.add(kw)
            }
        }
        list
    }

    val keywordsList = remember { mutableStateListOf<String>().apply { addAll(initialKeywords) } }
    var keywordInputText by remember { mutableStateOf("") }
    var optionalRegexPattern by remember {
        mutableStateOf(if (initialRule?.isRegexEnabled == true) initialRule.primaryKeywords.firstOrNull() ?: "" else "")
    }
    var isRegexMode by remember { mutableStateOf(initialRule?.isRegexEnabled ?: false) }

    var selectedCategoryId by remember {
        mutableStateOf(initialRule?.targetCategoryId ?: categories.firstOrNull()?.id ?: 1L)
    }
    var priority by remember { mutableStateOf(initialRule?.priority ?: 0) }
    var matchWholeWord by remember { mutableStateOf(initialRule?.matchWholeWord ?: false) }
    var onlyNewScreenshots by remember { mutableStateOf(initialRule?.onlyNewScreenshots ?: true) }
    var isEnabled by remember { mutableStateOf(initialRule?.isEnabled ?: true) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    // Test this rule sample text
    var sampleTestText by remember { mutableStateOf("") }

    val selectedCategory = categories.find { it.id == selectedCategoryId } ?: categories.firstOrNull()

    // Transient rule instance for live testing
    val currentPreviewRule = remember(
        name, keywordsList.toList(), optionalRegexPattern, isRegexMode, selectedCategoryId,
        priority, matchWholeWord, onlyNewScreenshots, isEnabled
    ) {
        val finalKeywords = if (isRegexMode && optionalRegexPattern.isNotBlank()) {
            listOf(optionalRegexPattern.trim())
        } else {
            keywordsList.toList()
        }
        AutoCategorizationRule(
            id = initialRule?.id ?: "preview",
            name = name,
            primaryKeywords = finalKeywords,
            secondaryKeywords = emptyList(),
            targetCategoryId = selectedCategory?.id ?: 1L,
            targetCategoryName = selectedCategory?.name ?: "Category",
            priority = priority,
            isEnabled = isEnabled,
            onlyNewScreenshots = onlyNewScreenshots,
            matchWholeWord = matchWholeWord,
            isRegexEnabled = isRegexMode
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
            Text(if (initialRule == null) "Create Auto-Categorization Rule" else "Edit Auto-Categorization Rule")
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
                        placeholder = { Text("e.g. Movies & TV, Tech Repos") },
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

                // Regex mode toggle
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Optional Regex Pattern Mode", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                            Text("Use regex pattern instead of keyword chips", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isRegexMode, onCheckedChange = { isRegexMode = it })
                    }
                }

                if (isRegexMode) {
                    item {
                        OutlinedTextField(
                            value = optionalRegexPattern,
                            onValueChange = { optionalRegexPattern = it },
                            label = { Text("Regex Pattern") },
                            placeholder = { Text("e.g. (invoice|receipt) #[0-9]+") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("Evaluates matches against OCR text using standard regex", fontSize = 10.sp) }
                        )
                    }
                } else {
                    // Keywords Chips input
                    item {
                        Column {
                            Text(
                                text = "Keywords (Chips)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = keywordInputText,
                                    onValueChange = { keywordInputText = it },
                                    label = { Text("Add Keyword") },
                                    placeholder = { Text("e.g. netflix, invoice") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val kw = keywordInputText.trim()
                                        if (kw.isNotBlank() && !keywordsList.contains(kw)) {
                                            keywordsList.add(kw)
                                            keywordInputText = ""
                                        }
                                    },
                                    enabled = keywordInputText.isNotBlank()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Keyword")
                                }
                            }

                            if (keywordsList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    keywordsList.forEach { kw ->
                                        InputChip(
                                            selected = true,
                                            onClick = { keywordsList.remove(kw) },
                                            label = { Text(kw) },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove $kw",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Whole-word matching switch
                if (!isRegexMode) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Whole-word matching", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                Text("Match exact word boundaries to avoid partial substrings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = matchWholeWord, onCheckedChange = { matchWholeWord = it })
                        }
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Process only unorganized screenshots", style = MaterialTheme.typography.bodySmall)
                        Checkbox(checked = onlyNewScreenshots, onCheckedChange = { onlyNewScreenshots = it })
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable rule", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                    }
                }

                // Priority Slider
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Execution Priority: $priority", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        Text(if (priority == 0) "Highest" else "Lower", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = priority.toFloat(),
                        onValueChange = { priority = it.toInt() },
                        valueRange = 0f..10f,
                        steps = 9
                    )
                }

                // Live Simulator / Tester
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Live Rule Tester",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = sampleTestText,
                                onValueChange = { sampleTestText = it },
                                label = { Text("Sample Text") },
                                placeholder = { Text("Paste sample OCR text here to test rule matching...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )

                            if (testEvaluation != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (testEvaluation.isAssigned) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    border = BorderStroke(1.dp, if (testEvaluation.isAssigned) Color(0xFF4CAF50) else Color(0xFFEF5350)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = if (testEvaluation.isAssigned) "✔ MATCH -> Assigned to ${selectedCategory?.name}" else "✖ NO MATCH",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (testEvaluation.isAssigned) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = testEvaluation.reason,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val hasValidContent = if (isRegexMode) {
                optionalRegexPattern.isNotBlank()
            } else {
                keywordsList.isNotEmpty()
            }

            Button(
                onClick = {
                    val finalKeywords = if (isRegexMode && optionalRegexPattern.isNotBlank()) {
                        listOf(optionalRegexPattern.trim())
                    } else {
                        keywordsList.toList()
                    }

                    val finalRule = AutoCategorizationRule(
                        id = initialRule?.id ?: java.util.UUID.randomUUID().toString(),
                        name = if (name.isBlank()) "Categorization Rule" else name.trim(),
                        primaryKeywords = finalKeywords,
                        secondaryKeywords = emptyList(),
                        targetCategoryId = selectedCategory?.id ?: 1L,
                        targetCategoryName = selectedCategory?.name ?: "Tech",
                        priority = priority,
                        isEnabled = isEnabled,
                        onlyNewScreenshots = onlyNewScreenshots,
                        matchWholeWord = matchWholeWord,
                        isRegexEnabled = isRegexMode,
                        matchCount = initialRule?.matchCount ?: 0,
                        lastRunTimestamp = initialRule?.lastRunTimestamp
                    )
                    onSave(finalRule)
                },
                enabled = name.isNotBlank() && hasValidContent
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

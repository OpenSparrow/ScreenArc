package com.screenarc.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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

                // Interactive "Test this rule" Sandbox Box
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

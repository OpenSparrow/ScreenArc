package com.screenarc.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import com.screenarc.app.RedactionEditorActivity
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FilterList
import com.screenarc.app.ui.components.EmptyStateView
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.screenarc.app.data.OcrRepositoryImpl
import com.screenarc.app.model.CategoryUiModel
import com.screenarc.app.model.OcrBlock
import com.screenarc.app.model.ScreenshotUiModel
import com.screenarc.app.ui.dialogs.OcrOverlayImageViewer
import com.screenarc.app.ui.dialogs.OcrResultDialog
import com.screenarc.app.viewmodel.SearchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotsScreen(
    searchViewModel: SearchViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val query by searchViewModel.queryFlow.collectAsState()
    val uiState by searchViewModel.uiState.collectAsState()
    val results by searchViewModel.searchResults.collectAsState()
    val categories by searchViewModel.categories.collectAsState()

    var screenshotForOcrModal by remember { mutableStateOf<ScreenshotUiModel?>(null) }
    var screenshotForImageViewer by remember { mutableStateOf<ScreenshotUiModel?>(null) }
    var screenshotForMoveCategory by remember { mutableStateOf<ScreenshotUiModel?>(null) }
    var screenshotForCopyCategory by remember { mutableStateOf<ScreenshotUiModel?>(null) }
    var screenshotToDelete by remember { mutableStateOf<ScreenshotUiModel?>(null) }

    var showBulkMoveDialog by remember { mutableStateOf(false) }
    var showBulkCopyDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var pdfExportProgress by remember { mutableStateOf(0 to 0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar with Debouncing, Clear Button & Count
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.text.isEmpty()) {
                            Text(
                                text = "Search by filename, OCR text, category...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = query.text,
                            onValueChange = { searchViewModel.updateSearchText(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (query.text.isNotEmpty()) {
                        IconButton(
                            onClick = { searchViewModel.clearSearch() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Multi-selection mode toggle button
                    IconButton(
                        onClick = {
                            searchViewModel.setSelectionMode(!uiState.isSelectionMode)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = "Select mode",
                            tint = if (uiState.isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = query.categoryId == null,
                        onClick = { searchViewModel.setCategoryFilter(null) },
                        label = { Text("All Categories") }
                    )
                }
                items(categories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = query.categoryId == cat.id,
                        onClick = {
                            val newId = if (query.categoryId == cat.id) null else cat.id
                            searchViewModel.setCategoryFilter(newId)
                        },
                        label = { Text(cat.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(cat.composeColor)
                            )
                        }
                    )
                }
            }

            val batchProgress by searchViewModel.batchProgress.collectAsState()

            // Search options bar: OCR search toggle, batch OCR triggers, search count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.isSelectionMode && uiState.selectedUris.isNotEmpty())
                        "${uiState.selectedUris.size} selected / ${results.size} total"
                    else
                        "${results.size} result(s)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (uiState.isSelectionMode) {
                        // Selection Mode Actions
                        TextButton(
                            onClick = {
                                if (uiState.selectedUris.size == results.size) {
                                    searchViewModel.clearSelection()
                                } else {
                                    searchViewModel.selectAll()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (uiState.selectedUris.size == results.size) "Deselect All" else "Select All",
                                fontSize = 11.sp
                            )
                        }

                        TextButton(
                            onClick = { searchViewModel.setSelectionMode(false) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Done", fontSize = 11.sp)
                        }
                    } else {
                        // Process & Index All Images Action
                        if (!uiState.isOcrRunning && !batchProgress.isRunning) {
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { searchViewModel.processAndIndexAllImages() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Process All", fontSize = 11.sp)
                            }
                        }

                        // Run OCR for current search set
                        if (results.isNotEmpty() && !uiState.isOcrRunning && !batchProgress.isRunning) {
                            OutlinedButton(
                                onClick = { searchViewModel.runOcrForSearchResults() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("OCR Filtered", fontSize = 11.sp)
                            }
                        }

                        if (uiState.isOcrRunning || batchProgress.isRunning) {
                            Button(
                                onClick = { searchViewModel.cancelOcr() },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel OCR", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Detailed Batch Progress Card
            if (batchProgress.isRunning) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
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

                        androidx.compose.material3.LinearProgressIndicator(
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
                                onClick = { searchViewModel.cancelOcr() },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
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
            } else if (uiState.isOcrRunning) {
                // Single image OCR progress indicator bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Processing...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Info / Error Message banner
            uiState.infoMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { searchViewModel.clearInfoMessage() }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Results Grid
            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        title = if (query.text.isNotEmpty()) "No screenshots matched '${query.text}'" else "No screenshots found",
                        description = "Try searching by filename, detected text (e.g. 'movie', 'tech'), or clear filters.",
                        icon = Icons.Default.Search
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { it.uri }) { screenshot ->
                        val isSelected = uiState.selectedUris.contains(screenshot.uri)
                        ScreenshotCardItem(
                            screenshot = screenshot,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelection = { searchViewModel.toggleSelection(screenshot.uri) },
                            onDetectText = {
                                screenshotForOcrModal = screenshot
                                searchViewModel.runOcrForScreenshot(screenshot)
                            },
                            onViewImage = {
                                screenshotForImageViewer = screenshot
                            },
                            onMove = { screenshotForMoveCategory = screenshot },
                            onCopy = { screenshotForCopyCategory = screenshot },
                            onDelete = { screenshotToDelete = screenshot }
                        )
                    }
                }
            }
        }

        // Floating Bulk Action Bar at Bottom when items are selected
        AnimatedVisibility(
            visible = uiState.selectedUris.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${uiState.selectedUris.size} selected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Bulk operations",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Bulk Move Button
                        Button(
                            onClick = { showBulkMoveDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Move", fontSize = 12.sp)
                        }

                        // Export as PDF Button
                        OutlinedButton(
                            onClick = {
                                val selectedItems = results.filter { uiState.selectedUris.contains(it.uri) }
                                if (selectedItems.isNotEmpty()) {
                                    isExportingPdf = true
                                    scope.launch {
                                        val pdfUri = com.screenarc.app.util.PdfExporter.exportScreenshotsToPdf(
                                            context = context,
                                            screenshots = selectedItems,
                                            onProgress = { cur, tot ->
                                                pdfExportProgress = cur to tot
                                            }
                                        )
                                        isExportingPdf = false
                                        if (pdfUri != null) {
                                            android.widget.Toast.makeText(context, "PDF saved to Downloads", android.widget.Toast.LENGTH_LONG).show()
                                            com.screenarc.app.util.PdfExporter.sharePdf(context, pdfUri)
                                        } else {
                                            android.widget.Toast.makeText(context, "Failed to create PDF", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isExportingPdf,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            if (isExportingPdf) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${pdfExportProgress.first}/${pdfExportProgress.second}", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF", fontSize = 12.sp)
                            }
                        }

                        // Export Expenses CSV (PRO Feature)
                        OutlinedButton(
                            onClick = {
                                val selectedItems = results.filter { uiState.selectedUris.contains(it.uri) }
                                val extractedExpenses = selectedItems.mapNotNull { item ->
                                    val text = item.recognizedText ?: return@mapNotNull null
                                    com.screenarc.app.util.ReceiptExpenseExtractor.extractExpense(
                                        rawText = text,
                                        screenshotId = item.id,
                                        screenshotUri = item.uri,
                                        fileName = item.displayName
                                    )
                                }
                                if (extractedExpenses.isNotEmpty()) {
                                    scope.launch {
                                        val csv = com.screenarc.app.util.ReceiptExpenseExtractor.exportToCsv(extractedExpenses)
                                        val uri = com.screenarc.app.util.ReceiptExpenseExtractor.saveCsvToDownloads(context, csv)
                                        if (uri != null) {
                                            android.widget.Toast.makeText(context, "Exported " + extractedExpenses.size + " expense(s) to CSV", android.widget.Toast.LENGTH_SHORT).show()
                                            com.screenarc.app.util.ReceiptExpenseExtractor.shareCsv(context, uri)
                                        }
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "No receipts detected in selected screenshots", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CSV", fontSize = 12.sp)
                        }

                        // Bulk Copy Button
                        OutlinedButton(
                            onClick = { showBulkCopyDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 12.sp)
                        }

                        // Bulk Delete Button
                        IconButton(
                            onClick = { showBulkDeleteDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                        }

                        // Clear Button
                        IconButton(
                            onClick = { searchViewModel.clearSelection() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }

    // OCR Result Modal Dialog
    screenshotForOcrModal?.let { item ->
        OcrResultDialog(
            screenshot = item,
            ocrResult = uiState.activeOcrResult,
            isLoading = uiState.isOcrRunning && uiState.activeOcrUri == item.uri,
            onRetry = { searchViewModel.runOcrForScreenshot(item) },
            onAddToSearchIndex = {
                searchViewModel.dismissOcrModal()
                screenshotForOcrModal = null
            },
            onDismiss = {
                searchViewModel.dismissOcrModal()
                screenshotForOcrModal = null
            },
            onViewOverlay = {
                screenshotForImageViewer = item
            }
        )
    }

    // Fullscreen Image Viewer with Visual OCR Boxes
    screenshotForImageViewer?.let { item ->
        val blocks = if (uiState.activeOcrResult?.screenshotUri == item.uri && !uiState.activeOcrResult?.blocks.isNullOrEmpty()) {
            uiState.activeOcrResult!!.blocks
        } else if (item.blocks.isNotEmpty()) {
            item.blocks
        } else {
            emptyList()
        }
        OcrOverlayImageViewer(
            screenshot = item,
            blocks = blocks,
            allScreenshots = results,
            searchQuery = query.text.ifBlank { null },
            onDismiss = { screenshotForImageViewer = null }
        )
    }

    // Bulk Move to Category Dialog
    if (showBulkMoveDialog) {
        AlertDialog(
            onDismissRequest = { showBulkMoveDialog = false },
            title = { Text("Move ${uiState.selectedUris.size} Selected Screenshots") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    searchViewModel.moveSelectedToCategory(cat)
                                    showBulkMoveDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(cat.composeColor)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(cat.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBulkMoveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Bulk Copy to Category Dialog
    if (showBulkCopyDialog) {
        AlertDialog(
            onDismissRequest = { showBulkCopyDialog = false },
            title = { Text("Copy ${uiState.selectedUris.size} Selected Screenshots") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    searchViewModel.copySelectedToCategory(cat)
                                    showBulkCopyDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(cat.composeColor)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(cat.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBulkCopyDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete ${uiState.selectedUris.size} Screenshots") },
            text = { Text("Are you sure you want to delete ${uiState.selectedUris.size} selected screenshot(s) from storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        searchViewModel.deleteSelectedScreenshots()
                        showBulkDeleteDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Single Move to Category Dialog
    screenshotForMoveCategory?.let { item ->
        AlertDialog(
            onDismissRequest = { screenshotForMoveCategory = null },
            title = { Text("Move to Category") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    searchViewModel.moveToCategory(item, cat)
                                    screenshotForMoveCategory = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(cat.composeColor)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(cat.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { screenshotForMoveCategory = null }) { Text("Cancel") }
            }
        )
    }

    // Single Copy to Category Dialog
    screenshotForCopyCategory?.let { item ->
        AlertDialog(
            onDismissRequest = { screenshotForCopyCategory = null },
            title = { Text("Copy to Category") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    searchViewModel.copyToCategory(item, cat)
                                    screenshotForCopyCategory = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(cat.composeColor)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(cat.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { screenshotForCopyCategory = null }) { Text("Cancel") }
            }
        )
    }

    // Single Delete Confirmation
    screenshotToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { screenshotToDelete = null },
            title = { Text("Delete Screenshot") },
            text = { Text("Are you sure you want to delete '${item.displayName}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        searchViewModel.deleteScreenshot(item)
                        screenshotToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { screenshotToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScreenshotCardItem(
    screenshot: ScreenshotUiModel,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onDetectText: () -> Unit,
    onViewImage: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // OCR status badge styling
    val (statusLabel, statusColor, statusBg) = when (screenshot.ocrStatus) {
        "COMPLETED" -> Triple("Text Found", Color(0xFF2E7D32), Color(0xFFE8F5E9))
        "NO_TEXT" -> Triple("No Text", Color(0xFF616161), Color(0xFFF5F5F5))
        "PROCESSING" -> Triple("Processing...", Color(0xFF1565C0), Color(0xFFE3F2FD))
        "FAILED" -> Triple("Failed", Color(0xFFC62828), Color(0xFFFFEBEE))
        else -> Triple("Not Processed", Color(0xFFE65100), Color(0xFFFFF3E0))
    }

    val cardModifier = if (isSelected) {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else {
                        onViewImage()
                    }
                },
                onLongClick = {
                    onToggleSelection()
                }
            )
    } else {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else {
                        onViewImage()
                    }
                },
                onLongClick = {
                    onToggleSelection()
                }
            )
    }

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = cardModifier
    ) {
        Column {
            // Image Preview with OCR Badge & Selection Checkbox
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(screenshot.contentUri)
                        .size(720, 520)
                        .crossfade(true)
                        .build(),
                    contentDescription = screenshot.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // OCR Status Badge at Top-Left
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Selection Checkbox at Top-Right
                if (isSelectionMode || isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.TopEnd)
                            .size(30.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelection() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Category pill at Bottom-Left if categorized
                screenshot.categoryName?.let { catName ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.BottomStart)
                    ) {
                        Text(
                            text = catName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Info and Card Actions
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = screenshot.displayName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = dateFormat.format(Date(screenshot.dateAdded * 1000)),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Recognized text snippet if present
                if (!screenshot.recognizedText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${screenshot.recognizedText.trim()}\"",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Card actions: Detect Text & Overflow Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDetectText,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Detect Text", fontSize = 10.sp)
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit & Annotate") },
                                onClick = {
                                    showMenu = false
                                    val intent = RedactionEditorActivity.newIntent(context, screenshot, mode = RedactionEditorActivity.MODE_EDIT)
                                    context.startActivity(intent)
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Category") },
                                onClick = {
                                    showMenu = false
                                    onMove()
                                },
                                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy to Category") },
                                onClick = {
                                    showMenu = false
                                    onCopy()
                                },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }
        }
    }
}

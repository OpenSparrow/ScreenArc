package com.screenarc.app.stitch.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.screenarc.app.R
import com.screenarc.app.RedactionEditorActivity
import com.screenarc.app.model.ScreenshotUiModel
import com.screenarc.app.stitch.model.StitchConfig
import com.screenarc.app.stitch.model.StitchFormat
import com.screenarc.app.stitch.model.StitchImageItem
import com.screenarc.app.stitch.model.StitchMode
import com.screenarc.app.stitch.model.StitchSeam
import com.screenarc.app.stitch.model.StitchUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StitchScreen(
    viewModel: StitchViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var showDeleteOriginalsConfirm by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Preview, 1: Sequence & Seams, 2: Settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stitch_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState is StitchUiState.Preview) {
                        Button(
                            onClick = { viewModel.saveStitchedImage() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.stitch_save_action), fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is StitchUiState.Idle -> {
                    // Initial load placeholder
                }

                is StitchUiState.Analyzing -> {
                    AnalyzingView(progress = state.progressPercent, message = state.message)
                }

                is StitchUiState.Saving -> {
                    SavingView(progress = state.progressPercent, message = state.message)
                }

                is StitchUiState.Failure -> {
                    ErrorView(
                        message = state.errorMessage,
                        canRetry = state.canRetry,
                        onRetry = { /* triggers reload via activity */ },
                        onClose = onNavigateBack
                    )
                }

                is StitchUiState.Saved -> {
                    SavedSuccessDialog(
                        state = state,
                        onDismiss = onNavigateBack,
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, state.outputUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Stitched Screenshot"))
                        },
                        onEdit = {
                            val dummyUiModel = ScreenshotUiModel(
                                id = System.currentTimeMillis(),
                                uri = state.outputUri.toString(),
                                displayName = "Screenshot_Stitched.png",
                                dateAdded = System.currentTimeMillis() / 1000,
                                size = 0L,
                                relativePath = "Pictures/Screenshots/"
                            )
                            val editIntent = RedactionEditorActivity.newIntent(context, dummyUiModel, mode = RedactionEditorActivity.MODE_EDIT)
                            context.startActivity(editIntent)
                        },
                        onDeleteOriginalsRequest = {
                            showDeleteOriginalsConfirm = true
                        },
                        onDeleteStitched = {
                            scope.launch {
                                val success = viewModel.deleteStitchedImage(state.outputUri)
                                if (success) {
                                    Toast.makeText(context, "Deleted stitched image", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            }
                        }
                    )
                }

                is StitchUiState.Preview -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Section navigation tabs
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Preview") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Images & Seams (${state.images.size})") }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Options") }
                            )
                        }

                        when (selectedTab) {
                            0 -> {
                                // Full scrollable stitched preview
                                StitchedPreviewPane(
                                    previewUri = state.previewUri,
                                    estimatedHeight = state.estimatedHeight,
                                    config = state.config,
                                    onSwitchToManual = { viewModel.setMode(StitchMode.MANUAL) },
                                    onSwitchToAuto = { viewModel.setMode(StitchMode.AUTO) }
                                )
                            }
                            1 -> {
                                // Reordering & Seam fine-tuning
                                ImagesAndSeamsPane(
                                    images = state.images,
                                    seams = state.seams,
                                    onMoveUp = { idx -> viewModel.moveImage(idx, idx - 1) },
                                    onMoveDown = { idx -> viewModel.moveImage(idx, idx + 1) },
                                    onRemove = { idx -> viewModel.removeImage(idx) },
                                    onAdjustSeam = { seamIdx, delta -> viewModel.adjustSeam(seamIdx, delta) }
                                )
                            }
                            2 -> {
                                // Configuration toggles (Crop status bar, format, quality, gap)
                                ConfigOptionsPane(
                                    config = state.config,
                                    onToggleMode = { viewModel.setMode(if (state.config.mode == StitchMode.AUTO) StitchMode.MANUAL else StitchMode.AUTO) },
                                    onToggleCropStatusBar = { viewModel.toggleCropStatusBar() },
                                    onToggleCropNavigationBar = { viewModel.toggleCropNavigationBar() },
                                    onSetFormat = { format, q -> viewModel.setFormat(format, q) },
                                    onSetManualGap = { gap -> viewModel.setManualGap(gap) }
                                )
                            }
                        }
                    }
                }
            }

            // Confirm Delete Originals Dialog
            if (showDeleteOriginalsConfirm && uiState is StitchUiState.Saved) {
                val savedState = uiState as StitchUiState.Saved
                AlertDialog(
                    onDismissRequest = { showDeleteOriginalsConfirm = false },
                    title = { Text(stringResource(R.string.stitch_dialog_delete_originals_title)) },
                    text = {
                        Text(stringResource(R.string.stitch_dialog_delete_originals_desc, savedState.sourceUris.size))
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteOriginalsConfirm = false
                                scope.launch {
                                    val count = viewModel.deleteOriginalScreenshots()
                                    Toast.makeText(context, context.getString(R.string.stitch_delete_originals_success, count), Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.stitch_dialog_confirm_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteOriginalsConfirm = false }) {
                            Text(stringResource(R.string.stitch_dialog_cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StitchedPreviewPane(
    previewUri: Uri?,
    estimatedHeight: Int,
    config: StitchConfig,
    onSwitchToManual: () -> Unit,
    onSwitchToAuto: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Mode Quick Switcher Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.stitch_preview_header),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = config.mode == StitchMode.AUTO,
                    onClick = onSwitchToAuto,
                    label = { Text("Auto") }
                )
                FilterChip(
                    selected = config.mode == StitchMode.MANUAL,
                    onClick = onSwitchToManual,
                    label = { Text("Manual") }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Scrollable Card with stitched preview
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (previewUri != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(previewUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Stitched Preview",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Estimated height: ${estimatedHeight}px • Scroll inside preview to inspect seams",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImagesAndSeamsPane(
    images: List<StitchImageItem>,
    seams: List<StitchSeam>,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdjustSeam: (Int, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.stitch_reorder_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        itemsIndexed(images) { index, item ->
            ElevatedCard(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.uri)
                            .size(120, 160)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(45.dp, 60.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.originalWidth} × ${item.originalHeight} px",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { onMoveUp(index) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { onMoveDown(index) },
                            enabled = index < images.lastIndex,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { onRemove(index) },
                            enabled = images.size > 2,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Seam adjustment row between this image and the next
            if (index < seams.size) {
                val seam = seams[index]
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Seam ${index + 1} ↔ ${index + 2}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = seam.statusMessage,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (seam.hasConfidentMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        // Fine tune nudge controls
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = { onAdjustSeam(index, -10) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("-10", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { onAdjustSeam(index, -1) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("-1", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { onAdjustSeam(index, 1) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("+1", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { onAdjustSeam(index, 10) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("+10", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigOptionsPane(
    config: StitchConfig,
    onToggleMode: () -> Unit,
    onToggleCropStatusBar: () -> Unit,
    onToggleCropNavigationBar: () -> Unit,
    onSetFormat: (StitchFormat, Int) -> Unit,
    onSetManualGap: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Option
        ElevatedCard(shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.stitch_mode_auto),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Detects scrolling overlap and removes duplicated text/cards",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.mode == StitchMode.AUTO,
                        onCheckedChange = { onToggleMode() }
                    )
                }
            }
        }

        // Crop System Bars
        ElevatedCard(shape = RoundedCornerShape(14.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.stitch_crop_status_bar),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Removes battery/clock bar from intermediate screenshots",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.cropStatusBar,
                        onCheckedChange = { onToggleCropStatusBar() }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.stitch_crop_nav_bar),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Removes bottom navigation bar or gesture pill",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.cropNavigationBar,
                        onCheckedChange = { onToggleCropNavigationBar() }
                    )
                }
            }
        }

        // Manual Mode Gap Slider
        if (config.mode == StitchMode.MANUAL) {
            ElevatedCard(shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.stitch_gap_label, config.manualGapPx),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Slider(
                        value = config.manualGapPx.toFloat(),
                        onValueChange = { onSetManualGap(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 9
                    )
                }
            }
        }

        // Format & Quality Option
        ElevatedCard(shape = RoundedCornerShape(14.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Output Format",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.format == StitchFormat.PNG,
                        onClick = { onSetFormat(StitchFormat.PNG, config.jpegQuality) },
                        label = { Text(stringResource(R.string.stitch_format_png)) }
                    )
                    FilterChip(
                        selected = config.format == StitchFormat.JPEG,
                        onClick = { onSetFormat(StitchFormat.JPEG, config.jpegQuality) },
                        label = { Text(stringResource(R.string.stitch_format_jpeg)) }
                    )
                }

                if (config.format == StitchFormat.JPEG) {
                    Text(
                        text = stringResource(R.string.stitch_quality_label, config.jpegQuality),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = config.jpegQuality.toFloat(),
                        onValueChange = { onSetFormat(StitchFormat.JPEG, it.toInt()) },
                        valueRange = 60f..100f,
                        steps = 7
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyzingView(progress: Float, message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(54.dp),
                    strokeWidth = 4.dp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.stitch_analyzing),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SavingView(progress: Float, message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(54.dp),
                    strokeWidth = 4.dp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.stitch_saving),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Stitching Failed",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onClose) {
                        Text("Close")
                    }
                    if (canRetry) {
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedSuccessDialog(
    state: StitchUiState.Saved,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDeleteOriginalsRequest: () -> Unit,
    onDeleteStitched: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.stitch_saved_title))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.stitch_saved_subtitle, state.width, state.height),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Combined ${state.sourceUris.size} screenshots seamlessly. OCR text and auto-categorization have been indexed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.stitch_action_share), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.stitch_action_edit), fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = onDeleteOriginalsRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.stitch_action_delete_originals), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDeleteStitched,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.stitch_action_delete_stitched), fontSize = 11.sp)
            }
        }
    )
}

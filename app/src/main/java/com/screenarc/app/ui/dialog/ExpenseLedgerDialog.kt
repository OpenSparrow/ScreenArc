package com.screenarc.app.ui.dialog

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.screenarc.app.model.ExpenseCategory
import com.screenarc.app.model.ExpenseItem
import com.screenarc.app.model.TransactionType
import com.screenarc.app.ui.theme.AuroraCyan
import com.screenarc.app.ui.theme.ElectricIndigo
import com.screenarc.app.ui.theme.LocalIsGlassmorphic
import com.screenarc.app.util.ReceiptExpenseExtractor
import kotlinx.coroutines.launch

@Composable
fun ExpenseLedgerDialog(
    expenses: List<ExpenseItem>,
    onDismiss: () -> Unit,
    onOpenScreenshot: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTypeFilter by remember { mutableStateOf<TransactionType?>(null) }
    var selectedAppFilter by remember { mutableStateOf<String?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf<ExpenseCategory?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    val filteredExpenses = remember(expenses, selectedTypeFilter, selectedAppFilter, selectedCategoryFilter) {
        expenses.filter { item ->
            val matchType = selectedTypeFilter == null || item.transactionType == selectedTypeFilter
            val matchApp = selectedAppFilter == null || item.sourceApp == selectedAppFilter
            val matchCategory = selectedCategoryFilter == null || item.category == selectedCategoryFilter
            matchType && matchApp && matchCategory
        }
    }

    val summary = remember(filteredExpenses) {
        ReceiptExpenseExtractor.computeSummary(filteredExpenses)
    }

    val availableApps = remember(expenses) {
        expenses.mapNotNull { it.sourceApp }.distinct()
    }

    val isGlass = LocalIsGlassmorphic.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 760.dp)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = if (isGlass) Color(0xF212101E) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (isGlass) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Advanced Expense & Voucher Ledger",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "PRO",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color(0xFF4CAF50),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "On-device credits, debits, voucher claims & net spend ledger",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // KPI Cashflow Summary Banner
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    ElectricIndigo.copy(alpha = 0.90f),
                                    AuroraCyan.copy(alpha = 0.75f)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Net Out-of-Pocket Spend",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                    if (summary.isFullyOffset) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF00E676)
                                        ) {
                                            Text(
                                                text = "100% Offset (₹0 Net) 🎉",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = summary.formattedNetSpend,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.35f)
                            ) {
                                Text(
                                    text = "${summary.totalReceiptsCount} Records",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Financial Breakdown Chips: Debits, Credits, Vouchers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.25f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Debited / Spent",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                    Text(
                                        text = "-${ReceiptExpenseExtractor.formatCurrency(summary.totalDebits, summary.primaryCurrencySymbol)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFFF8A80)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.25f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Credited / Inflow",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                    Text(
                                        text = "+${ReceiptExpenseExtractor.formatCurrency(summary.totalCredits, summary.primaryCurrencySymbol)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFB9F6CA)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.25f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Vouchers Saved",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                    Text(
                                        text = "+${ReceiptExpenseExtractor.formatCurrency(summary.totalVouchersClaimed, summary.primaryCurrencySymbol)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFE040FB)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Export Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (filteredExpenses.isNotEmpty()) {
                                        isExporting = true
                                        scope.launch {
                                            val csvText = ReceiptExpenseExtractor.exportToCsv(filteredExpenses)
                                            val uri = ReceiptExpenseExtractor.saveCsvToDownloads(context, csvText)
                                            isExporting = false
                                            if (uri != null) {
                                                Toast.makeText(context, "Advanced CSV saved to Downloads", Toast.LENGTH_SHORT).show()
                                                ReceiptExpenseExtractor.shareCsv(context, uri)
                                            } else {
                                                Toast.makeText(context, "Failed to save CSV", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isExporting && filteredExpenses.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Black)
                                } else {
                                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export to CSV (Excel)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (filteredExpenses.isNotEmpty()) {
                                        val jsonText = ReceiptExpenseExtractor.exportToJson(filteredExpenses)
                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(android.content.Intent.EXTRA_TEXT, jsonText)
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, "ScreenArc_Ledger.json")
                                        }
                                        val chooser = android.content.Intent.createChooser(sendIntent, "Export Ledger JSON").apply {
                                            if (context !is android.app.Activity) addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(chooser)
                                    }
                                },
                                enabled = filteredExpenses.isNotEmpty(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share JSON", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Transaction Type Filter Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedTypeFilter == null,
                            onClick = { selectedTypeFilter = null },
                            label = { Text("All Types (${expenses.size})") }
                        )
                    }
                    item {
                        val count = expenses.count { it.transactionType == TransactionType.DEBIT }
                        FilterChip(
                            selected = selectedTypeFilter == TransactionType.DEBIT,
                            onClick = {
                                selectedTypeFilter = if (selectedTypeFilter == TransactionType.DEBIT) null else TransactionType.DEBIT
                            },
                            label = { Text("Debits / Spent ($count)") }
                        )
                    }
                    item {
                        val count = expenses.count { it.transactionType == TransactionType.CREDIT || it.transactionType == TransactionType.REFUND }
                        FilterChip(
                            selected = selectedTypeFilter == TransactionType.CREDIT,
                            onClick = {
                                selectedTypeFilter = if (selectedTypeFilter == TransactionType.CREDIT) null else TransactionType.CREDIT
                            },
                            label = { Text("Credits / Refunds ($count)") }
                        )
                    }
                    item {
                        val count = expenses.count { it.transactionType == TransactionType.VOUCHER_CLAIM }
                        FilterChip(
                            selected = selectedTypeFilter == TransactionType.VOUCHER_CLAIM,
                            onClick = {
                                selectedTypeFilter = if (selectedTypeFilter == TransactionType.VOUCHER_CLAIM) null else TransactionType.VOUCHER_CLAIM
                            },
                            label = { Text("Vouchers & Rewards ($count)") }
                        )
                    }
                }

                // App-Specific Filter Chips
                if (availableApps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedAppFilter == null,
                                onClick = { selectedAppFilter = null },
                                label = { Text("All Apps") }
                            )
                        }
                        items(availableApps) { appName ->
                            val count = expenses.count { it.sourceApp == appName }
                            FilterChip(
                                selected = selectedAppFilter == appName,
                                onClick = {
                                    selectedAppFilter = if (selectedAppFilter == appName) null else appName
                                },
                                label = { Text("$appName ($count)") }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Records List
                if (filteredExpenses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching financial screenshots found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filteredExpenses, key = { it.screenshotUri }) { item ->
                            AdvancedExpenseItemRow(
                                item = item,
                                onClick = { onOpenScreenshot?.invoke(item.screenshotUri) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedExpenseItemRow(
    item: ExpenseItem,
    onClick: () -> Unit
) {
    val isDebit = item.transactionType == TransactionType.DEBIT
    val isVoucher = item.transactionType == TransactionType.VOUCHER_CLAIM

    val amountColor = when (item.transactionType) {
        TransactionType.DEBIT -> MaterialTheme.colorScheme.error
        TransactionType.CREDIT, TransactionType.REFUND -> Color(0xFF00C853)
        TransactionType.VOUCHER_CLAIM -> Color(0xFFAB47BC)
    }

    val typeBadgeColor = when (item.transactionType) {
        TransactionType.DEBIT -> Color(0xFFFF5252).copy(alpha = 0.15f)
        TransactionType.CREDIT, TransactionType.REFUND -> Color(0xFF00E676).copy(alpha = 0.15f)
        TransactionType.VOUCHER_CLAIM -> Color(0xFFBA68C8).copy(alpha = 0.15f)
    }

    val typeTextColor = when (item.transactionType) {
        TransactionType.DEBIT -> Color(0xFFFF5252)
        TransactionType.CREDIT, TransactionType.REFUND -> Color(0xFF00C853)
        TransactionType.VOUCHER_CLAIM -> Color(0xFF8E24AA)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(54.dp)
            ) {
                AsyncImage(
                    model = item.screenshotUri,
                    contentDescription = item.merchantName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.merchantName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Type Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = typeBadgeColor
                    ) {
                        Text(
                            text = item.transactionType.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = typeTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = item.dateString ?: "Recent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.sourceApp != null) {
                        Text(
                            text = " • via ${item.sourceApp}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // In-receipt Voucher Offset callout if applicable
                if (item.voucherOrDiscountOffset > 0.0) {
                    Text(
                        text = "Voucher offset: -${ReceiptExpenseExtractor.formatCurrency(item.voucherOrDiscountOffset, item.currencySymbol)} (Net: ${ReceiptExpenseExtractor.formatCurrency(item.netCashflowImpact, item.currencySymbol)})",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFF00B0FF),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = item.category.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (item.isReimbursable) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFF9800).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Reimbursable",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFFFF9800),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount Column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${item.transactionType.sign}${item.formattedAmount}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = amountColor
                )
                if (item.transactionId != null) {
                    Text(
                        text = "#${item.transactionId.take(8)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

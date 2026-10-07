package com.screenarc.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.screenarc.app.data.AppSettings
import com.screenarc.app.service.OverlayService
import com.screenarc.app.ui.theme.GlassElevatedCard

@Composable
fun DiagnosticsSection(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
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

    val reliabilityArrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "ReliabilityArrow"
    )

    GlassElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reliability & Diagnostics",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp).rotate(reliabilityArrowRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
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
                                    onUpdateSettings { it.copy(isServiceEnabled = isChecked) }
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Auto-Archive / Auto-Trash Policy
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Auto-Clean Uncategorized Screenshots",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Automatically move uncategorized screenshots older than chosen threshold to trash. Categorized screenshots are NEVER touched.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(0 to "Off", 30 to "30 days", 60 to "60 days", 90 to "90 days").forEach { (days, label) ->
                                    val isSelected = settings.autoArchiveDays == days
                                    androidx.compose.material3.FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            onUpdateSettings { it.copy(autoArchiveDays = days) }
                                        },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    if (com.screenarc.app.BuildConfig.DEBUG) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Developer Options",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "⚠️ Warning: Simulating a screenshot will create a test image record and trigger the radial overlay for UI testing.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Test / Demo Trigger Button (Debug Only)
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
                            Text("Simulate Screenshot (Developer Test)")
                        }
                    }
                }
            }
        }
    }
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

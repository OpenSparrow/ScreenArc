package com.screenarc.app.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenarc.app.model.SmartEntity
import com.screenarc.app.model.SmartEntityType

/**
 * Utility helper for handling Smart Entity actions (Intent launching & Clipboard copying).
 */
object SmartEntityActionHelper {

    /**
     * Safely executes the primary Android Intent for the given [SmartEntity].
     * Catches [ActivityNotFoundException] and security exceptions gracefully.
     * If no Intent is supported (e.g. OTP code), copies value to clipboard.
     */
    fun launchEntityIntent(context: Context, entity: SmartEntity) {
        val intent = entity.createIntent(context)
        if (intent != null) {
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    "No application installed to perform this action (${entity.displayLabel})",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Failed to launch action: ${e.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            // Entities without Intent (e.g. OTP) fallback to Clipboard copy
            copyEntityToClipboard(context, entity, showToast = true)
        }
    }

    /**
     * Copies the entity's raw value to system [ClipboardManager] and displays a confirmation Toast.
     */
    fun copyEntityToClipboard(context: Context, entity: SmartEntity, showToast: Boolean = true) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(entity.getClipLabel(), entity.rawValue)
            clipboard.setPrimaryClip(clip)
            if (showToast) {
                Toast.makeText(context, entity.getCopySuccessMessage(), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            if (showToast) {
                Toast.makeText(context, "Could not copy to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Modern Jetpack Compose horizontal scrollable row of Action Chips representing detected [SmartEntity] items.
 *
 * Features:
 * - Entity-specific Material Icons (Globe, Phone, Email, Pin/Map, QR, Payment, Key).
 * - Truncated preview text with action description.
 * - Single tap: Dispatches primary intent safely (Browser, Dialer, Maps, Email Client).
 * - Tap and hold (Long Press): Copies raw value to system Clipboard with haptic feedback.
 * - Glassmorphic / Tonal surface elevation matching Android 14/15 Material 3 expressiveness.
 */
@Composable
fun SmartEntityChipsRow(
    entities: List<SmartEntity>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    onEntityClick: ((SmartEntity) -> Unit)? = null,
    onEntityLongClick: ((SmartEntity) -> Unit)? = null
) {
    if (entities.isEmpty()) return

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = entities.isNotEmpty(),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        LazyRow(
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = entities,
                key = { "${it.type}_${it.rawValue}_${it.hashCode()}" }
            ) { entity ->
                SmartEntityChip(
                    entity = entity,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (onEntityClick != null) {
                            onEntityClick(entity)
                        } else {
                            SmartEntityActionHelper.launchEntityIntent(context, entity)
                        }
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (onEntityLongClick != null) {
                            onEntityLongClick(entity)
                        } else {
                            SmartEntityActionHelper.copyEntityToClipboard(context, entity, showToast = true)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Individual modern Action Chip component with combined tap and long-press gestures.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartEntityChip(
    entity: SmartEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chipColor = when (entity.type) {
        SmartEntityType.URL -> MaterialTheme.colorScheme.primaryContainer
        SmartEntityType.PHONE -> MaterialTheme.colorScheme.secondaryContainer
        SmartEntityType.EMAIL -> MaterialTheme.colorScheme.tertiaryContainer
        SmartEntityType.ADDRESS -> MaterialTheme.colorScheme.surfaceVariant
        SmartEntityType.BARCODE -> MaterialTheme.colorScheme.secondaryContainer
        SmartEntityType.UPI -> MaterialTheme.colorScheme.primaryContainer
        SmartEntityType.OTP -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
    }

    val contentColor = when (entity.type) {
        SmartEntityType.URL -> MaterialTheme.colorScheme.onPrimaryContainer
        SmartEntityType.PHONE -> MaterialTheme.colorScheme.onSecondaryContainer
        SmartEntityType.EMAIL -> MaterialTheme.colorScheme.onTertiaryContainer
        SmartEntityType.ADDRESS -> MaterialTheme.colorScheme.onSurfaceVariant
        SmartEntityType.BARCODE -> MaterialTheme.colorScheme.onSecondaryContainer
        SmartEntityType.UPI -> MaterialTheme.colorScheme.onPrimaryContainer
        SmartEntityType.OTP -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = chipColor.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = entity.icon,
                contentDescription = entity.displayLabel,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entity.displayLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = contentColor
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    Text(
                        text = entity.previewValue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

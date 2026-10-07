package com.screenarc.app.model

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Types of actionable entities recognized within screenshot OCR text.
 */
enum class SmartEntityType {
    URL,
    PHONE,
    EMAIL,
    ADDRESS,
    BARCODE,
    UPI,
    OTP
}

/**
 * Sealed hierarchy representing actionable entities detected by [com.screenarc.app.util.EntityParser].
 * Each entity provides its raw value, user-facing label, preview snippet, icon,
 * intent specifications, and safe Intent creation mechanism.
 */
sealed class SmartEntity(
    open val rawValue: String,
    open val displayLabel: String,
    open val previewValue: String,
    open val icon: ImageVector,
    open val type: SmartEntityType,
    open val intentAction: String? = null,
    open val actionUriString: String? = null
) {

    /**
     * Creates the Android [Intent] to perform the primary action for this entity.
     * Returns null if no direct system Intent is applicable (e.g. OTP requires copying).
     * Includes [Intent.FLAG_ACTIVITY_NEW_TASK] to allow starting from application or overlay contexts.
     */
    open fun createIntent(context: Context): Intent? {
        val action = intentAction ?: return null
        val intent = if (actionUriString != null) {
            Intent(action, Uri.parse(actionUriString))
        } else {
            Intent(action)
        }
        return intent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Returns a human-friendly label for the system [ClipboardManager].
     */
    abstract fun getClipLabel(): String

    /**
     * Returns a confirmation message to show in a Toast or Snackbar upon copying.
     */
    open fun getCopySuccessMessage(): String = "Copied to clipboard: $previewValue"

    /**
     * Web URLs and Links.
     * Action: Open in Browser via Intent.ACTION_VIEW.
     */
    data class Url(
        override val rawValue: String,
        val cleanUrl: String = sanitizeUrl(rawValue),
        override val displayLabel: String = "Open Link",
        override val previewValue: String = cleanUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .take(30)
    ) : SmartEntity(
        rawValue = rawValue,
        displayLabel = displayLabel,
        previewValue = previewValue,
        icon = Icons.Default.Language,
        type = SmartEntityType.URL,
        intentAction = Intent.ACTION_VIEW,
        actionUriString = cleanUrl
    ) {
        override fun getClipLabel(): String = "Link URL"
        override fun getCopySuccessMessage(): String = "Link copied: $cleanUrl"

        companion object {
            fun sanitizeUrl(input: String): String {
                val trimmed = input.trim()
                return if (!trimmed.startsWith("http://", ignoreCase = true) &&
                    !trimmed.startsWith("https://", ignoreCase = true)
                ) {
                    "https://$trimmed"
                } else {
                    trimmed
                }
            }
        }
    }

    /**
     * Phone Numbers (International, US, Indian, etc.).
     * Action: Open Dialer via Intent.ACTION_DIAL.
     */
    data class PhoneNumber(
        override val rawValue: String,
        val normalizedNumber: String = normalizePhone(rawValue),
        override val displayLabel: String = "Call",
        override val previewValue: String = rawValue.trim().take(22)
    ) : SmartEntity(
        rawValue = rawValue,
        displayLabel = displayLabel,
        previewValue = previewValue,
        icon = Icons.Default.Phone,
        type = SmartEntityType.PHONE,
        intentAction = Intent.ACTION_DIAL,
        actionUriString = "tel:$normalizedNumber"
    ) {
        override fun getClipLabel(): String = "Phone Number"
        override fun getCopySuccessMessage(): String = "Phone number copied: $rawValue"

        companion object {
            fun normalizePhone(input: String): String {
                val cleaned = input.filter { it.isDigit() || it == '+' }
                return cleaned.ifBlank { input.trim() }
            }
        }
    }

    /**
     * Email Addresses.
     * Action: Open Email Client via Intent.ACTION_SENDTO.
     */
    data class Email(
        override val rawValue: String,
        override val displayLabel: String = "Send Email",
        override val previewValue: String = rawValue.trim().take(28)
    ) : SmartEntity(
        rawValue = rawValue,
        displayLabel = displayLabel,
        previewValue = previewValue,
        icon = Icons.Default.Email,
        type = SmartEntityType.EMAIL,
        intentAction = Intent.ACTION_SENDTO,
        actionUriString = "mailto:${rawValue.trim()}"
    ) {
        override fun getClipLabel(): String = "Email Address"
        override fun getCopySuccessMessage(): String = "Email copied: $rawValue"
    }

    /**
     * Street Addresses / Locations.
     * Action: Open Maps via Intent.ACTION_VIEW with geo: URI.
     */
    data class Address(
        override val rawValue: String,
        override val displayLabel: String = "Open Maps",
        override val previewValue: String = rawValue.trim().take(32)
    ) : SmartEntity(
        rawValue = rawValue,
        displayLabel = displayLabel,
        previewValue = previewValue,
        icon = Icons.Default.Place,
        type = SmartEntityType.ADDRESS,
        intentAction = Intent.ACTION_VIEW,
        actionUriString = if (rawValue.trim().startsWith("geo:", ignoreCase = true) ||
            rawValue.trim().startsWith("http://", ignoreCase = true) ||
            rawValue.trim().startsWith("https://", ignoreCase = true)
        ) rawValue.trim() else "geo:0,0?q=${rawValue.trim()}"
    ) {
        override fun getClipLabel(): String = "Street Address"
        override fun getCopySuccessMessage(): String = "Address copied: $rawValue"
    }

    /**
     * QR Codes or Barcodes detected in the screenshot.
     * Action: Open linked content or web search.
     */
    data class Barcode(
        override val rawValue: String,
        val format: String = "QR_CODE",
        override val displayLabel: String = "Scan / QR",
        override val previewValue: String = rawValue.trim().take(26)
    ) : SmartEntity(
        rawValue = rawValue,
        displayLabel = displayLabel,
        previewValue = previewValue,
        icon = Icons.Default.QrCode,
        type = SmartEntityType.BARCODE,
        intentAction = resolveBarcodeIntentAction(rawValue),
        actionUriString = resolveBarcodeUriString(rawValue)
    ) {
        override fun createIntent(context: Context): Intent? {
            val action = intentAction ?: return null
            return if (actionUriString != null) {
                Intent(action, Uri.parse(actionUriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(action).apply {
                    putExtra("query", rawValue.trim())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        override fun getClipLabel(): String = "Barcode / QR Content"
        override fun getCopySuccessMessage(): String = "QR content copied: $rawValue"

        companion object {
            private fun resolveBarcodeIntentAction(raw: String): String {
                val trimmed = raw.trim()
                return when {
                    trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) ||
                    trimmed.startsWith("geo:", ignoreCase = true) -> Intent.ACTION_VIEW
                    trimmed.startsWith("mailto:", ignoreCase = true) -> Intent.ACTION_SENDTO
                    trimmed.startsWith("tel:", ignoreCase = true) -> Intent.ACTION_DIAL
                    else -> Intent.ACTION_WEB_SEARCH
                }
            }

            private fun resolveBarcodeUriString(raw: String): String? {
                val trimmed = raw.trim()
                return when {
                    trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) ||
                    trimmed.startsWith("geo:", ignoreCase = true) ||
                    trimmed.startsWith("mailto:", ignoreCase = true) ||
                    trimmed.startsWith("tel:", ignoreCase = true) -> trimmed
                    else -> null
                }
            }
        }
    }

    /**
     * UPI Payment Identifiers (e.g. merchant@icici, user@okhdfcbank).
     * Action: Launch UPI Payment Intent.
     */
    data class Upi(
        override val rawValue: String,
        val amount: String? = null,
        override val displayLabel: String = if (amount != null) "Pay $amount" else "Pay UPI",
        override val previewValue: String = rawValue.trim().take(24)
    ) : SmartEntity(
        rawValue = rawValue,
        displayLabel = displayLabel,
        previewValue = previewValue,
        icon = Icons.Default.Payment,
        type = SmartEntityType.UPI,
        intentAction = Intent.ACTION_VIEW,
        actionUriString = buildUpiUriString(rawValue, amount)
    ) {
        override fun getClipLabel(): String = "UPI ID"
        override fun getCopySuccessMessage(): String = "UPI ID copied: $rawValue"

        companion object {
            private fun buildUpiUriString(upi: String, amountStr: String?): String {
                val cleanUpi = upi.trim()
                return if (!amountStr.isNullOrBlank()) {
                    val numeric = amountStr.replace(Regex("[^0-9.]"), "")
                    "upi://pay?pa=$cleanUpi&pn=$cleanUpi&am=$numeric"
                } else {
                    "upi://pay?pa=$cleanUpi&pn=$cleanUpi"
                }
            }
        }
    }

    /**
     * One-Time Passwords / Verification Codes.
     * Action: Copy directly to clipboard.
     */
    data class Otp(
        override val rawValue: String,
        override val displayLabel: String = "Copy Code",
        override val previewValue: String = rawValue.trim()
    ) : SmartEntity(
        rawValue = rawValue,
        displayLabel = displayLabel,
        previewValue = previewValue,
        icon = Icons.Default.Password,
        type = SmartEntityType.OTP,
        intentAction = null,
        actionUriString = null
    ) {
        override fun createIntent(context: Context): Intent? = null

        override fun getClipLabel(): String = "OTP Code"
        override fun getCopySuccessMessage(): String = "OTP copied: $rawValue"
    }
}

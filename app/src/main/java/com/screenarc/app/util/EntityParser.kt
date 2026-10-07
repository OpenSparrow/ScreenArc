package com.screenarc.app.util

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import com.screenarc.app.model.SmartEntity

/**
 * High-performance parser that inspects raw OCR text to extract actionable [SmartEntity] items.
 *
 * Employs a robust hybrid detection engine:
 * 1. On Android O+ (API 26+), queries the platform [TextClassifier] when an application [Context] is supplied.
 * 2. Runs comprehensive regular expression patterns for URLs, phone numbers, emails, street addresses,
 *    UPI payment addresses, and OTP/verification codes.
 * 3. Sanitizes, deduplicates, and validates matches to prevent overlapping or malformed entities.
 */
object EntityParser {

    // Web URLs (http/https, www, and common modern domain extensions)
    private val URL_REGEX = Regex(
        "(?:https?://|www\\.)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|-]" +
        "|\\b[a-zA-Z0-9.-]+\\.(?:com|org|net|edu|gov|io|ai|app|dev|co|in|uk|me|info|biz|tech|store|online|site)\\b(?:/[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*)?",
        RegexOption.IGNORE_CASE
    )

    // Emails (standard RFC-friendly matching)
    private val EMAIL_REGEX = Regex(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b",
        RegexOption.IGNORE_CASE
    )

    // Phone numbers (international, US, Indian 5+5, European formats)
    private val PHONE_REGEX = Regex(
        "(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{2,5}\\)?[-.\\s]?\\d{3,5}[-.\\s]?\\d{3,5}\\b"
    )

    // Common non-phone numeric formats to discard (Dates, IP addresses, Timestamps)
    private val DATE_REGEX = Regex("^\\d{2,4}[-/. ]\\d{1,2}[-/. ]\\d{2,4}$")
    private val IP_REGEX = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")

    // Street addresses & physical locations
    private val STREET_ADDRESS_REGEX = Regex(
        "\\b\\d{1,6}\\s+(?:[A-Za-z0-9.\\-']+\\s+){1,4}(?:Street|St|Avenue|Ave|Boulevard|Blvd|Road|Rd|Drive|Dr|Lane|Ln|Way|Court|Ct|Circle|Cir|Highway|Hwy|Place|Pl|Square|Sq|Terrace|Ter|Parkway|Pkwy)(?:\\.|\\b)(?:\\s+(?:Suite|Ste|Apt|Unit|Floor|Fl)\\.?\\s*[A-Za-z0-9\\-]+)?(?:[,\n\r\\s]+[A-Za-z\\s]+(?:[,\n\r\\s]+[A-Z]{2}\\s+\\d{5}(?:-\\d{4})?)?)?",
        RegexOption.IGNORE_CASE
    )

    // Geo coordinates (e.g. geo:37.7749,-122.4194)
    private val GEO_URI_REGEX = Regex(
        "\\bgeo:[-+]?[0-9]*\\.?[0-9]+,[-+]?[0-9]*\\.?[0-9]+(?:\\?[^\\s]*)?",
        RegexOption.IGNORE_CASE
    )

    // Google Maps share / place links
    private val MAPS_LINK_REGEX = Regex(
        "https?://(?:maps\\.google\\.com|goo\\.gl/maps|maps\\.app\\.goo\\.gl)/[^\\s]+",
        RegexOption.IGNORE_CASE
    )

    // UPI handles (e.g. username@upi, merchant@okaxis)
    private val UPI_REGEX = Regex(
        "\\b[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}\\b"
    )

    // UPI Amount context (e.g. ₹500, $50, 450 INR)
    private val AMOUNT_REGEX = Regex(
        "(?:[₹\\$]|INR|USD)\\s*([0-9]+(?:[,.][0-9]{2})?)"
    )

    // OTP / Verification code keywords followed by 4-8 digits
    private val OTP_EXPLICIT_REGEX = Regex(
        "(?i)(?:otp|code|verification|passcode|one-time password)[^0-9]{1,12}([0-9]{4,8})\\b"
    )

    // QR formatted payload patterns (WiFi, VCard, SMS)
    private val QR_SPECIAL_REGEX = Regex(
        "(?:WIFI:T:[^;]+;S:[^;]+;P:[^;]+;;|SMSTO:\\+?[0-9]+:[^\\n]+|BEGIN:VCARD[\\s\\S]*?END:VCARD)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses the supplied raw text and returns a list of unique [SmartEntity] items.
     *
     * @param text The OCR recognized text or input string.
     * @param context Optional Android Context for system [TextClassifier] integration.
     */
    fun parseText(text: String, context: Context? = null): List<SmartEntity> {
        if (text.isBlank()) return emptyList()

        val results = mutableListOf<SmartEntity>()
        val seenUrls = mutableSetOf<String>()
        val seenPhones = mutableSetOf<String>()
        val seenEmails = mutableSetOf<String>()
        val seenAddresses = mutableSetOf<String>()
        val seenUpis = mutableSetOf<String>()
        val seenOtps = mutableSetOf<String>()
        val seenBarcodes = mutableSetOf<String>()

        // 1. Android Platform TextClassifier (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context != null) {
            try {
                val tcm = context.getSystemService(Context.TEXT_CLASSIFICATION_SERVICE) as? TextClassificationManager
                val classifier = tcm?.textClassifier
                if (classifier != null && !classifier.isDestroyed) {
                    val request = TextLinks.Request.Builder(text).build()
                    val textLinks = classifier.generateLinks(request)
                    for (link in textLinks.links) {
                        val subText = text.substring(link.start, link.end).trim()
                        if (subText.isBlank()) continue
                        val entityType = link.getEntity(0)
                        when (entityType) {
                            TextClassifier.TYPE_URL -> {
                                if (seenUrls.add(subText.lowercase())) {
                                    results.add(SmartEntity.Url(subText))
                                }
                            }
                            TextClassifier.TYPE_PHONE -> {
                                val normalized = subText.filter { it.isDigit() || it == '+' }
                                if (normalized.length in 7..15 && seenPhones.add(normalized)) {
                                    results.add(SmartEntity.PhoneNumber(subText))
                                }
                            }
                            TextClassifier.TYPE_EMAIL -> {
                                if (seenEmails.add(subText.lowercase())) {
                                    results.add(SmartEntity.Email(subText))
                                }
                            }
                            TextClassifier.TYPE_ADDRESS -> {
                                if (seenAddresses.add(subText.lowercase())) {
                                    results.add(SmartEntity.Address(subText))
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Graceful fallback to regex patterns
            }
        }

        // 2. Email extraction (run before URLs to avoid capturing email domains as standalone URLs)
        EMAIL_REGEX.findAll(text).forEach { match ->
            val email = match.value.trim()
            if (seenEmails.add(email.lowercase())) {
                results.add(SmartEntity.Email(email))
            }
        }

        // 3. URLs & Web Links
        URL_REGEX.findAll(text).forEach { match ->
            val raw = match.value.trim().trimEnd('.', ',', ')', ']', '}')
            // Exclude if it was part of an email (e.g. preceded by @)
            val matchStart = match.range.first
            val isPrecededByAt = matchStart > 0 && text[matchStart - 1] == '@'
            if (!isPrecededByAt && seenEmails.none { it.contains(raw.lowercase()) }) {
                val key = raw.lowercase()
                if (seenUrls.add(key)) {
                    results.add(SmartEntity.Url(raw))
                }
            }
        }

        // 4. Street Addresses & Map Links
        MAPS_LINK_REGEX.findAll(text).forEach { match ->
            val link = match.value.trim()
            if (seenAddresses.add(link.lowercase())) {
                results.add(SmartEntity.Address(link))
            }
        }

        GEO_URI_REGEX.findAll(text).forEach { match ->
            val geo = match.value.trim()
            if (seenAddresses.add(geo.lowercase())) {
                results.add(SmartEntity.Address(geo))
            }
        }

        STREET_ADDRESS_REGEX.findAll(text).forEach { match ->
            val address = match.value.trim().replace(Regex("\\s+"), " ")
            if (seenAddresses.add(address.lowercase())) {
                results.add(SmartEntity.Address(address))
            }
        }

        // 5. Phone Numbers
        PHONE_REGEX.findAll(text).forEach { match ->
            val phone = match.value.trim()
            val digitCount = phone.count { it.isDigit() }
            val normalized = phone.filter { it.isDigit() || it == '+' }
            // Filter out dates, IP addresses, and timestamps
            val isDate = DATE_REGEX.matches(phone)
            val isIp = IP_REGEX.matches(phone)
            val hasColons = phone.contains(':')
            val isPartOfUrl = seenUrls.any { it.contains(phone) }

            if (!isDate && !isIp && !hasColons && !isPartOfUrl && digitCount in 7..15) {
                if (seenPhones.add(normalized)) {
                    results.add(SmartEntity.PhoneNumber(phone))
                }
            }
        }

        // 6. UPI IDs
        UPI_REGEX.findAll(text).forEach { match ->
            val upiId = match.value.trim()
            val lower = upiId.lowercase()
            // Ignore standard email extensions and already detected emails
            val isEmailExtension = lower.endsWith(".com") || lower.endsWith(".net") ||
                    lower.endsWith(".org") || lower.endsWith(".edu") || lower.endsWith(".gov")
            if (!isEmailExtension && !seenEmails.contains(lower)) {
                if (seenUpis.add(lower)) {
                    val amountMatch = AMOUNT_REGEX.find(text)
                    results.add(SmartEntity.Upi(upiId, amount = amountMatch?.value))
                }
            }
        }

        // 7. OTP / Verification codes
        OTP_EXPLICIT_REGEX.findAll(text).forEach { match ->
            val code = match.groupValues[1].trim()
            if (seenOtps.add(code)) {
                results.add(SmartEntity.Otp(code))
            }
        }

        // 8. QR special payloads (WiFi, VCard, SMS)
        QR_SPECIAL_REGEX.findAll(text).forEach { match ->
            val qrContent = match.value.trim()
            if (seenBarcodes.add(qrContent)) {
                results.add(SmartEntity.Barcode(qrContent, format = "QR_CODE"))
            }
        }

        return results
    }

    /**
     * Checks whether the provided text contains an OTP/verification code pattern.
     */
    fun hasOtp(text: String): Boolean = OTP_EXPLICIT_REGEX.containsMatchIn(text)

    /**
     * Extracts only URL entities from the text.
     */
    fun extractUrls(text: String): List<SmartEntity.Url> =
        parseText(text).filterIsInstance<SmartEntity.Url>()

    /**
     * Extracts only Phone Number entities from the text.
     */
    fun extractPhoneNumbers(text: String): List<SmartEntity.PhoneNumber> =
        parseText(text).filterIsInstance<SmartEntity.PhoneNumber>()

    /**
     * Extracts only Email entities from the text.
     */
    fun extractEmails(text: String): List<SmartEntity.Email> =
        parseText(text).filterIsInstance<SmartEntity.Email>()

    /**
     * Extracts only Street Address entities from the text.
     */
    fun extractAddresses(text: String): List<SmartEntity.Address> =
        parseText(text).filterIsInstance<SmartEntity.Address>()

    /**
     * Constructs a [SmartEntity.Barcode] from a scanned barcode result.
     */
    fun fromBarcode(rawValue: String, format: String = "QR_CODE"): SmartEntity.Barcode {
        return SmartEntity.Barcode(rawValue = rawValue, format = format)
    }
}

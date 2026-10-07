package com.screenarc.app.util

data class ExtractedEntity(
    val type: EntityType,
    val value: String,
    val label: String
)

enum class EntityType {
    URL,
    UPI,
    OTP,
    PHONE,
    EMAIL
}

object SmartEntityExtractor {

    // Matches standard URLs (http, https, www, common domains)
    private val URL_REGEX = Regex(
        "\\b(https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]|www\\.[-a-zA-Z0-9+&@#/%?=~_|!:,.;]+)\\b",
        RegexOption.IGNORE_CASE
    )

    // Matches UPI VPA IDs (e.g., username@upi, name@okaxis, merchant@icici, mobile@paytm)
    // and optional amounts ₹500 or $50
    private val UPI_REGEX = Regex(
        "\\b[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}\\b"
    )

    private val AMOUNT_REGEX = Regex(
        "(?:[₹\\$]|INR|USD)\\s*([0-9]+(?:[,.][0-9]{2})?)"
    )

    // Matches OTP codes: 4 to 6 digit standalone numbers near OTP/code keywords or standalone 4-6 digit tokens
    private val OTP_EXPLICIT_REGEX = Regex(
        "(?i)(?:otp|code|verification|passcode|one-time password)[^0-9]{1,12}([0-9]{4,6})\\b"
    )

    // Phone numbers: international + Indian/US formats
    private val PHONE_REGEX = Regex(
        "(?:\\+?\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}\\b"
    )

    // Emails
    private val EMAIL_REGEX = Regex(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    )

    fun extractEntities(text: String): List<ExtractedEntity> {
        if (text.isBlank()) return emptyList()

        val results = mutableListOf<ExtractedEntity>()
        val seenValues = mutableSetOf<String>()

        // 1. URLs
        URL_REGEX.findAll(text).forEach { match ->
            val url = match.value.trim()
            if (seenValues.add(url.lowercase())) {
                val fullUrl = if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                    "https://$url"
                } else url
                results.add(ExtractedEntity(EntityType.URL, fullUrl, "Open Link"))
            }
        }

        // 2. UPI IDs (with optional amount context)
        UPI_REGEX.findAll(text).forEach { match ->
            val upiId = match.value.trim()
            // Exclude if it looks like an email with .com/.org/.edu/.net
            if (!upiId.contains(".com") && !upiId.contains(".net") && !upiId.contains(".org") && !upiId.contains(".edu")) {
                if (seenValues.add(upiId.lowercase())) {
                    val amountMatch = AMOUNT_REGEX.find(text)
                    val label = if (amountMatch != null) "Copy UPI (${amountMatch.value})" else "Copy UPI ID"
                    results.add(ExtractedEntity(EntityType.UPI, upiId, label))
                }
            }
        }

        // 3. OTP codes
        OTP_EXPLICIT_REGEX.findAll(text).forEach { match ->
            val code = match.groupValues[1].trim()
            if (seenValues.add(code)) {
                results.add(ExtractedEntity(EntityType.OTP, code, "Copy Code ($code)"))
            }
        }

        // 4. Emails
        EMAIL_REGEX.findAll(text).forEach { match ->
            val email = match.value.trim()
            if (seenValues.add(email.lowercase())) {
                results.add(ExtractedEntity(EntityType.EMAIL, email, "Send Email"))
            }
        }

        // 5. Phone numbers
        PHONE_REGEX.findAll(text).forEach { match ->
            val phone = match.value.trim()
            if (phone.length >= 10 && seenValues.add(phone)) {
                results.add(ExtractedEntity(EntityType.PHONE, phone, "Dial ($phone)"))
            }
        }

        return results
    }

    fun hasOtp(text: String): Boolean {
        return OTP_EXPLICIT_REGEX.containsMatchIn(text)
    }
}

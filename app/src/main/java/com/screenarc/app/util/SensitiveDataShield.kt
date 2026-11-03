package com.screenarc.app.util

import com.screenarc.app.model.OcrBlock

enum class SensitiveType(val label: String) {
    CREDIT_CARD("Credit/Debit Card"),
    CVV("CVV/Security Code"),
    PASSWORD_OR_PIN("Password / PIN"),
    NATIONAL_ID("National ID / SSN / Aadhaar"),
    API_KEY("API Key / Secret Token"),
    PHONE_NUMBER("Phone Number"),
    EMAIL("Email Address")
}

data class SensitiveFinding(
    val type: SensitiveType,
    val matchedText: String,
    val block: OcrBlock? = null
)

object SensitiveDataShield {

    private val CVV_REGEX = Regex("(?i)(?:cvv|cvc|security\\s*code|cpm)[:\\s=-]*([0-9]{3,4})\\b")
    private val PASSWORD_REGEX = Regex("(?i)(?:password|passcode|pin\\s*(?:code)?|secret)[:\\s=-]*([a-zA-Z0-9@#\$%^&*!_.-]{4,32})\\b")
    private val SSN_REGEX = Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b")
    private val AADHAAR_REGEX = Regex("\\b\\d{4}\\s+\\d{4}\\s+\\d{4}\\b")
    private val TOKEN_REGEX = Regex(
        "\\b(sk_live_[0-9a-zA-Z]{18,}|ghp_[0-9a-zA-Z]{30,}|AIza[0-9A-Za-z-_]{30,}|eyJ[a-zA-Z0-9_-]{10,}\\.eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,})\\b"
    )
    private val PHONE_REGEX = Regex("(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b")
    private val EMAIL_REGEX = Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")

    /**
     * Checks if a numeric string is a valid credit card using the Luhn Algorithm.
     */
    fun isValidLuhn(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        if (digits.length !in 13..19) return false

        var sum = 0
        var isSecond = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (isSecond) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            isSecond = !isSecond
        }
        return (sum % 10 == 0)
    }

    /**
     * Scans a text string for sensitive PII findings.
     */
    fun scanText(text: String): List<SensitiveFinding> {
        val findings = mutableListOf<SensitiveFinding>()

        // 1. Credit Cards
        val candidateNumbers = Regex("\\b(?:\\d[ -]*?){13,19}\\b").findAll(text)
        for (match in candidateNumbers) {
            val raw = match.value.trim()
            val cleanDigits = raw.filter { it.isDigit() }
            if (isValidLuhn(cleanDigits)) {
                findings.add(SensitiveFinding(SensitiveType.CREDIT_CARD, raw))
            }
        }

        // 2. CVV / Security codes
        CVV_REGEX.findAll(text).forEach { m ->
            findings.add(SensitiveFinding(SensitiveType.CVV, m.value.trim()))
        }

        // 3. Passwords / PINs
        PASSWORD_REGEX.findAll(text).forEach { m ->
            findings.add(SensitiveFinding(SensitiveType.PASSWORD_OR_PIN, m.value.trim()))
        }

        // 4. National IDs (SSN / Aadhaar)
        SSN_REGEX.findAll(text).forEach { m ->
            findings.add(SensitiveFinding(SensitiveType.NATIONAL_ID, m.value.trim()))
        }
        AADHAAR_REGEX.findAll(text).forEach { m ->
            findings.add(SensitiveFinding(SensitiveType.NATIONAL_ID, m.value.trim()))
        }

        // 5. Auth Tokens / API Keys
        TOKEN_REGEX.findAll(text).forEach { m ->
            findings.add(SensitiveFinding(SensitiveType.API_KEY, m.value.trim()))
        }

        // 6. Emails
        EMAIL_REGEX.findAll(text).forEach { m ->
            findings.add(SensitiveFinding(SensitiveType.EMAIL, m.value.trim()))
        }

        // 7. Phone numbers
        PHONE_REGEX.findAll(text).forEach { m ->
            findings.add(SensitiveFinding(SensitiveType.PHONE_NUMBER, m.value.trim()))
        }

        return findings
    }

    /**
     * Inspects OCR blocks and returns those containing sensitive data paired with the detected type.
     */
    fun scanBlocks(blocks: List<OcrBlock>): List<Pair<OcrBlock, SensitiveType>> {
        val results = mutableListOf<Pair<OcrBlock, SensitiveType>>()
        for (block in blocks) {
            val text = block.text
            val findings = scanText(text)
            if (findings.isNotEmpty()) {
                // Priority to highest risk
                val highestRisk = findings.minByOrNull { it.type.ordinal }!!.type
                results.add(block to highestRisk)
            }
        }
        return results
    }

    /**
     * Auto-generates [RedactionItem] entries for all sensitive OCR blocks.
     */
    fun createAutoRedactions(
        blocks: List<OcrBlock>,
        redactionType: RedactionType = RedactionType.BLACKOUT
    ): List<RedactionItem> {
        val sensitiveMatches = scanBlocks(blocks)
        return sensitiveMatches.map { (block, _) ->
            RedactionItem(block = block, type = redactionType)
        }
    }
}

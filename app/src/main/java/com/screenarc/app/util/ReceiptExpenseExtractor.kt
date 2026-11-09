package com.screenarc.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.screenarc.app.model.ExpenseCategory
import com.screenarc.app.model.ExpenseItem
import com.screenarc.app.model.ExpenseSummary
import com.screenarc.app.model.ScreenshotUiModel
import com.screenarc.app.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object ReceiptExpenseExtractor {
    private const val TAG = "ReceiptExpenseExtractor"

    // Known merchants grouped by category
    private val FOOD_MERCHANTS = listOf(
        "swiggy", "zomato", "starbucks", "mcdonald's", "mcdonalds", "domino's", "dominos",
        "subway", "doordash", "uber eats", "ubereats", "chipotle", "burger king", "pizza hut",
        "kfc", "taco bell", "dunkin", "panera", "wendy's", "chick-fil-a", "deliveroo", "grubhub"
    )

    private val TRAVEL_MERCHANTS = listOf(
        "uber", "lyft", "ola", "grab", "delta", "united airlines", "american airlines",
        "southwest", "emirates", "irctc", "booking.com", "airbnb", "expedia", "indigo",
        "air india", "makemytrip", "cleartrip", "redbus", "transit", "metro", "amtrak"
    )

    private val SHOPPING_MERCHANTS = listOf(
        "amazon", "flipkart", "walmart", "target", "best buy", "apple store", "ebay",
        "nike", "zara", "h&m", "sephora", "costco", "ikea", "home depot", "myntra",
        "aliexpress", "etsy", "shein", "meesho", "ajio"
    )

    private val ENTERTAINMENT_MERCHANTS = listOf(
        "netflix", "spotify", "disney+", "disney plus", "youtube premium", "hulu",
        "prime video", "hbo max", "steam", "playstation", "xbox", "bookmyshow",
        "paramount+", "apple tv", "audible", "nintendo"
    )

    private val BILLS_MERCHANTS = listOf(
        "at&t", "verizon", "t-mobile", "airtel", "jio", "vodafone", "pg&e",
        "electricity", "water bill", "gas bill", "broadband", "spectrum", "comcast", "xfinity",
        "bescom", "tneb", "cesc", "mahavitaran", "piped gas"
    )

    private val TECH_MERCHANTS = listOf(
        "google cloud", "aws", "github", "digitalocean", "microsoft", "openai",
        "anthropic", "heroku", "cloudflare", "adobe", "figma", "notion", "canva", "zoom"
    )

    private val GROCERY_MERCHANTS = listOf(
        "instacart", "whole foods", "trader joe's", "kroger", "blinkit", "zepto",
        "bigbasket", "safeway", "aldi", "sprouts", "tesco", "carrefour"
    )

    // Regex for prominent "Total" / "Amount Paid" / "Credited" values
    private val TOTAL_AMOUNT_REGEX = Regex(
        "(?i)(?:total(?:\\s+amount)?|grand\\s+total|amount\\s+paid|paid\\s+amount|net\\s+amount|total\\s+payable|order\\s+total|amount\\s+debited|debited\\s+amount|credited\\s+amount|amount\\s+credited|payment\\s+successful|billed\\s+amount|voucher\\s+amount|voucher\\s+value|cashback\\s+amount|voucher\\s+for|total)\\s*[:=-]?\\s*([₹\$€£¥]|INR|USD|EUR|GBP|Rs\\.?|CAD|AUD)?\\s*([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    )

    // Standalone currency + amount pattern (e.g. ₹1,499.00 or $49.99)
    private val CURRENCY_AMOUNT_REGEX = Regex(
        "([₹\$€£¥]|INR|USD|EUR|GBP|Rs\\.?)\\s*([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    )

    // Voucher / Discount deduction regex inside an invoice or receipt
    private val IN_RECEIPT_VOUCHER_REGEX = Regex(
        "(?i)(?:voucher(?:\\s+discount)?|coupon(?:\\s+discount)?(?:\\s+applied)?|promo\\s+code|discount(?:\\s+applied)?|gift\\s+card(?:\\s+applied)?|reward\\s+points|points\\s+redeemed)\\s*[:=-]?\\s*[-–]?\\s*([₹\$€£¥]|INR|USD|EUR|GBP|Rs\\.?)?\\s*([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    )

    // Transaction / Reference ID pattern
    private val TRANSACTION_ID_REGEX = Regex(
        "(?i)(?:txn\\s*(?:id|no)?|transaction\\s*(?:id|no|#)?|order\\s*(?:id|number|#)|upi\\s*ref(?:\\s*no)?|ref(?:erence)?\\s*(?:no|id|#)|utr)[:\\s#-]*([a-zA-Z0-9_-]{6,32})\\b"
    )

    // Payment method pattern
    private val PAYMENT_METHOD_REGEX = Regex(
        "(?i)\\b(visa|mastercard|amex|american express|discover|apple pay|google pay|gpay|phonepe|paytm|upi|cash|paypal|credit card|debit card|voucher|gift card|wallet)\\b"
    )

    // Common Date formats in receipts
    private val DATE_PATTERNS = listOf(
        Regex("\\b(\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2})\\b"),                       // 2026-11-03
        Regex("\\b(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})\\b"),                       // 03/11/2026 or 11/03/2026
        Regex("(?i)\\b(\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{2,4})\\b"), // 03 Nov 2026
        Regex("(?i)\\b((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s+\\d{2,4})\\b") // Nov 03, 2026
    )

    /**
     * Extracts structured expense data from a [ScreenshotUiModel].
     * Returns null if the screenshot text does not match financial / transaction criteria.
     */
    fun extractExpense(screenshot: ScreenshotUiModel): ExpenseItem? {
        val text = screenshot.recognizedText ?: return null
        return extractExpense(
            rawText = text,
            screenshotId = screenshot.id,
            screenshotUri = screenshot.uri,
            fileName = screenshot.displayName
        )
    }

    /**
     * Extracts structured expense data from raw OCR text.
     */
    fun extractExpense(
        rawText: String,
        screenshotId: Long,
        screenshotUri: String,
        fileName: String
    ): ExpenseItem? {
        if (rawText.isBlank()) return null
        val lowerText = rawText.lowercase()

        // Fast reject non-financial screens if there are no financial indicators
        val hasFinancialKeywords = lowerText.contains("total") ||
                lowerText.contains("amount") ||
                lowerText.contains("paid") ||
                lowerText.contains("debited") ||
                lowerText.contains("credited") ||
                lowerText.contains("received") ||
                lowerText.contains("voucher") ||
                lowerText.contains("cashback") ||
                lowerText.contains("refund") ||
                lowerText.contains("invoice") ||
                lowerText.contains("receipt") ||
                lowerText.contains("order") ||
                lowerText.contains("bill") ||
                lowerText.contains("payment") ||
                lowerText.contains("₹") ||
                lowerText.contains("$") ||
                lowerText.contains("€") ||
                lowerText.contains("£") ||
                lowerText.contains("inr") ||
                lowerText.contains("usd")

        if (!hasFinancialKeywords) return null

        // 1. Transaction Type Detection (Credit vs Debit vs Voucher vs Refund)
        val transactionType = detectTransactionType(lowerText)

        // 2. Amount and Currency Extraction
        val (amount, currencySymbol, currencyCode) = parseAmountAndCurrency(rawText) ?: return null
        if (amount <= 0.0) return null

        // 3. In-Receipt Voucher / Discount Offset Detection
        val voucherOffset = parseInReceiptVoucherOffset(rawText, totalAmount = amount)

        // 4. Source Application / Provider Identification
        val sourceApp = detectSourceApp(rawText, fileName)

        // 5. Merchant Extraction
        val merchant = extractMerchant(rawText, fileName, sourceApp, transactionType)

        // 6. Category Detection
        val category = inferCategory(merchant, lowerText, transactionType)

        // 7. Date Extraction
        val (dateStr, dateTimestamp) = parseDate(rawText)

        // 8. Transaction / Reference ID
        val transactionId = TRANSACTION_ID_REGEX.find(rawText)?.groupValues?.get(1)?.trim()

        // 9. Payment Method
        val paymentMethod = (PAYMENT_METHOD_REGEX.find(rawText)?.value?.trim() ?: sourceApp)?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }

        val formattedAmount = formatCurrency(amount, currencySymbol)

        // First 80 chars for context snippet
        val snippet = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(4)
            .joinToString(" • ")
            .take(90)

        return ExpenseItem(
            screenshotId = screenshotId,
            screenshotUri = screenshotUri,
            fileName = fileName,
            merchantName = merchant,
            amount = amount,
            formattedAmount = formattedAmount,
            currencySymbol = currencySymbol,
            currencyCode = currencyCode,
            dateString = dateStr,
            dateTimestamp = dateTimestamp,
            category = category,
            transactionId = transactionId,
            paymentMethod = paymentMethod,
            rawTextSnippet = snippet,
            transactionType = transactionType,
            sourceApp = sourceApp,
            voucherOrDiscountOffset = voucherOffset,
            isReimbursable = category == ExpenseCategory.TRAVEL_AND_TRANSPORT || category == ExpenseCategory.BILLS_AND_UTILITIES
        )
    }

    /**
     * Determines whether transaction is Debit, Credit, Voucher Claim, or Refund.
     */
    fun detectTransactionType(lowerText: String): TransactionType {
        // 1. Check Refund indicators
        if (lowerText.contains("refund processed") ||
            lowerText.contains("refund initiated") ||
            lowerText.contains("refund of") ||
            lowerText.contains("refunded to") ||
            lowerText.contains("amount refunded") ||
            lowerText.contains("reversal of")
        ) {
            return TransactionType.REFUND
        }

        // 2. Check Voucher Claim / Reward / Gift Card indicators
        if (lowerText.contains("voucher claimed") ||
            lowerText.contains("claim voucher") ||
            lowerText.contains("voucher for") ||
            lowerText.contains("gift card added") ||
            lowerText.contains("gift card redeemed") ||
            lowerText.contains("scratch card won") ||
            lowerText.contains("reward claimed") ||
            lowerText.contains("reward unlocked") ||
            lowerText.contains("coupon code claimed") ||
            lowerText.contains("cashback won")
        ) {
            return TransactionType.VOUCHER_CLAIM
        }

        // 3. Check General Inflow / Credit indicators
        if (lowerText.contains("credited to") ||
            lowerText.contains("credited into") ||
            lowerText.contains("money received") ||
            lowerText.contains("received from") ||
            lowerText.contains("payment received") ||
            lowerText.contains("cashback received") ||
            lowerText.contains("cashback credited") ||
            lowerText.contains("salary credited") ||
            lowerText.contains("deposited to") ||
            (lowerText.contains("credited") && !lowerText.contains("debited"))
        ) {
            return TransactionType.CREDIT
        }

        // Default: Debit / Expense (Paid to, Order Placed, Bill Payment, Debited, etc.)
        return TransactionType.DEBIT
    }

    /**
     * Identifies the source fintech / banking application or wallet from screenshot content.
     */
    fun detectSourceApp(text: String, fileName: String): String? {
        val lower = (text + " " + fileName).lowercase()
        return when {
            lower.contains("google pay") || lower.contains("gpay") -> "Google Pay"
            lower.contains("phonepe") -> "PhonePe"
            lower.contains("paytm") -> "Paytm"
            lower.contains("cred") -> "CRED"
            lower.contains("amazon pay") || (lower.contains("amazon") && lower.contains("pay")) -> "Amazon Pay"
            lower.contains("bhim") || lower.contains("upi") && lower.contains("npci") -> "BHIM UPI"
            lower.contains("apple pay") || lower.contains("apple wallet") -> "Apple Pay"
            lower.contains("paypal") -> "PayPal"
            lower.contains("hdfc bank") || lower.contains("hdfc") -> "HDFC Bank"
            lower.contains("sbi") || lower.contains("yono") || lower.contains("state bank") -> "SBI"
            lower.contains("icici") || lower.contains("imobile") -> "ICICI Bank"
            lower.contains("axis bank") || lower.contains("axis") -> "Axis Bank"
            lower.contains("chase") -> "Chase"
            lower.contains("american express") || lower.contains("amex") -> "American Express"
            lower.contains("swiggy") -> "Swiggy"
            lower.contains("zomato") -> "Zomato"
            lower.contains("uber") -> "Uber"
            lower.contains("flipkart") -> "Flipkart"
            lower.contains("blinkit") -> "Blinkit"
            lower.contains("zepto") -> "Zepto"
            else -> null
        }
    }

    /**
     * Checks if a single invoice/bill screenshot already contains a voucher/coupon deduction.
     * (e.g. Total: 1000, Voucher: -1000).
     */
    private fun parseInReceiptVoucherOffset(text: String, totalAmount: Double): Double {
        val matches = IN_RECEIPT_VOUCHER_REGEX.findAll(text).toList()
        for (match in matches) {
            val rawAmountStr = match.groupValues[2].replace(",", "").replace(" ", "").trim()
            val parsed = rawAmountStr.toDoubleOrNull()
            if (parsed != null && parsed > 0.0) {
                // If voucher equals total or is a deduction, cap at totalAmount
                return if (parsed >= totalAmount) totalAmount else parsed
            }
        }
        return 0.0
    }

    private fun parseAmountAndCurrency(text: String): Triple<Double, String, String>? {
        // Try matching explicit Total / Credited / Debited keyword lines first
        val totalMatches = TOTAL_AMOUNT_REGEX.findAll(text).toList()
        if (totalMatches.isNotEmpty()) {
            // Find highest valid match from the end
            for (match in totalMatches.reversed()) {
                val rawSymbolOrCode = match.groupValues[1].trim()
                val rawAmountStr = match.groupValues[2].replace(",", "").replace(" ", "").trim()
                val parsed = rawAmountStr.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    val (symbol, code) = normalizeCurrency(rawSymbolOrCode, text)
                    return Triple(parsed, symbol, code)
                }
            }
        }

        // Fallback: match standalone currency + amount
        val currencyMatches = CURRENCY_AMOUNT_REGEX.findAll(text).toList()
        if (currencyMatches.isNotEmpty()) {
            var maxAmount = 0.0
            var bestSymbol = "$"
            var bestCode = "USD"
            for (match in currencyMatches) {
                val rawSymbol = match.groupValues[1].trim()
                val rawAmountStr = match.groupValues[2].replace(",", "").replace(" ", "").trim()
                val parsed = rawAmountStr.toDoubleOrNull()
                if (parsed != null && parsed > maxAmount) {
                    maxAmount = parsed
                    val (s, c) = normalizeCurrency(rawSymbol, text)
                    bestSymbol = s
                    bestCode = c
                }
            }
            if (maxAmount > 0.0) {
                return Triple(maxAmount, bestSymbol, bestCode)
            }
        }

        return null
    }

    private fun normalizeCurrency(raw: String, fullText: String): Pair<String, String> {
        val lower = raw.lowercase()
        return when {
            lower.contains("₹") || lower.contains("inr") || lower.contains("rs") || fullText.contains("₹") -> "₹" to "INR"
            lower.contains("€") || lower.contains("eur") || fullText.contains("€") -> "€" to "EUR"
            lower.contains("£") || lower.contains("gbp") || fullText.contains("£") -> "£" to "GBP"
            lower.contains("¥") || lower.contains("jpy") || lower.contains("cny") -> "¥" to "JPY"
            lower.contains("$") || lower.contains("usd") || fullText.contains("$") -> "$" to "USD"
            lower.contains("cad") -> "CA$" to "CAD"
            lower.contains("aud") -> "A$" to "AUD"
            else -> {
                if (fullText.contains("₹")) "₹" to "INR"
                else if (fullText.contains("€")) "€" to "EUR"
                else if (fullText.contains("£")) "£" to "GBP"
                else "$" to "USD"
            }
        }
    }

    private fun extractMerchant(
        text: String,
        fileName: String,
        sourceApp: String?,
        transactionType: TransactionType
    ): String {
        val lower = text.lowercase()

        // If voucher claim, check for voucher brand or reward title
        if (transactionType == TransactionType.VOUCHER_CLAIM) {
            val voucherBrand = listOf("amazon", "swiggy", "zomato", "myntra", "flipkart", "uber", "starbucks", "dominos")
                .firstOrNull { lower.contains(it) }
            if (voucherBrand != null) {
                return "${capitalizeWords(voucherBrand)} Voucher"
            }
            return if (sourceApp != null) "$sourceApp Reward" else "Voucher Claim"
        }

        // 1. Check known merchants
        val allMerchants = (FOOD_MERCHANTS + TRAVEL_MERCHANTS + SHOPPING_MERCHANTS +
                ENTERTAINMENT_MERCHANTS + BILLS_MERCHANTS + TECH_MERCHANTS + GROCERY_MERCHANTS)

        for (m in allMerchants) {
            if (lower.contains(m)) {
                return capitalizeWords(m)
            }
        }

        // 2. Check "Paid to [Name]" or "Received from [Name]"
        val contactRegex = Regex("(?i)(?:paid\\s+to|payment\\s+to|to\\s*:|received\\s+from|from\\s*:|merchant\\s*:|billed\\s+by\\s*:)\\s*([A-Za-z0-9&.\\s'-]{2,30})")
        val match = contactRegex.find(text)
        if (match != null) {
            val candidate = match.groupValues[1].lines().firstOrNull()?.trim()
            if (!candidate.isNullOrBlank() && candidate.length in 3..28) {
                return capitalizeWords(candidate)
            }
        }

        // 3. Fallback: inspect top lines of receipt for vendor name
        val nonGenericLines = text.lines()
            .map { it.trim() }
            .filter { line ->
                line.length in 3..32 &&
                        !line.matches(Regex(".*(?:total|receipt|invoice|order|tax|date|thank you|welcome|subtotal|balance|credited|debited).*$", RegexOption.IGNORE_CASE)) &&
                        line.any { it.isLetter() }
            }

        if (nonGenericLines.isNotEmpty()) {
            return capitalizeWords(nonGenericLines.first())
        }

        // 4. Fallback to clean filename
        val cleanName = fileName.substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
        return capitalizeWords(cleanName)
    }

    private fun inferCategory(
        merchant: String,
        lowerText: String,
        transactionType: TransactionType
    ): ExpenseCategory {
        if (transactionType == TransactionType.VOUCHER_CLAIM) {
            return ExpenseCategory.SHOPPING
        }

        val lowerMerchant = merchant.lowercase()
        return when {
            FOOD_MERCHANTS.any { lowerMerchant.contains(it) } ||
                    lowerText.contains("restaurant") || lowerText.contains("cafe") ||
                    lowerText.contains("dinner") || lowerText.contains("lunch") || lowerText.contains("food") ->
                ExpenseCategory.FOOD_AND_DINING

            TRAVEL_MERCHANTS.any { lowerMerchant.contains(it) } ||
                    lowerText.contains("ride") || lowerText.contains("flight") ||
                    lowerText.contains("fare") || lowerText.contains("toll") || lowerText.contains("trip") ->
                ExpenseCategory.TRAVEL_AND_TRANSPORT

            GROCERY_MERCHANTS.any { lowerMerchant.contains(it) } ||
                    lowerText.contains("grocery") || lowerText.contains("supermarket") ||
                    lowerText.contains("mart") ->
                ExpenseCategory.GROCERIES

            SHOPPING_MERCHANTS.any { lowerMerchant.contains(it) } ||
                    lowerText.contains("retail") || lowerText.contains("store") || lowerText.contains("cart") ->
                ExpenseCategory.SHOPPING

            ENTERTAINMENT_MERCHANTS.any { lowerMerchant.contains(it) } ||
                    lowerText.contains("movie") || lowerText.contains("cinema") ||
                    lowerText.contains("theatre") || lowerText.contains("subscription") ->
                ExpenseCategory.ENTERTAINMENT

            BILLS_MERCHANTS.any { lowerMerchant.contains(it) } ||
                    lowerText.contains("electric") || lowerText.contains("utility") ||
                    lowerText.contains("recharge") || lowerText.contains("telecom") ||
                    lowerText.contains("bill payment") ->
                ExpenseCategory.BILLS_AND_UTILITIES

            TECH_MERCHANTS.any { lowerMerchant.contains(it) } ||
                    lowerText.contains("hosting") || lowerText.contains("domain") ||
                    lowerText.contains("saas") || lowerText.contains("cloud") ->
                ExpenseCategory.TECH_AND_SERVICES

            lowerText.contains("pharmacy") || lowerText.contains("hospital") ||
                    lowerText.contains("clinic") || lowerText.contains("medicine") || lowerText.contains("health") ->
                ExpenseCategory.HEALTH_AND_MEDICAL

            else -> ExpenseCategory.GENERAL
        }
    }

    private fun parseDate(text: String): Pair<String?, Long?> {
        for (pattern in DATE_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val dateStr = match.groupValues[1].trim()
                val parsedTimestamp = tryParseDateToTimestamp(dateStr)
                return dateStr to parsedTimestamp
            }
        }
        return null to null
    }

    private fun tryParseDateToTimestamp(dateStr: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd", "yyyy/MM/dd",
            "dd/MM/yyyy", "MM/dd/yyyy",
            "dd-MM-yyyy", "MM-dd-yyyy",
            "dd MMM yyyy", "MMM dd, yyyy", "dd MMMM yyyy", "MMMM dd, yyyy"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.ENGLISH)
                sdf.isLenient = false
                val date = sdf.parse(dateStr)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return null
    }

    fun formatCurrency(amount: Double, symbol: String): String {
        val formatted = if (amount % 1.0 == 0.0) {
            String.format(Locale.US, "%,d", amount.toLong())
        } else {
            String.format(Locale.US, "%,.2f", amount)
        }
        return "$symbol$formatted"
    }

    private fun capitalizeWords(input: String): String {
        return input.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }

    /**
     * Aggregates debits, credits, vouchers and calculates true Net Out-of-Pocket Cashflow.
     * For example, a ₹1,000 bill debit + ₹1,000 voucher claim results in Net Spend: ₹0.
     */
    fun computeSummary(expenses: List<ExpenseItem>): ExpenseSummary {
        val totalDebits = expenses
            .filter { it.transactionType == TransactionType.DEBIT }
            .sumOf { it.amount }

        val totalCredits = expenses
            .filter { it.transactionType == TransactionType.CREDIT || it.transactionType == TransactionType.REFUND }
            .sumOf { it.amount }

        val directVouchers = expenses
            .filter { it.transactionType == TransactionType.VOUCHER_CLAIM }
            .sumOf { it.amount }

        val inReceiptVoucherOffsets = expenses.sumOf { it.voucherOrDiscountOffset }
        val totalVouchers = directVouchers + inReceiptVoucherOffsets

        // Net Out-of-Pocket Spend: Debits - Credits - Vouchers
        val netSpend = totalDebits - totalCredits - totalVouchers
        val isFullyOffset = totalDebits > 0.0 && abs(netSpend) < 0.01

        val primarySymbol = expenses.firstOrNull()?.currencySymbol ?: "$"
        val breakdown = expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        // Source App Breakdown: Net balance per app
        val appBreakdown = expenses.groupBy { it.sourceApp ?: "Other" }
            .mapValues { entry ->
                entry.value.sumOf { it.netCashflowImpact }
            }

        return ExpenseSummary(
            totalAmount = totalDebits,
            formattedTotal = formatCurrency(totalDebits, primarySymbol),
            primaryCurrencySymbol = primarySymbol,
            totalReceiptsCount = expenses.size,
            categoryBreakdown = breakdown,
            totalDebits = totalDebits,
            totalCredits = totalCredits,
            totalVouchersClaimed = totalVouchers,
            netSpend = netSpend,
            formattedNetSpend = formatCurrency(netSpend, primarySymbol),
            sourceAppBreakdown = appBreakdown,
            isFullyOffset = isFullyOffset
        )
    }

    /**
     * Generates RFC 4180 compliant CSV content for spreadsheets with debits, credits, vouchers and net spend.
     */
    fun exportToCsv(expenses: List<ExpenseItem>): String {
        val sb = StringBuilder()
        // Header
        sb.append("Date,Merchant / Note,Type,Source App,Category,Gross Amount,Voucher / Discount Offset,Net Cashflow Impact,Currency,Transaction ID,Payment Method,Reimbursable,File Name,Screenshot URI\n")

        for (item in expenses) {
            val date = escapeCsv(item.dateString ?: "N/A")
            val merchant = escapeCsv(item.merchantName)
            val type = escapeCsv(item.transactionType.displayName)
            val sourceApp = escapeCsv(item.sourceApp ?: "N/A")
            val category = escapeCsv(item.category.displayName)
            val amount = item.amount.toString()
            val offset = item.voucherOrDiscountOffset.toString()
            val netImpact = item.netCashflowImpact.toString()
            val currency = escapeCsv(item.currencyCode)
            val txnId = escapeCsv(item.transactionId ?: "")
            val method = escapeCsv(item.paymentMethod ?: "")
            val reimbursable = if (item.isReimbursable) "Yes" else "No"
            val fileName = escapeCsv(item.fileName)
            val uri = escapeCsv(item.screenshotUri)

            sb.append("$date,$merchant,$type,$sourceApp,$category,$amount,$offset,$netImpact,$currency,$txnId,$method,$reimbursable,$fileName,$uri\n")
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\r", " ").replace("\n", " ").trim()
        return if (clean.contains(",") || clean.contains("\"") || clean.contains(";")) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }

    /**
     * Generates structured JSON representation for external bookkeeping or APIs.
     */
    fun exportToJson(expenses: List<ExpenseItem>): String {
        val summary = computeSummary(expenses)
        val root = JSONObject()
        root.put("app", "ScreenArc")
        root.put("feature", "AdvancedSmartExpenseLedger")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("totalCount", expenses.size)
        root.put("totalDebits", summary.totalDebits)
        root.put("totalCredits", summary.totalCredits)
        root.put("totalVouchersClaimed", summary.totalVouchersClaimed)
        root.put("netSpend", summary.netSpend)
        root.put("isFullyOffset", summary.isFullyOffset)

        val array = JSONArray()
        for (item in expenses) {
            val obj = JSONObject().apply {
                put("screenshotId", item.screenshotId)
                put("screenshotUri", item.screenshotUri)
                put("fileName", item.fileName)
                put("merchant", item.merchantName)
                put("transactionType", item.transactionType.name)
                put("sourceApp", item.sourceApp ?: JSONObject.NULL)
                put("amount", item.amount)
                put("voucherOrDiscountOffset", item.voucherOrDiscountOffset)
                put("netCashflowImpact", item.netCashflowImpact)
                put("formattedAmount", item.formattedAmount)
                put("currencySymbol", item.currencySymbol)
                put("currencyCode", item.currencyCode)
                put("date", item.dateString ?: JSONObject.NULL)
                put("dateTimestamp", item.dateTimestamp ?: JSONObject.NULL)
                put("category", item.category.name)
                put("categoryDisplayName", item.category.displayName)
                put("transactionId", item.transactionId ?: JSONObject.NULL)
                put("paymentMethod", item.paymentMethod ?: JSONObject.NULL)
                put("isReimbursable", item.isReimbursable)
            }
            array.put(obj)
        }
        root.put("expenses", array)
        return root.toString(2)
    }

    /**
     * Saves CSV to Android Downloads folder and returns content Uri.
     */
    suspend fun saveCsvToDownloads(
        context: Context,
        csvContent: String,
        fileName: String = "ScreenArc_Advanced_Ledger_${System.currentTimeMillis()}.csv"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val uri = resolver.insert(collection, contentValues) ?: return@withContext null
            resolver.openOutputStream(uri)?.use { out ->
                out.write(csvContent.toByteArray(Charsets.UTF_8))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            Log.d(TAG, "Saved advanced ledger CSV to $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving CSV to Downloads: ${e.message}", e)
            null
        }
    }

    /**
     * Dispatches the CSV file via system share chooser.
     */
    fun shareCsv(context: Context, csvUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, csvUri)
            putExtra(Intent.EXTRA_SUBJECT, "ScreenArc Advanced Expense & Inflow Ledger")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Advanced Ledger (CSV)").apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }
}

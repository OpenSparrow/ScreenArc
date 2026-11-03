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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        "electricity", "water bill", "gas bill", "broadband", "spectrum", "comcast", "xfinity"
    )

    private val TECH_MERCHANTS = listOf(
        "google cloud", "aws", "github", "digitalocean", "microsoft", "openai",
        "anthropic", "heroku", "cloudflare", "adobe", "figma", "notion", "canva", "zoom"
    )

    private val GROCERY_MERCHANTS = listOf(
        "instacart", "whole foods", "trader joe's", "kroger", "blinkit", "zepto",
        "bigbasket", "safeway", "aldi", "sprouts", "tesco", "carrefour"
    )

    // Regex for prominent "Total" / "Amount Paid" values
    private val TOTAL_AMOUNT_REGEX = Regex(
        "(?i)(?:total(?:\\s+amount)?|grand\\s+total|amount\\s+paid|paid\\s+amount|net\\s+amount|total\\s+payable|order\\s+total|amount\\s+debited|payment\\s+successful|billed\\s+amount|total)\\s*[:=-]?\\s*([₹\\$€£¥]|INR|USD|EUR|GBP|Rs\\.?|CAD|AUD)?\\s*([0-9]{1,3}(?:[,\\s][0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    )

    // Standalone currency + amount pattern (e.g. ₹1,499.00 or $49.99)
    private val CURRENCY_AMOUNT_REGEX = Regex(
        "([₹\\$€£¥]|INR|USD|EUR|GBP|Rs\\.?)\\s*([0-9]{1,3}(?:[,\\s][0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    )

    // Transaction / Reference ID pattern
    private val TRANSACTION_ID_REGEX = Regex(
        "(?i)(?:txn\\s*(?:id|no)?|transaction\\s*(?:id|no|#)?|order\\s*(?:id|number|#)|upi\\s*ref(?:\\s*no)?|ref(?:erence)?\\s*(?:no|id|#)|utr)[:\\s#-]*([a-zA-Z0-9_-]{6,32})\\b"
    )

    // Payment method pattern
    private val PAYMENT_METHOD_REGEX = Regex(
        "(?i)\\b(visa|mastercard|amex|american express|discover|apple pay|google pay|gpay|phonepe|paytm|upi|cash|paypal|credit card|debit card)\\b"
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
     * Returns null if the screenshot text does not match financial / receipt criteria.
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

        // Fast reject non-receipt screens if there are no financial indicators
        val hasReceiptKeywords = lowerText.contains("total") ||
                lowerText.contains("amount") ||
                lowerText.contains("paid") ||
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

        if (!hasReceiptKeywords) return null

        // 1. Amount and Currency Extraction
        val (amount, currencySymbol, currencyCode) = parseAmountAndCurrency(rawText) ?: return null
        if (amount <= 0.0) return null

        // 2. Merchant Extraction
        val merchant = extractMerchant(rawText, fileName)

        // 3. Category Detection
        val category = inferCategory(merchant, lowerText)

        // 4. Date Extraction
        val (dateStr, dateTimestamp) = parseDate(rawText)

        // 5. Transaction / Reference ID
        val transactionId = TRANSACTION_ID_REGEX.find(rawText)?.groupValues?.get(1)?.trim()

        // 6. Payment Method
        val paymentMethod = PAYMENT_METHOD_REGEX.find(rawText)?.value?.trim()?.replaceFirstChar {
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
            rawTextSnippet = snippet
        )
    }

    private fun parseAmountAndCurrency(text: String): Triple<Double, String, String>? {
        // Try matching explicit Total keyword lines first
        val totalMatches = TOTAL_AMOUNT_REGEX.findAll(text).toList()
        if (totalMatches.isNotEmpty()) {
            // Find highest valid total match
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
            // Pick the largest amount found (standard for receipts: total is largest)
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
                // Infer from full text symbols
                if (fullText.contains("₹")) "₹" to "INR"
                else if (fullText.contains("€")) "€" to "EUR"
                else if (fullText.contains("£")) "£" to "GBP"
                else "$" to "USD"
            }
        }
    }

    private fun extractMerchant(text: String, fileName: String): String {
        val lower = text.lowercase()

        // 1. Check known merchants
        val allMerchants = (FOOD_MERCHANTS + TRAVEL_MERCHANTS + SHOPPING_MERCHANTS +
                ENTERTAINMENT_MERCHANTS + BILLS_MERCHANTS + TECH_MERCHANTS + GROCERY_MERCHANTS)

        for (m in allMerchants) {
            if (lower.contains(m)) {
                return capitalizeWords(m)
            }
        }

        // 2. Check "Paid to [Name]" or "To: [Name]" or "Merchant: [Name]"
        val paidToRegex = Regex("(?i)(?:paid\\s+to|payment\\s+to|to\\s*:|merchant\\s*:|billed\\s+by\\s*:)\\s*([A-Za-z0-9&.\\s'-]{2,30})")
        val match = paidToRegex.find(text)
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
                        !line.matches(Regex(".*(?:total|receipt|invoice|order|tax|date|thank you|welcome|subtotal|balance).*$", RegexOption.IGNORE_CASE)) &&
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

    private fun inferCategory(merchant: String, lowerText: String): ExpenseCategory {
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
                    lowerText.contains("recharge") || lowerText.contains("telecom") ->
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
     * Aggregates expenses and computes statistics for dashboard presentation.
     */
    fun computeSummary(expenses: List<ExpenseItem>): ExpenseSummary {
        val total = expenses.sumOf { it.amount }
        val primarySymbol = expenses.firstOrNull()?.currencySymbol ?: "$"
        val breakdown = expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        return ExpenseSummary(
            totalAmount = total,
            formattedTotal = formatCurrency(total, primarySymbol),
            primaryCurrencySymbol = primarySymbol,
            totalReceiptsCount = expenses.size,
            categoryBreakdown = breakdown
        )
    }

    /**
     * Generates RFC 4180 compliant CSV content for spreadsheets (Excel, Google Sheets).
     */
    fun exportToCsv(expenses: List<ExpenseItem>): String {
        val sb = StringBuilder()
        // Header
        sb.append("Date,Merchant,Category,Amount,Currency,Formatted Amount,Transaction ID,Payment Method,File Name,Screenshot URI\n")

        for (item in expenses) {
            val date = escapeCsv(item.dateString ?: "N/A")
            val merchant = escapeCsv(item.merchantName)
            val category = escapeCsv(item.category.displayName)
            val amount = item.amount.toString()
            val currency = escapeCsv(item.currencyCode)
            val formatted = escapeCsv(item.formattedAmount)
            val txnId = escapeCsv(item.transactionId ?: "")
            val method = escapeCsv(item.paymentMethod ?: "")
            val fileName = escapeCsv(item.fileName)
            val uri = escapeCsv(item.screenshotUri)

            sb.append("$date,$merchant,$category,$amount,$currency,$formatted,$txnId,$method,$fileName,$uri\n")
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
        val root = JSONObject()
        root.put("app", "ScreenArc")
        root.put("feature", "SmartExpenseLedger")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("totalCount", expenses.size)
        root.put("totalAmount", expenses.sumOf { it.amount })

        val array = JSONArray()
        for (item in expenses) {
            val obj = JSONObject().apply {
                put("screenshotId", item.screenshotId)
                put("screenshotUri", item.screenshotUri)
                put("fileName", item.fileName)
                put("merchant", item.merchantName)
                put("amount", item.amount)
                put("formattedAmount", item.formattedAmount)
                put("currencySymbol", item.currencySymbol)
                put("currencyCode", item.currencyCode)
                put("date", item.dateString ?: JSONObject.NULL)
                put("dateTimestamp", item.dateTimestamp ?: JSONObject.NULL)
                put("category", item.category.name)
                put("categoryDisplayName", item.category.displayName)
                put("transactionId", item.transactionId ?: JSONObject.NULL)
                put("paymentMethod", item.paymentMethod ?: JSONObject.NULL)
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
        fileName: String = "ScreenArc_Expenses_${System.currentTimeMillis()}.csv"
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
            Log.d(TAG, "Saved expenses CSV to $uri")
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
            putExtra(Intent.EXTRA_SUBJECT, "ScreenArc Expense Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Expense Report (CSV)").apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }
}

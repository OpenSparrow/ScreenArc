package com.screenarc.app.model

enum class ExpenseCategory(val displayName: String, val iconName: String) {
    FOOD_AND_DINING("Food & Dining", "Restaurant"),
    TRAVEL_AND_TRANSPORT("Travel & Transport", "DirectionsCar"),
    SHOPPING("Shopping", "ShoppingCart"),
    BILLS_AND_UTILITIES("Bills & Utilities", "Receipt"),
    ENTERTAINMENT("Entertainment", "Movie"),
    GROCERIES("Groceries", "LocalGroceryStore"),
    HEALTH_AND_MEDICAL("Health & Medical", "LocalHospital"),
    TECH_AND_SERVICES("Tech & Services", "Computer"),
    GENERAL("General", "AttachMoney")
}

/**
 * Indicates whether money was spent (Debit) or received/saved (Credit/Voucher/Refund).
 */
enum class TransactionType(val displayName: String, val sign: String) {
    DEBIT("Debit / Expense", "-"),
    CREDIT("Credit / Inflow", "+"),
    VOUCHER_CLAIM("Voucher / Reward", "+"),
    REFUND("Refund / Reversal", "+")
}

data class ExpenseItem(
    val screenshotId: Long,
    val screenshotUri: String,
    val fileName: String,
    val merchantName: String,
    val amount: Double,
    val formattedAmount: String,
    val currencySymbol: String,
    val currencyCode: String,
    val dateString: String?,
    val dateTimestamp: Long?,
    val category: ExpenseCategory,
    val transactionId: String? = null,
    val paymentMethod: String? = null,
    val rawTextSnippet: String = "",
    val transactionType: TransactionType = TransactionType.DEBIT,
    val sourceApp: String? = null,
    val voucherOrDiscountOffset: Double = 0.0,
    val isReimbursable: Boolean = false
) {
    /**
     * Net financial impact:
     * Debits add positive expense (+amount).
     * Credits and voucher claims subtract from net spend (-amount).
     */
    val netCashflowImpact: Double
        get() = when (transactionType) {
            TransactionType.DEBIT -> amount - voucherOrDiscountOffset
            TransactionType.CREDIT,
            TransactionType.VOUCHER_CLAIM,
            TransactionType.REFUND -> -amount
        }
}

data class ExpenseSummary(
    val totalAmount: Double,
    val formattedTotal: String,
    val primaryCurrencySymbol: String,
    val totalReceiptsCount: Int,
    val categoryBreakdown: Map<ExpenseCategory, Double>,
    val totalDebits: Double = totalAmount,
    val totalCredits: Double = 0.0,
    val totalVouchersClaimed: Double = 0.0,
    val netSpend: Double = totalAmount,
    val formattedNetSpend: String = formattedTotal,
    val sourceAppBreakdown: Map<String, Double> = emptyMap(),
    val isFullyOffset: Boolean = false
)

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
    val rawTextSnippet: String = ""
)

data class ExpenseSummary(
    val totalAmount: Double,
    val formattedTotal: String,
    val primaryCurrencySymbol: String,
    val totalReceiptsCount: Int,
    val categoryBreakdown: Map<ExpenseCategory, Double>
)

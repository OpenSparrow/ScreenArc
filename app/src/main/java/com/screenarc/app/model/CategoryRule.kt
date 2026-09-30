package com.screenarc.app.model

enum class RuleConditionOperator {
    CONTAINS_ANY,
    CONTAINS_ALL,
    MATCHES_REGEX,
    DOMAINS_MATCH
}

data class ScreenOcrData(
    val recognizedText: String,
    val domains: List<String> = emptyList(),
    val visualLabels: List<String> = emptyList(),
    val barcodeResults: List<String> = emptyList()
)

data class CategoryRule(
    val categoryName: String,
    val destinationRelativeFolder: String,
    val conditionOperator: RuleConditionOperator,
    val conditionValues: List<String>,
    val priority: Int = 0,
    val useWebEnrichmentFallback: Boolean = false,
    val sparseOcrCharacterThreshold: Int = 20,
    val enabled: Boolean = true
)

data class CategoryResult(
    val categoryName: String,
    val destinationRelativeFolder: String,
    val tags: List<String>,
    val matchedRule: CategoryRule? = null,
    val usedEnrichment: Boolean = false,
    val enrichmentRecommended: Boolean = false
)

package com.screenarc.app.util

import com.screenarc.app.model.SmartEntity

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
    EMAIL,
    ADDRESS
}

/**
 * Adapter and legacy facade delegating to the unified [EntityParser].
 * Retained for backwards-compatibility across existing modules and tests.
 */
object SmartEntityExtractor {

    fun extractEntities(text: String): List<ExtractedEntity> {
        val parsed = EntityParser.parseText(text)
        return parsed.map { entity ->
            when (entity) {
                is SmartEntity.Url -> ExtractedEntity(
                    type = EntityType.URL,
                    value = entity.cleanUrl,
                    label = "Open Link"
                )
                is SmartEntity.Upi -> ExtractedEntity(
                    type = EntityType.UPI,
                    value = entity.rawValue,
                    label = if (entity.amount != null) "Copy UPI (${entity.amount})" else "Copy UPI ID"
                )
                is SmartEntity.Otp -> ExtractedEntity(
                    type = EntityType.OTP,
                    value = entity.rawValue,
                    label = "Copy Code (${entity.rawValue})"
                )
                is SmartEntity.Email -> ExtractedEntity(
                    type = EntityType.EMAIL,
                    value = entity.rawValue,
                    label = "Send Email"
                )
                is SmartEntity.PhoneNumber -> ExtractedEntity(
                    type = EntityType.PHONE,
                    value = entity.rawValue,
                    label = "Dial (${entity.rawValue})"
                )
                is SmartEntity.Address -> ExtractedEntity(
                    type = EntityType.ADDRESS,
                    value = entity.rawValue,
                    label = "Open Maps"
                )
                is SmartEntity.Barcode -> ExtractedEntity(
                    type = EntityType.URL,
                    value = entity.rawValue,
                    label = "Open QR"
                )
            }
        }
    }

    fun hasOtp(text: String): Boolean = EntityParser.hasOtp(text)
}

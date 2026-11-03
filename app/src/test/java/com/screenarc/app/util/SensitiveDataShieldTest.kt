package com.screenarc.app.util

import com.screenarc.app.model.OcrBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataShieldTest {

    @Test
    fun testLuhnAlgorithm_validAndInvalidCards() {
        // Known valid test card numbers (standard test vectors)
        assertTrue(SensitiveDataShield.isValidLuhn("4532015112830366"))
        assertTrue(SensitiveDataShield.isValidLuhn("5425233430109903"))
        assertTrue(SensitiveDataShield.isValidLuhn("378282246310005"))

        // Invalid numbers
        assertFalse(SensitiveDataShield.isValidLuhn("4532015112830367"))
        assertFalse(SensitiveDataShield.isValidLuhn("1234567890123456"))
        assertFalse(SensitiveDataShield.isValidLuhn("12345")) // too short
    }

    @Test
    fun testScanCreditCardInText() {
        val text = "Your payment card is 4532-0151-1283-0366 for this purchase"
        val findings = SensitiveDataShield.scanText(text)
        assertTrue("Should detect credit card", findings.any { it.type == SensitiveType.CREDIT_CARD })
    }

    @Test
    fun testScanCvvAndPasswords() {
        val text = "Security details: CVV: 842 and Password: SuperSecret#2026 for account"
        val findings = SensitiveDataShield.scanText(text)
        assertTrue("Should detect CVV", findings.any { it.type == SensitiveType.CVV })
        assertTrue("Should detect Password", findings.any { it.type == SensitiveType.PASSWORD_OR_PIN })
    }

    @Test
    fun testScanNationalIds() {
        val ssnText = "Tax SSN on file: 123-45-6789"
        val ssnFindings = SensitiveDataShield.scanText(ssnText)
        assertTrue("Should detect SSN", ssnFindings.any { it.type == SensitiveType.NATIONAL_ID })

        val aadhaarText = "Aadhaar Card: 1234 5678 9012"
        val aadhaarFindings = SensitiveDataShield.scanText(aadhaarText)
        assertTrue("Should detect Aadhaar", aadhaarFindings.any { it.type == SensitiveType.NATIONAL_ID })
    }

    @Test
    fun testScanApiKeysAndTokens() {
        val stripeKey = "sk_live_51Jabc1234567890abcdefghijklmnopqrstuvwxyz"
        val findings = SensitiveDataShield.scanText("Initialize client with key: $stripeKey")
        assertTrue("Should detect API key", findings.any { it.type == SensitiveType.API_KEY })
    }

    @Test
    fun testScanBlocksAndCreateAutoRedactions() {
        val blocks = listOf(
            OcrBlock(
                text = "Card: 4532015112830366",
                left = 50,
                top = 100,
                right = 300,
                bottom = 140
            ),
            OcrBlock(
                text = "Welcome to our shop",
                left = 50,
                top = 20,
                right = 250,
                bottom = 60
            ),
            OcrBlock(
                text = "CVV: 789",
                left = 50,
                top = 160,
                right = 150,
                bottom = 190
            )
        )

        val redactions = SensitiveDataShield.createAutoRedactions(blocks, RedactionType.BLACKOUT)
        assertEquals("Should create redactions for the 2 sensitive blocks", 2, redactions.size)
        assertEquals(RedactionType.BLACKOUT, redactions[0].type)
        assertEquals("Card: 4532015112830366", redactions[0].block.text)
        assertEquals("CVV: 789", redactions[1].block.text)
    }
}

package com.screenarc.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEntityExtractorTest {

    @Test
    fun testExtractUrls() {
        val text = "Visit our website at https://screenarc.app or check www.github.com/screenarc for documentation."
        val entities = SmartEntityExtractor.extractEntities(text)
        val urls = entities.filter { it.type == EntityType.URL }

        assertTrue(urls.any { it.value == "https://screenarc.app" })
        assertTrue(urls.any { it.value == "https://www.github.com/screenarc" })
    }

    @Test
    fun testExtractUpiId() {
        val text = "Payment request for ₹450 sent to user.name@okhdfcbank successfully."
        val entities = SmartEntityExtractor.extractEntities(text)
        val upis = entities.filter { it.type == EntityType.UPI }

        assertEquals(1, upis.size)
        assertEquals("user.name@okhdfcbank", upis[0].value)
        assertTrue(upis[0].label.contains("₹450"))
    }

    @Test
    fun testExtractOtpCode() {
        val text = "Your verification OTP is 482910. Do not share this code with anyone."
        val entities = SmartEntityExtractor.extractEntities(text)
        val otps = entities.filter { it.type == EntityType.OTP }

        assertEquals(1, otps.size)
        assertEquals("482910", otps[0].value)
        assertTrue(SmartEntityExtractor.hasOtp(text))
    }

    @Test
    fun testExtractEmailAndPhone() {
        val text = "Contact support@example.com or dial +1-555-123-4567 for inquiries."
        val entities = SmartEntityExtractor.extractEntities(text)

        val emails = entities.filter { it.type == EntityType.EMAIL }
        val phones = entities.filter { it.type == EntityType.PHONE }

        assertEquals(1, emails.size)
        assertEquals("support@example.com", emails[0].value)
        assertEquals(1, phones.size)
    }

    @Test
    fun testNoEntities() {
        val text = "Just some standard non-sensitive screenshot text here."
        val entities = SmartEntityExtractor.extractEntities(text)
        assertTrue(entities.isEmpty())
        assertFalse(SmartEntityExtractor.hasOtp(text))
    }
}

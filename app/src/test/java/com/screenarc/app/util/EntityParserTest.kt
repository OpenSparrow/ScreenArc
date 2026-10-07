package com.screenarc.app.util

import android.content.Context
import android.content.Intent
import com.screenarc.app.model.SmartEntity
import com.screenarc.app.model.SmartEntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class EntityParserTest {

    private val mockContext: Context = Mockito.mock(Context::class.java)

    @Test
    fun testParseUrls() {
        val text = """
            Check out https://screenarc.app/download and visit our documentation at
            www.github.com/screenarc/docs or try android.com/reference for developer APIs.
        """.trimIndent()

        val urls = EntityParser.extractUrls(text)

        assertEquals(3, urls.size)
        assertTrue(urls.any { it.cleanUrl == "https://screenarc.app/download" })
        assertTrue(urls.any { it.cleanUrl == "https://www.github.com/screenarc/docs" })
        assertTrue(urls.any { it.cleanUrl == "https://android.com/reference" })

        // Check intent metadata and properties
        val primary = urls.first { it.cleanUrl.contains("screenarc.app") }
        assertEquals("Open Link", primary.displayLabel)
        assertEquals(SmartEntityType.URL, primary.type)
        assertEquals(Intent.ACTION_VIEW, primary.intentAction)
        assertEquals("https://screenarc.app/download", primary.actionUriString)
        assertNotNull(primary.createIntent(mockContext))
    }

    @Test
    fun testEmailParsingDoesNotDuplicateAsUrl() {
        val text = "Please reach out to support@screenarc.app or dev.team+feedback@company.co.uk immediately."
        val entities = EntityParser.parseText(text)

        val emails = entities.filterIsInstance<SmartEntity.Email>()
        val urls = entities.filterIsInstance<SmartEntity.Url>()

        assertEquals(2, emails.size)
        assertTrue(emails.any { it.rawValue == "support@screenarc.app" })
        assertTrue(emails.any { it.rawValue == "dev.team+feedback@company.co.uk" })

        // The domain of the email should NOT be added as a separate URL
        assertFalse(urls.any { it.rawValue == "screenarc.app" })
        assertFalse(urls.any { it.rawValue == "company.co.uk" })

        val primaryEmail = emails.first { it.rawValue == "support@screenarc.app" }
        assertEquals(Intent.ACTION_SENDTO, primaryEmail.intentAction)
        assertEquals("mailto:support@screenarc.app", primaryEmail.actionUriString)
        assertEquals("Email Address", primaryEmail.getClipLabel())
        assertNotNull(primaryEmail.createIntent(mockContext))
    }

    @Test
    fun testParsePhoneNumbers() {
        val text = """
            Call customer support at +1 (800) 555-0199 or international line +91 98765 43210.
            Local desk: 555-123-4567.
            Meeting date is 2024-10-08 and local IP is 192.168.1.1 at 12:30:00.
        """.trimIndent()

        val phones = EntityParser.extractPhoneNumbers(text)

        // Dates, IPs, and timestamps should NOT be parsed as phone numbers
        assertTrue(phones.none { it.rawValue.contains("2024-10-08") })
        assertTrue(phones.none { it.rawValue.contains("192.168.1.1") })
        assertTrue(phones.none { it.rawValue.contains("12:30:00") })

        assertTrue(phones.any { it.normalizedNumber.contains("18005550199") })
        assertTrue(phones.any { it.normalizedNumber.contains("919876543210") })
        assertTrue(phones.any { it.normalizedNumber.contains("5551234567") })

        val primaryPhone = phones.first { it.normalizedNumber.contains("18005550199") }
        assertEquals(Intent.ACTION_DIAL, primaryPhone.intentAction)
        assertTrue(primaryPhone.actionUriString?.startsWith("tel:") == true)
        assertEquals("Phone Number", primaryPhone.getClipLabel())
        assertNotNull(primaryPhone.createIntent(mockContext))
    }

    @Test
    fun testParsePhysicalAddressesAndLocations() {
        val text = """
            Office HQ located at 1600 Amphitheatre Pkwy, Mountain View, CA 94043.
            Branch office: 10 Downing Street, London.
            Residential location: 742 Evergreen Terrace.
            GPS pin: geo:37.7749,-122.4194
            Directions: https://maps.google.com/?q=Central+Park
        """.trimIndent()

        val addresses = EntityParser.extractAddresses(text)

        assertTrue(addresses.isNotEmpty())
        assertTrue(addresses.any { it.rawValue.contains("1600 Amphitheatre Pkwy") })
        assertTrue(addresses.any { it.rawValue.contains("10 Downing Street") })
        assertTrue(addresses.any { it.rawValue.contains("geo:37.7749,-122.4194") })

        val mapEntity = addresses.first { it.rawValue.contains("1600 Amphitheatre Pkwy") }
        assertEquals(Intent.ACTION_VIEW, mapEntity.intentAction)
        assertTrue(mapEntity.actionUriString?.startsWith("geo:0,0?q=") == true)
        assertEquals("Street Address", mapEntity.getClipLabel())
        assertNotNull(mapEntity.createIntent(mockContext))
    }

    @Test
    fun testParseUpiAndOtp() {
        val text = "Transfer ₹750 to merchant.store@icici. Your security verification OTP is 924185."

        val entities = EntityParser.parseText(text)
        val upis = entities.filterIsInstance<SmartEntity.Upi>()
        val otps = entities.filterIsInstance<SmartEntity.Otp>()

        assertEquals(1, upis.size)
        assertEquals("merchant.store@icici", upis[0].rawValue)
        assertEquals("₹750", upis[0].amount)
        assertTrue(upis[0].displayLabel.contains("₹750"))
        assertEquals(Intent.ACTION_VIEW, upis[0].intentAction)
        assertTrue(upis[0].actionUriString?.startsWith("upi://pay") == true)
        assertNotNull(upis[0].createIntent(mockContext))

        assertEquals(1, otps.size)
        assertEquals("924185", otps[0].rawValue)
        assertTrue(EntityParser.hasOtp(text))

        // OTP has no direct intent and copies to clipboard instead
        assertNull(otps[0].intentAction)
        assertNull(otps[0].createIntent(mockContext))
        assertEquals("OTP Code", otps[0].getClipLabel())
    }

    @Test
    fun testBarcodesAndQrPayload() {
        val qrWifi = "WIFI:T:WPA;S:OfficeNet;P:SecretKey123;;"
        val entities = EntityParser.parseText(qrWifi)

        val barcodes = entities.filterIsInstance<SmartEntity.Barcode>()
        assertEquals(1, barcodes.size)
        assertEquals(qrWifi, barcodes[0].rawValue)
        assertEquals("QR_CODE", barcodes[0].format)
        assertNotNull(barcodes[0].createIntent(mockContext))

        val constructedBarcode = EntityParser.fromBarcode("https://screenarc.app/join", format = "QR_CODE")
        assertEquals(Intent.ACTION_VIEW, constructedBarcode.intentAction)
        assertEquals("https://screenarc.app/join", constructedBarcode.actionUriString)
        assertNotNull(constructedBarcode.createIntent(mockContext))
    }

    @Test
    fun testDeduplicationAndEmptyText() {
        assertTrue(EntityParser.parseText("").isEmpty())
        assertTrue(EntityParser.parseText("   ").isEmpty())

        val repeated = """
            Visit https://screenarc.app and https://screenarc.app again.
            Email test@example.com or test@example.com.
        """.trimIndent()

        val parsed = EntityParser.parseText(repeated)
        val urls = parsed.filterIsInstance<SmartEntity.Url>()
        val emails = parsed.filterIsInstance<SmartEntity.Email>()

        assertEquals(1, urls.size)
        assertEquals(1, emails.size)
    }
}

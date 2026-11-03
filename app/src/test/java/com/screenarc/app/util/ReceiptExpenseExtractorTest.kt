package com.screenarc.app.util

import com.screenarc.app.model.ExpenseCategory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptExpenseExtractorTest {

    @Test
    fun testAmazonReceiptExtraction() {
        val amazonText = """
            Amazon.com
            Order #112-9876543-1234567
            Order Date: Nov 03, 2026
            Shipping Address: 123 Tech Park, CA
            Items Ordered:
            1x Wireless Noise Cancelling Headphones - $149.99
            Subtotal: $149.99
            Shipping: Free
            Tax: $12.50
            Total Amount: $162.49
            Payment Method: Visa ending in 4321
        """.trimIndent()

        val item = ReceiptExpenseExtractor.extractExpense(
            rawText = amazonText,
            screenshotId = 1001L,
            screenshotUri = "content://media/external/images/media/1001",
            fileName = "amazon_receipt.png"
        )

        assertNotNull(item)
        assertEquals("Amazon", item!!.merchantName)
        assertEquals(162.49, item.amount, 0.001)
        assertEquals("$", item.currencySymbol)
        assertEquals("USD", item.currencyCode)
        assertEquals("$162.49", item.formattedAmount)
        assertEquals(ExpenseCategory.SHOPPING, item.category)
        assertEquals("112-9876543-1234567", item.transactionId)
        assertEquals("Visa", item.paymentMethod)
        assertNotNull(item.dateString)
    }

    @Test
    fun testUberRideReceiptExtraction() {
        val uberText = """
            Uber Technologies Inc.
            Thanks for riding with Uber!
            Date: 03/11/2026
            Trip Total: ₹485.00
            Payment: Google Pay UPI (Transaction ID: upi-87654321)
            From: Central Station
            To: Airport Terminal 2
        """.trimIndent()

        val item = ReceiptExpenseExtractor.extractExpense(
            rawText = uberText,
            screenshotId = 1002L,
            screenshotUri = "content://media/external/images/media/1002",
            fileName = "uber_ride.png"
        )

        assertNotNull(item)
        assertEquals("Uber", item!!.merchantName)
        assertEquals(485.0, item.amount, 0.001)
        assertEquals("₹", item.currencySymbol)
        assertEquals("INR", item.currencyCode)
        assertEquals("₹485", item.formattedAmount)
        assertEquals(ExpenseCategory.TRAVEL_AND_TRANSPORT, item.category)
        assertEquals("upi-87654321", item.transactionId)
    }

    @Test
    fun testSwiggyFoodDeliveryReceipt() {
        val swiggyText = """
            Swiggy Order Summary
            Restaurant: Starbucks Coffee
            Date: 2026-11-03
            1x Caramel Frappuccino - ₹350
            1x Blueberry Muffin - ₹220
            Delivery Fee: ₹40
            Total Amount Paid: ₹610.00
            Paid via PhonePe
        """.trimIndent()

        val item = ReceiptExpenseExtractor.extractExpense(
            rawText = swiggyText,
            screenshotId = 1003L,
            screenshotUri = "content://media/external/images/media/1003",
            fileName = "swiggy_order.png"
        )

        assertNotNull(item)
        assertTrue(item!!.merchantName == "Swiggy" || item.merchantName == "Starbucks")
        assertEquals(610.0, item.amount, 0.001)
        assertEquals(ExpenseCategory.FOOD_AND_DINING, item.category)
        assertEquals("₹610", item.formattedAmount)
    }

    @Test
    fun testNetflixSubscriptionExtraction() {
        val netflixText = """
            Netflix Billing Notification
            Thank you for being a member.
            Your plan renewed on: 15 Oct 2026
            Billed Amount: €17.99
            Payment Method: Mastercard
            Next bill date: 15 Nov 2026
        """.trimIndent()

        val item = ReceiptExpenseExtractor.extractExpense(
            rawText = netflixText,
            screenshotId = 1004L,
            screenshotUri = "content://media/external/images/media/1004",
            fileName = "netflix_invoice.png"
        )

        assertNotNull(item)
        assertEquals("Netflix", item!!.merchantName)
        assertEquals(17.99, item.amount, 0.001)
        assertEquals("€", item.currencySymbol)
        assertEquals("EUR", item.currencyCode)
        assertEquals(ExpenseCategory.ENTERTAINMENT, item.category)
        assertEquals("Mastercard", item.paymentMethod)
    }

    @Test
    fun testUtilityBillExtraction() {
        val billText = """
            Electricity Bill Receipt
            Merchant: PG&E Power
            Bill Date: 2026-10-28
            Account No: 9876543210
            Total Due & Paid: $124.50
        """.trimIndent()

        val item = ReceiptExpenseExtractor.extractExpense(
            rawText = billText,
            screenshotId = 1005L,
            screenshotUri = "content://media/external/images/media/1005",
            fileName = "electric_bill.png"
        )

        assertNotNull(item)
        assertEquals(124.50, item!!.amount, 0.001)
        assertEquals(ExpenseCategory.BILLS_AND_UTILITIES, item.category)
    }

    @Test
    fun testRejectNonReceiptScreenshot() {
        val chatText = """
            Hey, are we still meeting today at 5 PM?
            Yeah, see you at the library! Don't forget your laptop.
            Sure thing, see you soon.
        """.trimIndent()

        val item = ReceiptExpenseExtractor.extractExpense(
            rawText = chatText,
            screenshotId = 1006L,
            screenshotUri = "content://media/external/images/media/1006",
            fileName = "chat_message.png"
        )

        assertNull("Chat message should not be classified as expense", item)
    }

    @Test
    fun testComputeSummaryAggregation() {
        val expenses = listOf(
            ReceiptExpenseExtractor.extractExpense(
                rawText = "Amazon Order Total: $100.00",
                screenshotId = 1L,
                screenshotUri = "uri1",
                fileName = "a.png"
            )!!,
            ReceiptExpenseExtractor.extractExpense(
                rawText = "Uber Ride Total: $25.50",
                screenshotId = 2L,
                screenshotUri = "uri2",
                fileName = "b.png"
            )!!,
            ReceiptExpenseExtractor.extractExpense(
                rawText = "Chipotle Lunch Total: $14.50",
                screenshotId = 3L,
                screenshotUri = "uri3",
                fileName = "c.png"
            )!!
        )

        val summary = ReceiptExpenseExtractor.computeSummary(expenses)
        assertEquals(140.0, summary.totalAmount, 0.001)
        assertEquals(3, summary.totalReceiptsCount)
        assertEquals("$140", summary.formattedTotal)
        assertEquals(100.0, summary.categoryBreakdown[ExpenseCategory.SHOPPING] ?: 0.0, 0.001)
        assertEquals(25.50, summary.categoryBreakdown[ExpenseCategory.TRAVEL_AND_TRANSPORT] ?: 0.0, 0.001)
        assertEquals(14.50, summary.categoryBreakdown[ExpenseCategory.FOOD_AND_DINING] ?: 0.0, 0.001)
    }

    @Test
    fun testCsvExportFormat() {
        val expenses = listOf(
            ReceiptExpenseExtractor.extractExpense(
                rawText = "Amazon Order #123 Total: $100.00 Date: 2026-11-03",
                screenshotId = 1L,
                screenshotUri = "content://images/1",
                fileName = "amazon.png"
            )!!
        )

        val csv = ReceiptExpenseExtractor.exportToCsv(expenses)
        assertTrue(csv.contains("Date,Merchant,Category,Amount,Currency,Formatted Amount,Transaction ID,Payment Method,File Name,Screenshot URI"))
        assertTrue(csv.contains("Amazon"))
        assertTrue(csv.contains("100.0"))
        assertTrue(csv.contains("USD"))
    }

    @Test
    fun testJsonExportFormat() {
        val expenses = listOf(
            ReceiptExpenseExtractor.extractExpense(
                rawText = "Netflix Billing Total: €17.99 Date: 2026-10-15",
                screenshotId = 1L,
                screenshotUri = "content://images/2",
                fileName = "netflix.png"
            )!!
        )

        val jsonString = ReceiptExpenseExtractor.exportToJson(expenses)
        val json = JSONObject(jsonString)
        assertEquals("ScreenArc", json.getString("app"))
        assertEquals("SmartExpenseLedger", json.getString("feature"))
        assertEquals(1, json.getInt("totalCount"))
        assertEquals(17.99, json.getDouble("totalAmount"), 0.001)
    }
}

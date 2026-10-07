package com.screenarc.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    @Test
    fun testHammingDistance() {
        val hashA = 0b10101010L
        val hashB = 0b10101011L
        assertEquals(1, DuplicateDetector.hammingDistance(hashA, hashB))

        val hashC = 0b11110000L
        val hashD = 0b00001111L
        assertEquals(8, DuplicateDetector.hammingDistance(hashC, hashD))
    }

    @Test
    fun testJaccardSimilarity_identicalText() {
        val text1 = "Order confirmed for Amazon Delivery on Friday"
        val text2 = "Order confirmed for Amazon Delivery on Friday"
        val similarity = DuplicateDetector.computeJaccardSimilarity(text1, text2)
        assertEquals(1.0f, similarity, 0.001f)
    }

    @Test
    fun testJaccardSimilarity_highOverlap() {
        val text1 = "Order #12345 confirmed for Amazon Delivery on Friday 10 AM"
        val text2 = "Order #12345 confirmed for Amazon Delivery on Friday 10:01 AM"
        val similarity = DuplicateDetector.computeJaccardSimilarity(text1, text2)
        assertTrue("Similarity should be > 0.80, was $similarity", similarity > 0.80f)
    }

    @Test
    fun testJaccardSimilarity_disjointText() {
        val text1 = "Flight ticket booking from New Delhi to London"
        val text2 = "Grocery shopping list milk eggs bread cheese"
        val similarity = DuplicateDetector.computeJaccardSimilarity(text1, text2)
        assertEquals(0.0f, similarity, 0.001f)
    }
}

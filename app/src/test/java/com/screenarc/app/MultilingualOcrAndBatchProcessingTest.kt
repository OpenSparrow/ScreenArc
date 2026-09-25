package com.screenarc.app

import com.screenarc.app.data.OcrRepositoryImpl
import com.screenarc.app.data.local.db.entity.OcrTextEntity
import com.screenarc.app.model.OcrBatchProgress
import com.screenarc.app.model.OcrBlock
import com.screenarc.app.model.ScreenshotSearchQuery
import com.screenarc.app.model.ScreenshotUiModel
import com.screenarc.app.ocr.MlKitOcrProvider
import com.screenarc.app.ocr.OcrLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean

class MultilingualOcrAndBatchProcessingTest {

    // =========================================================================
    // 1. Unicode & Script Normalization Tests
    // =========================================================================

    @Test
    fun testEnglishNormalization() {
        val raw = "  Hello   WORLD!\nThis is a TEST.\r\n "
        val normalized = OcrTextEntity.normalizeText(raw)
        assertEquals("hello world! this is a test.", normalized)
    }

    @Test
    fun testDevanagariNormalization() {
        val raw = "  नमस्ते   दुनिया! \nस्वागत है।  "
        val normalized = OcrTextEntity.normalizeText(raw)
        assertEquals("नमस्ते दुनिया! स्वागत है।", normalized)
    }

    @Test
    fun testMixedDevanagariAndEnglishNormalization() {
        val raw = "Hello World\nनमस्ते दुनिया\r\nOrder ID: #98765"
        val normalized = OcrTextEntity.normalizeText(raw)
        assertEquals("hello world नमस्ते दुनिया order id: #98765", normalized)
    }

    @Test
    fun testIndianNativeLanguagesNormalization() {
        // Tamil
        val tamil = "வணக்கம்   தமிழ்நாடு\nவாழ்க"
        val normalizedTamil = OcrTextEntity.normalizeText(tamil)
        assertEquals("வணக்கம் தமிழ்நாடு வாழ்க", normalizedTamil)

        // Telugu
        val telugu = "నమస్కారం   ఆంధ్రప్రదేశ్\n"
        val normalizedTelugu = OcrTextEntity.normalizeText(telugu)
        assertEquals("నమస్కారం ఆంధ్రప్రదేశ్", normalizedTelugu)

        // Kannada
        val kannada = "ನಮಸ್ಕಾರ   ಕರ್ನಾಟಕ\n"
        val normalizedKannada = OcrTextEntity.normalizeText(kannada)
        assertEquals("ನಮಸ್ಕಾರ ಕರ್ನಾಟಕ", normalizedKannada)

        // Malayalam
        val malayalam = "നമസ്കാരം   കേരളം\n"
        val normalizedMalayalam = OcrTextEntity.normalizeText(malayalam)
        assertEquals("നമസ്കാരം കേരളം", normalizedMalayalam)

        // Bengali
        val bengali = "নমস্কার   পশ্চিমবঙ্গ\n"
        val normalizedBengali = OcrTextEntity.normalizeText(bengali)
        assertEquals("নমস্কার পশ্চিমবঙ্গ", normalizedBengali)
    }

    @Test
    fun testUnicodeCombiningCharactersEquivalence() {
        // Decomposed vs precomposed forms in Unicode must normalize to identical representation under NFKC
        val precomposed = "क़िताब"
        val decomposed = "क\u093C\u093Fताब" // 'क' + Nukta + 'ि' + 'त' + 'ा' + 'ब'

        val normPre = OcrTextEntity.normalizeText(precomposed)
        val normDec = OcrTextEntity.normalizeText(decomposed)

        assertEquals(normPre, normDec)
    }

    // =========================================================================
    // 2. Multilingual Search Matching Tests
    // =========================================================================

    @Test
    fun testDevanagariSearchMatching() {
        val item = ScreenshotUiModel(
            id = 101L,
            uri = "content://media/external/images/media/101",
            displayName = "hindi_invoice_2026.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1710000000L,
            size = 2048L,
            ocrStatus = "COMPLETED",
            recognizedText = "नमस्ते दुनिया आपका बिल ₹1500 है",
            normalizedText = OcrTextEntity.normalizeText("नमस्ते दुनिया आपका बिल ₹1500 है")
        )

        // Exact match
        assertTrue(matchesSearch(item, ScreenshotSearchQuery(text = "नमस्ते")))
        // Substring match
        assertTrue(matchesSearch(item, ScreenshotSearchQuery(text = "दुनिया")))
        // Currency / numeric match
        assertTrue(matchesSearch(item, ScreenshotSearchQuery(text = "₹1500")))
        // Mismatch
        assertFalse(matchesSearch(item, ScreenshotSearchQuery(text = "धन्यवाद")))
    }

    @Test
    fun testBilingualMixedSearchMatching() {
        val item = ScreenshotUiModel(
            id = 102L,
            uri = "content://media/external/images/media/102",
            displayName = "bilingual_note.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1710000000L,
            size = 2048L,
            ocrStatus = "COMPLETED",
            recognizedText = "Hello World\nनमस्ते भारत",
            normalizedText = OcrTextEntity.normalizeText("Hello World\nनमस्ते भारत")
        )

        // Match English term
        assertTrue(matchesSearch(item, ScreenshotSearchQuery(text = "hello")))
        assertTrue(matchesSearch(item, ScreenshotSearchQuery(text = "WORLD")))
        // Match Devanagari term
        assertTrue(matchesSearch(item, ScreenshotSearchQuery(text = "नमस्ते")))
        assertTrue(matchesSearch(item, ScreenshotSearchQuery(text = "भारत")))
    }

    @Test
    fun testTamilSearchMatching() {
        val item = ScreenshotUiModel(
            id = 103L,
            uri = "content://media/external/images/media/103",
            displayName = "chennai_screenshot.png",
            relativePath = "Pictures/Screenshots",
            dateAdded = 1710000000L,
            size = 2048L,
            ocrStatus = "COMPLETED",
            recognizedText = "வணக்கம் நண்பா சென்னை பயணம்",
            normalizedText = OcrTextEntity.normalizeText("வணக்கம் நண்பா சென்னை பயணம்")
        )

        assertTrue(matchesSearch(item, ScreenshotSearchQuery(text = "வணக்கம்")))
        assertTrue(matchesSearch(item, ScreenshotSearchQuery(text = "சென்னை")))
        assertFalse(matchesSearch(item, ScreenshotSearchQuery(text = "மதுரை")))
    }

    // =========================================================================
    // 3. OCR Engine & Language Abstraction Tests
    // =========================================================================

    @Test
    fun testOcrLanguageEnumResolution() {
        assertEquals(OcrLanguage.DEVANAGARI, OcrLanguage.fromId("devanagari"))
        assertEquals(OcrLanguage.DEVANAGARI, OcrLanguage.fromId("DEVANAGARI"))
        assertEquals(OcrLanguage.LATIN, OcrLanguage.fromId("latin"))
        assertEquals(OcrLanguage.LATIN, OcrLanguage.fromId("LATIN"))
        // Fallback default
        assertEquals(OcrLanguage.DEVANAGARI, OcrLanguage.fromId("non_existent_id"))
        assertEquals(OcrLanguage.DEVANAGARI, OcrLanguage.fromId(null))
    }

    @Test
    fun testMlKitOcrProviderAttributes() {
        val provider = MlKitOcrProvider()
        assertEquals("Google ML Kit (On-Device)", provider.engineName)
        assertTrue(provider.supportedLanguages.contains(OcrLanguage.DEVANAGARI))
        assertTrue(provider.supportedLanguages.contains(OcrLanguage.LATIN))
        assertTrue(provider.isLanguageAvailable(OcrLanguage.DEVANAGARI))
        assertTrue(provider.isLanguageAvailable(OcrLanguage.LATIN))
    }

    // =========================================================================
    // 4. Batch Processing Logic & Progress Tests
    // =========================================================================

    @Test
    fun testOcrBatchProgressFraction() {
        val p0 = OcrBatchProgress(total = 0, current = 0)
        assertEquals(0.0f, p0.progressFraction, 0.001f)

        val p1 = OcrBatchProgress(total = 100, current = 50)
        assertEquals(0.5f, p1.progressFraction, 0.001f)

        val p2 = OcrBatchProgress(total = 100, current = 100)
        assertEquals(1.0f, p2.progressFraction, 0.001f)
    }

    @Test
    fun testBatchProcessingLogicWithMockItems() {
        data class MockItem(val name: String, val isIndexed: Boolean, val willFail: Boolean)

        fun simulateBatch(
            items: List<MockItem>,
            forceReindex: Boolean,
            cancelAt: Int? = null
        ): OcrBatchProgress {
            val toProcess = if (forceReindex) items else items.filter { !it.isIndexed }
            val skipped = items.size - toProcess.size

            if (toProcess.isEmpty()) {
                return OcrBatchProgress(
                    isRunning = false,
                    total = items.size,
                    current = items.size,
                    indexedCount = 0,
                    skippedCount = skipped,
                    failedCount = 0,
                    isCompleted = true,
                    isCancelled = false
                )
            }

            var indexed = 0
            var failed = 0
            var isCancelled = false

            for ((idx, item) in toProcess.withIndex()) {
                if (cancelAt != null && idx == cancelAt) {
                    isCancelled = true
                    break
                }
                if (item.willFail) {
                    failed++
                } else {
                    indexed++
                }
            }

            return OcrBatchProgress(
                isRunning = false,
                total = toProcess.size,
                current = if (isCancelled) cancelAt ?: 0 else toProcess.size,
                indexedCount = indexed,
                skippedCount = skipped,
                failedCount = failed,
                isCompleted = !isCancelled,
                isCancelled = isCancelled
            )
        }

        // Test 1: 0 items
        val emptyResult = simulateBatch(emptyList(), forceReindex = false)
        assertEquals(0, emptyResult.total)
        assertEquals(0, emptyResult.indexedCount)
        assertTrue(emptyResult.isCompleted)

        // Test 2: 1 image successful
        val oneItem = listOf(MockItem("img1.png", isIndexed = false, willFail = false))
        val oneResult = simulateBatch(oneItem, forceReindex = false)
        assertEquals(1, oneResult.total)
        assertEquals(1, oneResult.indexedCount)
        assertEquals(0, oneResult.failedCount)
        assertEquals(0, oneResult.skippedCount)
        assertTrue(oneResult.isCompleted)

        // Test 3: Multiple images with already-indexed items
        val mixedItems = listOf(
            MockItem("img1.png", isIndexed = true, willFail = false),
            MockItem("img2.png", isIndexed = true, willFail = false),
            MockItem("img3.png", isIndexed = false, willFail = false),
            MockItem("img4.png", isIndexed = false, willFail = false),
            MockItem("img5.png", isIndexed = false, willFail = false)
        )
        val mixedResult = simulateBatch(mixedItems, forceReindex = false)
        assertEquals(3, mixedResult.total) // only unindexed
        assertEquals(2, mixedResult.skippedCount)
        assertEquals(3, mixedResult.indexedCount)
        assertEquals(0, mixedResult.failedCount)

        // Test 4: Mixed successful and failed images (Failed image must NOT stop the batch)
        val itemsWithErrors = listOf(
            MockItem("good1.png", isIndexed = false, willFail = false),
            MockItem("corrupt2.png", isIndexed = false, willFail = true),
            MockItem("good3.png", isIndexed = false, willFail = false),
            MockItem("corrupt4.png", isIndexed = false, willFail = true),
            MockItem("good5.png", isIndexed = false, willFail = false)
        )
        val errorResult = simulateBatch(itemsWithErrors, forceReindex = true)
        assertEquals(5, errorResult.total)
        assertEquals(3, errorResult.indexedCount)
        assertEquals(2, errorResult.failedCount)
        assertTrue(errorResult.isCompleted)

        // Test 5: Cancellation mid-batch
        val cancelledResult = simulateBatch(itemsWithErrors, forceReindex = true, cancelAt = 2)
        assertTrue(cancelledResult.isCancelled)
        assertFalse(cancelledResult.isCompleted)
        assertEquals(2, cancelledResult.current)
    }

    // =========================================================================
    // 5. JSON OCR Block Parsing Tests
    // =========================================================================

    @Test
    fun testParseBlocksFromJson() {
        val validJson = """[
            {"text": "नमस्ते दुनिया", "left": 10, "top": 20, "right": 200, "bottom": 60},
            {"text": "Invoice #42", "left": 15, "top": 80, "right": 150, "bottom": 120}
        ]"""

        val blocks = OcrRepositoryImpl.parseBlocksFromJson(validJson)
        assertEquals(2, blocks.size)
        assertEquals("नमस्ते दुनिया", blocks[0].text)
        assertEquals(10, blocks[0].left)
        assertEquals(20, blocks[0].top)
        assertEquals(200, blocks[0].right)
        assertEquals(60, blocks[0].bottom)

        assertEquals("Invoice #42", blocks[1].text)

        // Empty and malformed handling
        assertEquals(0, OcrRepositoryImpl.parseBlocksFromJson(null).size)
        assertEquals(0, OcrRepositoryImpl.parseBlocksFromJson("").size)
        assertEquals(0, OcrRepositoryImpl.parseBlocksFromJson("{ invalid json }").size)
    }

    private fun matchesSearch(item: ScreenshotUiModel, filter: ScreenshotSearchQuery): Boolean {
        val q = filter.text.trim()
        val normalizedQ = OcrTextEntity.normalizeText(q)
        if (q.isNotEmpty()) {
            val nameMatch = filter.includeFilename && item.displayName.lowercase().contains(q.lowercase())
            val ocrMatch = filter.includeOcrText && (
                item.recognizedText?.contains(q) == true ||
                item.normalizedText?.contains(normalizedQ) == true
            )
            if (!nameMatch && !ocrMatch) return false
        }
        return true
    }
}

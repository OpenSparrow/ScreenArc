package com.screenarc.app

import com.screenarc.app.service.ScreenshotDetection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotDetectionTest {
    @Test
    fun recognizesScreenshotByNameOrDirectory() {
        assertTrue(ScreenshotDetection.isScreenshotCandidate("Screenshot_20261001.png", "Pictures"))
        assertTrue(ScreenshotDetection.isScreenshotCandidate("image.png", "Pictures/Screenshots"))
        assertTrue(ScreenshotDetection.isScreenshotCandidate("Screenshot.png", "DCIM/Camera"))
        assertFalse(ScreenshotDetection.isScreenshotCandidate("vacation.png", "DCIM/Camera"))
    }

    @Test
    fun ignoresCategorizedFoldersButAcceptsScreenshotRoot() {
        assertFalse(ScreenshotDetection.isCategorizedSubfolder("Pictures/Screenshots", scopedStorage = true))
        assertFalse(ScreenshotDetection.isCategorizedSubfolder("Pictures/Screenshots/", scopedStorage = true))
        assertTrue(ScreenshotDetection.isCategorizedSubfolder("Pictures/Screenshots/Receipts", scopedStorage = true))
        assertTrue(ScreenshotDetection.isCategorizedSubfolder("Pictures\\Screenshots\\Receipts", scopedStorage = true))
        assertFalse(ScreenshotDetection.isCategorizedSubfolder("/storage/emulated/0/Pictures/Screenshots/image.png", scopedStorage = false))
        assertTrue(ScreenshotDetection.isCategorizedSubfolder("/storage/emulated/0/Pictures/Screenshots/Receipts/image.png", scopedStorage = false))
    }
}

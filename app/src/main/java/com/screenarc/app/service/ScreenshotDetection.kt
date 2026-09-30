package com.screenarc.app.service

import java.io.File

internal object ScreenshotDetection {
    fun isScreenshotCandidate(name: String, path: String): Boolean =
        name.contains("Screenshot", ignoreCase = true) ||
            path.contains("Screenshots", ignoreCase = true)

    fun isCategorizedSubfolder(path: String, scopedStorage: Boolean): Boolean {
        val normalizedPath = path.replace('\\', '/').trim('/')
        if (scopedStorage) {
            val root = "Pictures/Screenshots"
            return normalizedPath.startsWith("$root/", ignoreCase = true) &&
                normalizedPath.length > root.length + 1
        }

        val parentName = File(path).parentFile?.name.orEmpty()
        return path.contains("Screenshots", ignoreCase = true) &&
            !parentName.equals("Screenshots", ignoreCase = true)
    }
}

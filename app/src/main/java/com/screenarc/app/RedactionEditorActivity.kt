package com.screenarc.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.screenarc.app.model.ScreenshotItem
import com.screenarc.app.model.ScreenshotUiModel
import com.screenarc.app.ui.editor.RedactionEditorScreen
import com.screenarc.app.ui.theme.ScreenArcTheme

/**
 * Full-screen redaction editor launched from the overlay arc's Share / Delete
 * actions. Runs OCR on the screenshot, lets the user blackout/pixelate blocks,
 * then on OK shares the redacted copy (SHARE mode) and/or deletes the original.
 */
class RedactionEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uriString = intent.getStringExtra(EXTRA_URI)
        if (uriString.isNullOrEmpty()) {
            finish()
            return
        }
        val uri = Uri.parse(uriString)
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: "Screenshot.png"
        val isDeleteMode = intent.getStringExtra(EXTRA_MODE) == MODE_DELETE

        val container = (application as ScreenArcApplication).appContainer
        val screenshotUi = ScreenshotUiModel(
            id = intent.getLongExtra(EXTRA_ID, System.currentTimeMillis()),
            uri = uriString,
            displayName = displayName,
            dateAdded = intent.getLongExtra(EXTRA_DATE_ADDED, System.currentTimeMillis() / 1000),
            size = intent.getLongExtra(EXTRA_SIZE, 0L),
            relativePath = intent.getStringExtra(EXTRA_RELATIVE_PATH) ?: "Pictures/Screenshots/",
            mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "image/png"
        )

        setContent {
            val settings by container.settingsRepository.settings.collectAsState()
            ScreenArcTheme(
                themePreset = settings.themePreset,
                uiDesignStyle = settings.uiDesignStyle
            ) {
                RedactionEditorScreen(
                    uri = uri,
                    displayName = displayName,
                    isDeleteMode = isDeleteMode,
                    language = settings.ocrLanguage,
                    ocrRepository = container.ocrRepository,
                    screenshot = screenshotUi,
                    screenshotRepository = container.screenshotRepository,
                    onFinished = { finish() }
                )
            }
        }
    }

    companion object {
        const val MODE_SHARE = "share"
        const val MODE_DELETE = "delete"

        const val EXTRA_URI = "extra_redaction_uri"
        const val EXTRA_DISPLAY_NAME = "extra_redaction_display_name"
        const val EXTRA_MODE = "extra_redaction_mode"
        const val EXTRA_ID = "extra_redaction_id"
        const val EXTRA_DATE_ADDED = "extra_redaction_date_added"
        const val EXTRA_SIZE = "extra_redaction_size"
        const val EXTRA_RELATIVE_PATH = "extra_redaction_relative_path"
        const val EXTRA_MIME_TYPE = "extra_redaction_mime_type"

        fun newIntent(context: Context, screenshot: ScreenshotItem, mode: String): Intent {
            return Intent(context, RedactionEditorActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_URI, screenshot.uri.toString())
                putExtra(EXTRA_DISPLAY_NAME, screenshot.displayName)
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_ID, screenshot.id)
                putExtra(EXTRA_DATE_ADDED, screenshot.dateAdded)
                putExtra(EXTRA_SIZE, screenshot.size)
                putExtra(EXTRA_RELATIVE_PATH, screenshot.relativePath)
                putExtra(EXTRA_MIME_TYPE, screenshot.mimeType)
            }
        }
    }
}

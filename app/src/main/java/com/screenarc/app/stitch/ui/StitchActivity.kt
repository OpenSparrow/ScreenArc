package com.screenarc.app.stitch.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.screenarc.app.ScreenArcApplication
import com.screenarc.app.ui.theme.ScreenArcTheme

/**
 * Activity for stitching multiple screenshots into one continuous vertical image.
 */
class StitchActivity : ComponentActivity() {

    private val uris: List<Uri> by lazy {
        val uriStrings = intent.getStringArrayListExtra(EXTRA_URIS)
        uriStrings?.map { Uri.parse(it) } ?: emptyList()
    }

    private val viewModel: StitchViewModel by viewModels {
        StitchViewModel.provideFactory(application, uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (uris.size < 2) {
            Toast.makeText(this, "Please select at least 2 screenshots to stitch.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val container = (application as ScreenArcApplication).appContainer

        setContent {
            val settings by container.settingsRepository.settings.collectAsState()
            ScreenArcTheme(
                themePreset = settings.themePreset,
                uiDesignStyle = settings.uiDesignStyle
            ) {
                StitchScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_URIS = "extra_stitch_uris"

        fun newIntent(context: Context, uris: List<Uri>): Intent {
            return Intent(context, StitchActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris.map { it.toString() }))
            }
        }
    }
}

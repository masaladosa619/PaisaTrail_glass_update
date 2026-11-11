package com.paisatrail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisatrail.data.prefs.SettingsStore
import com.paisatrail.data.prefs.ThemeMode
import com.paisatrail.data.share.ShareInbox
import com.paisatrail.data.share.SharedItem
import com.paisatrail.ui.AppRoot
import com.paisatrail.ui.components.LocalReduceMotion
import com.paisatrail.ui.theme.PaisaTrailTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The only activity. It is also the Share-to-Track target: when another app shares an image or
 * a PDF, the file is handed to [ShareInbox] and the navigation graph opens the right screen.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shareInbox: ShareInbox

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Only on a fresh launch: after a rotation the same intent would otherwise be logged twice.
        if (savedInstanceState == null) handleShare(intent)

        setContent {
            val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
            PaisaTrailTheme(mode = settings?.theme ?: ThemeMode.DARK) {
                CompositionLocalProvider(LocalReduceMotion provides (settings?.reduceMotion ?: false)) {
                    AppRoot(inbox = shareInbox)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        val mime = intent.type ?: contentResolver.getType(uri).orEmpty()
        val isPdf = mime.equals("application/pdf", ignoreCase = true) ||
            uri.lastPathSegment.orEmpty().endsWith(".pdf", ignoreCase = true)
        shareInbox.post(SharedItem(uri = uri, isPdf = isPdf))
    }
}

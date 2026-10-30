package com.kingkang.app.browser

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.kingkang.app.data.SettingsStore
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.KingKangTheme
import com.kingkang.app.ui.theme.ThemeMode

/**
 * Hosts the in-app browser in its own `:browser` process. The process boundary plus the
 * data directory suffix set in [com.kingkang.app.KingKangApplication] keep this WebView's
 * cookies and storage away from the Harness/dsh loopback session.
 */
class BrowserActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Theme only; the engine and its ViewModel stay in the main process.
        val themeMode = ThemeMode.fromKey(SettingsStore(this).themeMode)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val initialUrl = intent?.getStringExtra(EXTRA_URL)
        setContent {
            val dark = when (themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }
            KingKangTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(Background)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        BrowserScreen(initialUrl = initialUrl, onClose = { finish() })
                    } else {
                        BrowserIsolationUnavailable(onClose = { finish() })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

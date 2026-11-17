package com.kingkang.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingkang.app.agent.AgentEngine
import com.kingkang.app.ui.KingKangApp
import com.kingkang.app.ui.theme.KingKangTheme
import com.kingkang.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        // Some devices stop the foreground service while backgrounded; recover the bridge
        // as soon as the app is visible again instead of leaving a dead connection.
        AgentEngine.onAppForegrounded(this)
        com.kingkang.app.device.UsbMonitor.setActive(true)
    }

    override fun onStop() {
        super.onStop()
        // Only while nothing is on screen do completion notices leave the app.
        AgentEngine.onAppBackgrounded()
        com.kingkang.app.device.UsbMonitor.setActive(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(AgentEngine.EXTRA_SESSION_ID)
            ?.let(AgentEngine::openSessionFromNotification)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // A notification tap can cold-start the activity; the engine holds the id until the
        // session list arrives.
        intent?.getStringExtra(AgentEngine.EXTRA_SESSION_ID)
            ?.let(AgentEngine::openSessionFromNotification)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // Completion notices and the install foreground-service notification need this
        // runtime grant on Android 13+; the manifest declaration alone is not enough.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0x4b50)
        }
        setContent {
            val vm: MainViewModel = viewModel()
            val mode by vm.themeMode.collectAsState()
            val dark = when (ThemeMode.fromKey(mode)) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            // Re-apply the bar styles whenever the resolved theme flips: transparent bars
            // with dark icons on paper surfaces, light icons on the charcoal ones.
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
                KingKangApp(vm)
            }
        }
    }
}

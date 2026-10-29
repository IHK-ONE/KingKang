package com.kingkang.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
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

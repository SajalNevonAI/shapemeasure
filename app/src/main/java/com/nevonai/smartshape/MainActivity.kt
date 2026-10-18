package com.nevonai.smartshape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nevonai.smartshape.data.AppSettings
import com.nevonai.smartshape.data.SettingsRepository
import com.nevonai.smartshape.data.ThemeMode
import com.nevonai.smartshape.ui.navigation.SmartShapeNavHost
import com.nevonai.smartshape.ui.theme.SmartShapeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings: AppSettings? by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SmartShapeTheme(darkTheme = dark) {
                // Wait for settings so onboarding doesn't flash for returning users.
                settings?.let { SmartShapeNavHost(showOnboarding = !it.onboardingDone) }
            }
        }
    }
}

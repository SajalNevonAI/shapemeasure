package com.nevonai.smartshape.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nevonai.smartshape.feature.detail.ScanDetailScreen
import com.nevonai.smartshape.feature.editor.EditorScreen
import com.nevonai.smartshape.feature.history.HistoryScreen
import com.nevonai.smartshape.feature.home.HomeScreen
import com.nevonai.smartshape.feature.onboarding.OnboardingScreen
import com.nevonai.smartshape.feature.scanner.ScannerScreen
import com.nevonai.smartshape.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object Onboarding
@Serializable data object Home
@Serializable data object Scanner
@Serializable data object History
@Serializable data object Settings

/** Opens the editor for a freshly captured/imported image ([imagePath]) or a saved scan ([scanId]). */
@Serializable data class Editor(val imagePath: String? = null, val scanId: Long? = null, val startOnText: Boolean = false)

@Serializable data class ScanDetail(val scanId: Long)

@Composable
fun SmartShapeNavHost(showOnboarding: Boolean) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = if (showOnboarding) Onboarding else Home,
        enterTransition = { slideInHorizontally(tween(320)) { it / 6 } + fadeIn(tween(320)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { slideOutHorizontally(tween(280)) { it / 6 } + fadeOut(tween(280)) },
    ) {
        composable<Onboarding> {
            OnboardingScreen(onDone = { nav.navigate(Home) { popUpTo(Onboarding) { inclusive = true } } })
        }
        composable<Home> {
            HomeScreen(
                onScan = { nav.navigate(Scanner) },
                onImported = { path -> nav.navigate(Editor(imagePath = path)) },
                onHistory = { nav.navigate(History) },
                onSettings = { nav.navigate(Settings) },
                onOpenScan = { nav.navigate(ScanDetail(it)) },
            )
        }
        composable<Scanner> {
            ScannerScreen(
                onCaptured = { path -> nav.navigate(Editor(imagePath = path)) { popUpTo(Scanner) { inclusive = true } } },
                onClose = { nav.popBackStack() },
            )
        }
        composable<Editor> {
            EditorScreen(
                onSaved = { id -> nav.navigate(ScanDetail(id)) { popUpTo(Home) } },
                onBack = { nav.popBackStack() },
            )
        }
        composable<ScanDetail> {
            ScanDetailScreen(
                onEdit = { id, text -> nav.navigate(Editor(scanId = id, startOnText = text)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable<History> {
            HistoryScreen(onOpenScan = { nav.navigate(ScanDetail(it)) }, onBack = { nav.popBackStack() })
        }
        composable<Settings> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}

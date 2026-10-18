package com.nevonai.smartshape.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nevonai.smartshape.model.LengthUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val unit: LengthUnit = LengthUnit.MM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val haptics: Boolean = true,
    val showGrid: Boolean = true,
    val onboardingDone: Boolean = false,
)

private val Context.settingsStore by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val unit = stringPreferencesKey("unit")
        val theme = stringPreferencesKey("theme")
        val haptics = booleanPreferencesKey("haptics")
        val grid = booleanPreferencesKey("grid")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            unit = p[Keys.unit]?.let { runCatching { LengthUnit.valueOf(it) }.getOrNull() } ?: LengthUnit.MM,
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            haptics = p[Keys.haptics] ?: true,
            showGrid = p[Keys.grid] ?: true,
            onboardingDone = p[Keys.onboarding] ?: false,
        )
    }

    suspend fun setUnit(unit: LengthUnit) { context.settingsStore.edit { it[Keys.unit] = unit.name } }
    suspend fun setTheme(mode: ThemeMode) { context.settingsStore.edit { it[Keys.theme] = mode.name } }
    suspend fun setHaptics(on: Boolean) { context.settingsStore.edit { it[Keys.haptics] = on } }
    suspend fun setGrid(on: Boolean) { context.settingsStore.edit { it[Keys.grid] = on } }
    suspend fun setOnboardingDone() { context.settingsStore.edit { it[Keys.onboarding] = true } }
}

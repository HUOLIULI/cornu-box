package com.aggregator.shell.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B6CFF),
    secondary = Color(0xFF4FC3F7),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BAAFF),
    secondary = Color(0xFF6FD3FF),
    background = Color(0xFF0E1016),
    surface = Color(0xFF161A22)
)

// 与 SettingsActivity 一致：DataStore 名 "shell_prefs"，key "dark_mode"
private val Context.shellDataStore by preferencesDataStore(name = "shell_prefs")

val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")

val LocalThemeConfig = staticCompositionLocalOf {
    ThemeConfig(dark = false, language = Locale.getDefault().language)
}

data class ThemeConfig(
    val dark: Boolean,
    val language: String
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkModeFlow: Flow<Boolean> = remember {
        context.shellDataStore.data.map { it[DARK_MODE_KEY] ?: false }
    }
    val isUserDark by darkModeFlow.collectAsState(initial = false)
    val useDarkTheme = isUserDark || isSystemInDarkTheme()
    val themeConfig = remember(isUserDark) {
        ThemeConfig(dark = useDarkTheme, language = Locale.getDefault().language)
    }

    CompositionLocalProvider(LocalThemeConfig provides themeConfig) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) DarkColors else LightColors,
            content = content
        )
    }
}

suspend fun Context.setDarkTheme(enabled: Boolean) {
    shellDataStore.edit { preferences ->
        preferences[DARK_MODE_KEY] = enabled
    }
}

suspend fun Context.getDarkTheme(): Boolean {
    return shellDataStore.data.map { it[DARK_MODE_KEY] ?: false }.first()
}

suspend fun Context.setLanguage(language: String) {
    shellDataStore.edit { preferences ->
        preferences[stringPreferencesKey("language")] = language
    }
}

suspend fun Context.getLanguage(): String {
    return shellDataStore.data.map { it[stringPreferencesKey("language")] ?: Locale.getDefault().language }.first()
}

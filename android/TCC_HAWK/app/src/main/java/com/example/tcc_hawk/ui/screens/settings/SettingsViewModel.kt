package com.example.tcc_hawk.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tcc_hawk.data.local.ThemePrefs
import com.example.tcc_hawk.ui.theme.AppPalette
import com.example.tcc_hawk.ui.theme.AppThemeState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = ThemePrefs(app)

    init {
        viewModelScope.launch {
            combine(prefs.darkModeFlow, prefs.paletteFlow) { dark, palette ->
                dark to palette
            }.collect { (dark, palStr) ->
                AppThemeState.darkMode = dark
                AppThemeState.palette =
                    runCatching { AppPalette.valueOf(palStr) }.getOrElse { AppPalette.DEFAULT }
            }
        }
    }

    fun setDarkMode(value: Boolean) {
        AppThemeState.darkMode = value
        viewModelScope.launch { prefs.setDarkMode(value) }
    }

    fun setPalette(value: AppPalette) {
        AppThemeState.palette = value
        viewModelScope.launch { prefs.setPalette(value.name) }
    }
}
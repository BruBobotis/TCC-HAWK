package com.example.tcc_hawk.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppPalette { DEFAULT, MINT, SKY }

object AppThemeState {
    var darkMode by mutableStateOf(true)
    var palette by mutableStateOf(AppPalette.DEFAULT)
}
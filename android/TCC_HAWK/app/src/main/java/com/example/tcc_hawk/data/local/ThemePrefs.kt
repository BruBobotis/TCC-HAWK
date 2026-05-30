package com.example.tcc_hawk.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore(name = "hawk_prefs")

class ThemePrefs(private val context: Context) {

    private val KEY_DARK = booleanPreferencesKey("dark_mode")
    private val KEY_PALETTE = stringPreferencesKey("palette")

    val darkModeFlow: Flow<Boolean> = context.ds.data.map { it[KEY_DARK] ?: true }
    val paletteFlow: Flow<String> = context.ds.data.map { it[KEY_PALETTE] ?: "DEFAULT" }

    suspend fun setDarkMode(value: Boolean) {
        context.ds.edit { it[KEY_DARK] = value }
    }

    suspend fun setPalette(value: String) {
        context.ds.edit { it[KEY_PALETTE] = value }
    }
}
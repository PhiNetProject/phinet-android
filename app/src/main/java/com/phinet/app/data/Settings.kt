package com.phinet.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.phinet.app.node.NodeConfig
import com.phinet.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "phinet_settings")

/**
 * User settings: theme mode, chat background, and the (local) com API URL.
 * chatBackground is one of the built-in ids ("aurora", "mesh", "plain") or
 * "custom:<absolute file path>" for an uploaded image.
 */
class Settings(private val context: Context) {
    private val COM_URL = stringPreferencesKey("com_base_url")
    private val THEME   = stringPreferencesKey("theme_mode")
    private val BG      = stringPreferencesKey("chat_bg")
    private val LOCK    = stringPreferencesKey("app_lock")

    val comBaseUrl: Flow<String> = context.dataStore.data.map { it[COM_URL] ?: NodeConfig.COM_BASE_URL }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        when (it[THEME]) { "LIGHT" -> ThemeMode.LIGHT; "DARK" -> ThemeMode.DARK; else -> ThemeMode.SYSTEM }
    }
    val chatBackground: Flow<String> = context.dataStore.data.map { it[BG] ?: DEFAULT_BG }
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[LOCK] == "1" }

    suspend fun setComBaseUrl(url: String) {
        context.dataStore.edit { it[COM_URL] = url.trimEnd('/') + "/" }
    }
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME] = mode.name }
    }
    suspend fun setChatBackground(value: String) {
        context.dataStore.edit { it[BG] = value }
    }
    suspend fun setAppLock(enabled: Boolean) {
        context.dataStore.edit { it[LOCK] = if (enabled) "1" else "0" }
    }

    companion object {
        const val DEFAULT_BG = "aurora"
    }
}

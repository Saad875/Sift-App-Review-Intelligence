package com.sift.app.data.settings

import android.content.Context
import androidx.core.content.edit
import com.sift.app.domain.model.ThemeMode
import com.sift.app.domain.repository.SettingsRepository

/** Stores preferences in SharedPreferences. */
class SharedPrefsSettingsRepository(
    context: Context,
    override val defaultBackendUrl: String
) : SettingsRepository {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun backendUrl(): String = prefs.getString(KEY_BACKEND_URL, null) ?: defaultBackendUrl

    override fun setBackendUrl(url: String) = prefs.edit { putString(KEY_BACKEND_URL, url) }

    override fun isIntroCompleted(): Boolean = prefs.getBoolean(KEY_INTRO_DONE, false)

    override fun setIntroCompleted() = prefs.edit { putBoolean(KEY_INTRO_DONE, true) }

    override fun themeMode(): ThemeMode =
        prefs.getString(KEY_THEME, null)?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.DARK

    override fun setThemeMode(mode: ThemeMode) = prefs.edit { putString(KEY_THEME, mode.name) }

    override fun lastCountry(): String = prefs.getString(KEY_COUNTRY, null) ?: DEFAULT_COUNTRY

    override fun setLastCountry(country: String) = prefs.edit { putString(KEY_COUNTRY, country.lowercase()) }

    private companion object {
        const val FILE = "sift_settings"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_INTRO_DONE = "intro_done"
        const val KEY_THEME = "theme_mode"
        const val KEY_COUNTRY = "last_country"
        const val DEFAULT_COUNTRY = "us"
    }
}

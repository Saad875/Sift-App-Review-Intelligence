package com.sift.app.presentation.common

import androidx.appcompat.app.AppCompatDelegate
import com.sift.app.domain.model.ThemeMode

/** Maps the stored [ThemeMode] onto AppCompat night mode. */
object ThemeApplier {
    fun apply(mode: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}

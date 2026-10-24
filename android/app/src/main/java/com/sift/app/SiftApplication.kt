package com.sift.app

import android.app.Application
import com.sift.app.di.AppContainer
import com.sift.app.presentation.common.ThemeApplier

/** Creates the dependency container and applies the saved theme (dark by default). */
class SiftApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ThemeApplier.apply(container.preferences.themeMode())
    }
}

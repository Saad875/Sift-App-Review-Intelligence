package com.sift.app.data.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sift.app.domain.repository.LanguageRepository

/**
 * Per-app language through AppCompat. On Android 13+ the system stores it; on older
 * versions AppCompat persists it (autoStoreLocales in the manifest) and applies it to
 * every AppCompatActivity. Changing it recreates the activity.
 */
class AppCompatLanguageRepository : LanguageRepository {

    override fun selectedTag(): String? =
        AppCompatDelegate.getApplicationLocales().takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()

    override fun deviceTags(): List<String> {
        val locales = LocaleListCompat.getAdjustedDefault()
        return (0 until locales.size()).mapNotNull { locales[it]?.toLanguageTag() }
    }

    override fun select(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}

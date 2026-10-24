package com.sift.app.presentation.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.os.ConfigurationCompat
import com.sift.app.R
import com.sift.app.domain.model.AppLanguage
import java.util.Locale

/** How a language is labelled: its own name, a short badge, and its name in the current UI language. */
object LanguageLabels {

    @StringRes
    fun nativeName(language: AppLanguage): Int = when (language) {
        AppLanguage.ENGLISH -> R.string.language_native_en
        AppLanguage.CHINESE -> R.string.language_native_zh
        AppLanguage.HINDI -> R.string.language_native_hi
        AppLanguage.SPANISH -> R.string.language_native_es
        AppLanguage.FRENCH -> R.string.language_native_fr
        AppLanguage.ARABIC -> R.string.language_native_ar
        AppLanguage.BENGALI -> R.string.language_native_bn
        AppLanguage.PORTUGUESE -> R.string.language_native_pt
        AppLanguage.RUSSIAN -> R.string.language_native_ru
        AppLanguage.URDU -> R.string.language_native_ur
        AppLanguage.INDONESIAN -> R.string.language_native_in
        AppLanguage.GERMAN -> R.string.language_native_de
        AppLanguage.JAPANESE -> R.string.language_native_ja
        AppLanguage.SWAHILI -> R.string.language_native_sw
        AppLanguage.TURKISH -> R.string.language_native_tr
    }

    @StringRes
    fun mark(language: AppLanguage): Int = when (language) {
        AppLanguage.ENGLISH -> R.string.language_mark_en
        AppLanguage.CHINESE -> R.string.language_mark_zh
        AppLanguage.HINDI -> R.string.language_mark_hi
        AppLanguage.SPANISH -> R.string.language_mark_es
        AppLanguage.FRENCH -> R.string.language_mark_fr
        AppLanguage.ARABIC -> R.string.language_mark_ar
        AppLanguage.BENGALI -> R.string.language_mark_bn
        AppLanguage.PORTUGUESE -> R.string.language_mark_pt
        AppLanguage.RUSSIAN -> R.string.language_mark_ru
        AppLanguage.URDU -> R.string.language_mark_ur
        AppLanguage.INDONESIAN -> R.string.language_mark_in
        AppLanguage.GERMAN -> R.string.language_mark_de
        AppLanguage.JAPANESE -> R.string.language_mark_ja
        AppLanguage.SWAHILI -> R.string.language_mark_sw
        AppLanguage.TURKISH -> R.string.language_mark_tr
    }

    /** The language's name written in the app's current language, for example "Spanish" or "Espagnol". */
    fun localizedName(context: Context, language: AppLanguage): String {
        val uiLocale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        return Locale.forLanguageTag(language.tag).getDisplayLanguage(uiLocale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(uiLocale) else it.toString() }
    }
}

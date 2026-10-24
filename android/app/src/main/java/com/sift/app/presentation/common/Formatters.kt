package com.sift.app.presentation.common

import android.content.Context
import com.sift.app.R
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** Locale-aware formatting helpers. */
object Formatters {
    fun date(millis: Long?): String? =
        millis?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }

    fun count(value: Int): String = NumberFormat.getIntegerInstance().format(value)

    fun rating(context: Context, value: Double): String = context.getString(R.string.rating_value, value)

    fun percent(context: Context, share: Float): String =
        context.getString(R.string.percent_value, (share * PERCENT).toInt())

    /** Country name in the user's language, for example "United Kingdom" for "gb". */
    fun countryName(code: String): String =
        Locale("", code.uppercase()).getDisplayCountry(Locale.getDefault()).ifBlank { code.uppercase() }

    /** Emoji flag for a two letter country code. */
    fun countryFlag(code: String): String {
        if (code.length != 2) return ""
        val base = REGIONAL_INDICATOR_A - 'A'.code
        return code.uppercase().map { Character.toChars(base + it.code).concatToString() }.joinToString("")
    }

    private const val PERCENT = 100
    private const val REGIONAL_INDICATOR_A = 0x1F1E6
}

/** App Store storefront codes offered in the country selector. */
object Countries {
    val codes = listOf(
        "us", "gb", "ca", "au", "in", "de", "fr", "es", "it", "nl", "se", "no", "dk", "fi", "ie", "pl",
        "pt", "br", "mx", "ar", "jp", "kr", "cn", "tw", "hk", "sg", "id", "my", "th", "vn", "ph", "pk",
        "ae", "sa", "tr", "eg", "za", "ng", "ke", "nz", "ch", "at", "be", "il", "ru", "ua"
    )
}

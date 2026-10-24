package com.sift.app.presentation.common

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat

/** Loads a bundled font for custom-drawn text; falls back to the system font (for example in layout previews). */
fun Context.appTypeface(@FontRes id: Int): Typeface =
    runCatching { ResourcesCompat.getFont(this, id) }.getOrNull() ?: Typeface.DEFAULT

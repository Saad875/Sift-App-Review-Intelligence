package com.sift.app.presentation.common

import android.widget.ImageView
import coil.load
import com.sift.app.R

/** Loads an app icon with Coil, falling back to a neutral placeholder. */
fun ImageView.loadAppIcon(url: String?) {
    if (url.isNullOrBlank()) {
        setImageResource(R.drawable.ic_app_placeholder)
        return
    }
    load(url) {
        crossfade(Motion.enabled(context))
        placeholder(R.drawable.ic_app_placeholder)
        error(R.drawable.ic_app_placeholder)
    }
}

package com.sift.app.presentation.common

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Resolves the display name of a document picked with the Storage Access Framework. */
object DocumentNames {
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}

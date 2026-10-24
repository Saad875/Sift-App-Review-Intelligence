package com.sift.app.domain.util

import com.sift.app.domain.error.AppError
import com.sift.app.domain.error.InvalidReason
import java.net.URI

/** Validates and normalises the backend base URL entered in Settings. */
object BackendUrlRules {
    /** Returns the URL with a trailing slash, or throws [AppError.Invalid]. */
    fun normalize(input: String): String {
        val trimmed = input.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: throw AppError.Invalid(InvalidReason.BACKEND_URL_INVALID)
        val schemeOk = uri.scheme.equals("http", true) || uri.scheme.equals("https", true)
        if (!schemeOk || uri.host.isNullOrBlank() || uri.query != null || uri.fragment != null) {
            throw AppError.Invalid(InvalidReason.BACKEND_URL_INVALID)
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}

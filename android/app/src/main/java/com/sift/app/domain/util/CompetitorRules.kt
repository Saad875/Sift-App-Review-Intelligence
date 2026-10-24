package com.sift.app.domain.util

import com.sift.app.domain.error.AppError
import com.sift.app.domain.error.InvalidReason

/** Validation rules for competitor names. */
object CompetitorRules {
    const val MAX_COMPETITORS = 3
    const val MAX_NAME_LENGTH = 60

    /** Trims, removes duplicates (case-insensitive) and enforces the limits. */
    fun normalize(names: List<String>): List<String> {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (cleaned.size > MAX_COMPETITORS) throw AppError.Invalid(InvalidReason.TOO_MANY_COMPETITORS)
        if (cleaned.any { it.length > MAX_NAME_LENGTH }) {
            throw AppError.Invalid(InvalidReason.COMPETITOR_NAME_INVALID)
        }
        return cleaned
    }

    /** True when [name] can be added to [current]. */
    fun canAdd(current: List<String>, name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() &&
            trimmed.length <= MAX_NAME_LENGTH &&
            current.size < MAX_COMPETITORS &&
            current.none { it.equals(trimmed, ignoreCase = true) }
    }
}

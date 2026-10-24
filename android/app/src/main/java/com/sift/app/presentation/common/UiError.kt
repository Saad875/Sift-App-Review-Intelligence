package com.sift.app.presentation.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sift.app.R
import com.sift.app.domain.error.AppError
import com.sift.app.domain.error.InvalidReason

/**
 * An error ready to display. [detail] carries a server supplied message when
 * there is one; otherwise [messageRes] is shown.
 */
data class UiError(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    @DrawableRes val iconRes: Int,
    val detail: String? = null,
    val retryable: Boolean = true
)

/** Converts domain failures into display-ready errors. */
fun Throwable.toUiError(): UiError = when (this) {
    is AppError.NoInternet -> UiError(R.string.error_offline_title, R.string.error_offline_message, R.drawable.ic_cloud_off)
    is AppError.BackendUnreachable -> UiError(
        R.string.error_backend_title, R.string.error_backend_message, R.drawable.ic_cloud_off
    )
    is AppError.Server -> UiError(
        R.string.error_server_title, R.string.error_generic_message, R.drawable.ic_error, detail = serverMessage
    )
    is AppError.RateLimited -> UiError(
        R.string.error_rate_limited_title, R.string.error_rate_limited_message, R.drawable.ic_error
    )
    is AppError.NotFound -> UiError(
        R.string.error_not_found_title, R.string.error_not_found_message, R.drawable.ic_inbox, retryable = false
    )
    is AppError.FileUnreadable -> UiError(
        R.string.error_file_title, R.string.error_file_message, R.drawable.ic_upload_file
    )
    is AppError.Invalid -> UiError(
        R.string.error_invalid_title, reason.messageRes(), R.drawable.ic_info, retryable = false
    )
    else -> UiError(R.string.error_generic_title, R.string.error_generic_message, R.drawable.ic_error)
}

@StringRes
fun InvalidReason.messageRes(): Int = when (this) {
    InvalidReason.QUERY_TOO_SHORT -> R.string.error_query_too_short
    InvalidReason.TOO_MANY_COMPETITORS -> R.string.error_too_many_competitors
    InvalidReason.COMPETITOR_NAME_INVALID -> R.string.error_competitor_name
    InvalidReason.BACKEND_URL_INVALID -> R.string.error_backend_url
    InvalidReason.REPLY_UNAVAILABLE -> R.string.error_reply_unavailable
}

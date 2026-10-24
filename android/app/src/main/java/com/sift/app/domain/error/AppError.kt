package com.sift.app.domain.error

/** Failures the UI knows how to explain. Repositories throw these; use cases surface them in Result. */
sealed class AppError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** The device has no network connection. */
    class NoInternet : AppError()

    /** The backend did not answer (wrong URL, server down, timeout). */
    class BackendUnreachable(cause: Throwable? = null) : AppError(cause = cause)

    /** The backend answered with an error; [serverMessage] is safe to show. */
    class Server(val code: String, val serverMessage: String) : AppError(serverMessage)

    /** Too many requests from this device. */
    class RateLimited(val retryAfterSeconds: Int?) : AppError()

    /** The requested analysis or review does not exist. */
    class NotFound : AppError()

    /** The picked CSV file could not be read. */
    class FileUnreadable(cause: Throwable? = null) : AppError(cause = cause)

    /** Input failed validation before any request was made. */
    class Invalid(val reason: InvalidReason) : AppError()

    /** Anything else. */
    class Unexpected(cause: Throwable? = null) : AppError(cause = cause)
}

/** Validation failures detected locally. */
enum class InvalidReason {
    QUERY_TOO_SHORT,
    TOO_MANY_COMPETITORS,
    COMPETITOR_NAME_INVALID,
    BACKEND_URL_INVALID,
    REPLY_UNAVAILABLE
}

/** Wraps any throwable into an [AppError]. */
fun Throwable.toAppError(): AppError = this as? AppError ?: AppError.Unexpected(this)

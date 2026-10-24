package com.sift.app.domain.usecase

import com.sift.app.domain.error.AppError
import com.sift.app.domain.error.InvalidReason
import com.sift.app.domain.error.toAppError
import com.sift.app.domain.model.AppSummary
import com.sift.app.domain.repository.AppSearchRepository
import kotlin.coroutines.cancellation.CancellationException

/** Searches the App Store. Queries shorter than two characters are rejected locally. */
class SearchAppsUseCase(private val repository: AppSearchRepository) {
    suspend operator fun invoke(query: String, country: String): Result<List<AppSummary>> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            return Result.failure(AppError.Invalid(InvalidReason.QUERY_TOO_SHORT))
        }
        return runCatchingApp { repository.search(trimmed, country.lowercase()) }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

/** Like runCatching, but rethrows cancellation and maps failures to [AppError]. */
internal suspend fun <T> runCatchingApp(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Result.failure(throwable.toAppError())
}

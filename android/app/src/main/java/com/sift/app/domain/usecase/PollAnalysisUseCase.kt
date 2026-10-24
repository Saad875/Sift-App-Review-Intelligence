package com.sift.app.domain.usecase

import com.sift.app.domain.error.AppError
import com.sift.app.domain.error.toAppError
import com.sift.app.domain.model.Analysis
import com.sift.app.domain.model.AnalysisStatus
import com.sift.app.domain.repository.AnalysisRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.cancellation.CancellationException

/** Events emitted while polling an analysis. */
sealed interface PollEvent {
    data class Updated(val analysis: Analysis) : PollEvent
    data class Finished(val analysis: Analysis) : PollEvent
    data class TransientError(val error: AppError) : PollEvent
    data class Fatal(val error: AppError) : PollEvent
}

/**
 * Polls the backend every [intervalMillis] until the analysis completes or fails.
 * Network hiccups are reported as [PollEvent.TransientError] and polling continues;
 * after [maxConsecutiveErrors] failures in a row polling stops with [PollEvent.Fatal].
 * When the analysis completes, the full report is synced into the local cache first.
 */
class PollAnalysisUseCase(
    private val repository: AnalysisRepository,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val maxConsecutiveErrors: Int = DEFAULT_MAX_ERRORS
) {
    operator fun invoke(analysisId: String): Flow<PollEvent> = flow {
        var consecutiveErrors = 0
        while (true) {
            // Work happens inside try; emissions stay outside it to keep exception transparency.
            val outcome = try {
                val analysis = repository.refreshStatus(analysisId)
                if (analysis.status == AnalysisStatus.COMPLETED) repository.syncReport(analysisId)
                Result.success(analysis)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable.toAppError())
            }
            val analysis = outcome.getOrNull()
            if (analysis != null) {
                consecutiveErrors = 0
                if (analysis.status.isTerminal) {
                    emit(PollEvent.Finished(analysis))
                    return@flow
                }
                emit(PollEvent.Updated(analysis))
            } else {
                val error = (outcome.exceptionOrNull() ?: AppError.Unexpected()).toAppError()
                consecutiveErrors++
                if (error is AppError.NotFound || consecutiveErrors >= maxConsecutiveErrors) {
                    emit(PollEvent.Fatal(error))
                    return@flow
                }
                emit(PollEvent.TransientError(error))
            }
            delay(intervalMillis)
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 2_000L
        const val DEFAULT_MAX_ERRORS = 15
    }
}

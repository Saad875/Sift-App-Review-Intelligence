package com.sift.app.domain.usecase

import com.sift.app.domain.model.Analysis
import com.sift.app.domain.model.BacklogItem
import com.sift.app.domain.model.Report
import com.sift.app.domain.model.Review
import com.sift.app.domain.model.ReviewFilter
import com.sift.app.domain.repository.AnalysisRepository
import com.sift.app.domain.util.MarkdownBuilder
import com.sift.app.domain.util.MarkdownLabels
import kotlinx.coroutines.flow.Flow

/** Recent analyses, newest first, from the local cache. */
class ObserveRecentAnalysesUseCase(private val repository: AnalysisRepository) {
    operator fun invoke(): Flow<List<Analysis>> = repository.observeRecent()
}

/** A single cached analysis (used by the progress screen). */
class ObserveAnalysisUseCase(private val repository: AnalysisRepository) {
    operator fun invoke(analysisId: String): Flow<Analysis?> = repository.observeAnalysis(analysisId)
}

/** The full cached report. Works offline. */
class ObserveReportUseCase(private val repository: AnalysisRepository) {
    operator fun invoke(analysisId: String): Flow<Report?> = repository.observeReport(analysisId)
}

/** Downloads a completed report into the cache when it is not there yet. */
class SyncReportUseCase(private val repository: AnalysisRepository) {
    suspend operator fun invoke(analysisId: String): Result<Unit> = runCatchingApp {
        repository.syncReport(analysisId)
    }
}

/** Cached reviews filtered by sentiment and category. */
class ObserveReviewsUseCase(private val repository: AnalysisRepository) {
    operator fun invoke(analysisId: String, filter: ReviewFilter): Flow<List<Review>> =
        repository.observeReviews(analysisId, filter)
}

/** One cached review. */
class GetReviewUseCase(private val repository: AnalysisRepository) {
    suspend operator fun invoke(reviewId: Long): Result<Review?> = runCatchingApp { repository.getReview(reviewId) }
}

/** One cached backlog item. */
class GetBacklogItemUseCase(private val repository: AnalysisRepository) {
    suspend operator fun invoke(itemId: Long): Result<BacklogItem?> =
        runCatchingApp { repository.getBacklogItem(itemId) }
}

/** Deletes every cached analysis. */
class ClearLocalDataUseCase(private val repository: AnalysisRepository) {
    suspend operator fun invoke(): Result<Unit> = runCatchingApp { repository.clearAll() }
}

/** Formats the report or a single ticket as Markdown. */
class BuildMarkdownUseCase {
    fun report(report: Report, labels: MarkdownLabels): String = MarkdownBuilder(labels).report(report)
    fun ticket(item: BacklogItem, labels: MarkdownLabels): String = MarkdownBuilder(labels).ticket(item)
}

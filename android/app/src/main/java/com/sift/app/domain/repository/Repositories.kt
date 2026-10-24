package com.sift.app.domain.repository

import com.sift.app.domain.model.Analysis
import com.sift.app.domain.model.AnalysisRequest
import com.sift.app.domain.model.AppSummary
import com.sift.app.domain.model.BackendHealth
import com.sift.app.domain.model.BacklogItem
import com.sift.app.domain.model.CsvDocument
import com.sift.app.domain.model.ReplyDraft
import com.sift.app.domain.model.ReplyTone
import com.sift.app.domain.model.Report
import com.sift.app.domain.model.Review
import com.sift.app.domain.model.ReviewFilter
import com.sift.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Searches the App Store through the backend. */
interface AppSearchRepository {
    suspend fun search(query: String, country: String): List<AppSummary>
}

/**
 * Creates analyses on the backend and caches them locally.
 * Room is the single source of truth: every observe function reads the cache.
 */
interface AnalysisRepository {
    suspend fun startAppStoreAnalysis(request: AnalysisRequest): Analysis
    suspend fun startCsvAnalysis(document: CsvDocument, competitors: List<String>): Analysis

    /** Fetches the latest status from the backend and stores it. */
    suspend fun refreshStatus(analysisId: String): Analysis

    /** Downloads themes, backlog, competitors and reviews of a completed analysis into the cache. */
    suspend fun syncReport(analysisId: String)

    fun observeAnalysis(analysisId: String): Flow<Analysis?>
    fun observeRecent(): Flow<List<Analysis>>
    fun observeReport(analysisId: String): Flow<Report?>
    fun observeReviews(analysisId: String, filter: ReviewFilter): Flow<List<Review>>
    suspend fun getReview(reviewId: Long): Review?
    suspend fun getBacklogItem(itemId: Long): BacklogItem?

    /** Loads the bundled sample analysis into the cache and returns its id. */
    suspend fun loadDemo(): String

    suspend fun clearAll()
}

/** Drafts developer replies through the backend. */
interface ReplyRepository {
    suspend fun draftReply(reviewRemoteId: Long, tone: ReplyTone): ReplyDraft
}

/** Checks that the configured backend is reachable. */
interface BackendRepository {
    suspend fun health(): BackendHealth
}

/** User preferences. */
interface SettingsRepository {
    val defaultBackendUrl: String
    fun backendUrl(): String
    fun setBackendUrl(url: String)
    fun isIntroCompleted(): Boolean
    fun setIntroCompleted()
    fun themeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    fun lastCountry(): String
    fun setLastCountry(country: String)
}

package com.sift.app.domain

import com.sift.app.domain.model.Analysis
import com.sift.app.domain.model.AnalysisRequest
import com.sift.app.domain.model.AnalysisSource
import com.sift.app.domain.model.AnalysisStats
import com.sift.app.domain.model.AnalysisStatus
import com.sift.app.domain.model.AppSummary
import com.sift.app.domain.model.BacklogItem
import com.sift.app.domain.model.CsvDocument
import com.sift.app.domain.model.ReplyDraft
import com.sift.app.domain.model.ReplyTone
import com.sift.app.domain.model.Report
import com.sift.app.domain.model.Review
import com.sift.app.domain.model.ReviewFilter
import com.sift.app.domain.model.ThemeMode
import com.sift.app.domain.repository.AnalysisRepository
import com.sift.app.domain.repository.AppSearchRepository
import com.sift.app.domain.repository.ReplyRepository
import com.sift.app.domain.repository.LanguageRepository
import com.sift.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

fun analysis(status: AnalysisStatus = AnalysisStatus.RUNNING, id: String = "a1") = Analysis(
    id = id, appName = "Notes", appId = "1", developer = "Dev", iconUrl = "", source = AnalysisSource.APP_STORE,
    country = "us", competitors = emptyList(), status = status, currentStep = null, progress = 0, error = "",
    warnings = emptyList(), createdAtMillis = 0L, steps = emptyList(), stats = AnalysisStats.EMPTY, isDemo = false
)

val sampleApp = AppSummary("1", "Notes", "Dev", "", 4.5, 10, "Productivity")

class FakeSearchRepository(var result: List<AppSummary> = listOf(sampleApp)) : AppSearchRepository {
    val queries = mutableListOf<Pair<String, String>>()
    override suspend fun search(query: String, country: String): List<AppSummary> {
        queries += query to country
        return result
    }
}

class FakeLanguageRepository(
    var selected: String? = null,
    var device: List<String> = listOf("en-US")
) : LanguageRepository {
    val applied = mutableListOf<String>()
    override fun selectedTag() = selected
    override fun deviceTags() = device
    override fun select(tag: String) { applied += tag; selected = tag }
}

class FakeSettingsRepository : SettingsRepository {
    override val defaultBackendUrl = "http://10.0.2.2:8000/"
    var url = defaultBackendUrl
    var country = "us"
    var onboarding = false
    var theme = ThemeMode.DARK
    override fun backendUrl() = url
    override fun setBackendUrl(url: String) { this.url = url }
    override fun isOnboardingCompleted() = onboarding
    override fun setOnboardingCompleted() { onboarding = true }
    override fun themeMode() = theme
    override fun setThemeMode(mode: ThemeMode) { theme = mode }
    override fun lastCountry() = country
    override fun setLastCountry(country: String) { this.country = country }
}

/** Analysis repository whose status calls return scripted results in order. */
class FakeAnalysisRepository(
    private val statuses: MutableList<Result<Analysis>> = mutableListOf()
) : AnalysisRepository {
    var started: AnalysisRequest? = null
    var startedCsvCompetitors: List<String>? = null
    var syncCount = 0
    var refreshCount = 0
    var syncFailure: Throwable? = null

    override suspend fun startAppStoreAnalysis(request: AnalysisRequest): Analysis {
        started = request
        return analysis(AnalysisStatus.QUEUED)
    }

    override suspend fun startCsvAnalysis(document: CsvDocument, competitors: List<String>): Analysis {
        startedCsvCompetitors = competitors
        return analysis(AnalysisStatus.QUEUED)
    }

    override suspend fun refreshStatus(analysisId: String): Analysis {
        refreshCount++
        return statuses.removeAt(0).getOrThrow()
    }

    override suspend fun syncReport(analysisId: String) {
        syncFailure?.let { syncFailure = null; throw it }
        syncCount++
    }

    override fun observeAnalysis(analysisId: String): Flow<Analysis?> = flowOf(null)
    override fun observeRecent(): Flow<List<Analysis>> = flowOf(emptyList())
    override fun observeReport(analysisId: String): Flow<Report?> = flowOf(null)
    override fun observeReviews(analysisId: String, filter: ReviewFilter): Flow<List<Review>> = flowOf(emptyList())
    override suspend fun getReview(reviewId: Long): Review? = null
    override suspend fun getBacklogItem(itemId: Long): BacklogItem? = null
    override suspend fun loadDemo(): String = "demo"
    override suspend fun clearAll() = Unit
}

class FakeReplyRepository : ReplyRepository {
    val calls = mutableListOf<Pair<Long, ReplyTone>>()
    override suspend fun draftReply(reviewRemoteId: Long, tone: ReplyTone): ReplyDraft {
        calls += reviewRemoteId to tone
        return ReplyDraft("Thanks!", tone)
    }
}

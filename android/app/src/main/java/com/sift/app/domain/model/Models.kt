package com.sift.app.domain.model

/** An App Store app returned by search. */
data class AppSummary(
    val appId: String,
    val name: String,
    val developer: String,
    val iconUrl: String,
    val rating: Double?,
    val ratingCount: Int?,
    val genre: String
)

/** One step of the analysis pipeline with the model working on it. */
data class PipelineStep(
    val key: StepKey,
    val state: StepState,
    val modelRole: ModelRole?,
    val modelId: String?
) {
    val variant: NemotronVariant get() = NemotronVariant.fromModelId(modelId, modelRole)
}

/** Counts of reviews per sentiment. */
data class SentimentBreakdown(val positive: Int, val neutral: Int, val negative: Int) {
    val total: Int get() = positive + neutral + negative

    fun share(sentiment: Sentiment): Float {
        if (total == 0) return 0f
        val count = when (sentiment) {
            Sentiment.POSITIVE -> positive
            Sentiment.NEUTRAL -> neutral
            Sentiment.NEGATIVE -> negative
        }
        return count.toFloat() / total
    }

    companion object {
        val EMPTY = SentimentBreakdown(0, 0, 0)
    }
}

/** Summary statistics of an analysis. [ratingDistribution] holds counts for 1 to 5 stars. */
data class AnalysisStats(
    val totalReviews: Int,
    val classifiedReviews: Int,
    val averageRating: Double?,
    val sentiment: SentimentBreakdown,
    val categories: Map<ReviewCategory, Int>,
    val ratingDistribution: List<Int>,
    val themeCount: Int,
    val backlogCount: Int,
    val competitorCount: Int
) {
    companion object {
        val EMPTY = AnalysisStats(
            totalReviews = 0,
            classifiedReviews = 0,
            averageRating = null,
            sentiment = SentimentBreakdown.EMPTY,
            categories = emptyMap(),
            ratingDistribution = List(5) { 0 },
            themeCount = 0,
            backlogCount = 0,
            competitorCount = 0
        )
    }
}

/** An analysis and its progress. */
data class Analysis(
    val id: String,
    val appName: String,
    val appId: String,
    val developer: String,
    val iconUrl: String,
    val source: AnalysisSource,
    val country: String,
    val competitors: List<String>,
    val status: AnalysisStatus,
    val currentStep: StepKey?,
    val progress: Int,
    val error: String,
    val warnings: List<String>,
    val createdAtMillis: Long,
    val steps: List<PipelineStep>,
    val stats: AnalysisStats
)

/** A user review and its classification. [remoteId] is the backend id used for reply drafting. */
data class Review(
    val id: Long,
    val remoteId: Long?,
    val author: String,
    val title: String,
    val text: String,
    val rating: Int?,
    val dateMillis: Long?,
    val appVersion: String,
    val sentiment: Sentiment?,
    val category: ReviewCategory?,
    val severity: Int?,
    val summary: String,
    val themeTitle: String?
)

/** A cluster of related feedback. [negativeShare] is between 0 and 1. */
data class Theme(
    val id: Long,
    val position: Int,
    val title: String,
    val description: String,
    val reviewCount: Int,
    val negativeShare: Float,
    val quotes: List<String>
)

/** A ranked fix or feature. */
data class BacklogItem(
    val id: Long,
    val rank: Int,
    val title: String,
    val type: BacklogType,
    val priorityScore: Int,
    val reasoning: String,
    val userImpact: String,
    val suggestedFix: String,
    val effort: Effort,
    val themeTitle: String?,
    val evidenceQuotes: List<String>
)

/** A web source cited by competitor research. */
data class SourceLink(val title: String, val url: String)

/** Competitor research for one competitor. A non-empty [error] means research failed. */
data class CompetitorInsight(
    val id: Long,
    val name: String,
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val opportunities: List<String>,
    val sources: List<SourceLink>,
    val error: String
)

/** A finished report with everything the report screens show. */
data class Report(
    val analysis: Analysis,
    val themes: List<Theme>,
    val backlog: List<BacklogItem>,
    val competitors: List<CompetitorInsight>
)

/** Filter applied to the review list. Null means no filter. */
data class ReviewFilter(
    val sentiment: Sentiment? = null,
    val category: ReviewCategory? = null
)

/** A drafted developer reply. */
data class ReplyDraft(val text: String, val tone: ReplyTone)

/** A CSV document picked through the Storage Access Framework. */
data class CsvDocument(val uri: String, val displayName: String?)

/** What the user selected on the analysis setup screen. */
data class AnalysisRequest(
    val app: AppSummary,
    val country: String,
    val competitors: List<String>
)

/** What the backend reports about itself: models in use and configured integrations. */
data class BackendHealth(
    val version: String,
    val fastModel: String,
    val reasoningModel: String,
    val nebiusConfigured: Boolean,
    val tavilyConfigured: Boolean
)

/** Theme preference. Dark is the default. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

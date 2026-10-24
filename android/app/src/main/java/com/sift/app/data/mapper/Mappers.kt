package com.sift.app.data.mapper

import com.sift.app.data.local.entity.AnalysisEntity
import com.sift.app.data.local.entity.BacklogItemEntity
import com.sift.app.data.local.entity.CompetitorEntity
import com.sift.app.data.local.entity.ReviewEntity
import com.sift.app.data.local.entity.StoredSource
import com.sift.app.data.local.entity.StoredStats
import com.sift.app.data.local.entity.StoredStep
import com.sift.app.data.local.entity.ThemeEntity
import com.sift.app.data.remote.dto.AnalysisDto
import com.sift.app.data.remote.dto.AppDto
import com.sift.app.data.remote.dto.BacklogItemDto
import com.sift.app.data.remote.dto.CompetitorDto
import com.sift.app.data.remote.dto.ReviewDto
import com.sift.app.data.remote.dto.StatsDto
import com.sift.app.data.remote.dto.ThemeDto
import com.sift.app.domain.model.Analysis
import com.sift.app.domain.model.AnalysisSource
import com.sift.app.domain.model.AnalysisStats
import com.sift.app.domain.model.AnalysisStatus
import com.sift.app.domain.model.AppSummary
import com.sift.app.domain.model.BacklogItem
import com.sift.app.domain.model.BacklogType
import com.sift.app.domain.model.CompetitorInsight
import com.sift.app.domain.model.Effort
import com.sift.app.domain.model.ModelRole
import com.sift.app.domain.model.PipelineStep
import com.sift.app.domain.model.Review
import com.sift.app.domain.model.ReviewCategory
import com.sift.app.domain.model.Sentiment
import com.sift.app.domain.model.SentimentBreakdown
import com.sift.app.domain.model.SourceLink
import com.sift.app.domain.model.StepKey
import com.sift.app.domain.model.StepState
import com.sift.app.domain.model.Theme

/* ---------------------------- DTO -> domain ---------------------------- */

/** Maps a search result; returns null when required fields are missing. */
fun AppDto.toDomain(): AppSummary? {
    val id = appId?.takeIf { it.isNotBlank() } ?: return null
    return AppSummary(
        appId = id,
        name = name.orEmpty(),
        developer = developer.orEmpty(),
        iconUrl = iconUrl.orEmpty(),
        rating = rating,
        ratingCount = ratingCount,
        genre = genre.orEmpty()
    )
}

/* ---------------------------- DTO -> entity ---------------------------- */

/**
 * Maps an analysis response to a cache row. [previous] keeps local-only fields
 * (creation time fallback, sync flag) when the row already exists.
 */
fun AnalysisDto.toEntity(previous: AnalysisEntity? = null, isDemo: Boolean = false): AnalysisEntity {
    val analysisId = requireNotNull(id) { "Analysis id missing" }
    return AnalysisEntity(
        id = analysisId,
        appName = appName.orEmpty(),
        appId = appId.orEmpty(),
        developer = developer.orEmpty(),
        iconUrl = iconUrl.orEmpty(),
        source = if (isDemo) AnalysisSource.DEMO.key else source.orEmpty(),
        country = country.orEmpty(),
        competitors = competitors.orEmpty(),
        status = status.orEmpty(),
        currentStep = currentStep,
        progress = (progress ?: 0).coerceIn(0, 100),
        error = error.orEmpty(),
        warnings = warnings.orEmpty(),
        createdAtMillis = IsoDateParser.parse(createdAt) ?: previous?.createdAtMillis ?: System.currentTimeMillis(),
        steps = steps.orEmpty().mapNotNull { step ->
            step.key?.let { StoredStep(it, step.state.orEmpty(), step.modelRole, step.model) }
        },
        stats = stats.toStored(),
        isDemo = isDemo,
        reportSynced = previous?.reportSynced ?: false
    )
}

fun StatsDto?.toStored(): StoredStats = StoredStats(
    totalReviews = this?.totalReviews ?: 0,
    classifiedReviews = this?.classifiedReviews ?: 0,
    averageRating = this?.averageRating,
    sentiment = this?.sentiment.orEmpty(),
    categories = this?.categories.orEmpty(),
    ratingDistribution = (1..5).map { star -> this?.ratingDistribution?.get(star.toString()) ?: 0 },
    themeCount = this?.themeCount ?: 0,
    backlogCount = this?.backlogCount ?: 0,
    competitorCount = this?.competitorCount ?: 0
)

fun ReviewDto.toEntity(analysisId: String, keepRemoteId: Boolean = true): ReviewEntity = ReviewEntity(
    analysisId = analysisId,
    remoteId = if (keepRemoteId) id else null,
    author = author.orEmpty(),
    title = title.orEmpty(),
    text = text.orEmpty(),
    rating = rating?.takeIf { it in 1..5 },
    dateMillis = IsoDateParser.parse(reviewDate),
    appVersion = appVersion.orEmpty(),
    sentiment = sentiment?.takeIf { it.isNotBlank() },
    category = category?.takeIf { it.isNotBlank() },
    severity = severity?.takeIf { it in 1..5 },
    summary = summary.orEmpty(),
    themeTitle = themeTitle
)

fun ThemeDto.toEntity(analysisId: String, index: Int): ThemeEntity = ThemeEntity(
    analysisId = analysisId,
    position = position ?: index,
    title = title.orEmpty(),
    description = description.orEmpty(),
    reviewCount = reviewCount ?: 0,
    negativeShare = (negativeShare ?: 0.0).toFloat().coerceIn(0f, 1f),
    quotes = quotes.orEmpty()
)

fun BacklogItemDto.toEntity(analysisId: String, index: Int): BacklogItemEntity = BacklogItemEntity(
    analysisId = analysisId,
    rank = rank ?: (index + 1),
    title = title.orEmpty(),
    type = type.orEmpty(),
    priorityScore = (priorityScore ?: 0).coerceIn(0, 100),
    reasoning = reasoning.orEmpty(),
    userImpact = userImpact.orEmpty(),
    suggestedFix = suggestedFix.orEmpty(),
    effort = effort.orEmpty(),
    themeTitle = themeTitle,
    evidenceQuotes = evidenceQuotes.orEmpty()
)

fun CompetitorDto.toEntity(analysisId: String): CompetitorEntity = CompetitorEntity(
    analysisId = analysisId,
    name = name.orEmpty(),
    summary = summary.orEmpty(),
    strengths = strengths.orEmpty(),
    weaknesses = weaknesses.orEmpty(),
    opportunities = opportunities.orEmpty(),
    sources = sources.orEmpty().mapNotNull { source ->
        source.url?.takeIf { it.startsWith("http") }?.let { StoredSource(source.title.orEmpty(), it) }
    },
    error = error.orEmpty()
)

/* --------------------------- entity -> domain --------------------------- */

fun AnalysisEntity.toDomain(): Analysis = Analysis(
    id = id,
    appName = appName,
    appId = appId,
    developer = developer,
    iconUrl = iconUrl,
    source = AnalysisSource.fromKey(source),
    country = country,
    competitors = competitors,
    status = AnalysisStatus.fromKey(status),
    currentStep = StepKey.fromKey(currentStep),
    progress = progress,
    error = error,
    warnings = warnings,
    createdAtMillis = createdAtMillis,
    steps = steps.mapNotNull { it.toDomain() },
    stats = stats.toDomain(),
    isDemo = isDemo
)

fun StoredStep.toDomain(): PipelineStep? {
    val stepKey = StepKey.fromKey(key) ?: return null
    return PipelineStep(stepKey, StepState.fromKey(state), ModelRole.fromKey(modelRole), model)
}

fun StoredStats.toDomain(): AnalysisStats = AnalysisStats(
    totalReviews = totalReviews,
    classifiedReviews = classifiedReviews,
    averageRating = averageRating,
    sentiment = SentimentBreakdown(
        positive = sentiment[Sentiment.POSITIVE.key] ?: 0,
        neutral = sentiment[Sentiment.NEUTRAL.key] ?: 0,
        negative = sentiment[Sentiment.NEGATIVE.key] ?: 0
    ),
    categories = categories.mapNotNull { (key, count) -> ReviewCategory.fromKey(key)?.let { it to count } }.toMap(),
    ratingDistribution = List(5) { index -> ratingDistribution.getOrElse(index) { 0 } },
    themeCount = themeCount,
    backlogCount = backlogCount,
    competitorCount = competitorCount
)

fun ReviewEntity.toDomain(): Review = Review(
    id = id,
    remoteId = remoteId,
    author = author,
    title = title,
    text = text,
    rating = rating,
    dateMillis = dateMillis,
    appVersion = appVersion,
    sentiment = Sentiment.fromKey(sentiment),
    category = ReviewCategory.fromKey(category),
    severity = severity,
    summary = summary,
    themeTitle = themeTitle
)

fun ThemeEntity.toDomain(): Theme = Theme(
    id = id,
    position = position,
    title = title,
    description = description,
    reviewCount = reviewCount,
    negativeShare = negativeShare,
    quotes = quotes
)

fun BacklogItemEntity.toDomain(): BacklogItem = BacklogItem(
    id = id,
    rank = rank,
    title = title,
    type = BacklogType.fromKey(type),
    priorityScore = priorityScore,
    reasoning = reasoning,
    userImpact = userImpact,
    suggestedFix = suggestedFix,
    effort = Effort.fromKey(effort),
    themeTitle = themeTitle,
    evidenceQuotes = evidenceQuotes
)

fun CompetitorEntity.toDomain(): CompetitorInsight = CompetitorInsight(
    id = id,
    name = name,
    summary = summary,
    strengths = strengths,
    weaknesses = weaknesses,
    opportunities = opportunities,
    sources = sources.map { SourceLink(it.title, it.url) },
    error = error
)

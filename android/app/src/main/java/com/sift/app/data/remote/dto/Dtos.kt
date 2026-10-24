package com.sift.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Response DTOs mirror the backend JSON exactly. Every field is nullable because JSON is untrusted. */

data class ErrorEnvelopeDto(@SerializedName("error") val error: ErrorDto?)

data class ErrorDto(
    @SerializedName("code") val code: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("retry_after_seconds") val retryAfterSeconds: Int?
)

data class HealthDto(
    @SerializedName("status") val status: String?,
    @SerializedName("version") val version: String?,
    @SerializedName("models") val models: HealthModelsDto?,
    @SerializedName("configured") val configured: HealthConfiguredDto?
)

data class HealthModelsDto(
    @SerializedName("fast") val fast: String?,
    @SerializedName("reasoning") val reasoning: String?
)

data class HealthConfiguredDto(
    @SerializedName("nebius") val nebius: Boolean?,
    @SerializedName("tavily") val tavily: Boolean?
)

data class AppSearchResponseDto(@SerializedName("results") val results: List<AppDto>?)

data class AppDto(
    @SerializedName("app_id") val appId: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("developer") val developer: String?,
    @SerializedName("icon_url") val iconUrl: String?,
    @SerializedName("rating") val rating: Double?,
    @SerializedName("rating_count") val ratingCount: Int?,
    @SerializedName("genre") val genre: String?
)

data class CreateAnalysisRequestDto(
    @SerializedName("source") val source: String,
    @SerializedName("app_id") val appId: String,
    @SerializedName("country") val country: String,
    @SerializedName("app_name") val appName: String,
    @SerializedName("developer") val developer: String,
    @SerializedName("icon_url") val iconUrl: String,
    @SerializedName("competitors") val competitors: List<String>
)

data class AnalysisDto(
    @SerializedName("id") val id: String?,
    @SerializedName("app_name") val appName: String?,
    @SerializedName("app_id") val appId: String?,
    @SerializedName("developer") val developer: String?,
    @SerializedName("icon_url") val iconUrl: String?,
    @SerializedName("source") val source: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("competitors") val competitors: List<String>?,
    @SerializedName("status") val status: String?,
    @SerializedName("current_step") val currentStep: String?,
    @SerializedName("progress") val progress: Int?,
    @SerializedName("error") val error: String?,
    @SerializedName("warnings") val warnings: List<String>?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("steps") val steps: List<StepDto>?,
    @SerializedName("stats") val stats: StatsDto?
)

data class StepDto(
    @SerializedName("key") val key: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("model_role") val modelRole: String?,
    @SerializedName("model") val model: String?
)

data class StatsDto(
    @SerializedName("total_reviews") val totalReviews: Int?,
    @SerializedName("classified_reviews") val classifiedReviews: Int?,
    @SerializedName("average_rating") val averageRating: Double?,
    @SerializedName("sentiment") val sentiment: Map<String, Int>?,
    @SerializedName("categories") val categories: Map<String, Int>?,
    @SerializedName("rating_distribution") val ratingDistribution: Map<String, Int>?,
    @SerializedName("theme_count") val themeCount: Int?,
    @SerializedName("backlog_count") val backlogCount: Int?,
    @SerializedName("competitor_count") val competitorCount: Int?
)

data class ReviewPageDto(
    @SerializedName("count") val count: Int?,
    @SerializedName("next") val next: String?,
    @SerializedName("results") val results: List<ReviewDto>?
)

data class ReviewDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("author") val author: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("text") val text: String?,
    @SerializedName("rating") val rating: Int?,
    @SerializedName("review_date") val reviewDate: String?,
    @SerializedName("app_version") val appVersion: String?,
    @SerializedName("sentiment") val sentiment: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("severity") val severity: Int?,
    @SerializedName("summary") val summary: String?,
    @SerializedName("theme_title") val themeTitle: String?
)

data class ListEnvelopeDto<T>(@SerializedName("results") val results: List<T>?)

data class ThemeDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("position") val position: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("review_count") val reviewCount: Int?,
    @SerializedName("negative_share") val negativeShare: Double?,
    @SerializedName("quotes") val quotes: List<String>?
)

data class BacklogItemDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("rank") val rank: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("priority_score") val priorityScore: Int?,
    @SerializedName("reasoning") val reasoning: String?,
    @SerializedName("user_impact") val userImpact: String?,
    @SerializedName("suggested_fix") val suggestedFix: String?,
    @SerializedName("effort") val effort: String?,
    @SerializedName("theme_title") val themeTitle: String?,
    @SerializedName("evidence_quotes") val evidenceQuotes: List<String>?
)

data class CompetitorDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("name") val name: String?,
    @SerializedName("summary") val summary: String?,
    @SerializedName("strengths") val strengths: List<String>?,
    @SerializedName("weaknesses") val weaknesses: List<String>?,
    @SerializedName("opportunities") val opportunities: List<String>?,
    @SerializedName("sources") val sources: List<SourceDto>?,
    @SerializedName("error") val error: String?
)

data class SourceDto(
    @SerializedName("title") val title: String?,
    @SerializedName("url") val url: String?
)

data class ReplyRequestDto(@SerializedName("tone") val tone: String)

data class ReplyDraftDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("tone") val tone: String?,
    @SerializedName("text") val text: String?
)

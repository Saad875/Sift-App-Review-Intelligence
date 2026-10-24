package com.sift.app.domain.util

/**
 * Localised text used when exporting Markdown. Templates use String.format
 * placeholders. The presentation layer fills this from string resources so the
 * domain layer stays free of Android and of hardcoded copy.
 */
data class MarkdownLabels(
    val reportTitle: String,
    val sourceLine: String,
    val countryLine: String,
    val overviewHeading: String,
    val reviewsAnalysed: String,
    val averageRating: String,
    val sentimentLine: String,
    val positive: String,
    val neutral: String,
    val negative: String,
    val starsColumn: String,
    val reviewsColumn: String,
    val categoryColumn: String,
    val categoryNames: Map<com.sift.app.domain.model.ReviewCategory, String>,
    val sourceNames: Map<com.sift.app.domain.model.AnalysisSource, String>,
    val themesHeading: String,
    val themeStats: String,
    val backlogHeading: String,
    val backlogItemHeading: String,
    val typeNames: Map<com.sift.app.domain.model.BacklogType, String>,
    val themeLine: String,
    val whyRanks: String,
    val userImpact: String,
    val suggestedFix: String,
    val evidence: String,
    val competitorsHeading: String,
    val strengths: String,
    val weaknesses: String,
    val opportunities: String,
    val sources: String,
    val footer: String,
    val ticketPriority: String,
    val ticketEffort: String,
    val ticketType: String
)

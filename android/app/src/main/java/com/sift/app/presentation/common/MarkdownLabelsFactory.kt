package com.sift.app.presentation.common

import android.content.Context
import com.sift.app.R
import com.sift.app.domain.model.AnalysisSource
import com.sift.app.domain.model.BacklogType
import com.sift.app.domain.model.ReviewCategory
import com.sift.app.domain.util.MarkdownLabels

/** Builds [MarkdownLabels] from string resources so exports follow the device language. */
object MarkdownLabelsFactory {
    fun create(context: Context): MarkdownLabels = with(context) {
        MarkdownLabels(
            reportTitle = getString(R.string.md_report_title),
            sampleNotice = getString(R.string.sample_data_banner),
            sourceLine = getString(R.string.md_source_line),
            countryLine = getString(R.string.md_country_line),
            overviewHeading = getString(R.string.report_tab_overview),
            reviewsAnalysed = getString(R.string.md_reviews_analysed),
            averageRating = getString(R.string.md_average_rating),
            sentimentLine = getString(R.string.md_sentiment_line),
            positive = getString(R.string.sentiment_positive).lowercase(),
            neutral = getString(R.string.sentiment_neutral).lowercase(),
            negative = getString(R.string.sentiment_negative).lowercase(),
            starsColumn = getString(R.string.md_stars_column),
            reviewsColumn = getString(R.string.md_reviews_column),
            categoryColumn = getString(R.string.md_category_column),
            categoryNames = ReviewCategory.entries.associateWith { getString(it.labelRes) },
            sourceNames = AnalysisSource.entries.associateWith { getString(it.labelRes) },
            themesHeading = getString(R.string.report_top_themes),
            themeStats = getString(R.string.md_theme_stats),
            backlogHeading = getString(R.string.md_backlog_heading),
            backlogItemHeading = getString(R.string.md_backlog_item_heading),
            typeNames = BacklogType.entries.associateWith { getString(it.labelRes) },
            themeLine = getString(R.string.md_theme_line),
            whyRanks = getString(R.string.md_why_ranks),
            userImpact = getString(R.string.md_user_impact),
            suggestedFix = getString(R.string.md_suggested_fix),
            evidence = getString(R.string.backlog_evidence),
            competitorsHeading = getString(R.string.report_tab_competitors),
            strengths = getString(R.string.competitor_strengths),
            weaknesses = getString(R.string.competitor_weaknesses),
            opportunities = getString(R.string.competitor_opportunities),
            sources = getString(R.string.competitor_sources),
            footer = getString(R.string.md_footer),
            ticketPriority = getString(R.string.md_ticket_priority),
            ticketEffort = getString(R.string.md_ticket_effort),
            ticketType = getString(R.string.md_ticket_type)
        )
    }
}

package com.sift.app.domain.util

import com.sift.app.domain.model.BacklogItem
import com.sift.app.domain.model.Report
import com.sift.app.domain.model.Sentiment
import kotlin.math.roundToInt

/** Builds Markdown for the full report and for single backlog tickets. */
class MarkdownBuilder(private val labels: MarkdownLabels) {

    /** The whole report: overview, themes, backlog and competitors. */
    fun report(report: Report): String = buildString {
        val analysis = report.analysis
        val stats = analysis.stats
        appendLine("# ${labels.reportTitle.format(analysis.appName)}")
        appendLine()
        if (analysis.isDemo) {
            appendLine("> ${labels.sampleNotice}")
            appendLine()
        }
        val meta = mutableListOf(labels.sourceLine.format(labels.sourceNames[analysis.source].orEmpty()))
        if (analysis.country.isNotBlank()) meta += labels.countryLine.format(analysis.country.uppercase())
        appendLine(meta.joinToString(" · "))
        appendLine()

        appendLine("## ${labels.overviewHeading}")
        appendLine()
        appendLine("- ${labels.reviewsAnalysed.format(stats.totalReviews)}")
        stats.averageRating?.let { appendLine("- ${labels.averageRating.format(it)}") }
        val sentiment = stats.sentiment
        val shares = listOf(
            percent(sentiment.share(Sentiment.POSITIVE)) to labels.positive,
            percent(sentiment.share(Sentiment.NEUTRAL)) to labels.neutral,
            percent(sentiment.share(Sentiment.NEGATIVE)) to labels.negative
        ).joinToString(", ") { (value, name) -> "$value% $name" }
        appendLine("- ${labels.sentimentLine.format(shares)}")
        appendLine()
        appendLine("| ${labels.starsColumn} | ${labels.reviewsColumn} |")
        appendLine("|---|---|")
        for (star in 5 downTo 1) {
            appendLine("| $star | ${stats.ratingDistribution.getOrElse(star - 1) { 0 }} |")
        }
        appendLine()
        val categories = stats.categories.filterValues { it > 0 }.entries.sortedByDescending { it.value }
        if (categories.isNotEmpty()) {
            appendLine("| ${labels.categoryColumn} | ${labels.reviewsColumn} |")
            appendLine("|---|---|")
            categories.forEach { (category, count) ->
                appendLine("| ${labels.categoryNames[category].orEmpty()} | $count |")
            }
            appendLine()
        }

        if (report.themes.isNotEmpty()) {
            appendLine("## ${labels.themesHeading}")
            appendLine()
            report.themes.forEach { theme ->
                appendLine("### ${theme.title}")
                appendLine(labels.themeStats.format(theme.reviewCount, percent(theme.negativeShare)))
                appendLine()
                appendLine(theme.description)
                appendLine()
                theme.quotes.forEach { appendQuote(it) }
            }
        }

        if (report.backlog.isNotEmpty()) {
            appendLine("## ${labels.backlogHeading}")
            appendLine()
            report.backlog.forEach { item ->
                appendLine(
                    "### " + labels.backlogItemHeading.format(
                        item.rank, item.title, labels.typeNames[item.type].orEmpty(),
                        item.priorityScore, item.effort.key
                    )
                )
                appendItemBody(item)
            }
        }

        val competitors = report.competitors.filter { it.error.isBlank() }
        if (competitors.isNotEmpty()) {
            appendLine("## ${labels.competitorsHeading}")
            appendLine()
            competitors.forEach { insight ->
                appendLine("### ${insight.name}")
                if (insight.summary.isNotBlank()) {
                    appendLine()
                    appendLine(insight.summary)
                }
                appendList(labels.strengths, insight.strengths)
                appendList(labels.weaknesses, insight.weaknesses)
                appendList(labels.opportunities, insight.opportunities)
                appendList(labels.sources, insight.sources.map { "[${it.title.ifBlank { it.url }}](${it.url})" })
                appendLine()
            }
        }
        appendLine("---")
        append(labels.footer)
        appendLine()
    }

    /** A single backlog item formatted as a ticket for an issue tracker. */
    fun ticket(item: BacklogItem): String = buildString {
        appendLine("## ${item.title}")
        appendLine()
        appendLine("- ${labels.ticketType.format(labels.typeNames[item.type].orEmpty())}")
        appendLine("- ${labels.ticketPriority.format(item.priorityScore)}")
        appendLine("- ${labels.ticketEffort.format(item.effort.key)}")
        item.themeTitle?.let { appendLine("- ${labels.themeLine.format(it)}") }
        appendLine()
        appendItemBody(item, includeTheme = false)
    }

    private fun StringBuilder.appendItemBody(item: BacklogItem, includeTheme: Boolean = true) {
        if (includeTheme) item.themeTitle?.let { appendLine(labels.themeLine.format(it)) }
        appendLine()
        appendLine("**${labels.whyRanks}** ${item.reasoning}")
        appendLine()
        appendLine("**${labels.userImpact}** ${item.userImpact}")
        appendLine()
        appendLine("**${labels.suggestedFix}** ${item.suggestedFix}")
        appendLine()
        if (item.evidenceQuotes.isNotEmpty()) {
            appendLine("**${labels.evidence}**")
            appendLine()
            item.evidenceQuotes.forEach { appendQuote(it) }
        }
    }

    private fun StringBuilder.appendQuote(text: String) {
        appendLine("> ${text.replace("\n", " ")}")
        appendLine()
    }

    private fun StringBuilder.appendList(heading: String, values: List<String>) {
        if (values.isEmpty()) return
        appendLine()
        appendLine("**$heading**")
        appendLine()
        values.forEach { appendLine("- $it") }
    }

    private fun percent(share: Float): Int = (share * 100).roundToInt()
}

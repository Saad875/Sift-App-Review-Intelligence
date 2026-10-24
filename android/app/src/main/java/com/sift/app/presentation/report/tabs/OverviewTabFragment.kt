package com.sift.app.presentation.report.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.sift.app.R
import com.sift.app.databinding.FragmentReportOverviewBinding
import com.sift.app.databinding.ItemLegendBinding
import com.sift.app.databinding.ItemMetricBinding
import com.sift.app.databinding.ItemThemeBinding
import com.sift.app.domain.model.Report
import com.sift.app.domain.model.Sentiment
import com.sift.app.domain.model.Theme
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.EmptyKind
import com.sift.app.presentation.common.Formatters
import com.sift.app.presentation.common.bind
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.colorRes
import com.sift.app.presentation.common.contentOrNull
import com.sift.app.presentation.common.countUp
import com.sift.app.presentation.common.countUpDecimal
import com.sift.app.presentation.common.fadeInUp
import com.sift.app.presentation.common.labelRes
import com.sift.app.presentation.report.ReportViewModel
import com.sift.app.presentation.widget.BarChartView
import com.sift.app.presentation.widget.DonutChartView

/**
 * Overview tab: headline metrics, sentiment donut, rating distribution, the
 * top themes and any pipeline warnings. Charts animate the first time the
 * page becomes visible (ViewPager2 only resumes the current page).
 */
class OverviewTabFragment : BaseFragment<FragmentReportOverviewBinding>(FragmentReportOverviewBinding::inflate) {

    private val viewModel: ReportViewModel by viewModels(ownerProducer = { requireParentFragment() }) {
        ReportViewModel.factory(container)
    }

    private var renderedReport: Report? = null
    private var animatePending = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderedReport = null
        // After a configuration change the user has already seen the entrance.
        animatePending = savedInstanceState == null
        collectWhileStarted(viewModel.state) { state -> state.contentOrNull?.let(::render) }
    }

    override fun onResume() {
        super.onResume()
        val report = renderedReport ?: return
        if (animatePending) {
            animatePending = false
            bindMetrics(report, animate = true)
            bindCharts(report, animate = true)
        }
    }

    private fun render(report: Report) {
        if (report == renderedReport) return
        val firstRender = renderedReport == null
        renderedReport = report
        val animate = animatePending && isResumed
        if (animate) animatePending = false
        bindMetrics(report, animate)
        bindCharts(report, animate)
        bindThemes(report.themes, animate && firstRender)
        bindWarnings(report.analysis.warnings)
    }

    private fun bindMetrics(report: Report, animate: Boolean) {
        val stats = report.analysis.stats
        val context = requireContext()
        val reviews = binding.metricReviews
        reviews.metricLabel.setText(R.string.report_metric_reviews)
        val reviewsText = Formatters.count(stats.totalReviews)
        if (animate) reviews.metricValue.countUp(stats.totalReviews) else reviews.metricValue.text = reviewsText
        describe(reviews, reviewsText)

        val rating = binding.metricRating
        rating.metricLabel.setText(R.string.report_metric_rating)
        val average = stats.averageRating
        val ratingText = average?.let { Formatters.rating(context, it) } ?: getString(R.string.value_unavailable)
        if (animate && average != null) {
            rating.metricValue.countUpDecimal(average) { Formatters.rating(context, it) }
        } else {
            rating.metricValue.text = ratingText
        }
        describe(rating, ratingText)

        val negative = binding.metricNegative
        negative.metricLabel.setText(R.string.report_metric_negative)
        val percent = (stats.sentiment.share(Sentiment.NEGATIVE) * PERCENT).toInt()
        val negativeText = getString(R.string.percent_value, percent)
        if (animate) {
            negative.metricValue.countUp(percent) { getString(R.string.percent_value, it) }
        } else {
            negative.metricValue.text = negativeText
        }
        describe(negative, negativeText)
    }

    /** Announces the final value even while the number is still counting up. */
    private fun describe(metric: ItemMetricBinding, value: String) {
        metric.root.contentDescription = getString(R.string.cd_metric, metric.metricLabel.text, value)
    }

    private fun bindCharts(report: Report, animate: Boolean) {
        val context = requireContext()
        val stats = report.analysis.stats
        val sentiment = stats.sentiment
        val order = listOf(Sentiment.POSITIVE, Sentiment.NEUTRAL, Sentiment.NEGATIVE)
        binding.donut.setSegments(
            order.map { DonutChartView.Segment(sentiment.share(it), ContextCompat.getColor(context, it.colorRes)) },
            animate
        )
        binding.donut.centerText = Formatters.count(sentiment.total)
        binding.donut.captionText = getString(R.string.report_donut_caption)
        val legends = listOf(binding.legendPositive, binding.legendNeutral, binding.legendNegative)
        order.zip(legends).forEach { (value, legend) -> bindLegend(legend, value, sentiment.share(value)) }
        binding.donut.contentDescription = getString(
            R.string.cd_sentiment_chart,
            Formatters.percent(context, sentiment.share(Sentiment.POSITIVE)),
            Formatters.percent(context, sentiment.share(Sentiment.NEUTRAL)),
            Formatters.percent(context, sentiment.share(Sentiment.NEGATIVE))
        )

        val distribution = stats.ratingDistribution
        val bars = (STARS downTo 1).map { star ->
            val count = distribution.getOrElse(star - 1) { 0 }
            BarChartView.Bar(
                label = getString(R.string.star_label, star),
                value = count,
                color = ContextCompat.getColor(context, starColor(star)),
                valueText = Formatters.count(count)
            )
        }
        binding.ratingBars.setBars(bars, animate)
        binding.ratingBars.contentDescription = getString(
            R.string.cd_rating_chart,
            bars.joinToString(getString(R.string.list_separator)) {
                getString(R.string.cd_rating_bar, it.label, it.valueText)
            }
        )
    }

    private fun bindLegend(legend: ItemLegendBinding, sentiment: Sentiment, share: Float) {
        val context = legend.root.context
        legend.legendDot.backgroundTintList = ContextCompat.getColorStateList(context, sentiment.colorRes)
        legend.legendLabel.setText(sentiment.labelRes)
        legend.legendValue.text = Formatters.percent(context, share)
    }

    private fun bindThemes(themes: List<Theme>, animate: Boolean) {
        val parent = binding.themeContainer
        parent.removeAllViews()
        val top = themes.sortedBy { it.position }.take(TOP_THEMES)
        binding.themesEmpty.root.isVisible = top.isEmpty()
        if (top.isEmpty()) binding.themesEmpty.bind(EmptyKind.NO_THEMES)
        val inflater = LayoutInflater.from(parent.context)
        val step = resources.getInteger(R.integer.motion_stagger_step).toLong()
        top.forEachIndexed { index, theme ->
            val item = ItemThemeBinding.inflate(inflater, parent, false)
            bindTheme(item, theme)
            parent.addView(item.root)
            if (animate) item.root.fadeInUp(step * index)
        }
    }

    private fun bindTheme(item: ItemThemeBinding, theme: Theme) {
        val context = item.root.context
        item.themeTitle.text = theme.title
        item.themeDescription.text = theme.description
        item.themeDescription.isVisible = theme.description.isNotBlank()
        val reviews = resources.getQuantityString(R.plurals.review_count, theme.reviewCount, Formatters.count(theme.reviewCount))
        item.themeStats.text = getString(R.string.theme_stats, reviews, Formatters.percent(context, theme.negativeShare))
        item.negativeShare.progress = (theme.negativeShare * PERCENT).toInt()
        val quote = theme.quotes.firstOrNull()
        item.themeQuote.isVisible = quote != null
        item.themeQuote.text = quote?.let { getString(R.string.quote_format, it) }
        item.root.contentDescription = listOfNotNull(
            theme.title, item.themeStats.text, theme.description.takeIf { it.isNotBlank() }, quote
        ).joinToString(getString(R.string.list_separator))
    }

    private fun bindWarnings(warnings: List<String>) {
        binding.warningsCard.isVisible = warnings.isNotEmpty()
        binding.warningsText.text = warnings.joinToString(LINE_BREAK)
    }

    private fun starColor(star: Int): Int = when {
        star >= GOOD_STARS -> R.color.sift_positive
        star == MIXED_STARS -> R.color.sift_neutral
        else -> R.color.sift_negative
    }

    override fun onDestroyView() {
        renderedReport = null
        super.onDestroyView()
    }

    private companion object {
        const val TOP_THEMES = 3
        const val STARS = 5
        const val GOOD_STARS = 4
        const val MIXED_STARS = 3
        const val PERCENT = 100
        const val LINE_BREAK = "\n"
    }
}

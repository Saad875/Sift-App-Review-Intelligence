package com.sift.app.presentation.report.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.sift.app.R
import com.sift.app.databinding.FragmentReportReviewsBinding
import com.sift.app.databinding.ItemFilterChipBinding
import com.sift.app.domain.model.Review
import com.sift.app.domain.model.ReviewCategory
import com.sift.app.domain.model.ReviewFilter
import com.sift.app.domain.model.Sentiment
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.EmptyKind
import com.sift.app.presentation.common.Formatters
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.bind
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.labelRes
import com.sift.app.presentation.report.ReportViewModel
import com.sift.app.presentation.review.ReviewDetailFragment
import com.sift.app.presentation.reviews.ReviewAdapter
import com.sift.app.presentation.reviews.ReviewsContent
import com.sift.app.presentation.reviews.ReviewsViewModel

/** Reviews tab: every analysed review, filterable by sentiment and category. */
class ReviewsTabFragment : BaseFragment<FragmentReportReviewsBinding>(FragmentReportReviewsBinding::inflate) {

    private val reportViewModel: ReportViewModel by viewModels(ownerProducer = { requireParentFragment() }) {
        ReportViewModel.factory(container)
    }
    private val viewModel: ReviewsViewModel by viewModels {
        ReviewsViewModel.factory(container, reportViewModel.analysisId)
    }

    private lateinit var adapter: ReviewAdapter
    private var renderedFilter: ReviewFilter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderedFilter = null
        adapter = ReviewAdapter(::open)
        binding.list.adapter = adapter

        val filter = viewModel.filter
        val sentimentIds = addChips(binding.sentimentChips, Sentiment.entries, { it.labelRes }, filter.sentiment)
        val categoryIds = addChips(binding.categoryChips, ReviewCategory.entries, { it.labelRes }, filter.category)
        binding.sentimentChips.setOnCheckedStateChangeListener { _, checked ->
            viewModel.setSentiment(checked.firstOrNull()?.let(sentimentIds::get))
        }
        binding.categoryChips.setOnCheckedStateChangeListener { _, checked ->
            viewModel.setCategory(checked.firstOrNull()?.let(categoryIds::get))
        }
        binding.clearFilters.setOnClickListener {
            binding.sentimentChips.clearCheck()
            binding.categoryChips.clearCheck()
            viewModel.clearFilters()
        }

        collectWhileStarted(viewModel.state, ::render)
    }

    /** Adds one filter chip per value and returns a map from chip id to value. */
    private fun <T> addChips(group: ChipGroup, values: List<T>, label: (T) -> Int, selected: T?): Map<Int, T> {
        val inflater = LayoutInflater.from(group.context)
        return values.associateBy { value ->
            val chip: Chip = ItemFilterChipBinding.inflate(inflater, group, false).root
            chip.id = View.generateViewId()
            chip.setText(label(value))
            chip.isChecked = value == selected
            group.addView(chip)
            chip.id
        }
    }

    private fun render(state: UiState<ReviewsContent>) {
        binding.skeleton.root.isVisible = state is UiState.Loading
        binding.list.isVisible = state is UiState.Content
        binding.emptyState.root.isVisible = state is UiState.Empty
        binding.errorState.root.isVisible = state is UiState.Error
        val filtered = viewModel.filter != ReviewFilter()
        binding.clearFilters.visibility = if (filtered) View.VISIBLE else View.INVISIBLE
        when (state) {
            is UiState.Content -> {
                val content = state.data
                if (content.filter != renderedFilter) {
                    adapter.resetEntrance()
                    renderedFilter = content.filter
                    adapter.submitList(content.reviews) { binding.list.scrollToPosition(0) }
                } else {
                    adapter.submitList(content.reviews)
                }
                binding.resultCount.text = resources.getQuantityString(
                    R.plurals.review_count, content.reviews.size, Formatters.count(content.reviews.size)
                )
            }
            is UiState.Empty -> {
                binding.resultCount.text = resources.getQuantityString(R.plurals.review_count, 0, Formatters.count(0))
                if (state.kind == EmptyKind.NO_FILTERED_REVIEWS) {
                    binding.emptyState.bind(state.kind, R.string.reviews_clear_filters) { binding.clearFilters.performClick() }
                } else {
                    binding.emptyState.bind(state.kind)
                }
            }
            is UiState.Error -> binding.errorState.bind(state.error, viewModel::retry)
            UiState.Loading -> Unit
        }
    }

    private fun open(review: Review) {
        navigator.push(ReviewDetailFragment.newInstance(review.id))
    }

    override fun onDestroyView() {
        binding.list.adapter = null
        super.onDestroyView()
    }
}

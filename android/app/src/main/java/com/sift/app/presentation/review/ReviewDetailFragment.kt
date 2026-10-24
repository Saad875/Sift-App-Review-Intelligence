package com.sift.app.presentation.review

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.sift.app.R
import com.sift.app.databinding.FragmentReviewDetailBinding
import com.sift.app.domain.model.ReplyTone
import com.sift.app.domain.model.Review
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.Formatters
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.bind
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.colorRes
import com.sift.app.presentation.common.containerColorRes
import com.sift.app.presentation.common.copyToClipboard
import com.sift.app.presentation.common.errorMessage
import com.sift.app.presentation.common.fadeInUp
import com.sift.app.presentation.common.fadeVisible
import com.sift.app.presentation.common.labelRes
import com.sift.app.presentation.reviews.ReviewText

/** A review with its classification, and tone-aware reply drafting. */
class ReviewDetailFragment : BaseFragment<FragmentReviewDetailBinding>(FragmentReviewDetailBinding::inflate) {

    private val viewModel: ReviewDetailViewModel by viewModels { ReviewDetailViewModel.factory(container) }
    private var renderedReview: Review? = null

    private val toneByChip by lazy {
        mapOf(
            R.id.toneFriendly to ReplyTone.FRIENDLY,
            R.id.toneFormal to ReplyTone.FORMAL,
            R.id.toneApologetic to ReplyTone.APOLOGETIC
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderedReview = null
        binding.toolbar.applySystemBarPadding(top = true)
        binding.scroll.applySystemBarPadding(bottom = true)
        binding.toolbar.setNavigationOnClickListener { navigator.back() }

        binding.toneChips.setOnCheckedStateChangeListener { _, checked ->
            checked.firstOrNull()?.let(toneByChip::get)?.let(viewModel::selectTone)
        }
        binding.draftButton.setOnClickListener { viewModel.draft() }
        binding.replyInput.doAfterTextChanged { viewModel.onReplyEdited(it?.toString().orEmpty()) }
        binding.copyReply.setOnClickListener {
            val text = binding.replyInput.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                requireContext().copyToClipboard(getString(R.string.review_reply_clip_label), text, R.string.copied_to_clipboard)
            }
        }

        collectWhileStarted(viewModel.state, ::render)
    }

    private fun render(state: UiState<ReviewDetailContent>) {
        binding.skeleton.root.isVisible = state is UiState.Loading
        binding.errorState.root.isVisible = state is UiState.Error
        binding.scroll.isVisible = state is UiState.Content
        when (state) {
            is UiState.Content -> renderContent(state.data)
            is UiState.Error -> binding.errorState.bind(state.error, viewModel::load)
            UiState.Loading, is UiState.Empty -> Unit
        }
    }

    private fun renderContent(content: ReviewDetailContent) {
        if (content.review != renderedReview) {
            val firstRender = renderedReview == null
            renderedReview = content.review
            bindReview(content.review)
            if (firstRender) binding.content.fadeInUp()
        }

        val chipId = toneByChip.entries.first { it.value == content.tone }.key
        if (binding.toneChips.checkedChipId != chipId) binding.toneChips.check(chipId)

        val drafting = content.reply == ReplyState.Drafting
        binding.sampleNote.isVisible = !content.canReply
        binding.toneChips.isEnabled = content.canReply && !drafting
        for (index in 0 until binding.toneChips.childCount) {
            binding.toneChips.getChildAt(index).isEnabled = content.canReply && !drafting
        }
        binding.draftButton.isEnabled = content.canReply && !drafting
        binding.draftButton.setText(
            when {
                drafting -> R.string.review_drafting
                content.reply is ReplyState.Ready -> R.string.review_redraft_reply
                else -> R.string.review_draft_reply
            }
        )
        binding.draftProgress.fadeVisible(drafting)

        val failed = content.reply as? ReplyState.Failed
        binding.replyError.isVisible = failed != null
        binding.replyError.text = failed?.let { requireContext().errorMessage(it.error) }

        val ready = content.reply as? ReplyState.Ready
        val showReply = ready != null
        if (showReply && !binding.replyLayout.isVisible) binding.replyLayout.fadeInUp()
        binding.replyLayout.isVisible = showReply
        binding.copyReply.isVisible = showReply
        if (ready != null && binding.replyInput.text?.toString() != ready.text) {
            binding.replyInput.setText(ready.text)
        }
    }

    private fun bindReview(review: Review) {
        val context = requireContext()
        binding.stars.isVisible = review.rating != null
        binding.stars.text = review.rating?.let { ReviewText.stars(context, it) }
        binding.stars.contentDescription = review.rating?.let {
            resources.getQuantityString(R.plurals.cd_star_rating, it, it)
        }
        binding.date.text = Formatters.date(review.dateMillis).orEmpty()

        val sentiment = review.sentiment
        binding.sentimentBadge.isVisible = sentiment != null
        if (sentiment != null) {
            binding.sentimentBadge.setText(sentiment.labelRes)
            binding.sentimentBadge.setTextColor(ContextCompat.getColor(context, sentiment.colorRes))
            ViewCompat.setBackgroundTintList(
                binding.sentimentBadge, ContextCompat.getColorStateList(context, sentiment.containerColorRes)
            )
        }
        binding.categoryBadge.isVisible = review.category != null
        review.category?.let { binding.categoryBadge.setText(it.labelRes) }
        binding.severityBadge.isVisible = review.severity != null
        binding.severityBadge.text = review.severity?.let { getString(R.string.review_severity, it) }

        binding.reviewTitle.isVisible = review.title.isNotBlank()
        binding.reviewTitle.text = review.title
        binding.reviewText.text = review.text
        val byline = ReviewText.byline(context, review)
        binding.byline.isVisible = byline.isNotEmpty()
        binding.byline.text = byline

        val hasSummary = review.summary.isNotBlank()
        binding.summaryLabel.isVisible = hasSummary
        binding.summary.isVisible = hasSummary
        binding.summary.text = review.summary
        binding.themeLine.isVisible = !review.themeTitle.isNullOrBlank()
        binding.themeLine.text = review.themeTitle?.let { getString(R.string.backlog_theme, it) }
    }

    override fun onDestroyView() {
        renderedReview = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(reviewId: Long) = ReviewDetailFragment().apply {
            arguments = bundleOf(ReviewDetailViewModel.KEY_REVIEW_ID to reviewId)
        }
    }
}

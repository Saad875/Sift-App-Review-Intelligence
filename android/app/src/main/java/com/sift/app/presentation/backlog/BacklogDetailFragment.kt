package com.sift.app.presentation.backlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.viewModels
import com.sift.app.R
import com.sift.app.databinding.FragmentBacklogDetailBinding
import com.sift.app.databinding.ItemDetailSectionBinding
import com.sift.app.databinding.ItemEvidenceQuoteBinding
import com.sift.app.domain.model.BacklogItem
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.MarkdownLabelsFactory
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.applySystemBarBottomMargin
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.bind
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.colorRes
import com.sift.app.presentation.common.copyToClipboard
import com.sift.app.presentation.common.fadeInUp
import com.sift.app.presentation.common.iconRes
import com.sift.app.presentation.common.labelRes
import com.sift.app.presentation.common.priorityColorRes

/** A backlog item with its reasoning, impact, fix and evidence, and "Copy as ticket". */
class BacklogDetailFragment : BaseFragment<FragmentBacklogDetailBinding>(FragmentBacklogDetailBinding::inflate) {

    private val viewModel: BacklogDetailViewModel by viewModels { BacklogDetailViewModel.factory(container) }
    private var rendered: BacklogItem? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rendered = null
        binding.toolbar.applySystemBarPadding(top = true)
        binding.scroll.applySystemBarPadding()
        binding.copyTicket.applySystemBarBottomMargin()
        binding.toolbar.setNavigationOnClickListener { navigator.back() }
        binding.copyTicket.setOnClickListener { copyTicket() }
        collectWhileStarted(viewModel.state, ::render)
    }

    private fun render(state: UiState<BacklogItem>) {
        binding.skeleton.root.isVisible = state is UiState.Loading
        binding.errorState.root.isVisible = state is UiState.Error
        binding.scroll.isVisible = state is UiState.Content
        binding.copyTicket.isVisible = state is UiState.Content
        when (state) {
            is UiState.Content -> bindItem(state.data)
            is UiState.Error -> binding.errorState.bind(state.error, viewModel::load)
            UiState.Loading, is UiState.Empty -> Unit
        }
    }

    private fun bindItem(item: BacklogItem) {
        if (item == rendered) return
        val firstRender = rendered == null
        rendered = item
        val context = requireContext()
        binding.rank.text = getString(R.string.backlog_rank, item.rank)
        binding.title.text = item.title
        binding.score.setScore(item.priorityScore, ContextCompat.getColor(context, priorityColorRes(item.priorityScore)))
        binding.score.contentDescription = getString(R.string.cd_priority_score, item.priorityScore)

        binding.typeBadge.setText(item.type.labelRes)
        binding.typeBadge.setCompoundDrawablesRelativeWithIntrinsicBounds(item.type.iconRes, 0, 0, 0)
        val typeColor = ContextCompat.getColorStateList(context, item.type.colorRes)
        TextViewCompat.setCompoundDrawableTintList(binding.typeBadge, typeColor)
        binding.typeBadge.setTextColor(ContextCompat.getColor(context, item.type.colorRes))
        binding.effortBadge.text = getString(R.string.backlog_effort, getString(item.effort.labelRes))

        binding.themeLine.isVisible = !item.themeTitle.isNullOrBlank()
        binding.themeLine.text = item.themeTitle?.let { getString(R.string.backlog_theme, it) }

        bindSection(binding.reasoning, R.string.backlog_section_reasoning, item.reasoning)
        bindSection(binding.impact, R.string.backlog_section_impact, item.userImpact)
        bindSection(binding.fix, R.string.backlog_section_fix, item.suggestedFix)

        val evidence = binding.evidenceContainer
        evidence.removeAllViews()
        binding.evidenceLabel.isVisible = item.evidenceQuotes.isNotEmpty()
        val inflater = LayoutInflater.from(evidence.context)
        item.evidenceQuotes.forEach { quote ->
            val row = ItemEvidenceQuoteBinding.inflate(inflater, evidence, false).root
            row.text = getString(R.string.quote_format, quote)
            evidence.addView(row)
        }
        if (firstRender) binding.content.fadeInUp()
    }

    private fun bindSection(section: ItemDetailSectionBinding, @StringRes title: Int, body: String) {
        section.root.isVisible = body.isNotBlank()
        section.sectionTitle.setText(title)
        section.sectionBody.text = body
    }

    private fun copyTicket() {
        val context = requireContext()
        val ticket = viewModel.ticket(MarkdownLabelsFactory.create(context)) ?: return
        context.copyToClipboard(getString(R.string.backlog_ticket_clip_label), ticket, R.string.backlog_ticket_copied)
    }

    override fun onDestroyView() {
        rendered = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(itemId: Long) = BacklogDetailFragment().apply {
            arguments = bundleOf(BacklogDetailViewModel.KEY_ITEM_ID to itemId)
        }
    }
}

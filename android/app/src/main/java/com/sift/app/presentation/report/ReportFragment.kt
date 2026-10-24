package com.sift.app.presentation.report

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.sift.app.R
import com.sift.app.databinding.FragmentReportBinding
import com.sift.app.domain.model.Report
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.ExternalActions
import com.sift.app.presentation.common.Formatters
import com.sift.app.presentation.common.MarkdownLabelsFactory
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.bind
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.labelRes
import com.sift.app.presentation.common.snackbar
import com.sift.app.presentation.home.HomeFragment
import com.sift.app.presentation.report.tabs.BacklogTabFragment
import com.sift.app.presentation.report.tabs.CompetitorsTabFragment
import com.sift.app.presentation.report.tabs.OverviewTabFragment
import com.sift.app.presentation.report.tabs.ReviewsTabFragment
import kotlinx.coroutines.launch

/** The report: Overview, Backlog, Reviews and Competitors tabs, with Markdown sharing. */
class ReportFragment : BaseFragment<FragmentReportBinding>(FragmentReportBinding::inflate) {

    private val viewModel: ReportViewModel by viewModels { ReportViewModel.factory(container) }
    private var mediator: TabLayoutMediator? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.applySystemBarPadding(top = true)
        binding.toolbar.setNavigationOnClickListener { if (!navigator.back()) navigator.setRoot(HomeFragment.newInstance()) }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.actionShare) {
                share()
                true
            } else {
                false
            }
        }

        binding.pager.adapter = ReportPagerAdapter(this)
        mediator = TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.setText(TAB_TITLES[position])
        }.also { it.attach() }

        collectWhileStarted(viewModel.state, ::render)
    }

    private fun render(state: UiState<Report>) {
        val content = state is UiState.Content
        binding.skeleton.root.isVisible = state is UiState.Loading
        binding.tabs.isVisible = content
        binding.tabDivider.isVisible = content
        binding.pager.isVisible = content
        binding.errorState.root.isVisible = state is UiState.Error
        binding.emptyState.root.isVisible = state is UiState.Empty
        binding.toolbar.menu.findItem(R.id.actionShare)?.isVisible = content
        when (state) {
            is UiState.Content -> renderHeader(state.data)
            is UiState.Error -> binding.errorState.bind(state.error, viewModel::retry)
            is UiState.Empty -> binding.emptyState.bind(state.kind, R.string.progress_back_home) {
                navigator.setRoot(HomeFragment.newInstance())
            }
            UiState.Loading -> Unit
        }
    }

    private fun renderHeader(report: Report) {
        val analysis = report.analysis
        binding.toolbar.title = analysis.appName
        val parts = mutableListOf(
            resources.getQuantityString(
                R.plurals.review_count, analysis.stats.totalReviews, Formatters.count(analysis.stats.totalReviews)
            ),
            getString(analysis.source.labelRes)
        )
        binding.toolbar.subtitle = parts.joinToString(getString(R.string.meta_separator))
    }

    private fun share() {
        val context = requireContext()
        val markdown = viewModel.markdown(MarkdownLabelsFactory.create(context)) ?: return
        val report = (viewModel.state.value as? UiState.Content)?.data ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                ExternalActions.shareMarkdown(
                    context, report.analysis.appName, getString(R.string.md_report_title, report.analysis.appName), markdown
                )
            }.onFailure { binding.root.snackbar(R.string.report_share_failed) }
        }
    }

    override fun onDestroyView() {
        mediator?.detach()
        mediator = null
        binding.pager.adapter = null
        super.onDestroyView()
    }

    private class ReportPagerAdapter(host: Fragment) :
        FragmentStateAdapter(host.childFragmentManager, host.viewLifecycleOwner.lifecycle) {
        override fun getItemCount(): Int = TAB_TITLES.size

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> OverviewTabFragment()
            1 -> BacklogTabFragment()
            2 -> ReviewsTabFragment()
            else -> CompetitorsTabFragment()
        }
    }

    companion object {
        private val TAB_TITLES = intArrayOf(
            R.string.report_tab_overview,
            R.string.report_tab_backlog,
            R.string.report_tab_reviews,
            R.string.report_tab_competitors
        )

        fun newInstance(analysisId: String) = ReportFragment().apply {
            arguments = bundleOf(ReportViewModel.KEY_ANALYSIS_ID to analysisId)
        }
    }
}

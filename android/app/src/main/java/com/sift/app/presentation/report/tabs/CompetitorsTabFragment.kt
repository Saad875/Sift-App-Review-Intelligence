package com.sift.app.presentation.report.tabs

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.sift.app.R
import com.sift.app.databinding.FragmentReportListBinding
import com.sift.app.domain.model.SourceLink
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.EmptyKind
import com.sift.app.presentation.common.ExternalActions
import com.sift.app.presentation.common.bind
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.contentOrNull
import com.sift.app.presentation.common.snackbar
import com.sift.app.presentation.report.ReportViewModel

/** Competitors tab: web research by Nemotron with Tavily search, with the sources it used. */
class CompetitorsTabFragment : BaseFragment<FragmentReportListBinding>(FragmentReportListBinding::inflate) {

    private val viewModel: ReportViewModel by viewModels(ownerProducer = { requireParentFragment() }) {
        ReportViewModel.factory(container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = CompetitorAdapter(::openSource)
        binding.list.adapter = adapter
        binding.listHeader.setText(R.string.competitors_header)
        collectWhileStarted(viewModel.state) { state ->
            val items = state.contentOrNull?.competitors ?: return@collectWhileStarted
            binding.list.isVisible = items.isNotEmpty()
            binding.listHeader.isVisible = items.isNotEmpty()
            binding.emptyState.root.isVisible = items.isEmpty()
            if (items.isEmpty()) binding.emptyState.bind(EmptyKind.NO_COMPETITORS)
            adapter.submitList(items)
        }
    }

    private fun openSource(source: SourceLink) {
        if (!ExternalActions.openUrl(requireContext(), source.url)) {
            binding.root.snackbar(R.string.error_no_browser)
        }
    }

    override fun onDestroyView() {
        binding.list.adapter = null
        super.onDestroyView()
    }
}

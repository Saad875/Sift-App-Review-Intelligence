package com.sift.app.presentation.report.tabs

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.sift.app.R
import com.sift.app.databinding.FragmentReportListBinding
import com.sift.app.domain.model.BacklogItem
import com.sift.app.presentation.backlog.BacklogDetailFragment
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.EmptyKind
import com.sift.app.presentation.common.bind
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.contentOrNull
import com.sift.app.presentation.report.ReportViewModel

/** Backlog tab: the prioritised fixes and features, highest priority first. */
class BacklogTabFragment : BaseFragment<FragmentReportListBinding>(FragmentReportListBinding::inflate) {

    private val viewModel: ReportViewModel by viewModels(ownerProducer = { requireParentFragment() }) {
        ReportViewModel.factory(container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = BacklogAdapter(::open)
        binding.list.adapter = adapter
        binding.listHeader.setText(R.string.backlog_header)
        collectWhileStarted(viewModel.state) { state ->
            val items = state.contentOrNull?.backlog?.sortedBy { it.rank } ?: return@collectWhileStarted
            binding.list.isVisible = items.isNotEmpty()
            binding.listHeader.isVisible = items.isNotEmpty()
            binding.emptyState.root.isVisible = items.isEmpty()
            if (items.isEmpty()) binding.emptyState.bind(EmptyKind.NO_BACKLOG)
            adapter.submitList(items)
        }
    }

    private fun open(item: BacklogItem) {
        navigator.push(BacklogDetailFragment.newInstance(item.id))
    }

    override fun onDestroyView() {
        binding.list.adapter = null
        super.onDestroyView()
    }
}

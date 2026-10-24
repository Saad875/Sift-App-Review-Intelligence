package com.sift.app.presentation.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import com.sift.app.R
import com.sift.app.databinding.FragmentHomeBinding
import com.sift.app.domain.model.Analysis
import com.sift.app.domain.usecase.SearchAppsUseCase
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.DocumentNames
import com.sift.app.presentation.common.EmptyKind
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.bind
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.errorMessage
import com.sift.app.presentation.common.fadeInUp
import com.sift.app.presentation.common.hapticTap
import com.sift.app.presentation.common.hideKeyboard
import com.sift.app.presentation.common.matchStatusBarHeight
import com.sift.app.presentation.common.playLogo
import com.sift.app.presentation.common.snackbar
import com.sift.app.presentation.common.spinOnce
import com.sift.app.presentation.progress.ProgressFragment
import com.sift.app.presentation.report.ReportFragment
import com.sift.app.presentation.search.SearchFragment
import com.sift.app.presentation.settings.SettingsFragment
import com.sift.app.presentation.setup.SetupFragment

/** Home: search, CSV import and recent analyses. */
class HomeFragment : BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    private val viewModel: HomeViewModel by viewModels { HomeViewModel.factory(container) }
    private lateinit var adapter: RecentAnalysisAdapter

    private val pickCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::onCsvPicked)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarPadding(top = true, bottom = true)
        binding.statusScrim.matchStatusBarHeight()

        adapter = RecentAnalysisAdapter(viewModel::onAnalysisClicked)
        binding.recentList.adapter = adapter

        binding.brandMark.playLogo()
        binding.settingsButton.setOnClickListener {
            binding.settingsButton.spinOnce()
            navigator.push(SettingsFragment.newInstance())
        }
        binding.scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            binding.aurora.setScrollOffset(scrollY)
        })
        if (savedInstanceState == null) {
            val step = resources.getInteger(R.integer.motion_stagger_step).toLong()
            listOf(binding.headline, binding.subtitle, binding.searchLayout, binding.importCard)
                .forEachIndexed { index, target -> target.fadeInUp(delayMillis = step * (index + 1)) }
        }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else {
                false
            }
        }
        binding.searchLayout.setEndIconOnClickListener { submitSearch() }
        binding.importCard.setOnClickListener {
            it.hapticTap()
            pickCsv.launch(CSV_MIME_TYPES)
        }

        collectWhileStarted(viewModel.state, ::render)
        collectWhileStarted(viewModel.events, ::handle)
    }

    private fun submitSearch() {
        val query = binding.searchInput.text?.toString().orEmpty().trim()
        if (query.length < SearchAppsUseCase.MIN_QUERY_LENGTH) {
            binding.searchLayout.error = getString(R.string.error_query_too_short)
            return
        }
        binding.searchLayout.error = null
        binding.searchInput.hideKeyboard()
        navigator.push(SearchFragment.newInstance(query))
    }

    private fun onCsvPicked(uri: Uri) {
        // Keep read access across process death while the user finishes setup.
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        navigator.push(SetupFragment.forCsv(uri.toString(), DocumentNames.displayName(requireContext(), uri)))
    }

    private fun render(state: UiState<List<Analysis>>) {
        binding.recentSkeleton.root.isVisible = state is UiState.Loading
        binding.recentList.isVisible = state is UiState.Content
        binding.recentEmpty.root.isVisible = state is UiState.Empty
        binding.recentError.root.isVisible = state is UiState.Error
        when (state) {
            is UiState.Content -> adapter.submitList(state.data)
            is UiState.Empty -> binding.recentEmpty.bind(EmptyKind.NO_RECENT, R.string.home_import_csv) {
                pickCsv.launch(CSV_MIME_TYPES)
            }
            is UiState.Error -> binding.recentError.bind(state.error, viewModel::retry)
            UiState.Loading -> Unit
        }
    }

    private fun handle(event: HomeEvent) {
        when (event) {
            is HomeEvent.OpenReport -> navigator.push(ReportFragment.newInstance(event.analysisId))
            is HomeEvent.OpenProgress -> navigator.push(ProgressFragment.newInstance(event.analysisId))
            is HomeEvent.ShowError -> binding.root.snackbar(requireContext().errorMessage(event.error))
        }
    }

    companion object {
        private val CSV_MIME_TYPES = arrayOf(
            "text/csv",
            "text/comma-separated-values",
            "text/plain",
            "text/tab-separated-values",
            "application/csv",
            "application/vnd.ms-excel",
            "application/octet-stream"
        )

        fun newInstance() = HomeFragment()
    }
}

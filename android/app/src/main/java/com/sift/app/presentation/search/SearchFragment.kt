package com.sift.app.presentation.search

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.sift.app.R
import com.sift.app.databinding.FragmentSearchBinding
import com.sift.app.domain.model.AppSummary
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.Formatters
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.bind
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.hideKeyboard
import com.sift.app.presentation.setup.SetupFragment

/** Search results: icon, name, developer and rating. Tap to select. */
class SearchFragment : BaseFragment<FragmentSearchBinding>(FragmentSearchBinding::inflate) {

    private val viewModel: SearchViewModel by viewModels { SearchViewModel.factory(container) }
    private lateinit var adapter: AppResultAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.applySystemBarPadding(top = true)
        binding.resultList.applySystemBarPadding(bottom = true)

        adapter = AppResultAdapter(::onAppSelected)
        binding.resultList.adapter = adapter
        binding.backButton.setOnClickListener { navigator.back() }
        binding.storeCaption.text = getString(
            R.string.search_store_caption, Formatters.countryFlag(viewModel.country),
            Formatters.countryName(viewModel.country)
        )

        if (binding.searchInput.text.isNullOrEmpty()) binding.searchInput.setText(viewModel.currentQuery)
        binding.searchInput.setSelection(binding.searchInput.text?.length ?: 0)
        binding.searchInput.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        binding.searchInput.setOnEditorActionListener { input, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) input.hideKeyboard()
            actionId == EditorInfo.IME_ACTION_SEARCH
        }

        collectWhileStarted(viewModel.state, ::render)
    }

    private fun render(state: UiState<List<AppSummary>>) {
        binding.skeleton.root.isVisible = state is UiState.Loading
        binding.resultList.isVisible = state is UiState.Content
        binding.emptyState.root.isVisible = state is UiState.Empty
        binding.errorState.root.isVisible = state is UiState.Error
        when (state) {
            is UiState.Content -> adapter.submitResults(state.data)
            is UiState.Empty -> binding.emptyState.bind(state.kind)
            is UiState.Error -> binding.errorState.bind(state.error, viewModel::retry)
            UiState.Loading -> Unit
        }
    }

    private fun onAppSelected(app: AppSummary) {
        binding.searchInput.hideKeyboard()
        navigator.push(SetupFragment.forApp(app, viewModel.country))
    }

    companion object {
        fun newInstance(query: String) = SearchFragment().apply {
            arguments = bundleOf(SearchViewModel.KEY_QUERY to query)
        }
    }
}

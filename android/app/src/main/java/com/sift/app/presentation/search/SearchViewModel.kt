package com.sift.app.presentation.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.di.AppContainer
import com.sift.app.domain.model.AppSummary
import com.sift.app.domain.usecase.PreferencesUseCase
import com.sift.app.domain.usecase.SearchAppsUseCase
import com.sift.app.presentation.common.EmptyKind
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.toUiError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App Store search with debounced, cancellable queries. The query survives process death. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val searchApps: SearchAppsUseCase,
    preferences: PreferencesUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {

    /** Storefront used for search and preselected on the setup screen. */
    val country: String = preferences.lastCountry()

    private val retryTrigger = MutableStateFlow(0)
    private val query = savedState.getStateFlow(KEY_QUERY, "")

    val state: StateFlow<UiState<List<AppSummary>>> = combine(
        query.map { it.trim() }.distinctUntilChanged().debounce(DEBOUNCE_MILLIS),
        retryTrigger
    ) { text, _ -> text }
        .flatMapLatest { text -> search(text) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val currentQuery: String get() = query.value

    fun onQueryChanged(text: String) {
        savedState[KEY_QUERY] = text
    }

    fun retry() {
        retryTrigger.value++
    }

    private fun search(text: String) = flow<UiState<List<AppSummary>>> {
        if (text.length < SearchAppsUseCase.MIN_QUERY_LENGTH) {
            emit(UiState.Empty(EmptyKind.SEARCH_PROMPT))
            return@flow
        }
        emit(UiState.Loading)
        searchApps(text, country).fold(
            onSuccess = { apps ->
                emit(if (apps.isEmpty()) UiState.Empty(EmptyKind.NO_SEARCH_RESULTS) else UiState.Content(apps))
            },
            onFailure = { emit(UiState.Error(it.toUiError())) }
        )
    }

    companion object {
        /** Must match the fragment argument key so the initial query is picked up. */
        const val KEY_QUERY = "query"
        private const val DEBOUNCE_MILLIS = 350L
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SearchViewModel(container.searchApps, container.preferences, createSavedStateHandle()) }
        }
    }
}

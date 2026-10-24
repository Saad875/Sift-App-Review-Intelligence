package com.sift.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.di.AppContainer
import com.sift.app.domain.model.Analysis
import com.sift.app.domain.model.AnalysisStatus
import com.sift.app.domain.usecase.LoadDemoUseCase
import com.sift.app.domain.usecase.ObserveRecentAnalysesUseCase
import com.sift.app.presentation.common.EmptyKind
import com.sift.app.presentation.common.UiError
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.toUiError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-off actions for the Home screen. */
sealed interface HomeEvent {
    data class OpenReport(val analysisId: String) : HomeEvent
    data class OpenProgress(val analysisId: String) : HomeEvent
    data class ShowError(val error: UiError) : HomeEvent
}

/** Recent analyses from the local cache, plus the demo action. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val observeRecent: ObserveRecentAnalysesUseCase,
    private val loadDemo: LoadDemoUseCase
) : ViewModel() {

    private val reload = MutableStateFlow(0)
    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)

    /** Navigation and messages that must be handled exactly once. */
    val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<UiState<List<Analysis>>> = reload
        .flatMapLatest {
            observeRecent()
                .map<List<Analysis>, UiState<List<Analysis>>> { items ->
                    if (items.isEmpty()) UiState.Empty(EmptyKind.NO_RECENT) else UiState.Content(items)
                }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(it.toUiError())) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun retry() {
        reload.value++
    }

    fun onAnalysisClicked(analysis: Analysis) {
        val event = when (analysis.status) {
            AnalysisStatus.COMPLETED -> HomeEvent.OpenReport(analysis.id)
            else -> HomeEvent.OpenProgress(analysis.id)
        }
        eventChannel.trySend(event)
    }

    fun onDemoClicked() {
        viewModelScope.launch {
            loadDemo()
                .onSuccess { eventChannel.send(HomeEvent.OpenReport(it)) }
                .onFailure { eventChannel.send(HomeEvent.ShowError(it.toUiError())) }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HomeViewModel(container.observeRecent, container.loadDemo) }
        }
    }
}

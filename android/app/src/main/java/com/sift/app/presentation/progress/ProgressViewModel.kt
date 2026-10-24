package com.sift.app.presentation.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.di.AppContainer
import com.sift.app.domain.model.Analysis
import com.sift.app.domain.model.AnalysisRequest
import com.sift.app.domain.model.AnalysisSource
import com.sift.app.domain.model.AnalysisStatus
import com.sift.app.domain.model.AppSummary
import com.sift.app.domain.usecase.ObserveAnalysisUseCase
import com.sift.app.domain.usecase.PollAnalysisUseCase
import com.sift.app.domain.usecase.PollEvent
import com.sift.app.domain.usecase.StartAnalysisUseCase
import com.sift.app.presentation.common.UiError
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.toUiError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the progress screen renders. */
data class ProgressContent(
    val analysis: Analysis,
    val connectionIssue: UiError?,
    val reportReady: Boolean,
    val isRestarting: Boolean
) {
    val canRestart: Boolean get() = analysis.source == AnalysisSource.APP_STORE && analysis.appId.isNotBlank()
}

sealed interface ProgressEvent {
    data class Restarted(val analysisId: String) : ProgressEvent
    data class ShowError(val error: UiError) : ProgressEvent
}

/** Poll status layered over the cached analysis. */
private sealed interface PollStatus {
    data object Active : PollStatus
    data class Reconnecting(val error: UiError) : PollStatus
    data class Fatal(val error: UiError) : PollStatus
    data object Finished : PollStatus
}

/**
 * Shows live pipeline progress. The display reads Room; polling runs only while
 * the screen is visible (plus 5 s to survive rotation) and resumes on return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    observeAnalysis: ObserveAnalysisUseCase,
    private val pollAnalysis: PollAnalysisUseCase,
    private val startAnalysis: StartAnalysisUseCase,
    savedState: SavedStateHandle
) : ViewModel() {

    private val analysisId: String = checkNotNull(savedState.get<String>(KEY_ANALYSIS_ID))
    private val pollRestart = MutableStateFlow(0)
    private val restarting = MutableStateFlow(false)
    private val eventChannel = Channel<ProgressEvent>(Channel.BUFFERED)
    val events: Flow<ProgressEvent> = eventChannel.receiveAsFlow()

    private val pollStatus: Flow<PollStatus> = pollRestart.flatMapLatest {
        pollAnalysis(analysisId)
            .map { event ->
                when (event) {
                    is PollEvent.Updated -> PollStatus.Active
                    is PollEvent.TransientError -> PollStatus.Reconnecting(event.error.toUiError())
                    is PollEvent.Fatal -> PollStatus.Fatal(event.error.toUiError())
                    is PollEvent.Finished -> PollStatus.Finished
                }
            }
            .onStart { emit(PollStatus.Active) }
    }

    val state: StateFlow<UiState<ProgressContent>> = combine(
        observeAnalysis(analysisId), pollStatus, restarting
    ) { analysis, poll, isRestarting ->
        when {
            analysis == null && poll is PollStatus.Fatal -> UiState.Error(poll.error)
            analysis == null -> UiState.Loading
            poll is PollStatus.Fatal && !analysis.status.isTerminal -> UiState.Error(poll.error)
            else -> UiState.Content(
                ProgressContent(
                    analysis = analysis,
                    connectionIssue = (poll as? PollStatus.Reconnecting)?.error,
                    reportReady = poll is PollStatus.Finished && analysis.status == AnalysisStatus.COMPLETED,
                    isRestarting = isRestarting
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    /** Restarts polling after a fatal error. */
    fun retry() {
        pollRestart.value++
    }

    /** Starts a fresh App Store analysis with the same app, country and competitors. */
    fun restart(analysis: Analysis) {
        if (restarting.value) return
        restarting.value = true
        viewModelScope.launch {
            val app = AppSummary(analysis.appId, analysis.appName, analysis.developer, analysis.iconUrl, null, null, "")
            startAnalysis.appStore(AnalysisRequest(app, analysis.country, analysis.competitors))
                .onSuccess { eventChannel.send(ProgressEvent.Restarted(it.id)) }
                .onFailure { eventChannel.send(ProgressEvent.ShowError(it.toUiError())) }
            restarting.value = false
        }
    }

    companion object {
        const val KEY_ANALYSIS_ID = "analysis_id"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ProgressViewModel(
                    container.observeAnalysis, container.pollAnalysis, container.startAnalysis, createSavedStateHandle()
                )
            }
        }
    }
}

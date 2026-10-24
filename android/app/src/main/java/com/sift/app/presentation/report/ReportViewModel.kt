package com.sift.app.presentation.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.di.AppContainer
import com.sift.app.domain.model.Report
import com.sift.app.domain.usecase.BuildMarkdownUseCase
import com.sift.app.domain.usecase.ObserveReportUseCase
import com.sift.app.domain.usecase.SyncReportUseCase
import com.sift.app.domain.util.MarkdownLabels
import com.sift.app.presentation.common.EmptyKind
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.contentOrNull
import com.sift.app.presentation.common.toUiError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * The report shared by the report screen and its four tabs. Reads the Room
 * cache, so finished reports open offline; downloads the report only when the
 * cache does not have it yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(
    private val observeReport: ObserveReportUseCase,
    private val syncReport: SyncReportUseCase,
    private val buildMarkdown: BuildMarkdownUseCase,
    savedState: SavedStateHandle
) : ViewModel() {

    val analysisId: String = checkNotNull(savedState.get<String>(KEY_ANALYSIS_ID))
    private val reload = MutableStateFlow(0)

    val state: StateFlow<UiState<Report>> = reload
        .flatMapLatest { reportStates() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private fun reportStates() = flow<UiState<Report>> {
        var syncTried = false
        observeReport(analysisId).collect { report ->
            when {
                report == null -> if (!syncTried) {
                    syncTried = true
                    emit(UiState.Loading)
                    syncReport(analysisId).onFailure { emit(UiState.Error(it.toUiError())) }
                }
                report.analysis.stats.totalReviews == 0 -> emit(UiState.Empty(EmptyKind.NO_REVIEWS))
                else -> emit(UiState.Content(report))
            }
        }
    }

    fun retry() {
        reload.value++
    }

    /** Markdown for the whole report, or null while it is not loaded. */
    fun markdown(labels: MarkdownLabels): String? =
        state.value.contentOrNull?.let { buildMarkdown.report(it, labels) }

    companion object {
        const val KEY_ANALYSIS_ID = "analysis_id"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ReportViewModel(container.observeReport, container.syncReport, container.buildMarkdown, createSavedStateHandle())
            }
        }
    }
}

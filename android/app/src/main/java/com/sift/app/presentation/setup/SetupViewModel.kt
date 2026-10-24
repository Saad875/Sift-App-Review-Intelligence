package com.sift.app.presentation.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.di.AppContainer
import com.sift.app.domain.model.AnalysisRequest
import com.sift.app.domain.model.AppSummary
import com.sift.app.domain.model.CsvDocument
import com.sift.app.domain.usecase.StartAnalysisUseCase
import com.sift.app.domain.util.CompetitorRules
import com.sift.app.presentation.common.UiError
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.toUiError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What is being analysed: an App Store app or an uploaded CSV. */
sealed interface SetupTarget {
    data class App(val app: AppSummary) : SetupTarget
    data class Csv(val document: CsvDocument) : SetupTarget
}

/** Everything the setup screen renders. */
data class SetupContent(
    val target: SetupTarget,
    val country: String,
    val competitors: List<String>,
    val isCountryPanelOpen: Boolean,
    val isStarting: Boolean
) {
    val canAddCompetitor: Boolean get() = competitors.size < CompetitorRules.MAX_COMPETITORS && !isStarting
}

sealed interface SetupEvent {
    data class Started(val analysisId: String) : SetupEvent
    data class ShowError(val error: UiError) : SetupEvent
}

/** Country, competitors and the start action. Selections survive rotation and process death. */
class SetupViewModel(
    private val startAnalysis: StartAnalysisUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val target: SetupTarget = SetupArgs.readTarget(savedState)
    private val starting = MutableStateFlow(false)
    private val eventChannel = Channel<SetupEvent>(Channel.BUFFERED)
    val events: Flow<SetupEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<UiState<SetupContent>> = combine(
        savedState.getStateFlow(SetupArgs.KEY_COUNTRY, SetupArgs.DEFAULT_COUNTRY),
        savedState.getStateFlow(KEY_COMPETITORS, arrayListOf<String>()),
        savedState.getStateFlow(KEY_PANEL_OPEN, false),
        starting
    ) { country, competitors, panelOpen, isStarting ->
        UiState.Content(SetupContent(target, country, competitors.toList(), panelOpen, isStarting))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    private val competitors: List<String>
        get() = savedState.get<ArrayList<String>>(KEY_COMPETITORS).orEmpty()

    /** Adds a competitor; returns false when the name is empty, too long, a duplicate or over the limit. */
    fun addCompetitor(name: String): Boolean {
        if (!CompetitorRules.canAdd(competitors, name)) return false
        savedState[KEY_COMPETITORS] = ArrayList(competitors + name.trim())
        return true
    }

    fun removeCompetitor(name: String) {
        savedState[KEY_COMPETITORS] = ArrayList(competitors - name)
    }

    fun setCountryPanelOpen(open: Boolean) {
        savedState[KEY_PANEL_OPEN] = open
    }

    fun selectCountry(code: String) {
        savedState[SetupArgs.KEY_COUNTRY] = code
        savedState[KEY_PANEL_OPEN] = false
    }

    fun start() {
        if (starting.value) return
        starting.value = true
        val country = savedState.get<String>(SetupArgs.KEY_COUNTRY) ?: SetupArgs.DEFAULT_COUNTRY
        viewModelScope.launch {
            val result = when (target) {
                is SetupTarget.App -> startAnalysis.appStore(AnalysisRequest(target.app, country, competitors))
                is SetupTarget.Csv -> startAnalysis.csv(target.document, competitors)
            }
            result
                .onSuccess { eventChannel.send(SetupEvent.Started(it.id)) }
                .onFailure { eventChannel.send(SetupEvent.ShowError(it.toUiError())) }
            starting.value = false
        }
    }

    companion object {
        private const val KEY_COMPETITORS = "competitors"
        private const val KEY_PANEL_OPEN = "country_panel_open"

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SetupViewModel(container.startAnalysis, createSavedStateHandle()) }
        }
    }
}

package com.sift.app.presentation.settings

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.R
import com.sift.app.di.AppContainer
import com.sift.app.domain.error.AppError
import com.sift.app.domain.model.BackendHealth
import com.sift.app.domain.model.ThemeMode
import com.sift.app.domain.usecase.CheckBackendUseCase
import com.sift.app.domain.usecase.ClearLocalDataUseCase
import com.sift.app.domain.usecase.PreferencesUseCase
import com.sift.app.presentation.common.UiError
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.messageRes
import com.sift.app.presentation.common.toUiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Result of the backend connection test. */
sealed interface HealthState {
    data object Idle : HealthState
    data object Checking : HealthState
    data class Connected(val health: BackendHealth) : HealthState
    data class Failed(val error: UiError) : HealthState
}

/** Everything the Settings screen shows. */
data class SettingsContent(
    val backendUrl: String,
    val isDefaultUrl: Boolean,
    @StringRes val urlError: Int?,
    val themeMode: ThemeMode,
    val health: HealthState,
    val confirmClearOpen: Boolean,
    val clearing: Boolean,
    val licenseExpanded: Boolean
)

/** One-off results shown as snackbars or applied by the activity. */
sealed interface SettingsEvent {
    data object UrlSaved : SettingsEvent
    data object UrlReset : SettingsEvent
    data object DataCleared : SettingsEvent
    data class ApplyTheme(val mode: ThemeMode) : SettingsEvent
    data class ShowError(val error: UiError) : SettingsEvent
}

/** Backend URL, connection test, theme, local data and the About section. */
class SettingsViewModel(
    private val preferences: PreferencesUseCase,
    private val checkBackend: CheckBackendUseCase,
    private val clearLocalData: ClearLocalDataUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SettingsContent>>(
        UiState.Content(
            SettingsContent(
                backendUrl = preferences.backendUrl(),
                isDefaultUrl = preferences.backendUrl() == preferences.defaultBackendUrl,
                urlError = null,
                themeMode = preferences.themeMode(),
                health = HealthState.Idle,
                confirmClearOpen = savedState[KEY_CONFIRM_OPEN] ?: false,
                clearing = false,
                licenseExpanded = savedState[KEY_LICENSE_EXPANDED] ?: false
            )
        )
    )
    val state: StateFlow<UiState<SettingsContent>> = _state.asStateFlow()

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()
    private var healthJob: Job? = null

    fun saveBackendUrl(input: String) {
        preferences.updateBackendUrl(input)
            .onSuccess { url ->
                update { it.copy(backendUrl = url, isDefaultUrl = url == preferences.defaultBackendUrl, urlError = null) }
                eventChannel.trySend(SettingsEvent.UrlSaved)
                testConnection()
            }
            .onFailure { error ->
                val reason = (error as? AppError.Invalid)?.reason
                update { it.copy(urlError = reason?.messageRes() ?: R.string.error_backend_url) }
            }
    }

    fun resetBackendUrl() {
        val url = preferences.resetBackendUrl()
        update { it.copy(backendUrl = url, isDefaultUrl = true, urlError = null, health = HealthState.Idle) }
        eventChannel.trySend(SettingsEvent.UrlReset)
    }

    fun clearUrlError() = update { if (it.urlError == null) it else it.copy(urlError = null) }

    fun testConnection() {
        healthJob?.cancel()
        update { it.copy(health = HealthState.Checking) }
        healthJob = viewModelScope.launch {
            val health = checkBackend().fold(
                onSuccess = { HealthState.Connected(it) },
                onFailure = { HealthState.Failed(it.toUiError()) }
            )
            update { it.copy(health = health) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        val current = (_state.value as? UiState.Content)?.data?.themeMode
        if (current == mode) return
        preferences.setThemeMode(mode)
        update { it.copy(themeMode = mode) }
        eventChannel.trySend(SettingsEvent.ApplyTheme(mode))
    }

    fun setConfirmClearOpen(open: Boolean) {
        savedState[KEY_CONFIRM_OPEN] = open
        update { it.copy(confirmClearOpen = open) }
    }

    fun clearData() {
        update { it.copy(clearing = true) }
        viewModelScope.launch {
            clearLocalData()
                .onSuccess { eventChannel.send(SettingsEvent.DataCleared) }
                .onFailure { eventChannel.send(SettingsEvent.ShowError(it.toUiError())) }
            savedState[KEY_CONFIRM_OPEN] = false
            update { it.copy(clearing = false, confirmClearOpen = false) }
        }
    }

    fun toggleLicense() {
        val expanded = !((_state.value as? UiState.Content)?.data?.licenseExpanded ?: false)
        savedState[KEY_LICENSE_EXPANDED] = expanded
        update { it.copy(licenseExpanded = expanded) }
    }

    private fun update(transform: (SettingsContent) -> SettingsContent) {
        _state.update { state -> if (state is UiState.Content) UiState.Content(transform(state.data)) else state }
    }

    companion object {
        private const val KEY_CONFIRM_OPEN = "confirm_clear_open"
        private const val KEY_LICENSE_EXPANDED = "license_expanded"

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    container.preferences, container.checkBackend, container.clearLocalData, createSavedStateHandle()
                )
            }
        }
    }
}

package com.sift.app.presentation.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.di.AppContainer
import com.sift.app.domain.model.AppLanguage
import com.sift.app.domain.model.ThemeMode
import com.sift.app.domain.usecase.ClearLocalDataUseCase
import com.sift.app.domain.usecase.LanguageUseCase
import com.sift.app.domain.usecase.PreferencesUseCase
import com.sift.app.presentation.common.UiError
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.toUiError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the Settings screen shows. */
data class SettingsContent(
    val language: AppLanguage,
    val themeMode: ThemeMode,
    val confirmClearOpen: Boolean,
    val clearing: Boolean,
    val licenseExpanded: Boolean
)

/** One-off results shown as snackbars or applied by the activity. */
sealed interface SettingsEvent {
    data object DataCleared : SettingsEvent
    data class ApplyTheme(val mode: ThemeMode) : SettingsEvent
    data class ShowError(val error: UiError) : SettingsEvent
}

/** Language, Pro preview, theme, local data and the About section. */
class SettingsViewModel(
    private val preferences: PreferencesUseCase,
    private val language: LanguageUseCase,
    private val clearLocalData: ClearLocalDataUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SettingsContent>>(
        UiState.Content(
            SettingsContent(
                language = language.current(),
                themeMode = preferences.themeMode(),
                confirmClearOpen = savedState[KEY_CONFIRM_OPEN] ?: false,
                clearing = false,
                licenseExpanded = savedState[KEY_LICENSE_EXPANDED] ?: false
            )
        )
    )
    val state: StateFlow<UiState<SettingsContent>> = _state.asStateFlow()

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    /** Re-reads the app language, which may have changed on the language screen. */
    fun refreshLanguage() {
        val current = language.current()
        if ((_state.value as? UiState.Content)?.data?.language != current) update { it.copy(language = current) }
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
                    container.preferences, container.language, container.clearLocalData,
                    createSavedStateHandle()
                )
            }
        }
    }
}

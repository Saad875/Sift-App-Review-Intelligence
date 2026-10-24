package com.sift.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.di.AppContainer
import com.sift.app.domain.model.AppLanguage
import com.sift.app.domain.usecase.LanguageUseCase
import com.sift.app.presentation.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The language list and the current choice. */
data class LanguageContent(
    val languages: List<AppLanguage>,
    val selected: AppLanguage
)

/**
 * Applies the chosen language straight away. Changing it recreates the activity,
 * after which this screen is restored with the new strings and the same selection.
 */
class LanguageViewModel(private val language: LanguageUseCase) : ViewModel() {

    private val _state = MutableStateFlow<UiState<LanguageContent>>(
        UiState.Content(LanguageContent(language.languages, language.current()))
    )
    val state: StateFlow<UiState<LanguageContent>> = _state.asStateFlow()

    fun select(choice: AppLanguage) {
        _state.value = UiState.Content(LanguageContent(language.languages, choice))
        language.select(choice)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LanguageViewModel(container.language) }
        }
    }
}

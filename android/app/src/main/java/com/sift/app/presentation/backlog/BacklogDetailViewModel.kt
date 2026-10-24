package com.sift.app.presentation.backlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.di.AppContainer
import com.sift.app.domain.error.AppError
import com.sift.app.domain.model.BacklogItem
import com.sift.app.domain.usecase.BuildMarkdownUseCase
import com.sift.app.domain.usecase.GetBacklogItemUseCase
import com.sift.app.domain.util.MarkdownLabels
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.contentOrNull
import com.sift.app.presentation.common.toUiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One backlog item from the cache, and its Markdown ticket. */
class BacklogDetailViewModel(
    private val getBacklogItem: GetBacklogItemUseCase,
    private val buildMarkdown: BuildMarkdownUseCase,
    savedState: SavedStateHandle
) : ViewModel() {

    private val itemId: Long = checkNotNull(savedState.get<Long>(KEY_ITEM_ID))
    private val _state = MutableStateFlow<UiState<BacklogItem>>(UiState.Loading)
    val state: StateFlow<UiState<BacklogItem>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = getBacklogItem(itemId).fold(
                onSuccess = { item -> item?.let { UiState.Content(it) } ?: UiState.Error(AppError.NotFound().toUiError()) },
                onFailure = { UiState.Error(it.toUiError()) }
            )
        }
    }

    /** The item formatted as a Markdown ticket, or null while it is not loaded. */
    fun ticket(labels: MarkdownLabels): String? = state.value.contentOrNull?.let { buildMarkdown.ticket(it, labels) }

    companion object {
        const val KEY_ITEM_ID = "item_id"

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                BacklogDetailViewModel(container.getBacklogItem, container.buildMarkdown, createSavedStateHandle())
            }
        }
    }
}

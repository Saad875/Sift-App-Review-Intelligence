package com.sift.app.presentation.common

/**
 * The single state type every screen renders. Each ViewModel exposes exactly
 * one StateFlow<UiState<T>>.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data class Empty(val kind: EmptyKind) : UiState<Nothing>
    data class Error(val error: UiError) : UiState<Nothing>
}

/** Which empty state illustration and copy to show. */
enum class EmptyKind {
    NO_RECENT,
    SEARCH_PROMPT,
    NO_SEARCH_RESULTS,
    NO_REVIEWS,
    NO_FILTERED_REVIEWS,
    NO_BACKLOG,
    NO_COMPETITORS,
    NO_THEMES
}

/** Content of the current state, or null. */
val <T> UiState<T>.contentOrNull: T?
    get() = (this as? UiState.Content<T>)?.data

package com.sift.app.presentation.reviews

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.di.AppContainer
import com.sift.app.domain.model.Review
import com.sift.app.domain.model.ReviewCategory
import com.sift.app.domain.model.ReviewFilter
import com.sift.app.domain.model.Sentiment
import com.sift.app.domain.usecase.ObserveReviewsUseCase
import com.sift.app.presentation.common.EmptyKind
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.toUiError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Reviews shown for the current filter. */
data class ReviewsContent(val filter: ReviewFilter, val reviews: List<Review>)

/**
 * Cached reviews of one analysis, filtered by sentiment and category. The
 * filter survives process death through [SavedStateHandle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewsViewModel(
    private val analysisId: String,
    private val observeReviews: ObserveReviewsUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val filterFlow = MutableStateFlow(
        ReviewFilter(
            sentiment = Sentiment.fromKey(savedState.get<String>(KEY_SENTIMENT)),
            category = ReviewCategory.fromKey(savedState.get<String>(KEY_CATEGORY))
        )
    )
    private val reload = MutableStateFlow(0)

    /** The filter currently applied, for restoring chip selection. */
    val filter: ReviewFilter get() = filterFlow.value

    val state: StateFlow<UiState<ReviewsContent>> = combine(filterFlow, reload) { filter, _ -> filter }
        .flatMapLatest { filter ->
            observeReviews(analysisId, filter)
                .map<List<Review>, UiState<ReviewsContent>> { reviews ->
                    when {
                        reviews.isNotEmpty() -> UiState.Content(ReviewsContent(filter, reviews))
                        filter == ReviewFilter() -> UiState.Empty(EmptyKind.NO_REVIEWS)
                        else -> UiState.Empty(EmptyKind.NO_FILTERED_REVIEWS)
                    }
                }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(it.toUiError())) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun setSentiment(sentiment: Sentiment?) = update(filterFlow.value.copy(sentiment = sentiment))

    fun setCategory(category: ReviewCategory?) = update(filterFlow.value.copy(category = category))

    fun clearFilters() = update(ReviewFilter())

    fun retry() {
        reload.value++
    }

    private fun update(filter: ReviewFilter) {
        if (filter == filterFlow.value) return
        savedState[KEY_SENTIMENT] = filter.sentiment?.key
        savedState[KEY_CATEGORY] = filter.category?.key
        filterFlow.value = filter
    }

    companion object {
        private const val KEY_SENTIMENT = "filter_sentiment"
        private const val KEY_CATEGORY = "filter_category"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer, analysisId: String) = viewModelFactory {
            initializer { ReviewsViewModel(analysisId, container.observeReviews, createSavedStateHandle()) }
        }
    }
}

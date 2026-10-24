package com.sift.app.presentation.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.di.AppContainer
import com.sift.app.domain.error.AppError
import com.sift.app.domain.model.ReplyTone
import com.sift.app.domain.model.Review
import com.sift.app.domain.usecase.DraftReplyUseCase
import com.sift.app.domain.usecase.GetReviewUseCase
import com.sift.app.presentation.common.UiError
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.toUiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Progress of the reply draft. */
sealed interface ReplyState {
    data object Idle : ReplyState
    data object Drafting : ReplyState
    data class Ready(val text: String) : ReplyState
    data class Failed(val error: UiError) : ReplyState
}

/** Everything the review detail screen shows. [canReply] is false for reviews without a backend id. */
data class ReviewDetailContent(
    val review: Review,
    val tone: ReplyTone,
    val reply: ReplyState,
    val canReply: Boolean
)

/**
 * A single review and an on-demand developer reply drafted by Nemotron through
 * the backend. The chosen tone and the edited reply survive process death.
 */
class ReviewDetailViewModel(
    private val getReview: GetReviewUseCase,
    private val draftReply: DraftReplyUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val reviewId: Long = checkNotNull(savedState.get<Long>(KEY_REVIEW_ID))
    private val _state = MutableStateFlow<UiState<ReviewDetailContent>>(UiState.Loading)
    val state: StateFlow<UiState<ReviewDetailContent>> = _state.asStateFlow()
    private var draftJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = getReview(reviewId).fold(
                onSuccess = { review ->
                    if (review == null) {
                        UiState.Error(AppError.NotFound().toUiError())
                    } else {
                        val savedDraft = savedState.get<String>(KEY_DRAFT)
                        UiState.Content(
                            ReviewDetailContent(
                                review = review,
                                tone = savedTone(),
                                reply = savedDraft?.let { ReplyState.Ready(it) } ?: ReplyState.Idle,
                                canReply = review.remoteId != null
                            )
                        )
                    }
                },
                onFailure = { UiState.Error(it.toUiError()) }
            )
        }
    }

    fun selectTone(tone: ReplyTone) {
        savedState[KEY_TONE] = tone.name
        updateContent { it.copy(tone = tone) }
    }

    fun draft() {
        val content = (_state.value as? UiState.Content)?.data ?: return
        if (!content.canReply || content.reply == ReplyState.Drafting) return
        updateContent { it.copy(reply = ReplyState.Drafting) }
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            val result = draftReply(content.review, content.tone)
            val reply = result.fold(
                onSuccess = { draft ->
                    savedState[KEY_DRAFT] = draft.text
                    ReplyState.Ready(draft.text)
                },
                onFailure = { ReplyState.Failed(it.toUiError()) }
            )
            updateContent { it.copy(reply = reply) }
        }
    }

    /** Keeps the user's edits to the drafted reply. */
    fun onReplyEdited(text: String) {
        val current = (_state.value as? UiState.Content)?.data?.reply as? ReplyState.Ready ?: return
        if (current.text == text) return
        savedState[KEY_DRAFT] = text
        updateContent { it.copy(reply = ReplyState.Ready(text)) }
    }

    private fun savedTone(): ReplyTone =
        ReplyTone.entries.firstOrNull { it.name == savedState.get<String>(KEY_TONE) } ?: ReplyTone.FRIENDLY

    private fun updateContent(transform: (ReviewDetailContent) -> ReviewDetailContent) {
        _state.update { state -> if (state is UiState.Content) UiState.Content(transform(state.data)) else state }
    }

    companion object {
        const val KEY_REVIEW_ID = "review_id"
        private const val KEY_TONE = "reply_tone"
        private const val KEY_DRAFT = "reply_draft"

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ReviewDetailViewModel(container.getReview, container.draftReply, createSavedStateHandle()) }
        }
    }
}

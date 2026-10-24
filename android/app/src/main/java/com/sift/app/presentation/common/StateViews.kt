package com.sift.app.presentation.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.sift.app.R
import com.sift.app.databinding.ViewStateEmptyBinding
import com.sift.app.databinding.ViewStateErrorBinding

/** Fills the shared error view. The retry button is hidden for errors that cannot be retried. */
fun ViewStateErrorBinding.bind(error: UiError, onRetry: () -> Unit) {
    errorIcon.setImageResource(error.iconRes)
    errorTitle.setText(error.titleRes)
    errorMessage.text = root.context.errorMessage(error)
    errorRetry.isVisible = error.retryable
    errorRetry.setOnClickListener { onRetry() }
}

/** Copy and artwork for each [EmptyKind]. */
data class EmptyContent(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val message: Int
)

val EmptyKind.content: EmptyContent
    get() = when (this) {
        EmptyKind.NO_RECENT -> EmptyContent(R.drawable.ic_history, R.string.empty_recent_title, R.string.empty_recent_message)
        EmptyKind.SEARCH_PROMPT -> EmptyContent(
            R.drawable.ic_search, R.string.empty_search_prompt_title, R.string.empty_search_prompt_message
        )
        EmptyKind.NO_SEARCH_RESULTS -> EmptyContent(
            R.drawable.ic_search, R.string.empty_search_title, R.string.empty_search_message
        )
        EmptyKind.NO_REVIEWS -> EmptyContent(R.drawable.ic_inbox, R.string.empty_reviews_title, R.string.empty_reviews_message)
        EmptyKind.NO_FILTERED_REVIEWS -> EmptyContent(
            R.drawable.ic_inbox, R.string.empty_filtered_title, R.string.empty_filtered_message
        )
        EmptyKind.NO_BACKLOG -> EmptyContent(
            R.drawable.ic_trending_up, R.string.empty_backlog_title, R.string.empty_backlog_message
        )
        EmptyKind.NO_COMPETITORS -> EmptyContent(
            R.drawable.ic_people, R.string.empty_competitors_title, R.string.empty_competitors_message
        )
        EmptyKind.NO_THEMES -> EmptyContent(R.drawable.ic_auto_awesome, R.string.empty_themes_title, R.string.empty_themes_message)
    }

/** Fills the shared empty view, with an optional action button. */
fun ViewStateEmptyBinding.bind(kind: EmptyKind, @StringRes actionText: Int? = null, onAction: (() -> Unit)? = null) {
    val content = kind.content
    emptyIcon.setImageResource(content.icon)
    emptyIcon.startFloat(root.resources.getDimension(R.dimen.welcome_float_distance))
    emptyTitle.setText(content.title)
    emptyMessage.setText(content.message)
    emptyAction.isVisible = actionText != null && onAction != null
    if (actionText != null && onAction != null) {
        emptyAction.setText(actionText)
        emptyAction.setOnClickListener { onAction() }
    }
}

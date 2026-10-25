package com.sift.app.presentation.common

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sift.app.R
import com.sift.app.domain.model.AnalysisSource
import com.sift.app.domain.model.BacklogType
import com.sift.app.domain.model.Effort
import com.sift.app.domain.model.ReplyTone
import com.sift.app.domain.model.ReviewCategory
import com.sift.app.domain.model.Sentiment
import com.sift.app.domain.model.StepKey

/** String, color and icon resources for domain enums. Keeps copy out of Kotlin. */

@get:StringRes
val Sentiment.labelRes: Int
    get() = when (this) {
        Sentiment.POSITIVE -> R.string.sentiment_positive
        Sentiment.NEUTRAL -> R.string.sentiment_neutral
        Sentiment.NEGATIVE -> R.string.sentiment_negative
    }

@get:ColorRes
val Sentiment.colorRes: Int
    get() = when (this) {
        Sentiment.POSITIVE -> R.color.sift_positive
        Sentiment.NEUTRAL -> R.color.sift_neutral
        Sentiment.NEGATIVE -> R.color.sift_negative
    }

@get:ColorRes
val Sentiment.containerColorRes: Int
    get() = when (this) {
        Sentiment.POSITIVE -> R.color.sift_positive_container
        Sentiment.NEUTRAL -> R.color.sift_neutral_container
        Sentiment.NEGATIVE -> R.color.sift_negative_container
    }

@get:StringRes
val ReviewCategory.labelRes: Int
    get() = when (this) {
        ReviewCategory.BUG -> R.string.category_bug
        ReviewCategory.PERFORMANCE -> R.string.category_performance
        ReviewCategory.UI_UX -> R.string.category_ui_ux
        ReviewCategory.FEATURE_REQUEST -> R.string.category_feature_request
        ReviewCategory.PRICING -> R.string.category_pricing
        ReviewCategory.ADS -> R.string.category_ads
        ReviewCategory.ACCOUNT -> R.string.category_account
        ReviewCategory.PRAISE -> R.string.category_praise
        ReviewCategory.OTHER -> R.string.category_other
    }

@get:StringRes
val StepKey.titleRes: Int
    get() = when (this) {
        StepKey.FETCHING -> R.string.step_fetching_title
        StepKey.CLASSIFYING -> R.string.step_classifying_title
        StepKey.CLUSTERING -> R.string.step_clustering_title
        StepKey.PRIORITIZING -> R.string.step_prioritizing_title
        StepKey.RESEARCHING -> R.string.step_researching_title
    }

/** Running text, formatted with the model name when the step uses a model. */
@get:StringRes
val StepKey.runningRes: Int
    get() = when (this) {
        StepKey.FETCHING -> R.string.step_fetching_running
        StepKey.CLASSIFYING -> R.string.step_classifying_running
        StepKey.CLUSTERING -> R.string.step_clustering_running
        StepKey.PRIORITIZING -> R.string.step_prioritizing_running
        StepKey.RESEARCHING -> R.string.step_researching_running
    }

@get:StringRes
val StepKey.waitingRes: Int
    get() = when (this) {
        StepKey.FETCHING -> R.string.step_fetching_waiting
        StepKey.CLASSIFYING -> R.string.step_classifying_waiting
        StepKey.CLUSTERING -> R.string.step_clustering_waiting
        StepKey.PRIORITIZING -> R.string.step_prioritizing_waiting
        StepKey.RESEARCHING -> R.string.step_researching_waiting
    }

@get:StringRes
val BacklogType.labelRes: Int
    get() = when (this) {
        BacklogType.BUG -> R.string.backlog_type_bug
        BacklogType.FEATURE -> R.string.backlog_type_feature
    }

@get:DrawableRes
val BacklogType.iconRes: Int
    get() = when (this) {
        BacklogType.BUG -> R.drawable.ic_bug
        BacklogType.FEATURE -> R.drawable.ic_lightbulb
    }

@get:ColorRes
val BacklogType.colorRes: Int
    get() = when (this) {
        BacklogType.BUG -> R.color.sift_negative
        BacklogType.FEATURE -> R.color.sift_info
    }

@get:ColorRes
val BacklogType.containerColorRes: Int
    get() = when (this) {
        BacklogType.BUG -> R.color.sift_negative_container
        BacklogType.FEATURE -> R.color.sift_info_container
    }

@get:StringRes
val Effort.labelRes: Int
    get() = when (this) {
        Effort.S -> R.string.effort_small
        Effort.M -> R.string.effort_medium
        Effort.L -> R.string.effort_large
    }

@get:StringRes
val ReplyTone.labelRes: Int
    get() = when (this) {
        ReplyTone.FRIENDLY -> R.string.tone_friendly
        ReplyTone.FORMAL -> R.string.tone_formal
        ReplyTone.APOLOGETIC -> R.string.tone_apologetic
    }

@get:StringRes
val AnalysisSource.labelRes: Int
    get() = when (this) {
        AnalysisSource.APP_STORE -> R.string.source_app_store
        AnalysisSource.CSV -> R.string.source_csv
    }

/** Colour for a 0 to 100 priority score. */
@ColorRes
fun priorityColorRes(score: Int): Int = when {
    score >= PRIORITY_HIGH -> R.color.sift_negative
    score >= PRIORITY_MEDIUM -> R.color.sift_neutral
    else -> R.color.sift_positive
}

private const val PRIORITY_HIGH = 70
private const val PRIORITY_MEDIUM = 40

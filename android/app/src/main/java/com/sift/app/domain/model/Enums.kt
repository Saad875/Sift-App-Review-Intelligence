package com.sift.app.domain.model

/** Review sentiment as classified by the fast model. */
enum class Sentiment(val key: String) {
    POSITIVE("positive"),
    NEUTRAL("neutral"),
    NEGATIVE("negative");

    companion object {
        fun fromKey(key: String?): Sentiment? = entries.firstOrNull { it.key == key }
    }
}

/** Review category as classified by the fast model. */
enum class ReviewCategory(val key: String) {
    BUG("bug"),
    PERFORMANCE("performance"),
    UI_UX("ui_ux"),
    FEATURE_REQUEST("feature_request"),
    PRICING("pricing"),
    ADS("ads"),
    ACCOUNT("account"),
    PRAISE("praise"),
    OTHER("other");

    companion object {
        fun fromKey(key: String?): ReviewCategory? = entries.firstOrNull { it.key == key }
    }
}

/** Where the reviews of an analysis came from. */
enum class AnalysisSource(val key: String) {
    APP_STORE("appstore"),
    CSV("csv"),
    DEMO("demo");

    companion object {
        fun fromKey(key: String?): AnalysisSource = entries.firstOrNull { it.key == key } ?: APP_STORE
    }
}

/** Lifecycle of an analysis on the backend. */
enum class AnalysisStatus(val key: String) {
    QUEUED("queued"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed");

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED

    companion object {
        fun fromKey(key: String?): AnalysisStatus = entries.firstOrNull { it.key == key } ?: QUEUED
    }
}

/** The five pipeline steps, in order. */
enum class StepKey(val key: String) {
    FETCHING("fetching"),
    CLASSIFYING("classifying"),
    CLUSTERING("clustering"),
    PRIORITIZING("prioritizing"),
    RESEARCHING("researching");

    companion object {
        fun fromKey(key: String?): StepKey? = entries.firstOrNull { it.key == key }
    }
}

/** State of one pipeline step. */
enum class StepState(val key: String) {
    WAITING("waiting"),
    RUNNING("running"),
    DONE("done"),
    FAILED("failed"),
    SKIPPED("skipped");

    companion object {
        fun fromKey(key: String?): StepState = entries.firstOrNull { it.key == key } ?: WAITING
    }
}

/** Which configured model performs a step. */
enum class ModelRole(val key: String) {
    FAST("fast"),
    REASONING("reasoning");

    companion object {
        fun fromKey(key: String?): ModelRole? = entries.firstOrNull { it.key == key }
    }
}

/** Backlog item type. */
enum class BacklogType(val key: String) {
    BUG("bug"),
    FEATURE("feature");

    companion object {
        fun fromKey(key: String?): BacklogType = entries.firstOrNull { it.key == key } ?: FEATURE
    }
}

/** T-shirt size effort estimate. */
enum class Effort(val key: String) {
    S("S"),
    M("M"),
    L("L");

    companion object {
        fun fromKey(key: String?): Effort = entries.firstOrNull { it.key == key } ?: M
    }
}

/** Tone for a drafted developer reply. */
enum class ReplyTone(val key: String) {
    FRIENDLY("friendly"),
    FORMAL("formal"),
    APOLOGETIC("apologetic")
}

/** Model family shown in the UI, derived from the configured model id. */
enum class NemotronVariant {
    NANO,
    SUPER,
    ULTRA,
    UNKNOWN;

    companion object {
        /** Infers the Nemotron family from a Token Factory model id. */
        fun fromModelId(modelId: String?, role: ModelRole?): NemotronVariant {
            val id = modelId.orEmpty().lowercase()
            return when {
                "ultra" in id -> ULTRA
                "super" in id -> SUPER
                "nano" in id -> NANO
                role == ModelRole.FAST -> NANO
                role == ModelRole.REASONING -> SUPER
                else -> UNKNOWN
            }
        }
    }
}

package com.sift.app.domain.usecase

import com.sift.app.domain.error.AppError
import com.sift.app.domain.error.InvalidReason
import com.sift.app.domain.model.AppLanguage
import com.sift.app.domain.model.BackendHealth
import com.sift.app.domain.model.ReplyDraft
import com.sift.app.domain.model.ReplyTone
import com.sift.app.domain.model.Review
import com.sift.app.domain.model.ThemeMode
import com.sift.app.domain.repository.BackendRepository
import com.sift.app.domain.repository.LanguageRepository
import com.sift.app.domain.repository.ReplyRepository
import com.sift.app.domain.repository.SettingsRepository
import com.sift.app.domain.util.BackendUrlRules

/** Drafts a reply for a review. Sample reviews have no backend id and cannot be replied to. */
class DraftReplyUseCase(private val repository: ReplyRepository) {
    suspend operator fun invoke(review: Review, tone: ReplyTone): Result<ReplyDraft> {
        val remoteId = review.remoteId
            ?: return Result.failure(AppError.Invalid(InvalidReason.REPLY_UNAVAILABLE_FOR_SAMPLE))
        return runCatchingApp { repository.draftReply(remoteId, tone) }
    }
}

/** Reads and updates user preferences. */
class PreferencesUseCase(private val settings: SettingsRepository) {
    val defaultBackendUrl: String get() = settings.defaultBackendUrl

    fun backendUrl(): String = settings.backendUrl()

    /** Validates and stores the backend URL; returns the normalised value. */
    fun updateBackendUrl(input: String): Result<String> = runCatching {
        BackendUrlRules.normalize(input).also(settings::setBackendUrl)
    }

    fun resetBackendUrl(): String = settings.defaultBackendUrl.also(settings::setBackendUrl)

    fun isOnboardingCompleted(): Boolean = settings.isOnboardingCompleted()

    fun completeOnboarding() = settings.setOnboardingCompleted()

    fun themeMode(): ThemeMode = settings.themeMode()

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    fun lastCountry(): String = settings.lastCountry()
}

/** Calls the backend health endpoint at the configured URL. */
class CheckBackendUseCase(private val repository: BackendRepository) {
    suspend operator fun invoke(): Result<BackendHealth> = runCatchingApp { repository.health() }
}

/** Lists the supported languages and reads or changes the app language. */
class LanguageUseCase(private val repository: LanguageRepository) {
    val languages: List<AppLanguage> get() = AppLanguage.entries

    /** The chosen language, else the first supported device language, else English. */
    fun current(): AppLanguage =
        AppLanguage.fromTag(repository.selectedTag())
            ?: repository.deviceTags().firstNotNullOfOrNull(AppLanguage::fromTag)
            ?: AppLanguage.DEFAULT

    /** Applies [language]. Returns false when it is already the current one. */
    fun select(language: AppLanguage): Boolean {
        if (AppLanguage.fromTag(repository.selectedTag()) == language) return false
        repository.select(language.tag)
        return true
    }
}

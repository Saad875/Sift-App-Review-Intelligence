package com.sift.app.domain

import com.sift.app.domain.error.AppError
import com.sift.app.domain.error.InvalidReason
import com.sift.app.domain.model.AnalysisRequest
import com.sift.app.domain.model.AppLanguage
import com.sift.app.domain.model.AnalysisStatus
import com.sift.app.domain.model.BackendHealth
import com.sift.app.domain.model.CsvDocument
import com.sift.app.domain.model.ReplyTone
import com.sift.app.domain.model.Review
import com.sift.app.domain.repository.BackendRepository
import com.sift.app.domain.usecase.CheckBackendUseCase
import com.sift.app.domain.usecase.DraftReplyUseCase
import com.sift.app.domain.usecase.LanguageUseCase
import com.sift.app.domain.usecase.PollAnalysisUseCase
import com.sift.app.domain.usecase.PollEvent
import com.sift.app.domain.usecase.PreferencesUseCase
import com.sift.app.domain.usecase.SearchAppsUseCase
import com.sift.app.domain.usecase.StartAnalysisUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAppsUseCaseTest {
    @Test
    fun `short query fails locally without calling the repository`() = runTest {
        val repository = FakeSearchRepository()
        val result = SearchAppsUseCase(repository)(" a ", "us")
        val error = result.exceptionOrNull() as AppError.Invalid
        assertEquals(InvalidReason.QUERY_TOO_SHORT, error.reason)
        assertTrue(repository.queries.isEmpty())
    }

    @Test
    fun `query is trimmed and country lowercased`() = runTest {
        val repository = FakeSearchRepository()
        val result = SearchAppsUseCase(repository)("  notes ", "GB")
        assertEquals(1, result.getOrThrow().size)
        assertEquals("notes" to "gb", repository.queries.single())
    }
}

class StartAnalysisUseCaseTest {
    private val repository = FakeAnalysisRepository()
    private val settings = FakeSettingsRepository()
    private val useCase = StartAnalysisUseCase(repository, settings)

    @Test
    fun `competitors are trimmed and de-duplicated and the country remembered`() = runTest {
        val request = AnalysisRequest(sampleApp, "de", listOf(" Bear ", "bear", "Evernote", ""))
        assertTrue(useCase.appStore(request).isSuccess)
        assertEquals(listOf("Bear", "Evernote"), repository.started?.competitors)
        assertEquals("de", settings.country)
    }

    @Test
    fun `more than three competitors is rejected`() = runTest {
        val request = AnalysisRequest(sampleApp, "us", listOf("A", "B", "C", "D"))
        val error = useCase.appStore(request).exceptionOrNull() as AppError.Invalid
        assertEquals(InvalidReason.TOO_MANY_COMPETITORS, error.reason)
        assertEquals(null, repository.started)
    }

    @Test
    fun `csv analysis validates competitors too`() = runTest {
        assertTrue(useCase.csv(CsvDocument("content://x", "a.csv"), listOf("A")).isSuccess)
        assertEquals(listOf("A"), repository.startedCsvCompetitors)
        val tooLong = "x".repeat(61)
        val error = useCase.csv(CsvDocument("content://x", null), listOf(tooLong)).exceptionOrNull()
        assertEquals(InvalidReason.COMPETITOR_NAME_INVALID, (error as AppError.Invalid).reason)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PollAnalysisUseCaseTest {
    @Test
    fun `polls every interval until completed then syncs the report`() = runTest {
        val repository = FakeAnalysisRepository(
            mutableListOf(
                Result.success(analysis(AnalysisStatus.QUEUED)),
                Result.success(analysis(AnalysisStatus.RUNNING)),
                Result.success(analysis(AnalysisStatus.COMPLETED))
            )
        )
        val events = PollAnalysisUseCase(repository, intervalMillis = 2_000).invoke("a1").toList()
        assertEquals(3, events.size)
        assertTrue(events[0] is PollEvent.Updated)
        assertTrue(events[2] is PollEvent.Finished)
        assertEquals(1, repository.syncCount)
        assertEquals(4_000, currentTime)
    }

    @Test
    fun `transient errors keep polling and failure finishes without sync`() = runTest {
        val repository = FakeAnalysisRepository(
            mutableListOf(
                Result.failure(AppError.BackendUnreachable()),
                Result.success(analysis(AnalysisStatus.FAILED))
            )
        )
        val events = PollAnalysisUseCase(repository).invoke("a1").toList()
        assertTrue(events[0] is PollEvent.TransientError)
        assertEquals(AnalysisStatus.FAILED, (events[1] as PollEvent.Finished).analysis.status)
        assertEquals(0, repository.syncCount)
    }

    @Test
    fun `sync failure is retried on the next poll`() = runTest {
        val repository = FakeAnalysisRepository(
            mutableListOf(
                Result.success(analysis(AnalysisStatus.COMPLETED)),
                Result.success(analysis(AnalysisStatus.COMPLETED))
            )
        ).apply { syncFailure = AppError.BackendUnreachable() }
        val events = PollAnalysisUseCase(repository).invoke("a1").toList()
        assertTrue(events[0] is PollEvent.TransientError)
        assertTrue(events[1] is PollEvent.Finished)
        assertEquals(1, repository.syncCount)
    }

    @Test
    fun `not found stops immediately and too many errors become fatal`() = runTest {
        val notFound = FakeAnalysisRepository(mutableListOf(Result.failure(AppError.NotFound())))
        assertTrue(PollAnalysisUseCase(notFound).invoke("a1").toList().single() is PollEvent.Fatal)

        val flaky = FakeAnalysisRepository(MutableList(3) { Result.failure(AppError.NoInternet()) })
        val events = PollAnalysisUseCase(flaky, maxConsecutiveErrors = 3).invoke("a1").toList()
        assertEquals(3, events.size)
        assertTrue(events.last() is PollEvent.Fatal)
    }
}

class DraftReplyUseCaseTest {
    private fun review(remoteId: Long?) = Review(
        id = 1, remoteId = remoteId, author = "", title = "", text = "Great", rating = 5, dateMillis = null,
        appVersion = "", sentiment = null, category = null, severity = null, summary = "", themeTitle = null
    )

    @Test
    fun `reviews without a backend id cannot be replied to`() = runTest {
        val repository = FakeReplyRepository()
        val error = DraftReplyUseCase(repository)(review(null), ReplyTone.FORMAL).exceptionOrNull()
        assertEquals(InvalidReason.REPLY_UNAVAILABLE, (error as AppError.Invalid).reason)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `live reviews use the backend id and tone`() = runTest {
        val repository = FakeReplyRepository()
        val draft = DraftReplyUseCase(repository)(review(42), ReplyTone.APOLOGETIC).getOrThrow()
        assertEquals("Thanks!", draft.text)
        assertEquals(42L to ReplyTone.APOLOGETIC, repository.calls.single())
    }
}

class PreferencesUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val useCase = PreferencesUseCase(settings)

    @Test
    fun `valid url is normalised with a trailing slash`() {
        assertEquals("https://sift.example.com/", useCase.updateBackendUrl(" https://sift.example.com ").getOrThrow())
        assertEquals("https://sift.example.com/", settings.url)
    }

    @Test
    fun `invalid urls are rejected and not stored`() {
        listOf("ftp://x.com", "not a url", "http://", "https://x.com/?a=1").forEach { input ->
            val error = useCase.updateBackendUrl(input).exceptionOrNull()
            assertEquals(input, InvalidReason.BACKEND_URL_INVALID, (error as AppError.Invalid).reason)
        }
        assertEquals(settings.defaultBackendUrl, settings.url)
    }

    @Test
    fun `reset restores the default`() {
        settings.url = "https://other/"
        assertEquals(settings.defaultBackendUrl, useCase.resetBackendUrl())
    }

    @Test
    fun `onboarding runs once`() {
        assertFalse(useCase.isOnboardingCompleted())
        useCase.completeOnboarding()
        assertTrue(useCase.isOnboardingCompleted())
    }
}

class LanguageUseCaseTest {
    @Test
    fun `offers the 15 languages with English first`() {
        val languages = LanguageUseCase(FakeLanguageRepository()).languages
        assertEquals(15, languages.size)
        assertEquals(AppLanguage.ENGLISH, languages.first())
        assertEquals(languages.size, languages.map { it.tag }.toSet().size)
    }

    @Test
    fun `chosen language wins over the device language`() {
        val useCase = LanguageUseCase(FakeLanguageRepository(selected = "ar", device = listOf("de-DE")))
        assertEquals(AppLanguage.ARABIC, useCase.current())
    }

    @Test
    fun `falls back to the first supported device language, then English`() {
        assertEquals(AppLanguage.GERMAN, LanguageUseCase(FakeLanguageRepository(device = listOf("nl-NL", "de-AT"))).current())
        assertEquals(AppLanguage.ENGLISH, LanguageUseCase(FakeLanguageRepository(device = listOf("nl-NL"))).current())
    }

    @Test
    fun `region and script variants and the legacy Indonesian code are matched`() {
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTag("pt-BR"))
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromTag("zh-Hans-CN"))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromTag("id"))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromTag("in_ID"))
        assertNull(AppLanguage.fromTag("xx"))
        assertNull(AppLanguage.fromTag(null))
    }

    @Test
    fun `selecting applies the tag once`() {
        val repository = FakeLanguageRepository(selected = "en")
        val useCase = LanguageUseCase(repository)
        assertTrue(useCase.select(AppLanguage.JAPANESE))
        assertFalse(useCase.select(AppLanguage.JAPANESE))
        assertEquals(listOf("ja"), repository.applied)
    }
}

class CheckBackendUseCaseTest {
    private val health = BackendHealth(
        version = "1.0.0",
        fastModel = "provider/fast-model",
        reasoningModel = "provider/reasoning-model",
        nebiusConfigured = true,
        tavilyConfigured = false
    )

    @Test
    fun `returns what the backend reports`() = runTest {
        val repository = object : BackendRepository {
            override suspend fun health() = health
        }
        assertEquals(health, CheckBackendUseCase(repository)().getOrThrow())
    }

    @Test
    fun `wraps failures as app errors`() = runTest {
        val repository = object : BackendRepository {
            override suspend fun health(): BackendHealth = throw IllegalStateException("boom")
        }
        assertTrue(CheckBackendUseCase(repository)().exceptionOrNull() is AppError.Unexpected)
    }
}

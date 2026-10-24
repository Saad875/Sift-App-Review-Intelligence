package com.sift.app.domain.usecase

import com.sift.app.domain.model.Analysis
import com.sift.app.domain.model.AnalysisRequest
import com.sift.app.domain.model.CsvDocument
import com.sift.app.domain.repository.AnalysisRepository
import com.sift.app.domain.repository.SettingsRepository
import com.sift.app.domain.util.CompetitorRules

/** Validates the request and starts an App Store or CSV analysis on the backend. */
class StartAnalysisUseCase(
    private val repository: AnalysisRepository,
    private val settings: SettingsRepository
) {
    suspend fun appStore(request: AnalysisRequest): Result<Analysis> = runCatchingApp {
        val competitors = CompetitorRules.normalize(request.competitors)
        settings.setLastCountry(request.country)
        repository.startAppStoreAnalysis(request.copy(competitors = competitors))
    }

    suspend fun csv(document: CsvDocument, competitors: List<String>): Result<Analysis> = runCatchingApp {
        repository.startCsvAnalysis(document, CompetitorRules.normalize(competitors))
    }
}

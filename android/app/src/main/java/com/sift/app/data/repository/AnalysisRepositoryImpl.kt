package com.sift.app.data.repository

import com.sift.app.data.csv.CsvDocumentReader
import com.sift.app.data.local.dao.AnalysisDao
import com.sift.app.data.mapper.toDomain
import com.sift.app.data.mapper.toEntity
import com.sift.app.data.network.ApiCaller
import com.sift.app.data.remote.SiftApi
import com.sift.app.data.remote.dto.CreateAnalysisRequestDto
import com.sift.app.data.remote.dto.ReviewDto
import com.sift.app.domain.error.AppError
import com.sift.app.domain.model.Analysis
import com.sift.app.domain.model.AnalysisRequest
import com.sift.app.domain.model.AnalysisSource
import com.sift.app.domain.model.BacklogItem
import com.sift.app.domain.model.CsvDocument
import com.sift.app.domain.model.Report
import com.sift.app.domain.model.Review
import com.sift.app.domain.model.ReviewFilter
import com.sift.app.domain.repository.AnalysisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Network plus Room implementation. The UI only ever reads from Room. */
class AnalysisRepositoryImpl(
    private val api: SiftApi,
    private val caller: ApiCaller,
    private val dao: AnalysisDao,
    private val csvReader: CsvDocumentReader
) : AnalysisRepository {

    override suspend fun startAppStoreAnalysis(request: AnalysisRequest): Analysis {
        val body = CreateAnalysisRequestDto(
            source = AnalysisSource.APP_STORE.key,
            appId = request.app.appId,
            country = request.country,
            appName = request.app.name,
            developer = request.app.developer,
            iconUrl = request.app.iconUrl,
            competitors = request.competitors
        )
        val dto = caller.call { api.createAnalysis(body) }
        val entity = dto.toEntity()
        dao.upsertAnalysis(entity)
        return entity.toDomain()
    }

    override suspend fun startCsvAnalysis(document: CsvDocument, competitors: List<String>): Analysis {
        val (bytes, fileName) = csvReader.read(document)
        val text = TEXT_PLAIN.toMediaType()
        val filePart = MultipartBody.Part.createFormData(
            FILE_FIELD, fileName, bytes.toRequestBody(TEXT_CSV.toMediaType())
        )
        val dto = caller.call {
            api.createCsvAnalysis(
                source = AnalysisSource.CSV.key.toRequestBody(text),
                appName = fileName.substringBeforeLast('.').toRequestBody(text),
                competitors = competitors.joinToString(",").toRequestBody(text),
                file = filePart
            )
        }
        val entity = dto.toEntity()
        dao.upsertAnalysis(entity)
        return entity.toDomain()
    }

    override suspend fun refreshStatus(analysisId: String): Analysis {
        val dto = caller.call { api.getAnalysis(analysisId) }
        val entity = dto.toEntity(previous = dao.getAnalysis(analysisId))
        dao.upsertAnalysis(entity)
        return entity.toDomain()
    }

    override suspend fun syncReport(analysisId: String) {
        val existing = dao.getAnalysis(analysisId)
        val analysis = caller.call { api.getAnalysis(analysisId) }
        val themes = caller.call { api.getThemes(analysisId) }.results.orEmpty()
        val backlog = caller.call { api.getBacklog(analysisId) }.results.orEmpty()
        val competitors = caller.call { api.getCompetitors(analysisId) }.results.orEmpty()
        val reviews = fetchAllReviews(analysisId)
        dao.replaceReport(
            analysis = analysis.toEntity(previous = existing).copy(reportSynced = true),
            themes = themes.mapIndexed { index, dto -> dto.toEntity(analysisId, index) },
            backlog = backlog.mapIndexed { index, dto -> dto.toEntity(analysisId, index) },
            competitors = competitors.map { it.toEntity(analysisId) },
            reviews = reviews.map { it.toEntity(analysisId) }
        )
    }

    private suspend fun fetchAllReviews(analysisId: String): List<ReviewDto> {
        val all = mutableListOf<ReviewDto>()
        var page = 1
        while (page <= MAX_REVIEW_PAGES) {
            val response = caller.call { api.getReviews(analysisId, page, PAGE_SIZE) }
            all += response.results.orEmpty()
            if (response.next.isNullOrBlank()) break
            page++
        }
        return all
    }

    override fun observeAnalysis(analysisId: String): Flow<Analysis?> =
        dao.observeAnalysis(analysisId).map { it?.toDomain() }.distinctUntilChanged()

    override fun observeRecent(): Flow<List<Analysis>> =
        dao.observeRecent(RECENT_LIMIT).map { rows -> rows.map { it.toDomain() } }

    override fun observeReport(analysisId: String): Flow<Report?> = combine(
        dao.observeAnalysis(analysisId),
        dao.observeThemes(analysisId),
        dao.observeBacklog(analysisId),
        dao.observeCompetitors(analysisId)
    ) { analysis, themes, backlog, competitors ->
        if (analysis == null || !analysis.reportSynced) {
            null
        } else {
            Report(
                analysis = analysis.toDomain(),
                themes = themes.map { it.toDomain() },
                backlog = backlog.map { it.toDomain() },
                competitors = competitors.map { it.toDomain() }
            )
        }
    }.distinctUntilChanged()

    override fun observeReviews(analysisId: String, filter: ReviewFilter): Flow<List<Review>> =
        dao.observeReviews(analysisId, filter.sentiment?.key, filter.category?.key)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getReview(reviewId: Long): Review? = dao.getReview(reviewId)?.toDomain()

    override suspend fun getBacklogItem(itemId: Long): BacklogItem? = dao.getBacklogItem(itemId)?.toDomain()

    override suspend fun clearAll() = dao.deleteAll()

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_REVIEW_PAGES = 10
        const val RECENT_LIMIT = 30
        const val FILE_FIELD = "file"
        const val TEXT_PLAIN = "text/plain"
        const val TEXT_CSV = "text/csv"
    }
}

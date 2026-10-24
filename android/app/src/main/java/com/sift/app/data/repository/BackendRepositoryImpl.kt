package com.sift.app.data.repository

import com.sift.app.data.network.ApiCaller
import com.sift.app.data.remote.SiftApi
import com.sift.app.domain.error.AppError
import com.sift.app.domain.model.BackendHealth
import com.sift.app.domain.repository.BackendRepository

/** Health check against the backend URL from Settings. */
class BackendRepositoryImpl(
    private val api: SiftApi,
    private val caller: ApiCaller
) : BackendRepository {
    override suspend fun health(): BackendHealth {
        val dto = caller.call { api.health() }
        if (dto.status != STATUS_OK) throw AppError.BackendUnreachable()
        return BackendHealth(
            version = dto.version.orEmpty(),
            fastModel = dto.models?.fast.orEmpty(),
            reasoningModel = dto.models?.reasoning.orEmpty(),
            nebiusConfigured = dto.configured?.nebius == true,
            tavilyConfigured = dto.configured?.tavily == true
        )
    }

    private companion object {
        const val STATUS_OK = "ok"
    }
}

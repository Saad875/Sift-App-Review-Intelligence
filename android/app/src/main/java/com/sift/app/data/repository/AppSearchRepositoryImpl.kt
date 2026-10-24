package com.sift.app.data.repository

import com.sift.app.data.mapper.toDomain
import com.sift.app.data.network.ApiCaller
import com.sift.app.data.remote.SiftApi
import com.sift.app.domain.model.AppSummary
import com.sift.app.domain.repository.AppSearchRepository

/** App search through the backend's iTunes Search API proxy. */
class AppSearchRepositoryImpl(
    private val api: SiftApi,
    private val caller: ApiCaller
) : AppSearchRepository {
    override suspend fun search(query: String, country: String): List<AppSummary> =
        caller.call { api.searchApps(query, country) }.results.orEmpty().mapNotNull { it.toDomain() }
}

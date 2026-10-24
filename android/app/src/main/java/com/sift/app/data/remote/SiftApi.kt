package com.sift.app.data.remote

import com.sift.app.data.remote.dto.AnalysisDto
import com.sift.app.data.remote.dto.AppSearchResponseDto
import com.sift.app.data.remote.dto.BacklogItemDto
import com.sift.app.data.remote.dto.CompetitorDto
import com.sift.app.data.remote.dto.CreateAnalysisRequestDto
import com.sift.app.data.remote.dto.HealthDto
import com.sift.app.data.remote.dto.ListEnvelopeDto
import com.sift.app.data.remote.dto.ReplyDraftDto
import com.sift.app.data.remote.dto.ReplyRequestDto
import com.sift.app.data.remote.dto.ReviewPageDto
import com.sift.app.data.remote.dto.ThemeDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the Sift backend (/api/v1/). Paths are relative; the
 * host comes from Settings at request time through [BaseUrlInterceptor].
 */
interface SiftApi {
    @GET("api/v1/health/")
    suspend fun health(): HealthDto

    @GET("api/v1/apps/search/")
    suspend fun searchApps(@Query("q") query: String, @Query("country") country: String): AppSearchResponseDto

    @POST("api/v1/analyses/")
    suspend fun createAnalysis(@Body body: CreateAnalysisRequestDto): AnalysisDto

    @Multipart
    @POST("api/v1/analyses/")
    suspend fun createCsvAnalysis(
        @Part("source") source: RequestBody,
        @Part("app_name") appName: RequestBody,
        @Part("competitors") competitors: RequestBody,
        @Part file: MultipartBody.Part
    ): AnalysisDto

    @GET("api/v1/analyses/{id}/")
    suspend fun getAnalysis(@Path("id") id: String): AnalysisDto

    @GET("api/v1/analyses/{id}/reviews/")
    suspend fun getReviews(
        @Path("id") id: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int
    ): ReviewPageDto

    @GET("api/v1/analyses/{id}/themes/")
    suspend fun getThemes(@Path("id") id: String): ListEnvelopeDto<ThemeDto>

    @GET("api/v1/analyses/{id}/backlog/")
    suspend fun getBacklog(@Path("id") id: String): ListEnvelopeDto<BacklogItemDto>

    @GET("api/v1/analyses/{id}/competitors/")
    suspend fun getCompetitors(@Path("id") id: String): ListEnvelopeDto<CompetitorDto>

    @POST("api/v1/reviews/{id}/reply/")
    suspend fun draftReply(@Path("id") reviewId: Long, @Body body: ReplyRequestDto): ReplyDraftDto
}

package com.sift.app.di

import android.content.Context
import com.google.gson.Gson
import com.sift.app.BuildConfig
import com.sift.app.data.csv.CsvDocumentReader
import com.sift.app.data.language.AppCompatLanguageRepository
import com.sift.app.data.local.SiftDatabase
import com.sift.app.data.network.ApiCaller
import com.sift.app.data.network.ConnectivityChecker
import com.sift.app.data.remote.BaseUrlInterceptor
import com.sift.app.data.remote.SiftApi
import com.sift.app.data.repository.AnalysisRepositoryImpl
import com.sift.app.data.repository.AppSearchRepositoryImpl
import com.sift.app.data.repository.BackendRepositoryImpl
import com.sift.app.data.repository.ReplyRepositoryImpl
import com.sift.app.data.settings.SharedPrefsSettingsRepository
import com.sift.app.domain.repository.AnalysisRepository
import com.sift.app.domain.repository.AppSearchRepository
import com.sift.app.domain.repository.BackendRepository
import com.sift.app.domain.repository.ReplyRepository
import com.sift.app.domain.repository.SettingsRepository
import com.sift.app.domain.usecase.BuildMarkdownUseCase
import com.sift.app.domain.usecase.CheckBackendUseCase
import com.sift.app.domain.usecase.ClearLocalDataUseCase
import com.sift.app.domain.usecase.DraftReplyUseCase
import com.sift.app.domain.usecase.GetBacklogItemUseCase
import com.sift.app.domain.usecase.GetReviewUseCase
import com.sift.app.domain.usecase.LanguageUseCase
import com.sift.app.domain.usecase.ObserveAnalysisUseCase
import com.sift.app.domain.usecase.ObserveRecentAnalysesUseCase
import com.sift.app.domain.usecase.ObserveReportUseCase
import com.sift.app.domain.usecase.ObserveReviewsUseCase
import com.sift.app.domain.usecase.PollAnalysisUseCase
import com.sift.app.domain.usecase.PreferencesUseCase
import com.sift.app.domain.usecase.SearchAppsUseCase
import com.sift.app.domain.usecase.StartAnalysisUseCase
import com.sift.app.domain.usecase.SyncReportUseCase
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Manual dependency injection. Created once in [com.sift.app.SiftApplication];
 * everything is lazy so start-up stays fast.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val gson: Gson by lazy { Gson() }

    val settingsRepository: SettingsRepository by lazy {
        SharedPrefsSettingsRepository(appContext, BuildConfig.DEFAULT_BACKEND_URL)
    }

    private val connectivity by lazy { ConnectivityChecker(appContext) }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(BaseUrlInterceptor { settingsRepository.backendUrl() })
            .addInterceptor(logging)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val api: SiftApi by lazy {
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SiftApi::class.java)
    }

    private val apiCaller by lazy { ApiCaller(connectivity, gson) }
    private val database by lazy { SiftDatabase.create(appContext) }

    private val appSearchRepository: AppSearchRepository by lazy { AppSearchRepositoryImpl(api, apiCaller) }
    private val replyRepository: ReplyRepository by lazy { ReplyRepositoryImpl(api, apiCaller) }
    private val backendRepository: BackendRepository by lazy { BackendRepositoryImpl(api, apiCaller) }
    private val analysisRepository: AnalysisRepository by lazy {
        AnalysisRepositoryImpl(
            api = api,
            caller = apiCaller,
            dao = database.analysisDao(),
            csvReader = CsvDocumentReader(appContext.contentResolver)
        )
    }

    // Use cases
    val searchApps by lazy { SearchAppsUseCase(appSearchRepository) }
    val startAnalysis by lazy { StartAnalysisUseCase(analysisRepository, settingsRepository) }
    val pollAnalysis by lazy { PollAnalysisUseCase(analysisRepository) }
    val observeAnalysis by lazy { ObserveAnalysisUseCase(analysisRepository) }
    val observeRecent by lazy { ObserveRecentAnalysesUseCase(analysisRepository) }
    val observeReport by lazy { ObserveReportUseCase(analysisRepository) }
    val syncReport by lazy { SyncReportUseCase(analysisRepository) }
    val observeReviews by lazy { ObserveReviewsUseCase(analysisRepository) }
    val getReview by lazy { GetReviewUseCase(analysisRepository) }
    val getBacklogItem by lazy { GetBacklogItemUseCase(analysisRepository) }
    val clearLocalData by lazy { ClearLocalDataUseCase(analysisRepository) }
    val draftReply by lazy { DraftReplyUseCase(replyRepository) }
    val buildMarkdown by lazy { BuildMarkdownUseCase() }
    val preferences by lazy { PreferencesUseCase(settingsRepository) }
    val checkBackend by lazy { CheckBackendUseCase(backendRepository) }
    val language by lazy { LanguageUseCase(AppCompatLanguageRepository()) }

    private companion object {
        // Retrofit needs a syntactically valid base URL; BaseUrlInterceptor swaps in the real one.
        const val PLACEHOLDER_BASE_URL = "http://localhost/"
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 60L
    }
}

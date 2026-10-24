package com.sift.app.presentation.setup

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import com.sift.app.domain.model.AppSummary
import com.sift.app.domain.model.CsvDocument

/** Fragment arguments for the setup screen, readable from a SavedStateHandle. */
object SetupArgs {
    const val KEY_COUNTRY = "country"
    const val DEFAULT_COUNTRY = "us"
    private const val KEY_MODE = "mode"
    private const val MODE_APP = "app"
    private const val MODE_CSV = "csv"
    private const val KEY_APP_ID = "app_id"
    private const val KEY_APP_NAME = "app_name"
    private const val KEY_DEVELOPER = "developer"
    private const val KEY_ICON = "icon_url"
    private const val KEY_RATING = "rating"
    private const val KEY_RATING_COUNT = "rating_count"
    private const val KEY_GENRE = "genre"
    private const val KEY_CSV_URI = "csv_uri"
    private const val KEY_CSV_NAME = "csv_name"

    fun forApp(app: AppSummary, country: String): Bundle = bundleOf(
        KEY_MODE to MODE_APP,
        KEY_APP_ID to app.appId,
        KEY_APP_NAME to app.name,
        KEY_DEVELOPER to app.developer,
        KEY_ICON to app.iconUrl,
        KEY_RATING to app.rating,
        KEY_RATING_COUNT to app.ratingCount,
        KEY_GENRE to app.genre,
        KEY_COUNTRY to country
    )

    fun forCsv(uri: String, displayName: String?): Bundle = bundleOf(
        KEY_MODE to MODE_CSV,
        KEY_CSV_URI to uri,
        KEY_CSV_NAME to displayName
    )

    fun readTarget(state: SavedStateHandle): SetupTarget =
        if (state.get<String>(KEY_MODE) == MODE_CSV) {
            SetupTarget.Csv(CsvDocument(state.get<String>(KEY_CSV_URI).orEmpty(), state.get<String>(KEY_CSV_NAME)))
        } else {
            SetupTarget.App(
                AppSummary(
                    appId = state.get<String>(KEY_APP_ID).orEmpty(),
                    name = state.get<String>(KEY_APP_NAME).orEmpty(),
                    developer = state.get<String>(KEY_DEVELOPER).orEmpty(),
                    iconUrl = state.get<String>(KEY_ICON).orEmpty(),
                    rating = state.get<Double>(KEY_RATING),
                    ratingCount = state.get<Int>(KEY_RATING_COUNT),
                    genre = state.get<String>(KEY_GENRE).orEmpty()
                )
            )
        }
}

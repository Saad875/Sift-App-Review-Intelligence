package com.sift.app.data.demo

import android.content.res.AssetManager
import com.google.gson.Gson
import com.sift.app.data.remote.dto.DemoBundleDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the bundled sample analysis from assets. Works with no network. */
class DemoDataSource(private val assets: AssetManager, private val gson: Gson) {
    suspend fun load(): DemoBundleDto = withContext(Dispatchers.IO) {
        assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, DemoBundleDto::class.java)
        }
    }

    companion object {
        const val ASSET_PATH = "demo/demo_analysis.json"
        const val DEMO_ANALYSIS_ID = "sample-demo-analysis"
    }
}

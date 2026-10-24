package com.sift.app.data.local.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sift.app.data.local.entity.StoredSource
import com.sift.app.data.local.entity.StoredStats
import com.sift.app.data.local.entity.StoredStep

/** Stores lists and small objects as JSON text columns. */
class Converters {
    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type
    private val stepListType = object : TypeToken<List<StoredStep>>() {}.type
    private val sourceListType = object : TypeToken<List<StoredSource>>() {}.type

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        runCatching { gson.fromJson<List<String>>(value, stringListType) }.getOrNull().orEmpty()

    @TypeConverter
    fun fromStepList(value: List<StoredStep>): String = gson.toJson(value)

    @TypeConverter
    fun toStepList(value: String): List<StoredStep> =
        runCatching { gson.fromJson<List<StoredStep>>(value, stepListType) }.getOrNull().orEmpty()

    @TypeConverter
    fun fromSourceList(value: List<StoredSource>): String = gson.toJson(value)

    @TypeConverter
    fun toSourceList(value: String): List<StoredSource> =
        runCatching { gson.fromJson<List<StoredSource>>(value, sourceListType) }.getOrNull().orEmpty()

    @TypeConverter
    fun fromStats(value: StoredStats): String = gson.toJson(value)

    @TypeConverter
    fun toStats(value: String): StoredStats =
        runCatching { gson.fromJson(value, StoredStats::class.java) }.getOrNull() ?: EMPTY_STATS

    private companion object {
        val EMPTY_STATS = StoredStats(0, 0, null, emptyMap(), emptyMap(), List(5) { 0 }, 0, 0, 0)
    }
}

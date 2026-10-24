package com.sift.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached analysis. Room is the single source of truth for everything the UI shows. */
@Entity(tableName = "analyses")
data class AnalysisEntity(
    @PrimaryKey val id: String,
    val appName: String,
    val appId: String,
    val developer: String,
    val iconUrl: String,
    val source: String,
    val country: String,
    val competitors: List<String>,
    val status: String,
    val currentStep: String?,
    val progress: Int,
    val error: String,
    val warnings: List<String>,
    val createdAtMillis: Long,
    val steps: List<StoredStep>,
    val stats: StoredStats,
    val reportSynced: Boolean
)

/** Pipeline step stored as JSON inside [AnalysisEntity]. */
data class StoredStep(
    val key: String,
    val state: String,
    val modelRole: String?,
    val model: String?
)

/** Summary statistics stored as JSON inside [AnalysisEntity]. */
data class StoredStats(
    val totalReviews: Int,
    val classifiedReviews: Int,
    val averageRating: Double?,
    val sentiment: Map<String, Int>,
    val categories: Map<String, Int>,
    val ratingDistribution: List<Int>,
    val themeCount: Int,
    val backlogCount: Int,
    val competitorCount: Int
)

/** Source link stored as JSON inside [CompetitorEntity]. */
data class StoredSource(val title: String, val url: String)

@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = AnalysisEntity::class,
            parentColumns = ["id"],
            childColumns = ["analysisId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("analysisId"), Index(value = ["analysisId", "sentiment", "category"])]
)
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analysisId: String,
    val remoteId: Long?,
    val author: String,
    val title: String,
    val text: String,
    val rating: Int?,
    val dateMillis: Long?,
    val appVersion: String,
    val sentiment: String?,
    val category: String?,
    val severity: Int?,
    val summary: String,
    val themeTitle: String?
)

@Entity(
    tableName = "themes",
    foreignKeys = [
        ForeignKey(
            entity = AnalysisEntity::class,
            parentColumns = ["id"],
            childColumns = ["analysisId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("analysisId")]
)
data class ThemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analysisId: String,
    val position: Int,
    val title: String,
    val description: String,
    val reviewCount: Int,
    val negativeShare: Float,
    val quotes: List<String>
)

@Entity(
    tableName = "backlog_items",
    foreignKeys = [
        ForeignKey(
            entity = AnalysisEntity::class,
            parentColumns = ["id"],
            childColumns = ["analysisId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("analysisId")]
)
data class BacklogItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analysisId: String,
    val rank: Int,
    val title: String,
    val type: String,
    val priorityScore: Int,
    val reasoning: String,
    val userImpact: String,
    val suggestedFix: String,
    val effort: String,
    val themeTitle: String?,
    val evidenceQuotes: List<String>
)

@Entity(
    tableName = "competitor_insights",
    foreignKeys = [
        ForeignKey(
            entity = AnalysisEntity::class,
            parentColumns = ["id"],
            childColumns = ["analysisId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("analysisId")]
)
data class CompetitorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analysisId: String,
    val name: String,
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val opportunities: List<String>,
    val sources: List<StoredSource>,
    val error: String
)

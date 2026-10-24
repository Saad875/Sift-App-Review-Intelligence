package com.sift.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sift.app.data.local.entity.AnalysisEntity
import com.sift.app.data.local.entity.BacklogItemEntity
import com.sift.app.data.local.entity.CompetitorEntity
import com.sift.app.data.local.entity.ReviewEntity
import com.sift.app.data.local.entity.ThemeEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes cached analyses and their report data. */
@Dao
interface AnalysisDao {
    @Upsert
    suspend fun upsertAnalysis(analysis: AnalysisEntity)

    @Query("SELECT * FROM analyses WHERE id = :id")
    suspend fun getAnalysis(id: String): AnalysisEntity?

    @Query("SELECT * FROM analyses WHERE id = :id")
    fun observeAnalysis(id: String): Flow<AnalysisEntity?>

    @Query("SELECT * FROM analyses ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AnalysisEntity>>

    @Query("SELECT * FROM themes WHERE analysisId = :analysisId ORDER BY position ASC")
    fun observeThemes(analysisId: String): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM backlog_items WHERE analysisId = :analysisId ORDER BY `rank` ASC")
    fun observeBacklog(analysisId: String): Flow<List<BacklogItemEntity>>

    @Query("SELECT * FROM competitor_insights WHERE analysisId = :analysisId ORDER BY id ASC")
    fun observeCompetitors(analysisId: String): Flow<List<CompetitorEntity>>

    @Query(
        """
        SELECT * FROM reviews
        WHERE analysisId = :analysisId
          AND (:sentiment IS NULL OR sentiment = :sentiment)
          AND (:category IS NULL OR category = :category)
        ORDER BY dateMillis IS NULL, dateMillis DESC, id ASC
        """
    )
    fun observeReviews(analysisId: String, sentiment: String?, category: String?): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE id = :id")
    suspend fun getReview(id: Long): ReviewEntity?

    @Query("SELECT * FROM backlog_items WHERE id = :id")
    suspend fun getBacklogItem(id: Long): BacklogItemEntity?

    @Insert
    suspend fun insertReviews(reviews: List<ReviewEntity>)

    @Insert
    suspend fun insertThemes(themes: List<ThemeEntity>)

    @Insert
    suspend fun insertBacklog(items: List<BacklogItemEntity>)

    @Insert
    suspend fun insertCompetitors(items: List<CompetitorEntity>)

    @Query("DELETE FROM reviews WHERE analysisId = :analysisId")
    suspend fun deleteReviews(analysisId: String)

    @Query("DELETE FROM themes WHERE analysisId = :analysisId")
    suspend fun deleteThemes(analysisId: String)

    @Query("DELETE FROM backlog_items WHERE analysisId = :analysisId")
    suspend fun deleteBacklog(analysisId: String)

    @Query("DELETE FROM competitor_insights WHERE analysisId = :analysisId")
    suspend fun deleteCompetitors(analysisId: String)

    @Query("DELETE FROM analyses")
    suspend fun deleteAll()

    /** Atomically replaces the analysis and all of its report data. */
    @Transaction
    suspend fun replaceReport(
        analysis: AnalysisEntity,
        themes: List<ThemeEntity>,
        backlog: List<BacklogItemEntity>,
        competitors: List<CompetitorEntity>,
        reviews: List<ReviewEntity>
    ) {
        upsertAnalysis(analysis)
        deleteReviews(analysis.id)
        deleteThemes(analysis.id)
        deleteBacklog(analysis.id)
        deleteCompetitors(analysis.id)
        insertThemes(themes)
        insertBacklog(backlog)
        insertCompetitors(competitors)
        insertReviews(reviews)
    }
}

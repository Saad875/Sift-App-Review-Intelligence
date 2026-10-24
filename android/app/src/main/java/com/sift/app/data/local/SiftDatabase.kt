package com.sift.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sift.app.data.local.converter.Converters
import com.sift.app.data.local.dao.AnalysisDao
import com.sift.app.data.local.entity.AnalysisEntity
import com.sift.app.data.local.entity.BacklogItemEntity
import com.sift.app.data.local.entity.CompetitorEntity
import com.sift.app.data.local.entity.ReviewEntity
import com.sift.app.data.local.entity.ThemeEntity

/** Local cache so finished reports open offline. */
@Database(
    entities = [
        AnalysisEntity::class,
        ReviewEntity::class,
        ThemeEntity::class,
        BacklogItemEntity::class,
        CompetitorEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SiftDatabase : RoomDatabase() {
    abstract fun analysisDao(): AnalysisDao

    companion object {
        private const val NAME = "sift.db"

        fun create(context: Context): SiftDatabase =
            Room.databaseBuilder(context.applicationContext, SiftDatabase::class.java, NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

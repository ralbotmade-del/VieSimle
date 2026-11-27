package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedDocumentEntity::class,
        CachedTaskEntity::class,
        CalendarSyncLinkEntity::class,
        CachedSubscriptionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VieSimpleLocalDatabase : RoomDatabase() {
    abstract fun cachedDocumentDao(): CachedDocumentDao
    abstract fun cachedTaskDao(): CachedTaskDao
    abstract fun calendarSyncLinkDao(): CalendarSyncLinkDao
    abstract fun cachedSubscriptionDao(): CachedSubscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: VieSimpleLocalDatabase? = null

        fun getInstance(context: Context): VieSimpleLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VieSimpleLocalDatabase::class.java,
                    "viesimple_offline_cache.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedDocumentDao {
    @Query("SELECT * FROM cached_documents WHERE ownerScopeUid = :userId ORDER BY updatedAtMillis DESC")
    fun observeForUser(userId: String): Flow<List<CachedDocumentEntity>>

    @Query("SELECT * FROM cached_documents WHERE ownerScopeUid = :userId ORDER BY updatedAtMillis DESC")
    suspend fun getAllForUser(userId: String): List<CachedDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedDocumentEntity>)

    @Query("DELETE FROM cached_documents WHERE ownerScopeUid = :userId AND id = :docId")
    suspend fun deleteOne(userId: String, docId: String)

    @Query("DELETE FROM cached_documents WHERE ownerScopeUid = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Transaction
    suspend fun replaceForUser(userId: String, items: List<CachedDocumentEntity>) {
        deleteAllForUser(userId)
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}

@Dao
interface CachedTaskDao {
    @Query("SELECT * FROM cached_tasks WHERE ownerScopeUid = :userId ORDER BY updatedAtMillis DESC")
    fun observeForUser(userId: String): Flow<List<CachedTaskEntity>>

    @Query("SELECT * FROM cached_tasks WHERE ownerScopeUid = :userId ORDER BY updatedAtMillis DESC")
    suspend fun getAllForUser(userId: String): List<CachedTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedTaskEntity>)

    @Query("DELETE FROM cached_tasks WHERE ownerScopeUid = :userId AND id = :taskId")
    suspend fun deleteOne(userId: String, taskId: String)

    @Query("DELETE FROM cached_tasks WHERE ownerScopeUid = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Transaction
    suspend fun replaceForUser(userId: String, items: List<CachedTaskEntity>) {
        deleteAllForUser(userId)
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}

@Dao
interface CalendarSyncLinkDao {
    @Query("SELECT * FROM calendar_sync_links WHERE ownerScopeUid = :userId")
    fun observeForUser(userId: String): Flow<List<CalendarSyncLinkEntity>>

    @Query("SELECT * FROM calendar_sync_links WHERE ownerScopeUid = :userId")
    suspend fun getAllForUser(userId: String): List<CalendarSyncLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: CalendarSyncLinkEntity)

    @Query("DELETE FROM calendar_sync_links WHERE ownerScopeUid = :userId AND deadlineId = :deadlineId")
    suspend fun deleteOne(userId: String, deadlineId: String)

    @Query("DELETE FROM calendar_sync_links WHERE ownerScopeUid = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface CachedSubscriptionDao {
    @Query("SELECT * FROM cached_subscriptions WHERE ownerScopeUid = :userId ORDER BY nextBillingDate ASC, name ASC")
    fun observeForUser(userId: String): Flow<List<CachedSubscriptionEntity>>

    @Query("SELECT * FROM cached_subscriptions WHERE ownerScopeUid = :userId AND frequency = 'monthly' ORDER BY nextBillingDate ASC, name ASC")
    fun observeMonthlyRecurringForUser(userId: String): Flow<List<CachedSubscriptionEntity>>

    @Query("SELECT * FROM cached_subscriptions WHERE ownerScopeUid = :userId ORDER BY nextBillingDate ASC, name ASC")
    suspend fun getAllForUser(userId: String): List<CachedSubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedSubscriptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedSubscriptionEntity>)

    @Query("DELETE FROM cached_subscriptions WHERE ownerScopeUid = :userId AND id = :subId")
    suspend fun deleteOne(userId: String, subId: String)

    @Query("DELETE FROM cached_subscriptions WHERE ownerScopeUid = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Transaction
    suspend fun replaceForUser(userId: String, items: List<CachedSubscriptionEntity>) {
        deleteAllForUser(userId)
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}

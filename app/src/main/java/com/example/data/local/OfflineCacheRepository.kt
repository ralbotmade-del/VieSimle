package com.example.data.local

import android.content.Context
import com.example.model.HouseholdTask
import com.example.model.SubscriptionItem
import com.example.model.VaultDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineCacheRepository(
    private val documentDao: CachedDocumentDao,
    private val taskDao: CachedTaskDao,
    private val calendarLinkDao: CalendarSyncLinkDao,
    private val subscriptionDao: CachedSubscriptionDao
) {
    constructor(context: Context) : this(VieSimpleLocalDatabase.getInstance(context))

    constructor(db: VieSimpleLocalDatabase) : this(
        documentDao = db.cachedDocumentDao(),
        taskDao = db.cachedTaskDao(),
        calendarLinkDao = db.calendarSyncLinkDao(),
        subscriptionDao = db.cachedSubscriptionDao()
    )

    fun observeDocuments(userId: String): Flow<List<VaultDocument>> =
        documentDao.observeForUser(userId).map { entities ->
            entities.map { it.toVaultDocument() }
        }

    suspend fun getDocumentsOnce(userId: String): List<VaultDocument> =
        documentDao.getAllForUser(userId).map { it.toVaultDocument() }

    suspend fun replaceDocuments(userId: String, documents: List<VaultDocument>) {
        val now = System.currentTimeMillis()
        val entities = documents
            .filter { it.id.isNotBlank() }
            .map { it.toCachedEntity(ownerScopeUid = userId, cachedAtMillis = now) }
        documentDao.replaceForUser(userId, entities)
    }

    suspend fun upsertDocument(userId: String, doc: VaultDocument) {
        if (doc.id.isBlank()) return
        documentDao.upsert(doc.toCachedEntity(ownerScopeUid = userId))
    }

    suspend fun deleteDocument(userId: String, docId: String) {
        if (docId.isBlank()) return
        documentDao.deleteOne(userId, docId)
    }

    fun observeTasks(userId: String): Flow<List<HouseholdTask>> =
        taskDao.observeForUser(userId).map { entities ->
            entities.map { it.toHouseholdTask() }
        }

    suspend fun getTasksOnce(userId: String): List<HouseholdTask> =
        taskDao.getAllForUser(userId).map { it.toHouseholdTask() }

    suspend fun replaceTasks(userId: String, tasks: List<HouseholdTask>) {
        val now = System.currentTimeMillis()
        val entities = tasks
            .filter { it.id.isNotBlank() }
            .map { it.toCachedEntity(ownerScopeUid = userId, cachedAtMillis = now) }
        taskDao.replaceForUser(userId, entities)
    }

    suspend fun upsertTask(userId: String, task: HouseholdTask) {
        if (task.id.isBlank()) return
        taskDao.upsert(task.toCachedEntity(ownerScopeUid = userId))
    }

    suspend fun deleteTask(userId: String, taskId: String) {
        if (taskId.isBlank()) return
        taskDao.deleteOne(userId, taskId)
    }

    fun observeCalendarLinks(userId: String): Flow<List<CalendarSyncLinkEntity>> =
        calendarLinkDao.observeForUser(userId)

    suspend fun getCalendarLinksOnce(userId: String): List<CalendarSyncLinkEntity> =
        calendarLinkDao.getAllForUser(userId)

    suspend fun upsertCalendarLink(link: CalendarSyncLinkEntity) {
        calendarLinkDao.upsert(link)
    }

    suspend fun deleteCalendarLink(userId: String, deadlineId: String) {
        calendarLinkDao.deleteOne(userId, deadlineId)
    }

    fun observeSubscriptions(userId: String): Flow<List<SubscriptionItem>> =
        subscriptionDao.observeForUser(userId).map { entities ->
            entities.map { it.toSubscriptionItem() }
        }

    fun observeMonthlyRecurringSubscriptions(userId: String): Flow<List<SubscriptionItem>> =
        subscriptionDao.observeMonthlyRecurringForUser(userId).map { entities ->
            entities.map { it.toSubscriptionItem() }
        }

    suspend fun getSubscriptionsOnce(userId: String): List<SubscriptionItem> =
        subscriptionDao.getAllForUser(userId).map { it.toSubscriptionItem() }

    suspend fun replaceSubscriptions(userId: String, subscriptions: List<SubscriptionItem>) {
        val now = System.currentTimeMillis()
        val entities = subscriptions
            .filter { it.id.isNotBlank() }
            .map { it.toCachedEntity(ownerScopeUid = userId, cachedAtMillis = now) }
        subscriptionDao.replaceForUser(userId, entities)
    }

    suspend fun upsertSubscription(userId: String, sub: SubscriptionItem) {
        if (sub.id.isBlank()) return
        subscriptionDao.upsert(sub.toCachedEntity(ownerScopeUid = userId))
    }

    suspend fun deleteSubscription(userId: String, subId: String) {
        if (subId.isBlank()) return
        subscriptionDao.deleteOne(userId, subId)
    }

    suspend fun clearAllUserCaches(userId: String) {
        documentDao.deleteAllForUser(userId)
        taskDao.deleteAllForUser(userId)
        calendarLinkDao.deleteAllForUser(userId)
        subscriptionDao.deleteAllForUser(userId)
    }
}

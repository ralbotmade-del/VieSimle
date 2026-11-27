package com.example.data

import android.content.Context
import com.example.R
import com.example.core.NetworkConnectivityMonitor
import com.example.core.OperationType
import com.example.core.SecurityPreferences
import com.example.core.handleFirestoreError
import com.example.data.local.OfflineCacheRepository
import com.example.model.AiMessageRecord
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.HouseholdTask
import com.example.model.SecurityLog
import com.example.model.SubscriptionItem
import com.example.model.UserProfile
import com.example.model.VaultDocument
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

data class FirestoreDocumentSearchQuery(
    val keywords: String = "",
    val category: String? = null,
    val dateQuery: String = "",
    val dateFrom: String = "",
    val dateTo: String = "",
    val onlyFavorites: Boolean = false
)

data class FirestoreDocumentSearchResult(
    val query: FirestoreDocumentSearchQuery,
    val matches: List<ScoredSearchMatch<VaultDocument>>,
    val usedServerQuery: Boolean,
    val appliedFirestoreFilters: List<String>,
    val fallbackNote: String? = null
)

class VieSimpleRepository(
    val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val appContext: Context? = null,
    val cacheRepo: OfflineCacheRepository? = appContext?.let { OfflineCacheRepository(it.applicationContext) },
    private val securityPrefs: SecurityPreferences? = appContext?.let { SecurityPreferences(it.applicationContext) }
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        appContext = context.applicationContext
    )

    private fun isDeviceOnline(): Boolean {
        val ctx = appContext ?: return true
        return NetworkConnectivityMonitor.isOnline(ctx)
    }

    suspend fun clearLocalCache(userId: String) {
        cacheRepo?.clearAllUserCaches(userId)
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("L’utilisateur doit être connecté avec Google.")
    }

    private fun handleStreamError(error: Throwable, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
    }

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "user_profiles/$userId"
        emitAll(
            db.collection("user_profiles").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .catch { error ->
                    handleStreamError(error, path, userId)
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(userId: String, displayName: String, email: String): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId)
        val docRef = db.collection("user_profiles").document(uid)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            val profile = UserProfile(
                userId = uid,
                displayName = displayName.ifBlank { "Utilisateur VieSimple" }.take(80),
                email = email.take(160),
                profileType = "solo",
                planTier = "free",
                onboardingCompleted = false,
                activeHouseholdId = ""
            )
            docRef.set(profile.toCreateMap()).await()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "user_profiles/$userId")
        Result.failure(e)
    }

    suspend fun updateProfile(
        displayName: String? = null,
        profileType: String? = null,
        planTier: String? = null,
        onboardingCompleted: Boolean? = null,
        activeHouseholdId: String? = null
    ): Result<Unit> = try {
        val uid = requireUserId()
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        displayName?.let { updates["displayName"] = it.trim().take(80).ifBlank { "Utilisateur" } }
        profileType?.let { updates["profileType"] = it }
        planTier?.let { updates["planTier"] = it }
        onboardingCompleted?.let { updates["onboardingCompleted"] = it }
        activeHouseholdId?.let { updates["activeHouseholdId"] = it.take(128) }
        db.collection("user_profiles").document(uid).update(updates).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "user_profiles/${auth.currentUser?.uid}")
        Result.failure(e)
    }

    fun observeDocuments(userId: String): Flow<List<VaultDocument>> {
        val localCache = cacheRepo ?: return flow {
            val path = "vault_documents"
            emitAll(
                db.collection(path)
                    .whereArrayContains("sharedWithUids", userId)
                    .snapshots()
                    .map { snap -> snap.toObjects(VaultDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .catch { error ->
                        handleStreamError(error, path, userId)
                        throw error
                    }
            )
        }

        return channelFlow {
            val path = "vault_documents"
            val remoteSettled = AtomicBoolean(false)

            val cacheJob = launch {
                localCache.observeDocuments(userId).collect { cached ->
                    if (cached.isNotEmpty() || remoteSettled.get() || !isDeviceOnline()) {
                        send(cached)
                    }
                }
            }

            val remoteJob = launch {
                try {
                    db.collection(path)
                        .whereArrayContains("sharedWithUids", userId)
                        .snapshots()
                        .map { snap -> snap.toObjects(VaultDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                        .collect { remoteDocs ->
                            remoteSettled.set(true)
                            localCache.replaceDocuments(userId, remoteDocs)
                            securityPrefs?.setLastRoomCacheSyncMillis(System.currentTimeMillis())
                            send(remoteDocs)
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleStreamError(e, path, userId)
                    remoteSettled.set(true)
                    send(localCache.getDocumentsOnce(userId))
                }
            }

            awaitClose {
                cacheJob.cancel()
                remoteJob.cancel()
            }
        }
    }

    suspend fun saveDocument(doc: VaultDocument): Result<String> = try {
        val uid = requireUserId()
        val isNew = doc.id.isBlank()
        val docRef = if (isNew) db.collection("vault_documents").document() else db.collection("vault_documents").document(doc.id)
        val safeScopeUids = if (doc.visibility == "household" && doc.householdId.isNotBlank()) {
            (doc.sharedWithUids + uid).distinct().take(10)
        } else {
            listOf(uid)
        }
        val safeVisibility = if (safeScopeUids.size > 1 || (doc.visibility == "household" && doc.householdId.isNotBlank())) "household" else "private"
        val safeHouseholdId = if (safeVisibility == "household") doc.householdId else ""

        if (isNew) {
            val payload = mapOf(
                "userId" to uid,
                "ownerName" to doc.ownerName.ifBlank { auth.currentUser?.displayName ?: "Moi" }.take(80),
                "householdId" to safeHouseholdId,
                "visibility" to safeVisibility,
                "sharedWithUids" to safeScopeUids,
                "title" to doc.title.trim().take(140).ifBlank { "Document sans titre" },
                "category" to doc.category.ifBlank { "Autre" }.take(50),
                "docType" to doc.docType.take(60),
                "folder" to doc.folder.ifBlank { "Général" }.take(60),
                "docDate" to doc.docDate.take(20),
                "expirationDate" to doc.expirationDate.take(20),
                "issuer" to doc.issuer.take(100),
                "referenceNumber" to doc.referenceNumber.take(80),
                "amount" to doc.amount.coerceAtLeast(0.0),
                "notes" to doc.notes.take(2000),
                "tags" to doc.tags.take(10),
                "confidentiality" to doc.confidentiality.ifBlank { "standard" },
                "status" to doc.status.ifBlank { "active" },
                "favorite" to doc.favorite,
                "ocrText" to doc.ocrText.take(15000),
                "ocrConfidence" to doc.ocrConfidence.ifBlank { "manual" },
                "localFileUri" to doc.localFileUri.take(500),
                "versionNumber" to 1L,
                "versionHistory" to listOf("v1 · ${DateUtils.todayNumeric()} · Ajout initial"),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            logSecurityAction("ADD_DOCUMENT", "Document ajouté (${doc.category})")
        } else {
            val updates = mapOf(
                "householdId" to safeHouseholdId,
                "visibility" to safeVisibility,
                "sharedWithUids" to safeScopeUids,
                "title" to doc.title.trim().take(140).ifBlank { "Document sans titre" },
                "category" to doc.category.ifBlank { "Autre" }.take(50),
                "docType" to doc.docType.take(60),
                "folder" to doc.folder.ifBlank { "Général" }.take(60),
                "docDate" to doc.docDate.take(20),
                "expirationDate" to doc.expirationDate.take(20),
                "issuer" to doc.issuer.take(100),
                "referenceNumber" to doc.referenceNumber.take(80),
                "amount" to doc.amount.coerceAtLeast(0.0),
                "notes" to doc.notes.take(2000),
                "tags" to doc.tags.take(10),
                "confidentiality" to doc.confidentiality.ifBlank { "standard" },
                "status" to doc.status.ifBlank { "active" },
                "favorite" to doc.favorite,
                "ocrText" to doc.ocrText.take(15000),
                "ocrConfidence" to doc.ocrConfidence.ifBlank { "manual" },
                "localFileUri" to doc.localFileUri.take(500),
                "versionNumber" to doc.versionNumber.coerceAtLeast(1L),
                "versionHistory" to doc.versionHistory.take(20),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
        }
        cacheRepo?.upsertDocument(
            uid,
            doc.copy(
                id = docRef.id,
                userId = if (isNew) uid else doc.userId.ifBlank { uid },
                householdId = safeHouseholdId,
                visibility = safeVisibility,
                sharedWithUids = safeScopeUids
            )
        )
        Result.success(docRef.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "vault_documents/${doc.id}")
        Result.failure(e)
    }

    suspend fun toggleDocumentFavorite(docId: String, nextFavorite: Boolean): Result<Unit> = try {
        val uid = requireUserId()
        db.collection("vault_documents").document(docId).update(
            mapOf("favorite" to nextFavorite, "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        cacheRepo?.getDocumentsOnce(uid)?.firstOrNull { it.id == docId }?.let { cached ->
            cacheRepo.upsertDocument(uid, cached.copy(favorite = nextFavorite))
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "vault_documents/$docId")
        Result.failure(e)
    }

    suspend fun deleteDocument(docId: String): Result<Unit> = try {
        val uid = requireUserId()
        db.collection("vault_documents").document(docId).delete().await()
        cacheRepo?.deleteDocument(uid, docId)
        logSecurityAction("DELETE_DOCUMENT", "Document supprimé définitivement")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "vault_documents/$docId")
        Result.failure(e)
    }

    suspend fun searchVaultDocumentsFirestore(
        searchQuery: FirestoreDocumentSearchQuery
    ): Result<FirestoreDocumentSearchResult> = try {
        val uid = requireUserId()
        val appliedFilters = mutableListOf("sharedWithUids")
        var firestoreQuery: Query = db.collection("vault_documents")
            .whereArrayContains("sharedWithUids", uid)

        val cleanCategory = searchQuery.category?.trim()?.takeIf { it.isNotBlank() }
        if (cleanCategory != null) {
            firestoreQuery = firestoreQuery.whereEqualTo("category", cleanCategory)
            appliedFilters.add("category")
        }

        val normFrom = normalizeStartDateIso(searchQuery.dateFrom)
        val normTo = normalizeEndDateIso(searchQuery.dateTo)
        if (normFrom.isNotBlank() && normTo.isNotBlank() && normFrom == normTo) {
            firestoreQuery = firestoreQuery.whereEqualTo("docDate", normFrom)
            appliedFilters.add("docDate==")
        } else {
            if (normFrom.isNotBlank()) {
                firestoreQuery = firestoreQuery.whereGreaterThanOrEqualTo("docDate", normFrom)
                appliedFilters.add("docDate>=")
            }
            if (normTo.isNotBlank()) {
                firestoreQuery = firestoreQuery.whereLessThanOrEqualTo("docDate", normTo)
                appliedFilters.add("docDate<=")
            }
        }

        if (searchQuery.onlyFavorites) {
            firestoreQuery = firestoreQuery.whereEqualTo("favorite", true)
            appliedFilters.add("favorite")
        }

        var usedServerQuery = true
        var fallbackNote: String? = null
        val sourceDocs: List<VaultDocument> = try {
            val snap = firestoreQuery.get(Source.DEFAULT).await()
            snap.toObjects(VaultDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION && appliedFilters.size > 1) {
                appliedFilters.clear()
                appliedFilters.add("sharedWithUids")
                fallbackNote = "Requête Firestore élargie (filtre combiné appliqué localement)."
                val baseSnap = db.collection("vault_documents")
                    .whereArrayContains("sharedWithUids", uid)
                    .get(Source.DEFAULT)
                    .await()
                baseSnap.toObjects(VaultDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                handleFirestoreError(e, OperationType.LIST, "vault_documents")
                val cached = cacheRepo?.getDocumentsOnce(uid)
                if (cached != null) {
                    usedServerQuery = false
                    appliedFilters.clear()
                    appliedFilters.add("room_cache")
                    fallbackNote = "Mode hors connexion : résultats issus du cache local."
                    cached
                } else {
                    throw e
                }
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "vault_documents")
            val cached = cacheRepo?.getDocumentsOnce(uid)
            if (cached != null) {
                usedServerQuery = false
                appliedFilters.clear()
                appliedFilters.add("room_cache")
                fallbackNote = "Hors ligne : résultats issus du cache local."
                cached
            } else {
                throw e
            }
        }

        if (usedServerQuery && appliedFilters.size == 1 && sourceDocs.isNotEmpty()) {
            cacheRepo?.replaceDocuments(uid, sourceDocs)
        }

        val matches = sourceDocs.mapNotNull { doc ->
            UniversalSearchEngine.scoreDocumentMatch(
                doc = doc,
                query = searchQuery.keywords,
                selectedCategory = cleanCategory,
                dateQuery = searchQuery.dateQuery,
                dateFrom = searchQuery.dateFrom,
                dateTo = searchQuery.dateTo,
                onlyFavorites = searchQuery.onlyFavorites
            )
        }.sortedWith(
            compareByDescending<ScoredSearchMatch<VaultDocument>> { it.score }
                .thenByDescending { it.item.favorite }
                .thenByDescending { it.item.docDate }
                .thenBy { UniversalSearchEngine.normalize(it.item.title) }
        )

        Result.success(
            FirestoreDocumentSearchResult(
                query = searchQuery,
                matches = matches,
                usedServerQuery = usedServerQuery,
                appliedFirestoreFilters = appliedFilters.toList(),
                fallbackNote = fallbackNote
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, "vault_documents")
        Result.failure(e)
    }

    private fun normalizeStartDateIso(raw: String): String {
        val clean = raw.trim()
        return when {
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(clean) -> clean
            Regex("^\\d{4}-\\d{2}$").matches(clean) -> "$clean-01"
            Regex("^\\d{4}$").matches(clean) -> "$clean-01-01"
            else -> ""
        }
    }

    private fun normalizeEndDateIso(raw: String): String {
        val clean = raw.trim()
        return when {
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(clean) -> clean
            Regex("^\\d{4}-\\d{2}$").matches(clean) -> "$clean-31"
            Regex("^\\d{4}$").matches(clean) -> "$clean-12-31"
            else -> ""
        }
    }

    fun observeDeadlines(userId: String): Flow<List<Deadline>> = flow {
        val path = "deadlines"
        emitAll(
            db.collection(path)
                .whereArrayContains("sharedWithUids", userId)
                .snapshots()
                .map { snap -> snap.toObjects(Deadline::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleStreamError(error, path, userId)
                    throw error
                }
        )
    }

    suspend fun saveDeadline(deadline: Deadline): Result<String> = try {
        val uid = requireUserId()
        val isNew = deadline.id.isBlank()
        val docRef = if (isNew) db.collection("deadlines").document() else db.collection("deadlines").document(deadline.id)
        val safeUids = if (deadline.visibility == "household" && deadline.householdId.isNotBlank()) {
            (deadline.sharedWithUids + uid).distinct().take(10)
        } else {
            listOf(uid)
        }
        val safeVisibility = if (safeUids.size > 1 || (deadline.visibility == "household" && deadline.householdId.isNotBlank())) "household" else "private"
        val safeHouseholdId = if (safeVisibility == "household") deadline.householdId else ""
        val safeDue = deadline.dueDate.ifBlank { DateUtils.todayIso() }.take(20)

        if (isNew) {
            val payload = mapOf(
                "userId" to uid,
                "householdId" to safeHouseholdId,
                "visibility" to safeVisibility,
                "sharedWithUids" to safeUids,
                "title" to deadline.title.trim().take(140).ifBlank { "Échéance" },
                "description" to deadline.description.take(1000),
                "category" to deadline.category.ifBlank { "Administratif" }.take(50),
                "dueDate" to safeDue,
                "recurrence" to deadline.recurrence.ifBlank { "none" },
                "customRecurrenceDays" to deadline.customRecurrenceDays.coerceIn(0L, 3650L),
                "reminders" to deadline.reminders.take(10),
                "status" to deadline.status.ifBlank { "pending" },
                "priority" to deadline.priority.ifBlank { "medium" },
                "linkedDocumentId" to deadline.linkedDocumentId.take(128),
                "linkedTaskId" to deadline.linkedTaskId.take(128),
                "assigneeUid" to deadline.assigneeUid.ifBlank { uid }.take(128),
                "assigneeName" to deadline.assigneeName.take(80),
                "confidentiality" to deadline.confidentiality.ifBlank { "standard" },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } else {
            val updates = mapOf(
                "householdId" to safeHouseholdId,
                "visibility" to safeVisibility,
                "sharedWithUids" to safeUids,
                "title" to deadline.title.trim().take(140).ifBlank { "Échéance" },
                "description" to deadline.description.take(1000),
                "category" to deadline.category.ifBlank { "Administratif" }.take(50),
                "dueDate" to safeDue,
                "recurrence" to deadline.recurrence.ifBlank { "none" },
                "customRecurrenceDays" to deadline.customRecurrenceDays.coerceIn(0L, 3650L),
                "reminders" to deadline.reminders.take(10),
                "status" to deadline.status.ifBlank { "pending" },
                "priority" to deadline.priority.ifBlank { "medium" },
                "linkedDocumentId" to deadline.linkedDocumentId.take(128),
                "linkedTaskId" to deadline.linkedTaskId.take(128),
                "assigneeUid" to deadline.assigneeUid.ifBlank { uid }.take(128),
                "assigneeName" to deadline.assigneeName.take(80),
                "confidentiality" to deadline.confidentiality.ifBlank { "standard" },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
        }
        Result.success(docRef.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "deadlines/${deadline.id}")
        Result.failure(e)
    }

    suspend fun markDeadlineDone(deadline: Deadline): Result<Unit> = try {
        requireUserId()
        db.collection("deadlines").document(deadline.id).update(
            mapOf("status" to "done", "updatedAt" to FieldValue.serverTimestamp())
        ).await()

        // Automatically generate next recurring occurrence when validated
        if (deadline.recurrence != "none") {
            val nextDate = DateUtils.computeNextOccurrence(
                deadline.dueDate,
                deadline.recurrence,
                deadline.customRecurrenceDays
            )
            val nextDeadline = deadline.copy(
                id = "",
                dueDate = nextDate,
                status = "pending"
            )
            saveDeadline(nextDeadline)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "deadlines/${deadline.id}")
        Result.failure(e)
    }

    suspend fun postponeDeadline(deadline: Deadline, days: Int = 7): Result<Unit> = try {
        requireUserId()
        val nextDate = DateUtils.addDaysIso(deadline.dueDate, days)
        db.collection("deadlines").document(deadline.id).update(
            mapOf(
                "dueDate" to nextDate,
                "status" to "pending",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "deadlines/${deadline.id}")
        Result.failure(e)
    }

    suspend fun deleteDeadline(deadlineId: String): Result<Unit> = try {
        requireUserId()
        db.collection("deadlines").document(deadlineId).delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "deadlines/$deadlineId")
        Result.failure(e)
    }

    fun observeSubscriptions(userId: String): Flow<List<SubscriptionItem>> {
        val localCache = cacheRepo ?: return flow {
            val path = "subscriptions"
            emitAll(
                db.collection(path)
                    .whereArrayContains("sharedWithUids", userId)
                    .snapshots()
                    .map { snap -> snap.toObjects(SubscriptionItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .catch { error ->
                        handleStreamError(error, path, userId)
                        throw error
                    }
            )
        }

        return channelFlow {
            val path = "subscriptions"
            val remoteSettled = AtomicBoolean(false)

            val cacheJob = launch {
                localCache.observeSubscriptions(userId).collect { cached ->
                    if (cached.isNotEmpty() || remoteSettled.get() || !isDeviceOnline()) {
                        send(cached)
                    }
                }
            }

            val remoteJob = launch {
                try {
                    db.collection(path)
                        .whereArrayContains("sharedWithUids", userId)
                        .snapshots()
                        .map { snap -> snap.toObjects(SubscriptionItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                        .collect { remoteSubs ->
                            remoteSettled.set(true)
                            localCache.replaceSubscriptions(userId, remoteSubs)
                            securityPrefs?.setLastRoomCacheSyncMillis(System.currentTimeMillis())
                            send(remoteSubs)
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleStreamError(e, path, userId)
                    remoteSettled.set(true)
                    send(localCache.getSubscriptionsOnce(userId))
                }
            }

            awaitClose {
                cacheJob.cancel()
                remoteJob.cancel()
            }
        }
    }

    suspend fun saveSubscription(sub: SubscriptionItem): Result<String> = try {
        val uid = requireUserId()
        val isNew = sub.id.isBlank()
        val docRef = if (isNew) db.collection("subscriptions").document() else db.collection("subscriptions").document(sub.id)
        val safeUids = if (sub.visibility == "household" && sub.householdId.isNotBlank()) {
            (sub.sharedWithUids + uid).distinct().take(10)
        } else {
            listOf(uid)
        }
        val safeVisibility = if (safeUids.size > 1 || (sub.visibility == "household" && sub.householdId.isNotBlank())) "household" else "private"
        val safeHouseholdId = if (safeVisibility == "household") sub.householdId else ""
        val cleanName = sub.name.trim().take(100).ifBlank { "Abonnement" }
        val cleanCategory = sub.category.ifBlank { "Abonnements" }.take(50)
        val cleanAmount = sub.amount.coerceAtLeast(0.0)
        val cleanCurrency = sub.currency.ifBlank { "EUR" }
        val cleanFrequency = sub.frequency.ifBlank { "monthly" }
        val cleanNextBilling = sub.nextBillingDate.ifBlank { DateUtils.todayIso() }.take(20)
        val cleanPaymentMethod = sub.paymentMethod.take(60)
        val cleanStatus = sub.status.ifBlank { "active" }
        val cleanCancelStatus = sub.cancellationStatus.ifBlank { "none" }
        val cleanNotes = sub.notes.take(1000)
        val cleanLinkedDocId = sub.linkedDocumentId.take(128)
        val cleanAlertDays = sub.alertDaysBefore.coerceIn(0L, 60L)

        if (isNew) {
            val payload = mapOf(
                "userId" to uid,
                "householdId" to safeHouseholdId,
                "visibility" to safeVisibility,
                "sharedWithUids" to safeUids,
                "name" to cleanName,
                "category" to cleanCategory,
                "amount" to cleanAmount,
                "currency" to cleanCurrency,
                "frequency" to cleanFrequency,
                "nextBillingDate" to cleanNextBilling,
                "paymentMethod" to cleanPaymentMethod,
                "status" to cleanStatus,
                "cancellationStatus" to cleanCancelStatus,
                "notes" to cleanNotes,
                "linkedDocumentId" to cleanLinkedDocId,
                "alertDaysBefore" to cleanAlertDays,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } else {
            val updates = mapOf(
                "householdId" to safeHouseholdId,
                "visibility" to safeVisibility,
                "sharedWithUids" to safeUids,
                "name" to cleanName,
                "category" to cleanCategory,
                "amount" to cleanAmount,
                "currency" to cleanCurrency,
                "frequency" to cleanFrequency,
                "nextBillingDate" to cleanNextBilling,
                "paymentMethod" to cleanPaymentMethod,
                "status" to cleanStatus,
                "cancellationStatus" to cleanCancelStatus,
                "notes" to cleanNotes,
                "linkedDocumentId" to cleanLinkedDocId,
                "alertDaysBefore" to cleanAlertDays,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
        }
        cacheRepo?.upsertSubscription(
            uid,
            sub.copy(
                id = docRef.id,
                userId = if (isNew) uid else sub.userId.ifBlank { uid },
                householdId = safeHouseholdId,
                visibility = safeVisibility,
                sharedWithUids = safeUids,
                name = cleanName,
                category = cleanCategory,
                amount = cleanAmount,
                currency = cleanCurrency,
                frequency = cleanFrequency,
                nextBillingDate = cleanNextBilling,
                paymentMethod = cleanPaymentMethod,
                status = cleanStatus,
                cancellationStatus = cleanCancelStatus,
                notes = cleanNotes,
                linkedDocumentId = cleanLinkedDocId,
                alertDaysBefore = cleanAlertDays
            )
        )
        Result.success(docRef.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "subscriptions/${sub.id}")
        Result.failure(e)
    }

    suspend fun deleteSubscription(subId: String): Result<Unit> = try {
        val uid = requireUserId()
        db.collection("subscriptions").document(subId).delete().await()
        cacheRepo?.deleteSubscription(uid, subId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "subscriptions/$subId")
        Result.failure(e)
    }

    fun observeTasks(userId: String): Flow<List<HouseholdTask>> {
        val localCache = cacheRepo ?: return flow {
            val path = "tasks"
            emitAll(
                db.collection(path)
                    .whereArrayContains("sharedWithUids", userId)
                    .snapshots()
                    .map { snap -> snap.toObjects(HouseholdTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .catch { error ->
                        handleStreamError(error, path, userId)
                        throw error
                    }
            )
        }

        return channelFlow {
            val path = "tasks"
            val remoteSettled = AtomicBoolean(false)

            val cacheJob = launch {
                localCache.observeTasks(userId).collect { cached ->
                    if (cached.isNotEmpty() || remoteSettled.get() || !isDeviceOnline()) {
                        send(cached)
                    }
                }
            }

            val remoteJob = launch {
                try {
                    db.collection(path)
                        .whereArrayContains("sharedWithUids", userId)
                        .snapshots()
                        .map { snap -> snap.toObjects(HouseholdTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                        .collect { remoteTasks ->
                            remoteSettled.set(true)
                            localCache.replaceTasks(userId, remoteTasks)
                            securityPrefs?.setLastRoomCacheSyncMillis(System.currentTimeMillis())
                            send(remoteTasks)
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleStreamError(e, path, userId)
                    remoteSettled.set(true)
                    send(localCache.getTasksOnce(userId))
                }
            }

            awaitClose {
                cacheJob.cancel()
                remoteJob.cancel()
            }
        }
    }

    suspend fun saveTask(task: HouseholdTask): Result<String> = try {
        val uid = requireUserId()
        val isNew = task.id.isBlank()
        val docRef = if (isNew) db.collection("tasks").document() else db.collection("tasks").document(task.id)
        val safeUids = if (task.visibility == "household" && task.householdId.isNotBlank()) {
            (task.sharedWithUids + uid).distinct().take(10)
        } else {
            listOf(uid)
        }
        val safeVisibility = if (safeUids.size > 1 || (task.visibility == "household" && task.householdId.isNotBlank())) "household" else "private"
        val safeHouseholdId = if (safeVisibility == "household") task.householdId else ""

        if (isNew) {
            val payload = mapOf(
                "userId" to uid,
                "householdId" to safeHouseholdId,
                "visibility" to safeVisibility,
                "sharedWithUids" to safeUids,
                "title" to task.title.trim().take(140).ifBlank { "Tâche" },
                "description" to task.description.take(1000),
                "category" to task.category.ifBlank { "Famille" }.take(50),
                "dueDate" to task.dueDate.take(20),
                "priority" to task.priority.ifBlank { "medium" },
                "assigneeUid" to task.assigneeUid.ifBlank { uid }.take(128),
                "assigneeName" to task.assigneeName.take(80),
                "status" to task.status.ifBlank { "todo" },
                "subtasks" to task.subtasks.take(30),
                "checklistTemplate" to task.checklistTemplate.take(60),
                "linkedDocumentId" to task.linkedDocumentId.take(128),
                "linkedDeadlineId" to task.linkedDeadlineId.take(128),
                "confidentiality" to task.confidentiality.ifBlank { "standard" },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } else {
            val updates = mapOf(
                "householdId" to safeHouseholdId,
                "visibility" to safeVisibility,
                "sharedWithUids" to safeUids,
                "title" to task.title.trim().take(140).ifBlank { "Tâche" },
                "description" to task.description.take(1000),
                "category" to task.category.ifBlank { "Famille" }.take(50),
                "dueDate" to task.dueDate.take(20),
                "priority" to task.priority.ifBlank { "medium" },
                "assigneeUid" to task.assigneeUid.ifBlank { uid }.take(128),
                "assigneeName" to task.assigneeName.take(80),
                "status" to task.status.ifBlank { "todo" },
                "subtasks" to task.subtasks.take(30),
                "checklistTemplate" to task.checklistTemplate.take(60),
                "linkedDocumentId" to task.linkedDocumentId.take(128),
                "linkedDeadlineId" to task.linkedDeadlineId.take(128),
                "confidentiality" to task.confidentiality.ifBlank { "standard" },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
        }
        cacheRepo?.upsertTask(
            uid,
            task.copy(
                id = docRef.id,
                userId = if (isNew) uid else task.userId.ifBlank { uid },
                householdId = safeHouseholdId,
                visibility = safeVisibility,
                sharedWithUids = safeUids
            )
        )
        Result.success(docRef.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "tasks/${task.id}")
        Result.failure(e)
    }

    suspend fun toggleTaskDone(task: HouseholdTask, done: Boolean): Result<Unit> = try {
        val uid = requireUserId()
        val nextStatus = if (done) "done" else "todo"
        db.collection("tasks").document(task.id).update(
            mapOf(
                "status" to nextStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        cacheRepo?.upsertTask(uid, task.copy(status = nextStatus))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "tasks/${task.id}")
        Result.failure(e)
    }

    suspend fun updateTaskSubtasks(taskId: String, subtasks: List<String>): Result<Unit> = try {
        val uid = requireUserId()
        val bounded = subtasks.take(30)
        db.collection("tasks").document(taskId).update(
            mapOf(
                "subtasks" to bounded,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        cacheRepo?.getTasksOnce(uid)?.firstOrNull { it.id == taskId }?.let { cached ->
            cacheRepo.upsertTask(uid, cached.copy(subtasks = bounded))
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "tasks/$taskId")
        Result.failure(e)
    }

    suspend fun postponeTask(task: HouseholdTask, days: Int = 3): Result<Unit> = try {
        val uid = requireUserId()
        val base = task.dueDate.ifBlank { DateUtils.todayIso() }
        val nextDue = DateUtils.addDaysIso(base, days)
        db.collection("tasks").document(task.id).update(
            mapOf(
                "dueDate" to nextDue,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        cacheRepo?.upsertTask(uid, task.copy(dueDate = nextDue))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "tasks/${task.id}")
        Result.failure(e)
    }

    suspend fun deleteTask(taskId: String): Result<Unit> = try {
        val uid = requireUserId()
        db.collection("tasks").document(taskId).delete().await()
        cacheRepo?.deleteTask(uid, taskId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "tasks/$taskId")
        Result.failure(e)
    }

    fun observeSecurityLogs(userId: String): Flow<List<SecurityLog>> = flow {
        val path = "users/$userId/security_logs"
        emitAll(
            db.collection("users").document(userId).collection("security_logs")
                .snapshots()
                .map { snap -> snap.toObjects(SecurityLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleStreamError(error, path, userId)
                    throw error
                }
        )
    }

    suspend fun logSecurityAction(action: String, detail: String): Result<Unit> = try {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("security_logs").document().set(
            mapOf(
                "userId" to uid,
                "action" to action.take(80),
                "detail" to detail.take(240),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/security_logs")
        Result.failure(e)
    }

    fun observeAiMessages(userId: String): Flow<List<AiMessageRecord>> = flow {
        val path = "users/$userId/ai_conversations"
        emitAll(
            db.collection("users").document(userId).collection("ai_conversations")
                .snapshots()
                .map { snap -> snap.toObjects(AiMessageRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleStreamError(error, path, userId)
                    throw error
                }
        )
    }

    suspend fun saveAiMessage(sender: String, text: String, proposedAction: String = ""): Result<Unit> = try {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("ai_conversations").document().set(
            mapOf(
                "userId" to uid,
                "sender" to sender,
                "text" to text.take(8000).ifBlank { "..." },
                "proposedAction" to proposedAction.take(240),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/ai_conversations")
        Result.failure(e)
    }

    suspend fun clearAiHistory(userId: String): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId)
        val snap = db.collection("users").document(uid).collection("ai_conversations").get().await()
        for (doc in snap.documents) {
            doc.reference.delete().await()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "users/$userId/ai_conversations")
        Result.failure(e)
    }

    suspend fun upsertFcmDeviceToken(
        deviceId: String,
        token: String,
        notifDeadlines: Boolean,
        notifTasks: Boolean,
        notifQuietMode: Boolean,
        notifHour: Int,
        upcomingDeadlinesCount: Int,
        overdueTasksCount: Int,
        nextDeadlineDate: String
    ): Result<Unit> = try {
        val uid = requireUserId()
        val safeDeviceId = deviceId.replace(Regex("[^a-zA-Z0-9_-]"), "").take(128).ifBlank { "android_default" }
        val safeToken = token.trim().take(512)
        require(safeToken.length >= 10) { "Jeton FCM invalide." }
        val docRef = db.collection("users").document(uid).collection("fcm_tokens").document(safeDeviceId)
        val existingSnap = docRef.get().await()
        val safeHour = notifHour.coerceIn(0, 23).toLong()
        val safeDeadlinesCount = upcomingDeadlinesCount.coerceIn(0, 500).toLong()
        val safeOverdueTasksCount = overdueTasksCount.coerceIn(0, 500).toLong()
        val safeNextDate = nextDeadlineDate.take(20)

        if (!existingSnap.exists()) {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "token" to safeToken,
                    "platform" to "android",
                    "privacyMode" to "strict_no_pii",
                    "notifDeadlines" to notifDeadlines,
                    "notifTasks" to notifTasks,
                    "notifQuietMode" to notifQuietMode,
                    "notifHour" to safeHour,
                    "upcomingDeadlinesCount" to safeDeadlinesCount,
                    "overdueTasksCount" to safeOverdueTasksCount,
                    "nextDeadlineDate" to safeNextDate,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "token" to safeToken,
                    "platform" to "android",
                    "privacyMode" to "strict_no_pii",
                    "notifDeadlines" to notifDeadlines,
                    "notifTasks" to notifTasks,
                    "notifQuietMode" to notifQuietMode,
                    "notifHour" to safeHour,
                    "upcomingDeadlinesCount" to safeDeadlinesCount,
                    "overdueTasksCount" to safeOverdueTasksCount,
                    "nextDeadlineDate" to safeNextDate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/fcm_tokens/$deviceId")
        Result.failure(e)
    }
}

package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.HouseholdTask
import com.example.model.SubscriptionItem
import com.example.model.VaultDocument
import com.google.firebase.Timestamp
import java.util.Date

@Entity(
    tableName = "cached_documents",
    indices = [Index(value = ["ownerScopeUid", "id"])]
)
data class CachedDocumentEntity(
    @PrimaryKey val cacheKey: String,
    val ownerScopeUid: String,
    val id: String,
    val userId: String,
    val ownerName: String,
    val householdId: String,
    val visibility: String,
    val sharedWithUidsJson: String,
    val title: String,
    val category: String,
    val docType: String,
    val folder: String,
    val docDate: String,
    val expirationDate: String,
    val issuer: String,
    val referenceNumber: String,
    val amount: Double,
    val notes: String,
    val tagsJson: String,
    val confidentiality: String,
    val status: String,
    val favorite: Boolean,
    val ocrText: String,
    val ocrConfidence: String,
    val localFileUri: String,
    val versionNumber: Long,
    val versionHistoryJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val cachedAtMillis: Long
) {
    fun toVaultDocument(): VaultDocument = VaultDocument(
        id = id,
        userId = userId,
        ownerName = ownerName,
        householdId = householdId,
        visibility = visibility,
        sharedWithUids = decodeStringList(sharedWithUidsJson),
        title = title,
        category = category,
        docType = docType,
        folder = folder,
        docDate = docDate,
        expirationDate = expirationDate,
        issuer = issuer,
        referenceNumber = referenceNumber,
        amount = amount,
        notes = notes,
        tags = decodeStringList(tagsJson),
        confidentiality = confidentiality,
        status = status,
        favorite = favorite,
        ocrText = ocrText,
        ocrConfidence = ocrConfidence,
        localFileUri = localFileUri,
        versionNumber = versionNumber,
        versionHistory = decodeStringList(versionHistoryJson),
        createdAt = createdAtMillis.takeIf { it > 0L }?.let { Timestamp(Date(it)) },
        updatedAt = updatedAtMillis.takeIf { it > 0L }?.let { Timestamp(Date(it)) }
    )
}

@Entity(
    tableName = "cached_tasks",
    indices = [Index(value = ["ownerScopeUid", "id"])]
)
data class CachedTaskEntity(
    @PrimaryKey val cacheKey: String,
    val ownerScopeUid: String,
    val id: String,
    val userId: String,
    val householdId: String,
    val visibility: String,
    val sharedWithUidsJson: String,
    val title: String,
    val description: String,
    val category: String,
    val dueDate: String,
    val priority: String,
    val assigneeUid: String,
    val assigneeName: String,
    val status: String,
    val subtasksJson: String,
    val checklistTemplate: String,
    val linkedDocumentId: String,
    val linkedDeadlineId: String,
    val confidentiality: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val cachedAtMillis: Long
) {
    fun toHouseholdTask(): HouseholdTask = HouseholdTask(
        id = id,
        userId = userId,
        householdId = householdId,
        visibility = visibility,
        sharedWithUids = decodeStringList(sharedWithUidsJson),
        title = title,
        description = description,
        category = category,
        dueDate = dueDate,
        priority = priority,
        assigneeUid = assigneeUid,
        assigneeName = assigneeName,
        status = status,
        subtasks = decodeStringList(subtasksJson),
        checklistTemplate = checklistTemplate,
        linkedDocumentId = linkedDocumentId,
        linkedDeadlineId = linkedDeadlineId,
        confidentiality = confidentiality,
        createdAt = createdAtMillis.takeIf { it > 0L }?.let { Timestamp(Date(it)) },
        updatedAt = updatedAtMillis.takeIf { it > 0L }?.let { Timestamp(Date(it)) }
    )
}

@Entity(
    tableName = "calendar_sync_links",
    indices = [Index(value = ["ownerScopeUid", "deadlineId"])]
)
data class CalendarSyncLinkEntity(
    @PrimaryKey val linkKey: String,
    val ownerScopeUid: String,
    val deadlineId: String,
    val calendarEventId: Long,
    val calendarId: Long,
    val syncedTitle: String,
    val syncedDueDate: String,
    val syncedRemindersCsv: String,
    val syncedHour: Int,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "cached_subscriptions",
    indices = [Index(value = ["ownerScopeUid", "id"])]
)
data class CachedSubscriptionEntity(
    @PrimaryKey val cacheKey: String,
    val ownerScopeUid: String,
    val id: String,
    val userId: String,
    val householdId: String,
    val visibility: String,
    val sharedWithUidsJson: String,
    val name: String,
    val category: String,
    val amount: Double,
    val currency: String,
    val frequency: String,
    val nextBillingDate: String,
    val paymentMethod: String,
    val status: String,
    val cancellationStatus: String,
    val notes: String,
    val linkedDocumentId: String,
    val alertDaysBefore: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val cachedAtMillis: Long
) {
    fun toSubscriptionItem(): SubscriptionItem = SubscriptionItem(
        id = id,
        userId = userId,
        householdId = householdId,
        visibility = visibility,
        sharedWithUids = decodeStringList(sharedWithUidsJson),
        name = name,
        category = category,
        amount = amount,
        currency = currency,
        frequency = frequency,
        nextBillingDate = nextBillingDate,
        paymentMethod = paymentMethod,
        status = status,
        cancellationStatus = cancellationStatus,
        notes = notes,
        linkedDocumentId = linkedDocumentId,
        alertDaysBefore = alertDaysBefore,
        createdAt = createdAtMillis.takeIf { it > 0L }?.let { Timestamp(Date(it)) },
        updatedAt = updatedAtMillis.takeIf { it > 0L }?.let { Timestamp(Date(it)) }
    )
}

fun VaultDocument.toCachedEntity(
    ownerScopeUid: String,
    cachedAtMillis: Long = System.currentTimeMillis()
): CachedDocumentEntity = CachedDocumentEntity(
    cacheKey = "${ownerScopeUid}_${id}",
    ownerScopeUid = ownerScopeUid,
    id = id,
    userId = userId,
    ownerName = ownerName,
    householdId = householdId,
    visibility = visibility,
    sharedWithUidsJson = encodeStringList(sharedWithUids),
    title = title,
    category = category,
    docType = docType,
    folder = folder,
    docDate = docDate,
    expirationDate = expirationDate,
    issuer = issuer,
    referenceNumber = referenceNumber,
    amount = amount,
    notes = notes,
    tagsJson = encodeStringList(tags),
    confidentiality = confidentiality,
    status = status,
    favorite = favorite,
    ocrText = ocrText,
    ocrConfidence = ocrConfidence,
    localFileUri = localFileUri,
    versionNumber = versionNumber,
    versionHistoryJson = encodeStringList(versionHistory),
    createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    updatedAtMillis = updatedAt?.toDate()?.time ?: cachedAtMillis,
    cachedAtMillis = cachedAtMillis
)

fun HouseholdTask.toCachedEntity(
    ownerScopeUid: String,
    cachedAtMillis: Long = System.currentTimeMillis()
): CachedTaskEntity = CachedTaskEntity(
    cacheKey = "${ownerScopeUid}_${id}",
    ownerScopeUid = ownerScopeUid,
    id = id,
    userId = userId,
    householdId = householdId,
    visibility = visibility,
    sharedWithUidsJson = encodeStringList(sharedWithUids),
    title = title,
    description = description,
    category = category,
    dueDate = dueDate,
    priority = priority,
    assigneeUid = assigneeUid,
    assigneeName = assigneeName,
    status = status,
    subtasksJson = encodeStringList(subtasks),
    checklistTemplate = checklistTemplate,
    linkedDocumentId = linkedDocumentId,
    linkedDeadlineId = linkedDeadlineId,
    confidentiality = confidentiality,
    createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    updatedAtMillis = updatedAt?.toDate()?.time ?: cachedAtMillis,
    cachedAtMillis = cachedAtMillis
)

fun SubscriptionItem.toCachedEntity(
    ownerScopeUid: String,
    cachedAtMillis: Long = System.currentTimeMillis()
): CachedSubscriptionEntity = CachedSubscriptionEntity(
    cacheKey = "${ownerScopeUid}_${id}",
    ownerScopeUid = ownerScopeUid,
    id = id,
    userId = userId,
    householdId = householdId,
    visibility = visibility,
    sharedWithUidsJson = encodeStringList(sharedWithUids),
    name = name,
    category = category,
    amount = amount,
    currency = currency,
    frequency = frequency,
    nextBillingDate = nextBillingDate,
    paymentMethod = paymentMethod,
    status = status,
    cancellationStatus = cancellationStatus,
    notes = notes,
    linkedDocumentId = linkedDocumentId,
    alertDaysBefore = alertDaysBefore,
    createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    updatedAtMillis = updatedAt?.toDate()?.time ?: cachedAtMillis,
    cachedAtMillis = cachedAtMillis
)

internal fun encodeStringList(list: List<String>): String {
    if (list.isEmpty()) return "[]"
    return buildString {
        append('[')
        list.forEachIndexed { index, item ->
            if (index > 0) append(',')
            append('"')
            for (ch in item) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
        append(']')
    }
}

internal fun decodeStringList(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed == "[]") return emptyList()
    if (!trimmed.startsWith('[') || !trimmed.endsWith(']')) return emptyList()
    val result = mutableListOf<String>()
    var i = 1
    val len = trimmed.length - 1
    while (i < len) {
        while (i < len && (trimmed[i].isWhitespace() || trimmed[i] == ',')) i++
        if (i >= len) break
        if (trimmed[i] != '"') {
            i++
            continue
        }
        i++ // skip opening quote
        val sb = StringBuilder()
        var closed = false
        while (i < len) {
            val ch = trimmed[i++]
            when (ch) {
                '\\' -> {
                    if (i < len) {
                        when (val esc = trimmed[i++]) {
                            '\\' -> sb.append('\\')
                            '"' -> sb.append('"')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            else -> sb.append(esc)
                        }
                    }
                }
                '"' -> {
                    closed = true
                    break
                }
                else -> sb.append(ch)
            }
        }
        if (closed && sb.isNotEmpty()) {
            result.add(sb.toString())
        }
    }
    return result
}

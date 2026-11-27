package com.example.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.SecurityPreferences
import com.example.core.UserSecuritySettings
import com.example.data.local.CalendarSyncLinkEntity
import com.example.data.local.OfflineCacheRepository
import com.example.model.DateUtils
import com.example.model.Deadline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

data class SystemCalendarSyncResult(
    val success: Boolean,
    val syncedCount: Int = 0,
    val created: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val summary: String
)

object SystemCalendarSyncManager {

    private const val TAG = "SystemCalendarSync"

    fun hasCalendarPermissions(context: Context): Boolean {
        val readOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readOk && writeOk
    }

    @SuppressLint("MissingPermission")
    fun findPrimaryWritableCalendarId(context: Context): Long? {
        if (!hasCalendarPermissions(context)) return null
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val sortOrder = "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.VISIBLE} DESC, ${CalendarContract.Calendars._ID} ASC"

        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    suspend fun syncDeadlinesToSystemCalendar(
        context: Context,
        userId: String,
        deadlines: List<Deadline>,
        settings: UserSecuritySettings,
        cacheRepo: OfflineCacheRepository,
        securityPrefs: SecurityPreferences
    ): SystemCalendarSyncResult = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext SystemCalendarSyncResult(
                success = false,
                summary = "Connectez-vous pour synchroniser le calendrier."
            )
        }
        if (!hasCalendarPermissions(context)) {
            val msg = "Autorisation Calendrier requise pour la synchronisation native."
            securityPrefs.setCalendarSyncStatus(msg)
            return@withContext SystemCalendarSyncResult(success = false, summary = msg)
        }

        val calendarId = findPrimaryWritableCalendarId(context)
        if (calendarId == null) {
            val msg = "Aucun agenda local modifiable trouvé sur cet appareil."
            securityPrefs.setCalendarSyncStatus(msg)
            return@withContext SystemCalendarSyncResult(success = false, summary = msg)
        }

        val resolver = context.contentResolver
        val existingLinks = cacheRepo.getCalendarLinksOnce(userId).associateBy { it.deadlineId }
        val activeDeadlineIds = deadlines.map { it.id }.filter { it.isNotBlank() }.toSet()
        val syncHour = settings.notifHour.coerceIn(7, 20)
        val syncMinute = settings.notifMinute.coerceIn(0, 59)

        var created = 0
        var updated = 0
        var deleted = 0
        var activeSyncedCount = 0

        deadlines.forEach { dl ->
            if (dl.id.isBlank()) return@forEach
            val existingLink = existingLinks[dl.id]
            val parsedDate = DateUtils.parseIso(dl.dueDate)

            if (dl.status != "pending" || parsedDate == null) {
                if (existingLink != null) {
                    deleteSystemEventSafe(resolver, existingLink.calendarEventId)
                    cacheRepo.deleteCalendarLink(userId, dl.id)
                    deleted++
                }
                return@forEach
            }

            val eventTitle = buildEventTitle(dl)
            val eventDescription = buildEventDescription(dl)
            val reminderMinutes = computeReminderMinutes(dl.reminders)
            val remindersCsv = reminderMinutes.joinToString(",")

            val startMillis = computeEventStartMillis(parsedDate, syncHour, syncMinute)
            val endMillis = startMillis + 30L * 60_000L

            if (existingLink != null && systemEventExists(resolver, existingLink.calendarEventId)) {
                activeSyncedCount++
                val needsUpdate = existingLink.syncedTitle != eventTitle ||
                    existingLink.syncedDueDate != dl.dueDate ||
                    existingLink.syncedRemindersCsv != remindersCsv ||
                    existingLink.syncedHour != syncHour ||
                    existingLink.calendarId != calendarId

                if (needsUpdate) {
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.TITLE, eventTitle)
                        put(CalendarContract.Events.DESCRIPTION, eventDescription)
                        put(CalendarContract.Events.DTSTART, startMillis)
                        put(CalendarContract.Events.DTEND, endMillis)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                        put(CalendarContract.Events.HAS_ALARM, 1)
                    }
                    val eventUri = ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_URI,
                        existingLink.calendarEventId
                    )
                    resolver.update(eventUri, values, null, null)
                    replaceEventReminders(resolver, existingLink.calendarEventId, reminderMinutes)
                    cacheRepo.upsertCalendarLink(
                        existingLink.copy(
                            calendarId = calendarId,
                            syncedTitle = eventTitle,
                            syncedDueDate = dl.dueDate,
                            syncedRemindersCsv = remindersCsv,
                            syncedHour = syncHour,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                    updated++
                }
            } else {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, eventTitle)
                    put(CalendarContract.Events.DESCRIPTION, eventDescription)
                    put(CalendarContract.Events.DTSTART, startMillis)
                    put(CalendarContract.Events.DTEND, endMillis)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    put(CalendarContract.Events.HAS_ALARM, 1)
                    put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
                }
                val insertedUri = runCatching {
                    resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                }.onFailure {
                    Log.w(TAG, "Failed inserting calendar event for ${dl.id}", it)
                }.getOrNull()

                val newEventId = insertedUri?.lastPathSegment?.toLongOrNull()
                if (newEventId != null && newEventId > 0L) {
                    replaceEventReminders(resolver, newEventId, reminderMinutes)
                    cacheRepo.upsertCalendarLink(
                        CalendarSyncLinkEntity(
                            linkKey = "${userId}_${dl.id}",
                            ownerScopeUid = userId,
                            deadlineId = dl.id,
                            calendarEventId = newEventId,
                            calendarId = calendarId,
                            syncedTitle = eventTitle,
                            syncedDueDate = dl.dueDate,
                            syncedRemindersCsv = remindersCsv,
                            syncedHour = syncHour,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                    created++
                    activeSyncedCount++
                }
            }
        }

        // Remove orphaned events deleted in Firestore
        existingLinks.values.forEach { link ->
            if (link.deadlineId !in activeDeadlineIds) {
                deleteSystemEventSafe(resolver, link.calendarEventId)
                cacheRepo.deleteCalendarLink(userId, link.deadlineId)
                deleted++
            }
        }

        val summary = buildString {
            append("$activeSyncedCount échéance(s) dans l’agenda système")
            val details = listOfNotNull(
                created.takeIf { it > 0 }?.let { "+$it ajoutée(s)" },
                updated.takeIf { it > 0 }?.let { "$it mise(s) à jour" },
                deleted.takeIf { it > 0 }?.let { "$it retirée(s)" }
            )
            if (details.isNotEmpty()) {
                append(" (${details.joinToString(", ")})")
            }
        }
        securityPrefs.setCalendarSyncStatus(summary)
        SystemCalendarSyncResult(
            success = true,
            syncedCount = activeSyncedCount,
            created = created,
            updated = updated,
            deleted = deleted,
            summary = summary
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun clearAllSyncedEvents(
        context: Context,
        userId: String,
        cacheRepo: OfflineCacheRepository,
        securityPrefs: SecurityPreferences
    ) = withContext(Dispatchers.IO) {
        val links = cacheRepo.getCalendarLinksOnce(userId)
        if (hasCalendarPermissions(context)) {
            val resolver = context.contentResolver
            links.forEach { link ->
                deleteSystemEventSafe(resolver, link.calendarEventId)
            }
        }
        links.forEach { link ->
            cacheRepo.deleteCalendarLink(userId, link.deadlineId)
        }
        securityPrefs.setCalendarSyncStatus("Synchronisation calendrier désactivée")
    }

    fun buildOpenInCalendarIntent(
        deadline: Deadline,
        linkedEventId: Long?,
        syncHour: Int = 9,
        syncMinute: Int = 0
    ): Intent {
        if (linkedEventId != null && linkedEventId > 0L) {
            val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, linkedEventId)
            return Intent(Intent.ACTION_VIEW).setData(eventUri)
        }
        val parsed = DateUtils.parseIso(deadline.dueDate)
        val startMillis = parsed?.let { computeEventStartMillis(it, syncHour.coerceIn(7, 20), syncMinute.coerceIn(0, 59)) }
            ?: System.currentTimeMillis()
        return Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, buildEventTitle(deadline))
            .putExtra(CalendarContract.Events.DESCRIPTION, buildEventDescription(deadline))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 30L * 60_000L)
    }

    private fun buildEventTitle(deadline: Deadline): String {
        return if (deadline.confidentiality == "sensitive") {
            "VieSimple · Échéance (${deadline.category})"
        } else {
            "VieSimple · ${deadline.title}"
        }
    }

    private fun buildEventDescription(deadline: Deadline): String {
        return if (deadline.confidentiality == "sensitive") {
            "Échéance confidentielle enregistrée dans VieSimple. Ouvrez l’application pour consulter les détails."
        } else {
            buildString {
                append("Catégorie : ${deadline.category}")
                if (deadline.description.isNotBlank()) {
                    append("\n\n${deadline.description.trim()}")
                }
            }
        }
    }

    private fun computeEventStartMillis(date: java.util.Date, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun computeReminderMinutes(daysBeforeList: List<Long>): List<Int> {
        val raw = daysBeforeList.ifEmpty { listOf(1L, 0L) }
        return raw.map { days ->
            if (days <= 0L) 15 else (days.coerceAtMost(365L) * 24L * 60L).toInt()
        }.distinct().sortedDescending()
    }

    @SuppressLint("MissingPermission")
    private fun systemEventExists(resolver: android.content.ContentResolver, eventId: Long): Boolean {
        if (eventId <= 0L) return false
        return runCatching {
            resolver.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DELETED),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use false
                val deletedFlag = cursor.getInt(1)
                deletedFlag == 0
            } ?: false
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun replaceEventReminders(
        resolver: android.content.ContentResolver,
        eventId: Long,
        reminderMinutes: List<Int>
    ) {
        runCatching {
            resolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString())
            )
            reminderMinutes.forEach { minutes ->
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, minutes)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
        }.onFailure {
            Log.w(TAG, "Unable to update calendar reminders for eventId=$eventId", it)
        }
    }

    @SuppressLint("MissingPermission")
    private fun deleteSystemEventSafe(resolver: android.content.ContentResolver, eventId: Long) {
        if (eventId <= 0L) return
        runCatching {
            resolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                null,
                null
            )
        }
    }
}

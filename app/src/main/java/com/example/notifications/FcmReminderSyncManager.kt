package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.core.SecurityPreferences
import com.example.core.UserSecuritySettings
import com.example.data.VieSimpleRepository
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.HouseholdTask
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class FcmSyncStatus(
    val isRegistered: Boolean = false,
    val upcomingDeadlinesCount: Int = 0,
    val overdueTasksCount: Int = 0,
    val lastSyncedMillis: Long = 0L,
    val statusMessage: String = "En attente de synchronisation FCM"
)

object FcmReminderSyncManager {

    private const val TAG = "FcmSyncManager"
    private const val TOPIC_REMINDERS_DIGEST = "viesimple_privacy_safe_reminders"

    suspend fun syncFcmTokenAndReminderSummary(
        context: Context,
        repo: VieSimpleRepository,
        securityPrefs: SecurityPreferences,
        deadlines: List<Deadline>,
        tasks: List<HouseholdTask>,
        settings: UserSecuritySettings
    ): Result<FcmSyncStatus> {
        val imminentDeadlines = deadlines.filter { dl ->
            dl.status == "pending" && (DateUtils.daysUntil(dl.dueDate) ?: Long.MAX_VALUE) in 0L..3L
        }.sortedBy { DateUtils.daysUntil(it.dueDate) ?: Long.MAX_VALUE }

        val overdueTasksCount = tasks.count { t ->
            t.status == "todo" && (DateUtils.daysUntil(t.dueDate) ?: 1L) < 0L
        }

        val nextDeadlineDate = imminentDeadlines.firstOrNull()?.dueDate.orEmpty()

        return try {
            val messaging = FirebaseMessaging.getInstance()
            if (settings.notificationsEnabled && !settings.notifQuietMode) {
                try {
                    messaging.subscribeToTopic(TOPIC_REMINDERS_DIGEST).await()
                } catch (_: Exception) {
                }
            }

            val token = messaging.token.await()
            if (token.isBlank()) {
                return Result.failure(IllegalStateException("Jeton FCM vide."))
            }

            securityPrefs.setLastFcmToken(token)
            val deviceId = securityPrefs.getOrCreateFcmDeviceId()

            repo.upsertFcmDeviceToken(
                deviceId = deviceId,
                token = token,
                notifDeadlines = settings.notifDeadlines,
                notifTasks = settings.notifTasks,
                notifQuietMode = settings.notifQuietMode,
                notifHour = settings.notifHour,
                upcomingDeadlinesCount = imminentDeadlines.size,
                overdueTasksCount = overdueTasksCount,
                nextDeadlineDate = nextDeadlineDate
            ).getOrThrow()

            ReminderScheduler.syncAutomaticPrivacySafeReminders(
                context = context,
                deadlines = deadlines,
                tasks = tasks,
                settings = settings,
                securityPrefs = securityPrefs
            )

            Result.success(
                FcmSyncStatus(
                    isRegistered = true,
                    upcomingDeadlinesCount = imminentDeadlines.size,
                    overdueTasksCount = overdueTasksCount,
                    lastSyncedMillis = System.currentTimeMillis(),
                    statusMessage = "FCM actif · Rappels confidentiels synchronisés"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FCM sync deferred/failed", e)
            // Even if network/FCM token retrieval fails offline, keep local privacy-safe alarms updated
            try {
                ReminderScheduler.syncAutomaticPrivacySafeReminders(
                    context = context,
                    deadlines = deadlines,
                    tasks = tasks,
                    settings = settings,
                    securityPrefs = securityPrefs
                )
            } catch (_: Exception) {
            }
            Result.failure(e)
        }
    }
}

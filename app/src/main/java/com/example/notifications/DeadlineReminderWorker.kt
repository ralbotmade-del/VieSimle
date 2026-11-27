package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.core.OperationType
import com.example.core.SecurityPreferences
import com.example.core.UserSecuritySettings
import com.example.core.handleFirestoreError
import com.example.data.local.OfflineCacheRepository
import com.example.model.DateUtils
import com.example.model.Deadline
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class DeadlineReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val securityPrefs = SecurityPreferences(applicationContext)
        val settings = try {
            securityPrefs.settingsFlow.first()
        } catch (_: Exception) {
            UserSecuritySettings()
        }

        if (!settings.notificationsEnabled || !settings.notifDeadlines || settings.notifQuietMode) {
            securityPrefs.setWorkerExecutionStatus("Notifications d’échéances désactivées ou mode silencieux")
            return Result.success()
        }
        if (settings.notifPausedUntilMillis > System.currentTimeMillis()) {
            securityPrefs.setWorkerExecutionStatus("Rappels temporairement en pause")
            return Result.success()
        }

        val uid = Firebase.auth.currentUser?.uid?.takeIf { it.isNotBlank() }
        if (uid == null) {
            securityPrefs.setWorkerExecutionStatus("En attente de connexion utilisateur")
            return Result.success()
        }

        val deadlines: List<Deadline> = try {
            val db = FirebaseFirestore.getInstance(
                applicationContext.getString(R.string.firestore_database_id)
            )
            val snap = db.collection("deadlines")
                .whereArrayContains("sharedWithUids", uid)
                .get()
                .await()
            snap.toObjects(Deadline::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "deadlines")
            Log.w(TAG, "Firestore deadline fetch failed in background worker", e)
            securityPrefs.setWorkerExecutionStatus("Échec temporaire Firestore (nouvelle tentative planifiée)")
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        val pending = deadlines.filter { it.status == "pending" }
        val upcoming7d = pending.filter { dl ->
            val days = DateUtils.daysUntil(dl.dueDate)
            days != null && days in 0L..7L
        }
        val dueInExactly3Days = pending.filter { dl ->
            DateUtils.daysUntil(dl.dueDate) == 3L
        }
        val dueWithin3Days = pending.filter { dl ->
            val days = DateUtils.daysUntil(dl.dueDate)
            days != null && days in 0L..3L
        }
        val overdue = pending.filter { dl ->
            val days = DateUtils.daysUntil(dl.dueDate)
            days != null && days < 0L
        }
        val adminUpcoming = upcoming7d.filter { dl ->
            dl.category in ADMIN_CATEGORIES
        }

        val totalAlertCount = upcoming7d.size + overdue.size
        val summary = if (totalAlertCount == 0) {
            "Vérification Firestore OK · Aucune échéance administrative imminente"
        } else {
            "Vérification Firestore OK · ${dueInExactly3Days.size} à J-3, ${dueWithin3Days.size} sous 3j (${adminUpcoming.size} admin.), ${overdue.size} en retard"
        }
        securityPrefs.setWorkerExecutionStatus(summary)

        if (settings.calendarSyncEnabled && SystemCalendarSyncManager.hasCalendarPermissions(applicationContext)) {
            runCatching {
                SystemCalendarSyncManager.syncDeadlinesToSystemCalendar(
                    context = applicationContext,
                    userId = uid,
                    deadlines = deadlines,
                    settings = settings,
                    cacheRepo = OfflineCacheRepository(applicationContext),
                    securityPrefs = securityPrefs
                )
            }
        }

        if (totalAlertCount == 0) {
            return Result.success()
        }

        val forceNotify = inputData.getBoolean(KEY_FORCE_NOTIFY, false)
        val today = DateUtils.todayIso()
        val notifyKey = "${today}_${dueInExactly3Days.size}_${dueWithin3Days.size}_${upcoming7d.size}_${overdue.size}"
        val lastNotifyKey = securityPrefs.getLastWorkerDeadlineNotifyKey()

        if ((forceNotify || notifyKey != lastNotifyKey) &&
            ReminderBroadcastReceiver.hasNotificationPermission(applicationContext)
        ) {
            val safeBody = buildPrivacySafeWorkerBody(
                dueIn3DaysCount = dueInExactly3Days.size,
                dueWithin3DaysCount = dueWithin3Days.size,
                upcoming7dCount = upcoming7d.size,
                adminUpcomingCount = adminUpcoming.size,
                overdueCount = overdue.size
            )
            ReminderBroadcastReceiver.postPrivacySafeNotification(
                context = applicationContext,
                notificationId = WORKER_NOTIFICATION_ID,
                safeTitle = "Rappel VieSimple · Échéances (J-3)",
                safeBody = safeBody,
                targetRoute = "deadlines"
            )
            securityPrefs.setLastWorkerDeadlineNotifyKey(notifyKey)
        }

        return Result.success()
    }

    private fun buildPrivacySafeWorkerBody(
        dueIn3DaysCount: Int,
        dueWithin3DaysCount: Int,
        upcoming7dCount: Int,
        adminUpcomingCount: Int,
        overdueCount: Int
    ): String {
        // Strict privacy: never reveal titles, issuers, references, or amounts in notifications.
        return when {
            dueIn3DaysCount > 0 && overdueCount > 0 ->
                "$dueIn3DaysCount échéance(s) administrative(s) arrivent dans 3 jours (J-3) et $overdueCount sont en retard. Ouvrez VieSimple pour agir."
            dueIn3DaysCount > 0 ->
                if (dueIn3DaysCount > 1) "$dueIn3DaysCount échéances administratives arrivent dans 3 jours (J-3). Ouvrez VieSimple pour vous préparer."
                else "Une échéance administrative arrive dans 3 jours (J-3). Ouvrez VieSimple pour vous préparer."
            overdueCount > 0 && dueWithin3DaysCount > 0 ->
                "$overdueCount échéance(s) en retard et $dueWithin3DaysCount à traiter sous 3 jours. Ouvrez VieSimple pour les consulter."
            overdueCount > 0 ->
                if (overdueCount > 1) "$overdueCount échéances administratives sont en retard. Ouvrez VieSimple pour agir."
                else "Une échéance administrative est en retard. Ouvrez VieSimple pour la consulter."
            dueWithin3DaysCount > 0 ->
                if (dueWithin3DaysCount > 1) "$dueWithin3DaysCount échéances administratives arrivent dans les 3 prochains jours. Ouvrez VieSimple pour les vérifier."
                else "Une échéance administrative arrive dans moins de 3 jours. Ouvrez VieSimple pour la vérifier."
            adminUpcomingCount > 0 ->
                if (adminUpcomingCount > 1) "$adminUpcomingCount échéances administratives arrivent cette semaine. Ouvrez VieSimple pour les suivre."
                else "Une échéance administrative arrive cette semaine. Ouvrez VieSimple pour la consulter."
            else ->
                "$upcoming7dCount échéance(s) à traiter dans les 7 prochains jours. Ouvrez VieSimple pour les consulter."
        }
    }

    companion object {
        private const val TAG = "DeadlineReminderWorker"
        private const val WORKER_NOTIFICATION_ID = 4302
        const val KEY_FORCE_NOTIFY = "key_force_notify"

        private val ADMIN_CATEGORIES = setOf(
            "Administratif",
            "Impôts",
            "Assurance",
            "Logement",
            "Santé",
            "Véhicule",
            "Banque",
            "Éducation"
        )
    }
}

object DeadlineWorkerScheduler {

    private const val PERIODIC_WORK_NAME = "viesimple_firestore_deadline_worker"
    private const val IMMEDIATE_WORK_NAME = "viesimple_firestore_deadline_worker_now"

    fun syncPeriodicSchedule(context: Context, settings: UserSecuritySettings) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!settings.notificationsEnabled || !settings.notifDeadlines) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<DeadlineReminderWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    fun triggerImmediateCheck(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val input = Data.Builder()
            .putBoolean(DeadlineReminderWorker.KEY_FORCE_NOTIFY, true)
            .build()
        val request = OneTimeWorkRequestBuilder<DeadlineReminderWorker>()
            .setInputData(input)
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAll(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }
}

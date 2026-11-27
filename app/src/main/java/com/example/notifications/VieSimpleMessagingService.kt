package com.example.notifications

import android.util.Log
import com.example.R
import com.example.core.SecurityPreferences
import com.example.core.UserSecuritySettings
import com.example.data.VieSimpleRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Firebase Cloud Messaging service that strictly sanitizes incoming push messages.
 * Never renders sensitive document titles, amounts, issuers, or personal details from push payloads.
 */
class VieSimpleMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token received")
        val appContext = applicationContext
        serviceScope.launch {
            try {
                val prefs = SecurityPreferences(appContext)
                prefs.setLastFcmToken(token)
                val user = Firebase.auth.currentUser
                if (user != null) {
                    val settings = prefs.settingsFlow.first()
                    val deviceId = prefs.getOrCreateFcmDeviceId()
                    val db = FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
                    val repo = VieSimpleRepository(db)
                    repo.upsertFcmDeviceToken(
                        deviceId = deviceId,
                        token = token,
                        notifDeadlines = settings.notifDeadlines,
                        notifTasks = settings.notifTasks,
                        notifQuietMode = settings.notifQuietMode,
                        notifHour = settings.notifHour,
                        upcomingDeadlinesCount = 0,
                        overdueTasksCount = 0,
                        nextDeadlineDate = ""
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to sync refreshed FCM token", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val appContext = applicationContext
        val prefs = SecurityPreferences(appContext)
        val settings = runBlocking {
            try {
                prefs.settingsFlow.first()
            } catch (_: Exception) {
                UserSecuritySettings()
            }
        }

        val data = remoteMessage.data
        val eventType = data["eventType"]?.trim()?.lowercase().orEmpty()
        val count = data["count"]?.toIntOrNull()?.coerceIn(1, 99)
        val daysUntil = data["daysUntil"]?.toIntOrNull()

        if (!settings.notificationsEnabled) return
        if (settings.notifQuietMode && eventType != "security_alert") return
        if (settings.notifPausedUntilMillis > System.currentTimeMillis() && eventType != "security_alert") return

        val (safeTitle, safeBody, targetRoute, notifId) = sanitizeFcmPayload(
            eventType = eventType,
            count = count,
            daysUntil = daysUntil,
            settings = settings
        ) ?: return

        ReminderBroadcastReceiver.postPrivacySafeNotification(
            context = appContext,
            notificationId = notifId,
            safeTitle = safeTitle,
            safeBody = safeBody,
            targetRoute = targetRoute
        )
    }

    private fun sanitizeFcmPayload(
        eventType: String,
        count: Int?,
        daysUntil: Int?,
        settings: UserSecuritySettings
    ): SanitizedFcmAlert? {
        return when (eventType) {
            "deadline_upcoming", "deadline_imminent" -> {
                if (!settings.notifDeadlines) return null
                val body = when {
                    daysUntil != null && daysUntil <= 0 ->
                        "Une échéance importante arrive aujourd’hui. Ouvrez VieSimple pour la consulter."
                    daysUntil == 1 ->
                        "Une échéance importante arrive demain. Ouvrez VieSimple pour vérifier les détails."
                    count != null && count > 1 ->
                        "$count échéances imminentes approchent. Ouvrez VieSimple pour vous préparer."
                    else ->
                        getString(R.string.safe_notif_deadline_body)
                }
                SanitizedFcmAlert(
                    title = "Rappel VieSimple · Échéances",
                    body = body,
                    targetRoute = "deadlines",
                    notificationId = 5101
                )
            }
            "task_overdue", "tasks_overdue" -> {
                if (!settings.notifTasks) return null
                val body = if (count != null && count > 1) {
                    "$count tâches du foyer sont en retard. Ouvrez VieSimple pour les traiter."
                } else {
                    getString(R.string.safe_notif_task_overdue_body)
                }
                SanitizedFcmAlert(
                    title = getString(R.string.safe_notif_task_overdue_title),
                    body = body,
                    targetRoute = "tasks",
                    notificationId = 5102
                )
            }
            "household_update" -> {
                if (!settings.notifHousehold) return null
                SanitizedFcmAlert(
                    title = "Rappel VieSimple · Foyer",
                    body = "Nouvelle activité dans votre espace familial. Ouvrez VieSimple pour la consulter.",
                    targetRoute = "household",
                    notificationId = 5103
                )
            }
            "security_alert" -> {
                if (!settings.notifSecurityAlerts) return null
                SanitizedFcmAlert(
                    title = "Sécurité VieSimple",
                    body = "Activité de sécurité détectée sur votre compte. Ouvrez VieSimple pour vérifier.",
                    targetRoute = "settings",
                    notificationId = 5104
                )
            }
            else -> SanitizedFcmAlert(
                title = getString(R.string.safe_notif_fcm_generic_title),
                body = getString(R.string.safe_notif_fcm_generic_body),
                targetRoute = "deadlines",
                notificationId = 5100
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private data class SanitizedFcmAlert(
        val title: String,
        val body: String,
        val targetRoute: String,
        val notificationId: Int
    )

    companion object {
        private const val TAG = "VieSimpleFCM"
    }
}

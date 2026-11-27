package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.R
import com.example.core.SecurityPreferences
import com.example.core.UserSecuritySettings
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.HouseholdTask
import java.util.Calendar

object ReminderScheduler {

    private const val DAILY_DIGEST_NOTIFICATION_ID = 4201
    private const val OVERDUE_TASK_ALARM_BASE = 71000

    fun schedulePrivacySafeDeadlineReminders(
        context: Context,
        deadline: Deadline,
        settings: UserSecuritySettings
    ) {
        if (!settings.notificationsEnabled || !settings.notifDeadlines || settings.notifQuietMode) return
        if (settings.notifPausedUntilMillis > System.currentTimeMillis()) return
        if (deadline.status != "pending") return

        val dueDate = DateUtils.parseIso(deadline.dueDate) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        deadline.reminders.forEachIndexed { idx, daysBefore ->
            val cal = Calendar.getInstance().apply {
                time = dueDate
                add(Calendar.DAY_OF_YEAR, -daysBefore.toInt())
                set(Calendar.HOUR_OF_DAY, settings.notifHour)
                set(Calendar.MINUTE, settings.notifMinute)
                set(Calendar.SECOND, 0)
            }
            if (cal.timeInMillis > System.currentTimeMillis()) {
                val reqCode = (deadline.id.hashCode() * 31) + idx
                val safeBody = when {
                    daysBefore <= 0L -> "Une échéance importante arrive aujourd’hui. Ouvrez VieSimple pour la consulter."
                    daysBefore == 1L -> "Une échéance importante arrive demain. Ouvrez VieSimple pour vérifier les détails."
                    else -> "Une échéance importante arrive dans $daysBefore jours. Ouvrez VieSimple pour vous préparer."
                }
                val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                    putExtra(ReminderBroadcastReceiver.EXTRA_NOTIFICATION_ID, reqCode)
                    putExtra(ReminderBroadcastReceiver.EXTRA_SAFE_TITLE, "Rappel VieSimple · Échéance")
                    putExtra(ReminderBroadcastReceiver.EXTRA_SAFE_BODY, safeBody)
                    putExtra(ReminderBroadcastReceiver.EXTRA_TARGET_ROUTE, "deadlines")
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                } catch (_: SecurityException) {
                    // Fallback gracefully if exact alarm permission is restricted
                }
            }
        }
    }

    fun scheduleNextOverdueTasksCheckAlarm(
        context: Context,
        overdueTasksCount: Int,
        imminentDeadlinesCount: Int,
        settings: UserSecuritySettings
    ) {
        if (!settings.notificationsEnabled || settings.notifQuietMode) return
        if (settings.notifPausedUntilMillis > System.currentTimeMillis()) return
        if (overdueTasksCount <= 0 && imminentDeadlinesCount <= 0) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.notifHour)
            set(Calendar.MINUTE, settings.notifMinute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val (title, body, route) = buildSanitizedDigestMessage(
            imminentDeadlinesCount = if (settings.notifDeadlines) imminentDeadlinesCount else 0,
            overdueTasksCount = if (settings.notifTasks) overdueTasksCount else 0
        ) ?: return

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTIFICATION_ID, DAILY_DIGEST_NOTIFICATION_ID)
            putExtra(ReminderBroadcastReceiver.EXTRA_SAFE_TITLE, title)
            putExtra(ReminderBroadcastReceiver.EXTRA_SAFE_BODY, body)
            putExtra(ReminderBroadcastReceiver.EXTRA_TARGET_ROUTE, route)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            OVERDUE_TASK_ALARM_BASE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        } catch (_: SecurityException) {
        }
    }

    suspend fun syncAutomaticPrivacySafeReminders(
        context: Context,
        deadlines: List<Deadline>,
        tasks: List<HouseholdTask>,
        settings: UserSecuritySettings,
        securityPrefs: SecurityPreferences
    ) {
        if (!settings.notificationsEnabled || settings.notifQuietMode) return
        if (settings.notifPausedUntilMillis > System.currentTimeMillis()) return

        if (settings.notifDeadlines) {
            deadlines.filter { it.status == "pending" }.forEach { dl ->
                schedulePrivacySafeDeadlineReminders(context, dl, settings)
            }
        }

        val imminentDeadlinesCount = if (settings.notifDeadlines) {
            deadlines.count { dl ->
                dl.status == "pending" && (DateUtils.daysUntil(dl.dueDate) ?: Long.MAX_VALUE) in 0L..3L
            }
        } else 0

        val overdueTasksCount = if (settings.notifTasks) {
            tasks.count { t ->
                t.status == "todo" && (DateUtils.daysUntil(t.dueDate) ?: 1L) < 0L
            }
        } else 0

        scheduleNextOverdueTasksCheckAlarm(
            context = context,
            overdueTasksCount = overdueTasksCount,
            imminentDeadlinesCount = imminentDeadlinesCount,
            settings = settings
        )

        val today = DateUtils.todayIso()
        val lastNotifiedDate = securityPrefs.getLastAutoReminderDate()
        if ((imminentDeadlinesCount > 0 || overdueTasksCount > 0) &&
            lastNotifiedDate != today &&
            ReminderBroadcastReceiver.hasNotificationPermission(context)
        ) {
            val digest = buildSanitizedDigestMessage(imminentDeadlinesCount, overdueTasksCount)
            if (digest != null) {
                ReminderBroadcastReceiver.postPrivacySafeNotification(
                    context = context,
                    notificationId = DAILY_DIGEST_NOTIFICATION_ID,
                    safeTitle = digest.first,
                    safeBody = digest.second,
                    targetRoute = digest.third
                )
                securityPrefs.setLastAutoReminderDate(today)
            }
        }
    }

    private fun buildSanitizedDigestMessage(
        imminentDeadlinesCount: Int,
        overdueTasksCount: Int
    ): Triple<String, String, String>? {
        return when {
            imminentDeadlinesCount > 0 && overdueTasksCount > 0 -> Triple(
                "Rappels VieSimple",
                "$imminentDeadlinesCount échéance(s) imminente(s) et $overdueTasksCount tâche(s) en retard nécessitent votre attention.",
                "deadlines"
            )
            imminentDeadlinesCount > 0 -> Triple(
                "Rappel VieSimple · Échéances",
                if (imminentDeadlinesCount > 1) "$imminentDeadlinesCount échéances arrivent bientôt. Ouvrez VieSimple pour vous préparer."
                else "Une échéance importante arrive sous peu. Ouvrez VieSimple pour la consulter.",
                "deadlines"
            )
            overdueTasksCount > 0 -> Triple(
                "Rappel VieSimple · Tâches",
                if (overdueTasksCount > 1) "$overdueTasksCount tâches du foyer sont en retard. Ouvrez VieSimple pour les traiter."
                else "Une tâche du foyer est en retard. Ouvrez VieSimple pour la consulter.",
                "tasks"
            )
            else -> null
        }
    }

    fun sendTestSafeNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ReminderBroadcastReceiver.ensureChannel(context, manager)
        ReminderBroadcastReceiver.postPrivacySafeNotification(
            context = context,
            notificationId = 9999,
            safeTitle = context.getString(R.string.safe_notif_fcm_generic_title),
            safeBody = "Rappel automatique confidentiel activé : échéances imminentes et tâches en retard protégées sans fuite de données.",
            targetRoute = "deadlines"
        )
    }
}

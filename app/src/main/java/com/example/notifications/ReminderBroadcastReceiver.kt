package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!hasNotificationPermission(context)) return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1001)
        // Strict privacy rule: never expose sensitive document titles, issuers, or amounts in notifications.
        val genericTitle = intent.getStringExtra(EXTRA_SAFE_TITLE)
            ?: context.getString(R.string.safe_notif_deadline_title)
        val genericMessage = intent.getStringExtra(EXTRA_SAFE_BODY)
            ?: context.getString(R.string.safe_notif_deadline_body)
        val targetRoute = intent.getStringExtra(EXTRA_TARGET_ROUTE).orEmpty()

        postPrivacySafeNotification(
            context = context,
            notificationId = notificationId,
            safeTitle = genericTitle,
            safeBody = genericMessage,
            targetRoute = targetRoute
        )
    }

    companion object {
        const val CHANNEL_ID = "viesimple_reminders"
        const val EXTRA_NOTIFICATION_ID = "extra_notif_id"
        const val EXTRA_SAFE_TITLE = "extra_safe_title"
        const val EXTRA_SAFE_BODY = "extra_safe_body"
        const val EXTRA_TARGET_ROUTE = "extra_target_route"

        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun ensureChannel(context: Context, manager: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                }
                manager.createNotificationChannel(channel)
            }
        }

        fun postPrivacySafeNotification(
            context: Context,
            notificationId: Int,
            safeTitle: String,
            safeBody: String,
            targetRoute: String = ""
        ) {
            if (!hasNotificationPermission(context)) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureChannel(context, manager)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (targetRoute.isNotBlank()) {
                    putExtra(EXTRA_TARGET_ROUTE, targetRoute)
                }
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Minimal lock-screen representation (hides even category/counts when device is locked)
            val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(context.getString(R.string.safe_notif_lockscreen_title))
                .setContentText(context.getString(R.string.safe_notif_lockscreen_body))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setStyle(NotificationCompat.BigTextStyle().bigText(safeBody))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .build()

            try {
                manager.notify(notificationId, notification)
            } catch (_: SecurityException) {
                // Permission revoked mid-flight; ignore safely
            }
        }
    }
}

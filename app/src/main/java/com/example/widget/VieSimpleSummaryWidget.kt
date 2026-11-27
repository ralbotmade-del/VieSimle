package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.core.LocaleHelper
import com.example.core.SecurityPreferences
import com.example.data.local.OfflineCacheRepository
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.SubscriptionItem
import com.example.notifications.ReminderBroadcastReceiver
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class VieSimpleSummaryWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 110.dp),
            DpSize(260.dp, 110.dp),
            DpSize(260.dp, 190.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = runCatching { SecurityPreferences(context.applicationContext).settingsFlow.first() }
            .getOrNull()
        val (localizedCtx, _) = LocaleHelper.wrapContextWithLocale(
            context,
            settings?.appLanguage ?: "system"
        )
        val snapshot = WidgetSnapshotStore.load(context)

        provideContent {
            GlanceTheme {
                WidgetContent(
                    context = localizedCtx,
                    snapshot = snapshot
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        snapshot: WidgetSummarySnapshot
    ) {
        val size = LocalSize.current
        val useSideBySide = size.width >= 245.dp

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.app_name),
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                        .clickable(actionStartActivity(launchAppIntent(context, null)))
                )
                Box(
                    modifier = GlanceModifier
                        .size(32.dp)
                        .cornerRadius(16.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionRunCallback<RefreshSummaryWidgetAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_refresh),
                        contentDescription = context.getString(R.string.widget_refresh),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                        modifier = GlanceModifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (!snapshot.isAuthenticated) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(14.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .padding(12.dp)
                        .clickable(actionStartActivity(launchAppIntent(context, null))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.widget_sign_in_prompt),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 3
                    )
                }
            } else if (useSideBySide) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubscriptionCard(
                        context = context,
                        sub = snapshot.nextSubscription,
                        modifier = GlanceModifier.defaultWeight().fillMaxSize()
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    DeadlineCard(
                        context = context,
                        deadline = snapshot.nearestDeadline,
                        modifier = GlanceModifier.defaultWeight().fillMaxSize()
                    )
                }
            } else {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    SubscriptionCard(
                        context = context,
                        sub = snapshot.nextSubscription,
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    DeadlineCard(
                        context = context,
                        deadline = snapshot.nearestDeadline,
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    )
                }
            }
        }
    }

    @Composable
    private fun SubscriptionCard(
        context: Context,
        sub: WidgetSubscriptionSnapshot?,
        modifier: GlanceModifier
    ) {
        Column(
            modifier = modifier
                .cornerRadius(14.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .padding(10.dp)
                .clickable(actionStartActivity(launchAppIntent(context, "subscriptions")))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_subscription),
                    contentDescription = context.getString(R.string.widget_next_sub),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    modifier = GlanceModifier.size(14.dp)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = context.getString(R.string.widget_next_sub),
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            if (sub == null) {
                Text(
                    text = context.getString(R.string.widget_no_sub),
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 12.sp
                    ),
                    maxLines = 2
                )
            } else {
                Text(
                    text = sub.name,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = WidgetSummaryCalculator.formatSubscriptionAmount(
                        sub.amount,
                        sub.currency,
                        sub.frequency
                    ),
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                val dateLabel = DateUtils.formatFrench(sub.nextBillingDate)
                val countdown = formatCountdown(context, sub.daysUntil)
                Text(
                    text = if (countdown != null) "$dateLabel · $countdown" else dateLabel,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun DeadlineCard(
        context: Context,
        deadline: WidgetDeadlineSnapshot?,
        modifier: GlanceModifier
    ) {
        val isUrgent = deadline?.daysUntil != null && deadline.daysUntil <= 3L
        val bgColor = if (isUrgent) GlanceTheme.colors.errorContainer else GlanceTheme.colors.secondaryContainer
        val fgColor = if (isUrgent) GlanceTheme.colors.onErrorContainer else GlanceTheme.colors.onSecondaryContainer

        Column(
            modifier = modifier
                .cornerRadius(14.dp)
                .background(bgColor)
                .padding(10.dp)
                .clickable(actionStartActivity(launchAppIntent(context, "deadlines")))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_deadline),
                    contentDescription = context.getString(R.string.widget_nearest_deadline),
                    colorFilter = ColorFilter.tint(fgColor),
                    modifier = GlanceModifier.size(14.dp)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = context.getString(R.string.widget_nearest_deadline),
                    style = TextStyle(
                        color = fgColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            if (deadline == null) {
                Text(
                    text = context.getString(R.string.widget_no_deadline),
                    style = TextStyle(
                        color = fgColor,
                        fontSize = 12.sp
                    ),
                    maxLines = 2
                )
            } else {
                Text(
                    text = deadline.title,
                    style = TextStyle(
                        color = fgColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = deadline.category,
                    style = TextStyle(
                        color = fgColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                val dateLabel = DateUtils.formatFrench(deadline.dueDate)
                val countdown = formatCountdown(context, deadline.daysUntil)
                Text(
                    text = if (countdown != null) "$dateLabel · $countdown" else dateLabel,
                    style = TextStyle(
                        color = fgColor,
                        fontSize = 11.sp,
                        fontWeight = if (isUrgent) FontWeight.Bold else FontWeight.Normal
                    ),
                    maxLines = 1
                )
            }
        }
    }

    private fun formatCountdown(context: Context, days: Long?): String? {
        if (days == null) return null
        return when {
            days == 0L -> context.getString(R.string.widget_due_today)
            days > 0L -> context.getString(R.string.widget_due_in_days, days.toInt())
            else -> context.getString(R.string.widget_overdue_days, kotlin.math.abs(days).toInt())
        }
    }

    private fun launchAppIntent(context: Context, targetRoute: String?): Intent {
        return Intent(context.applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!targetRoute.isNullOrBlank()) {
                putExtra(ReminderBroadcastReceiver.EXTRA_TARGET_ROUTE, targetRoute)
            }
        }
    }
}

class VieSimpleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VieSimpleSummaryWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSnapshotManager.enqueueBackgroundRefresh(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetSnapshotManager.enqueueBackgroundRefresh(context)
    }
}

class RefreshSummaryWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WidgetSnapshotManager.enqueueBackgroundRefresh(context)
        runCatching { VieSimpleSummaryWidget().update(context, glanceId) }
    }
}

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val uid = Firebase.auth.currentUser?.uid?.takeIf { it.isNotBlank() }
        if (uid == null) {
            WidgetSnapshotManager.clearAndUpdateWidget(applicationContext)
            return Result.success()
        }

        val cache = OfflineCacheRepository(applicationContext)
        val db = runCatching {
            FirebaseFirestore.getInstance(applicationContext.getString(R.string.firestore_database_id))
        }.getOrNull()

        val subscriptions: List<SubscriptionItem> = try {
            val remote = db?.collection("subscriptions")
                ?.whereArrayContains("sharedWithUids", uid)
                ?.get()
                ?.await()
                ?.toObjects(SubscriptionItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (remote != null) {
                cache.replaceSubscriptions(uid, remote)
                remote
            } else {
                cache.getSubscriptionsOnce(uid)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget refresh falling back to Room cache for subscriptions", e)
            cache.getSubscriptionsOnce(uid)
        }

        val deadlines: List<Deadline> = try {
            db?.collection("deadlines")
                ?.whereArrayContains("sharedWithUids", uid)
                ?.get()
                ?.await()
                ?.toObjects(Deadline::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget refresh could not fetch deadlines", e)
            emptyList()
        }

        val existing = WidgetSnapshotStore.load(applicationContext)
        val effectiveDeadlines = if (deadlines.isNotEmpty()) deadlines else {
            existing.nearestDeadline?.let {
                listOf(
                    Deadline(
                        id = it.id,
                        title = it.title,
                        category = it.category,
                        dueDate = it.dueDate,
                        status = "pending"
                    )
                )
            } ?: emptyList()
        }

        WidgetSnapshotManager.syncAndUpdateWidget(
            context = applicationContext,
            subscriptions = subscriptions,
            deadlines = effectiveDeadlines,
            isAuthenticated = true
        )
        return Result.success()
    }

    companion object {
        private const val TAG = "WidgetRefreshWorker"
    }
}

object WidgetSnapshotManager {
    private const val WIDGET_REFRESH_WORK_NAME = "viesimple_widget_refresh_work"

    suspend fun syncAndUpdateWidget(
        context: Context,
        subscriptions: List<SubscriptionItem>,
        deadlines: List<Deadline>,
        isAuthenticated: Boolean = true
    ) {
        val ctx = context.applicationContext
        val snapshot = WidgetSummaryCalculator.computeWidgetSummary(
            subscriptions = subscriptions,
            deadlines = deadlines,
            isAuthenticated = isAuthenticated
        )
        WidgetSnapshotStore.save(ctx, snapshot)
        runCatching {
            VieSimpleSummaryWidget().updateAll(ctx)
        }.onFailure {
            Log.w("WidgetSnapshotManager", "Unable to update Glance widget", it)
        }
    }

    suspend fun clearAndUpdateWidget(context: Context) {
        val ctx = context.applicationContext
        WidgetSnapshotStore.clear(ctx)
        runCatching {
            VieSimpleSummaryWidget().updateAll(ctx)
        }
    }

    fun enqueueBackgroundRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WIDGET_REFRESH_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

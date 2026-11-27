package com.example.widget

import android.content.Context
import android.util.Log
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.SubscriptionItem
import org.json.JSONObject
import java.util.Calendar

data class WidgetSubscriptionSnapshot(
    val id: String,
    val name: String,
    val amount: Double,
    val currency: String,
    val frequency: String,
    val nextBillingDate: String,
    val daysUntil: Long?
)

data class WidgetDeadlineSnapshot(
    val id: String,
    val title: String,
    val category: String,
    val dueDate: String,
    val daysUntil: Long?
)

data class WidgetSummarySnapshot(
    val nextSubscription: WidgetSubscriptionSnapshot? = null,
    val nearestDeadline: WidgetDeadlineSnapshot? = null,
    val updatedAtMillis: Long = 0L,
    val isAuthenticated: Boolean = true
)

object WidgetSummaryCalculator {

    private val ACTIVE_SUB_STATUSES = setOf("active", "to_cancel", "to_verify")

    fun daysBetweenIso(fromIso: String, toIso: String): Long? {
        val fromDate = DateUtils.parseIso(fromIso) ?: return null
        val toDate = DateUtils.parseIso(toIso) ?: return null
        val fromCal = Calendar.getInstance().apply {
            time = fromDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val toCal = Calendar.getInstance().apply {
            time = toDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = toCal.timeInMillis - fromCal.timeInMillis
        return kotlin.math.round(diffMillis / (24.0 * 60.0 * 60.0 * 1000.0)).toLong()
    }

    fun computeWidgetSummary(
        subscriptions: List<SubscriptionItem>,
        deadlines: List<Deadline>,
        todayIso: String = DateUtils.todayIso(),
        nowMillis: Long = System.currentTimeMillis(),
        isAuthenticated: Boolean = true
    ): WidgetSummarySnapshot {
        if (!isAuthenticated) {
            return WidgetSummarySnapshot(
                nextSubscription = null,
                nearestDeadline = null,
                updatedAtMillis = nowMillis,
                isAuthenticated = false
            )
        }

        val nextSub = pickNextSubscription(subscriptions, todayIso)
        val nearestDl = pickNearestDeadline(deadlines, todayIso)

        return WidgetSummarySnapshot(
            nextSubscription = nextSub,
            nearestDeadline = nearestDl,
            updatedAtMillis = nowMillis,
            isAuthenticated = true
        )
    }

    private fun pickNextSubscription(
        subscriptions: List<SubscriptionItem>,
        todayIso: String
    ): WidgetSubscriptionSnapshot? {
        val active = subscriptions.filter { it.status in ACTIVE_SUB_STATUSES && it.name.isNotBlank() }
        if (active.isEmpty()) return null

        val withDays = active.map { sub ->
            sub to daysBetweenIso(todayIso, sub.nextBillingDate)
        }

        val chosen = withDays
            .filter { (_, d) -> d != null && d >= 0L }
            .minWithOrNull(
                compareBy<Pair<SubscriptionItem, Long?>> { it.second ?: Long.MAX_VALUE }
                    .thenBy { it.first.nextBillingDate }
                    .thenByDescending { it.first.amount }
            )
            ?: withDays
                .filter { (_, d) -> d != null }
                .maxWithOrNull(
                    compareBy<Pair<SubscriptionItem, Long?>> { it.second ?: Long.MIN_VALUE }
                        .thenByDescending { it.first.amount }
                )
            ?: withDays.first()

        val (sub, days) = chosen
        return WidgetSubscriptionSnapshot(
            id = sub.id,
            name = sub.name,
            amount = sub.amount,
            currency = sub.currency.ifBlank { "EUR" },
            frequency = sub.frequency.ifBlank { "monthly" },
            nextBillingDate = sub.nextBillingDate,
            daysUntil = days
        )
    }

    private fun pickNearestDeadline(
        deadlines: List<Deadline>,
        todayIso: String
    ): WidgetDeadlineSnapshot? {
        val pending = deadlines.filter { it.status == "pending" && it.title.isNotBlank() }
        if (pending.isEmpty()) return null

        val withDays = pending.map { dl ->
            dl to daysBetweenIso(todayIso, dl.dueDate)
        }

        fun priorityRank(priority: String): Int = when (priority) {
            "urgent" -> 0
            "high" -> 1
            "medium" -> 2
            else -> 3
        }

        val chosen = withDays
            .filter { (_, d) -> d != null && d >= 0L }
            .minWithOrNull(
                compareBy<Pair<Deadline, Long?>> { it.second ?: Long.MAX_VALUE }
                    .thenBy { priorityRank(it.first.priority) }
                    .thenBy { it.first.dueDate }
            )
            ?: withDays
                .filter { (_, d) -> d != null }
                .maxWithOrNull(
                    compareBy<Pair<Deadline, Long?>> { it.second ?: Long.MIN_VALUE }
                        .thenBy { -priorityRank(it.first.priority) }
                )
            ?: withDays.first()

        val (dl, days) = chosen
        return WidgetDeadlineSnapshot(
            id = dl.id,
            title = dl.title,
            category = dl.category.ifBlank { "Administratif" },
            dueDate = dl.dueDate,
            daysUntil = days
        )
    }

    fun formatSubscriptionAmount(amount: Double, currency: String, frequency: String): String {
        val symbol = when (currency.uppercase()) {
            "USD" -> "$"
            "GBP" -> "£"
            "CHF" -> "CHF"
            else -> "€"
        }
        val formatted = String.format(java.util.Locale.getDefault(), "%.2f", amount)
        val suffix = when (frequency) {
            "yearly" -> "/an"
            "quarterly" -> "/trim."
            else -> "/mois"
        }
        return if (symbol == "CHF") "$formatted CHF$suffix" else "$formatted $symbol$suffix"
    }
}

object WidgetSnapshotStore {
    private const val PREFS_NAME = "viesimple_widget_snapshot"
    private const val KEY_JSON = "summary_json"
    private const val TAG = "WidgetSnapshotStore"

    fun save(context: Context, snapshot: WidgetSummarySnapshot) {
        val json = JSONObject().apply {
            put("updatedAtMillis", snapshot.updatedAtMillis)
            put("isAuthenticated", snapshot.isAuthenticated)
            snapshot.nextSubscription?.let { sub ->
                put(
                    "nextSubscription",
                    JSONObject().apply {
                        put("id", sub.id)
                        put("name", sub.name)
                        put("amount", sub.amount)
                        put("currency", sub.currency)
                        put("frequency", sub.frequency)
                        put("nextBillingDate", sub.nextBillingDate)
                    }
                )
            }
            snapshot.nearestDeadline?.let { dl ->
                put(
                    "nearestDeadline",
                    JSONObject().apply {
                        put("id", dl.id)
                        put("title", dl.title)
                        put("category", dl.category)
                        put("dueDate", dl.dueDate)
                    }
                )
            }
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, json.toString())
            .apply()
    }

    fun load(context: Context): WidgetSummarySnapshot {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null)
            ?: return WidgetSummarySnapshot(isAuthenticated = false)

        return try {
            val json = JSONObject(raw)
            val isAuth = json.optBoolean("isAuthenticated", true)
            val updatedAt = json.optLong("updatedAtMillis", 0L)
            val subObj = json.optJSONObject("nextSubscription")
            val sub = subObj?.let {
                val billingDate = it.optString("nextBillingDate", "")
                WidgetSubscriptionSnapshot(
                    id = it.optString("id", ""),
                    name = it.optString("name", ""),
                    amount = it.optDouble("amount", 0.0),
                    currency = it.optString("currency", "EUR"),
                    frequency = it.optString("frequency", "monthly"),
                    nextBillingDate = billingDate,
                    daysUntil = DateUtils.daysUntil(billingDate)
                )
            }
            val dlObj = json.optJSONObject("nearestDeadline")
            val dl = dlObj?.let {
                val dueDate = it.optString("dueDate", "")
                WidgetDeadlineSnapshot(
                    id = it.optString("id", ""),
                    title = it.optString("title", ""),
                    category = it.optString("category", "Administratif"),
                    dueDate = dueDate,
                    daysUntil = DateUtils.daysUntil(dueDate)
                )
            }
            WidgetSummarySnapshot(
                nextSubscription = sub,
                nearestDeadline = dl,
                updatedAtMillis = updatedAt,
                isAuthenticated = isAuth
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse widget snapshot", e)
            WidgetSummarySnapshot(isAuthenticated = false)
        }
    }

    fun clear(context: Context) {
        save(
            context = context,
            snapshot = WidgetSummarySnapshot(
                nextSubscription = null,
                nearestDeadline = null,
                updatedAtMillis = System.currentTimeMillis(),
                isAuthenticated = false
            )
        )
    }
}

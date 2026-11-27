package com.example.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    private val frShortFormat = SimpleDateFormat("dd MMM yyyy", Locale.FRENCH)
    private val frNumericFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)

    fun todayIso(): String = isoFormat.format(Date())

    fun addDaysIso(baseIso: String, days: Int): String {
        val date = parseIso(baseIso) ?: Date()
        val cal = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, days)
        }
        return isoFormat.format(cal.time)
    }

    fun parseIso(iso: String): Date? {
        if (iso.isBlank()) return null
        return try {
            isoFormat.parse(iso.trim())
        } catch (_: Exception) {
            null
        }
    }

    fun formatFrench(iso: String): String {
        val date = parseIso(iso) ?: return iso.ifBlank { "Non définie" }
        return frShortFormat.format(date)
    }

    fun formatFrenchNumeric(iso: String): String {
        val date = parseIso(iso) ?: return todayNumeric()
        return frNumericFormat.format(date)
    }

    fun todayNumeric(): String = frNumericFormat.format(Date())

    fun daysUntil(iso: String): Long? {
        val target = parseIso(iso) ?: return null
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCal = Calendar.getInstance().apply {
            time = target
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = targetCal.timeInMillis - todayCal.timeInMillis
        return diffMillis / (24L * 60L * 60L * 1000L)
    }

    fun computeNextOccurrence(currentDueIso: String, recurrence: String, customDays: Long): String {
        val baseDate = parseIso(currentDueIso) ?: Date()
        val cal = Calendar.getInstance().apply { time = baseDate }
        when (recurrence) {
            "weekly" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "monthly" -> cal.add(Calendar.MONTH, 1)
            "quarterly" -> cal.add(Calendar.MONTH, 3)
            "yearly" -> cal.add(Calendar.YEAR, 1)
            "custom" -> cal.add(Calendar.DAY_OF_YEAR, customDays.coerceAtLeast(1L).toInt())
            else -> return currentDueIso
        }
        return isoFormat.format(cal.time)
    }

    fun greetingFrench(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (hour >= 18) "Bonsoir" else "Bonjour"
    }
}

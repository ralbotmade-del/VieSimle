package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "viesimple_security_prefs")

data class UserSecuritySettings(
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = true,
    val autoLockMinutes: Int = 5,
    val themeMode: String = "system",
    val notificationsEnabled: Boolean = true,
    val notifDeadlines: Boolean = true,
    val notifDocuments: Boolean = true,
    val notifSubscriptions: Boolean = true,
    val notifTasks: Boolean = true,
    val notifHousehold: Boolean = true,
    val notifWeeklySummary: Boolean = true,
    val notifSecurityAlerts: Boolean = true,
    val notifQuietMode: Boolean = false,
    val notifHour: Int = 9,
    val notifMinute: Int = 0,
    val notifPausedUntilMillis: Long = 0L,
    val recentSearches: List<String> = emptyList(),
    val acceptedTerms: Boolean = false,
    val senderAddress: String = "",
    val senderCity: String = "",
    val senderPhone: String = "",
    val coachMarkSeen: Boolean = false,
    val lastWorkerRunMillis: Long = 0L,
    val lastWorkerSummary: String = "",
    val calendarSyncEnabled: Boolean = false,
    val lastCalendarSyncMillis: Long = 0L,
    val lastCalendarSyncSummary: String = "",
    val lastRoomCacheSyncMillis: Long = 0L,
    val protectSensitiveDocsWithBiometric: Boolean = true,
    val appLanguage: String = "system"
)

data class DocumentUsageStat(
    val openCount: Int = 0,
    val lastOpenedAtMillis: Long = 0L
)

class SecurityPreferences(private val context: Context) {
    private object Keys {
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
        val NOTIF_DEADLINES = booleanPreferencesKey("notif_deadlines")
        val NOTIF_DOCUMENTS = booleanPreferencesKey("notif_documents")
        val NOTIF_SUBSCRIPTIONS = booleanPreferencesKey("notif_subscriptions")
        val NOTIF_TASKS = booleanPreferencesKey("notif_tasks")
        val NOTIF_HOUSEHOLD = booleanPreferencesKey("notif_household")
        val NOTIF_WEEKLY = booleanPreferencesKey("notif_weekly")
        val NOTIF_SECURITY = booleanPreferencesKey("notif_security")
        val NOTIF_QUIET = booleanPreferencesKey("notif_quiet")
        val NOTIF_HOUR = intPreferencesKey("notif_hour")
        val NOTIF_MINUTE = intPreferencesKey("notif_minute")
        val NOTIF_PAUSED_UNTIL = longPreferencesKey("notif_paused_until")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val ACCEPTED_TERMS = booleanPreferencesKey("accepted_terms")
        val FCM_DEVICE_ID = stringPreferencesKey("fcm_device_id")
        val LAST_FCM_TOKEN = stringPreferencesKey("last_fcm_token")
        val LAST_AUTO_REMINDER_DATE = stringPreferencesKey("last_auto_reminder_date")
        val SENDER_ADDRESS = stringPreferencesKey("sender_address")
        val SENDER_CITY = stringPreferencesKey("sender_city")
        val SENDER_PHONE = stringPreferencesKey("sender_phone")
        val COACH_MARK_SEEN = booleanPreferencesKey("coach_mark_seen")
        val DOCUMENT_USAGE_STATS = stringPreferencesKey("document_usage_stats")
        val LAST_WORKER_RUN_MILLIS = longPreferencesKey("last_worker_run_millis")
        val LAST_WORKER_SUMMARY = stringPreferencesKey("last_worker_summary")
        val LAST_WORKER_DEADLINE_KEY = stringPreferencesKey("last_worker_deadline_key")
        val CALENDAR_SYNC_ENABLED = booleanPreferencesKey("calendar_sync_enabled")
        val LAST_CALENDAR_SYNC_MILLIS = longPreferencesKey("last_calendar_sync_millis")
        val LAST_CALENDAR_SYNC_SUMMARY = stringPreferencesKey("last_calendar_sync_summary")
        val LAST_ROOM_CACHE_SYNC_MILLIS = longPreferencesKey("last_room_cache_sync_millis")
        val PROTECT_SENSITIVE_DOCS_BIOMETRIC = booleanPreferencesKey("protect_sensitive_docs_biometric")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
    }

    val settingsFlow: Flow<UserSecuritySettings> = context.dataStore.data.map { prefs ->
        val rawSearches = prefs[Keys.RECENT_SEARCHES].orEmpty()
        UserSecuritySettings(
            pinEnabled = prefs[Keys.PIN_ENABLED] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: true,
            autoLockMinutes = prefs[Keys.AUTO_LOCK_MINUTES] ?: 5,
            themeMode = prefs[Keys.THEME_MODE] ?: "system",
            notificationsEnabled = prefs[Keys.NOTIF_ENABLED] ?: true,
            notifDeadlines = prefs[Keys.NOTIF_DEADLINES] ?: true,
            notifDocuments = prefs[Keys.NOTIF_DOCUMENTS] ?: true,
            notifSubscriptions = prefs[Keys.NOTIF_SUBSCRIPTIONS] ?: true,
            notifTasks = prefs[Keys.NOTIF_TASKS] ?: true,
            notifHousehold = prefs[Keys.NOTIF_HOUSEHOLD] ?: true,
            notifWeeklySummary = prefs[Keys.NOTIF_WEEKLY] ?: true,
            notifSecurityAlerts = prefs[Keys.NOTIF_SECURITY] ?: true,
            notifQuietMode = prefs[Keys.NOTIF_QUIET] ?: false,
            notifHour = prefs[Keys.NOTIF_HOUR] ?: 9,
            notifMinute = prefs[Keys.NOTIF_MINUTE] ?: 0,
            notifPausedUntilMillis = prefs[Keys.NOTIF_PAUSED_UNTIL] ?: 0L,
            recentSearches = rawSearches.split("||").filter { it.isNotBlank() },
            acceptedTerms = prefs[Keys.ACCEPTED_TERMS] ?: false,
            senderAddress = prefs[Keys.SENDER_ADDRESS].orEmpty(),
            senderCity = prefs[Keys.SENDER_CITY].orEmpty(),
            senderPhone = prefs[Keys.SENDER_PHONE].orEmpty(),
            coachMarkSeen = prefs[Keys.COACH_MARK_SEEN] ?: false,
            lastWorkerRunMillis = prefs[Keys.LAST_WORKER_RUN_MILLIS] ?: 0L,
            lastWorkerSummary = prefs[Keys.LAST_WORKER_SUMMARY].orEmpty(),
            calendarSyncEnabled = prefs[Keys.CALENDAR_SYNC_ENABLED] ?: false,
            lastCalendarSyncMillis = prefs[Keys.LAST_CALENDAR_SYNC_MILLIS] ?: 0L,
            lastCalendarSyncSummary = prefs[Keys.LAST_CALENDAR_SYNC_SUMMARY].orEmpty(),
            lastRoomCacheSyncMillis = prefs[Keys.LAST_ROOM_CACHE_SYNC_MILLIS] ?: 0L,
            protectSensitiveDocsWithBiometric = prefs[Keys.PROTECT_SENSITIVE_DOCS_BIOMETRIC] ?: true,
            appLanguage = prefs[Keys.APP_LANGUAGE] ?: "system"
        )
    }

    val coachMarkPendingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        !(prefs[Keys.COACH_MARK_SEEN] ?: false)
    }

    val documentUsageFlow: Flow<Map<String, DocumentUsageStat>> = context.dataStore.data.map { prefs ->
        parseUsageStats(prefs[Keys.DOCUMENT_USAGE_STATS].orEmpty())
    }

    suspend fun setPin(pin: String) {
        val salt = CryptoVaultHelper.generateSaltHex()
        val hash = CryptoVaultHelper.hashPinWithSalt(pin, salt)
        context.dataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.PIN_HASH] = hash
            prefs[Keys.PIN_ENABLED] = true
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val salt = prefs[Keys.PIN_SALT] ?: return false
        val expectedHash = prefs[Keys.PIN_HASH] ?: return false
        return CryptoVaultHelper.hashPinWithSalt(pin, salt) == expectedHash
    }

    suspend fun disablePin() {
        context.dataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = false
            prefs.remove(Keys.PIN_HASH)
            prefs.remove(Keys.PIN_SALT)
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setProtectSensitiveDocsWithBiometric(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PROTECT_SENSITIVE_DOCS_BIOMETRIC] = enabled }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.AUTO_LOCK_MINUTES] = minutes }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setAppLanguage(languageCode: String) {
        val safe = when (languageCode.lowercase()) {
            "fr", "en", "es" -> languageCode.lowercase()
            else -> "system"
        }
        context.dataStore.edit { it[Keys.APP_LANGUAGE] = safe }
    }

    suspend fun setAcceptedTerms(accepted: Boolean) {
        context.dataStore.edit { it[Keys.ACCEPTED_TERMS] = accepted }
    }

    suspend fun updateNotificationSetting(update: (UserSecuritySettings) -> UserSecuritySettings) {
        val current = settingsFlow.first()
        val next = update(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIF_ENABLED] = next.notificationsEnabled
            prefs[Keys.NOTIF_DEADLINES] = next.notifDeadlines
            prefs[Keys.NOTIF_DOCUMENTS] = next.notifDocuments
            prefs[Keys.NOTIF_SUBSCRIPTIONS] = next.notifSubscriptions
            prefs[Keys.NOTIF_TASKS] = next.notifTasks
            prefs[Keys.NOTIF_HOUSEHOLD] = next.notifHousehold
            prefs[Keys.NOTIF_WEEKLY] = next.notifWeeklySummary
            prefs[Keys.NOTIF_SECURITY] = next.notifSecurityAlerts
            prefs[Keys.NOTIF_QUIET] = next.notifQuietMode
            prefs[Keys.NOTIF_HOUR] = next.notifHour
            prefs[Keys.NOTIF_MINUTE] = next.notifMinute
            prefs[Keys.NOTIF_PAUSED_UNTIL] = next.notifPausedUntilMillis
        }
    }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_SEARCHES].orEmpty()
                .split("||")
                .filter { it.isNotBlank() && !it.equals(trimmed, ignoreCase = true) }
            val updated = (listOf(trimmed) + current).take(8)
            prefs[Keys.RECENT_SEARCHES] = updated.joinToString("||")
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { it.remove(Keys.RECENT_SEARCHES) }
    }

    suspend fun getOrCreateFcmDeviceId(): String {
        val existing = context.dataStore.data.first()[Keys.FCM_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = "android_" + java.util.UUID.randomUUID().toString().replace("-", "").take(24)
        context.dataStore.edit { it[Keys.FCM_DEVICE_ID] = generated }
        return generated
    }

    suspend fun getLastFcmToken(): String =
        context.dataStore.data.first()[Keys.LAST_FCM_TOKEN].orEmpty()

    suspend fun setLastFcmToken(token: String) {
        context.dataStore.edit { it[Keys.LAST_FCM_TOKEN] = token.take(512) }
    }

    suspend fun getLastAutoReminderDate(): String =
        context.dataStore.data.first()[Keys.LAST_AUTO_REMINDER_DATE].orEmpty()

    suspend fun setLastAutoReminderDate(dateIso: String) {
        context.dataStore.edit { it[Keys.LAST_AUTO_REMINDER_DATE] = dateIso.take(20) }
    }

    suspend fun saveSenderProfileDetails(address: String, city: String, phone: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SENDER_ADDRESS] = address.trim().take(240)
            prefs[Keys.SENDER_CITY] = city.trim().take(80)
            prefs[Keys.SENDER_PHONE] = phone.trim().take(32)
        }
    }

    suspend fun setCoachMarkSeen(seen: Boolean = true) {
        context.dataStore.edit { it[Keys.COACH_MARK_SEEN] = seen }
    }

    suspend fun recordDocumentOpened(docId: String) {
        val cleanId = docId.trim().replace(";", "").replace("=", "").replace(":", "")
        if (cleanId.isBlank()) return
        val now = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            val map = parseUsageStats(prefs[Keys.DOCUMENT_USAGE_STATS].orEmpty()).toMutableMap()
            val prev = map[cleanId]
            map[cleanId] = DocumentUsageStat(
                openCount = (prev?.openCount ?: 0) + 1,
                lastOpenedAtMillis = now
            )
            // Keep up to 200 most recently used entries
            val trimmed = map.entries
                .sortedByDescending { it.value.lastOpenedAtMillis }
                .take(200)
                .joinToString(";") { (id, stat) ->
                    "$id=${stat.openCount}:${stat.lastOpenedAtMillis}"
                }
            prefs[Keys.DOCUMENT_USAGE_STATS] = trimmed
        }
    }

    suspend fun getLastWorkerDeadlineNotifyKey(): String =
        context.dataStore.data.first()[Keys.LAST_WORKER_DEADLINE_KEY].orEmpty()

    suspend fun setLastWorkerDeadlineNotifyKey(key: String) {
        context.dataStore.edit { it[Keys.LAST_WORKER_DEADLINE_KEY] = key.take(80) }
    }

    suspend fun setWorkerExecutionStatus(summary: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_WORKER_RUN_MILLIS] = System.currentTimeMillis()
            prefs[Keys.LAST_WORKER_SUMMARY] = summary.take(200)
        }
    }

    suspend fun setCalendarSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CALENDAR_SYNC_ENABLED] = enabled }
    }

    suspend fun setCalendarSyncStatus(summary: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_CALENDAR_SYNC_MILLIS] = System.currentTimeMillis()
            prefs[Keys.LAST_CALENDAR_SYNC_SUMMARY] = summary.take(220)
        }
    }

    suspend fun setLastRoomCacheSyncMillis(timestampMillis: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[Keys.LAST_ROOM_CACHE_SYNC_MILLIS] = timestampMillis }
    }

    suspend fun clearAllLocalPreferences() {
        context.dataStore.edit { it.clear() }
    }

    private fun parseUsageStats(raw: String): Map<String, DocumentUsageStat> {
        if (raw.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, DocumentUsageStat>()
        raw.split(";").forEach { entry ->
            val eqIdx = entry.indexOf('=')
            if (eqIdx <= 0) return@forEach
            val id = entry.substring(0, eqIdx)
            val payload = entry.substring(eqIdx + 1)
            val colonIdx = payload.indexOf(':')
            if (colonIdx <= 0) return@forEach
            val count = payload.substring(0, colonIdx).toIntOrNull() ?: 0
            val ts = payload.substring(colonIdx + 1).toLongOrNull() ?: 0L
            if (id.isNotBlank() && count > 0) {
                result[id] = DocumentUsageStat(openCount = count, lastOpenedAtMillis = ts)
            }
        }
        return result
    }
}

package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.R
import com.example.core.AppLanguages
import com.example.core.BiometricAuthHelper
import com.example.core.BiometricCapability
import com.example.core.LocaleHelper
import com.example.core.SecurityPreferences
import com.example.core.UserSecuritySettings
import com.example.export.PdfGenerator
import com.example.model.LetterTemplates
import com.example.model.SecurityLog
import com.example.model.UserProfile
import com.example.notifications.FcmSyncStatus
import com.example.notifications.ReminderBroadcastReceiver
import com.example.notifications.ReminderScheduler
import com.example.notifications.SystemCalendarSyncManager
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.StaggeredEntrance
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.OfflineCacheStatus
import kotlinx.coroutines.launch

@Composable
fun LegalDocumentDialog(
    type: String,
    onDismiss: () -> Unit
) {
    val isPrivacy = type == "privacy"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isPrivacy) "Politique de confidentialité" else "Conditions Générales d’Utilisation")
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isPrivacy) {
                    Text(
                        "1. Chiffrement et confidentialité : vos fichiers scannés sont chiffrés localement (AES-256-GCM) via le Keystore Android et vos données cloud sont cloisonnées par contrôle d’accès.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "2. Aucune revente de données : VieSimple ne revend aucune donnée personnelle et n’affiche aucune publicité.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "3. Notifications confidentielles : les rappels système ne révèlent jamais de données sensibles sur l’écran verrouillé.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "4. Vos droits (RGPD) : vous pouvez exporter l’intégralité de vos données au format JSON ou supprimer définitivement votre compte gratuitement à tout moment.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "1. Objet : VieSimple est un assistant d’organisation personnelle et familiale (documents, échéances, abonnements, tâches, modèles de courriers).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "2. Limite de responsabilité : les informations générées par l’assistant IA ou les modèles de courriers ont une valeur informative et ne constituent ni un conseil juridique, ni fiscal, ni médical.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "3. Partage familial : chaque membre contrôle le partage de ses documents. Par défaut, tout élément ajouté est strictement privé.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
fun MoreHubScreen(
    profile: UserProfile?,
    onOpenSubscriptions: () -> Unit,
    onOpenHousehold: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenLetters: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenDeleteAccount: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(stringResource(R.string.nav_more), style = MaterialTheme.typography.headlineMedium)
            Text(
                "${profile?.displayName ?: "Utilisateur"} · ${MainViewModel.planLabel(profile?.planTier ?: "free")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
        }

        item { StaggeredEntrance(index = 0) { MoreRow(stringResource(R.string.more_subs_title), stringResource(R.string.more_subs_sub), Icons.Outlined.Subscriptions, onOpenSubscriptions, "more_subs_btn") } }
        item { StaggeredEntrance(index = 1) { MoreRow(stringResource(R.string.more_household_title), stringResource(R.string.more_household_sub), Icons.Outlined.Group, onOpenHousehold, "more_household_btn") } }
        item { StaggeredEntrance(index = 2) { MoreRow(stringResource(R.string.more_ai_title), stringResource(R.string.more_ai_sub), Icons.Outlined.AutoAwesome, onOpenAi, "more_ai_btn") } }
        item { StaggeredEntrance(index = 3) { MoreRow("Modèles de courriers (${LetterTemplates.ALL.size})", stringResource(R.string.more_letters_sub), Icons.Outlined.MailOutline, onOpenLetters, "more_letters_btn") } }
        item { StaggeredEntrance(index = 4) { MoreRow(stringResource(R.string.more_search_title), stringResource(R.string.more_search_sub), Icons.Outlined.Search, onOpenSearch, "more_search_btn") } }

        item { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.more_account_security), style = MaterialTheme.typography.titleSmall) }
        item { StaggeredEntrance(index = 5) { MoreRow(stringResource(R.string.more_profile_title), stringResource(R.string.more_profile_sub), Icons.Outlined.Person, onOpenProfile, "more_profile_btn") } }
        item { StaggeredEntrance(index = 6) { MoreRow(stringResource(R.string.more_settings_title), stringResource(R.string.more_settings_sub), Icons.Outlined.Settings, onOpenSettings, "more_settings_btn") } }
        item { StaggeredEntrance(index = 7) { MoreRow(stringResource(R.string.more_notif_title), stringResource(R.string.more_notif_sub), Icons.Outlined.NotificationsActive, onOpenNotifications, "more_notif_btn") } }
        item { StaggeredEntrance(index = 8) { MoreRow(stringResource(R.string.more_premium_title), stringResource(R.string.more_premium_sub), Icons.Outlined.WorkspacePremium, onOpenPremium, "more_premium_btn") } }
        item { StaggeredEntrance(index = 9) { MoreRow(stringResource(R.string.more_support_title), stringResource(R.string.more_support_sub), Icons.Outlined.HelpOutline, onOpenSupport, "more_support_btn") } }
        item { StaggeredEntrance(index = 10) { MoreRow(stringResource(R.string.more_export_title), stringResource(R.string.more_export_sub), Icons.Outlined.Download, onOpenExport, "more_export_btn") } }
        item { StaggeredEntrance(index = 11) { MoreRow(stringResource(R.string.more_delete_title), stringResource(R.string.more_delete_sub), Icons.Outlined.DeleteForever, onOpenDeleteAccount, "more_delete_btn") } }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun MoreRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(9.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: UserProfile?,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    var confirmSignOut by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mon profil") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profile?.displayName ?: "Utilisateur VieSimple", style = MaterialTheme.typography.headlineSmall)
                    Text(profile?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Formule : ${MainViewModel.planLabel(profile?.planTier ?: "free")}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            OutlinedButton(
                onClick = { confirmSignOut = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Se déconnecter de cette session")
            }
        }
    }

    if (confirmSignOut) {
        ConfirmActionDialog(
            title = "Se déconnecter ?",
            message = "Vous pourrez vous reconnecter à tout moment avec votre compte Google.",
            confirmLabel = "Déconnexion",
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSecuritySettings,
    securityPrefs: SecurityPreferences,
    securityLogs: List<SecurityLog>,
    offlineCacheStatus: OfflineCacheStatus = OfflineCacheStatus(),
    onRefreshOfflineCache: () -> Unit = {},
    onClearOfflineCache: () -> Unit = {},
    onBack: () -> Unit,
    onLockNow: () -> Unit = {},
    onReplayCoachMark: () -> Unit = {},
    onSignOutAll: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val biometricCapability = remember { BiometricAuthHelper.checkCapability(context) }
    var showPinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Verrouillage avant le tableau de bord", style = MaterialTheme.typography.titleMedium)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Empreinte digitale / Biométrie", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    BiometricAuthHelper.capabilityLabel(biometricCapability),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.biometricEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        securityPrefs.setBiometricEnabled(enabled)
                                        onShowMessage(
                                            if (enabled) "Authentification biométrique activée à l’ouverture."
                                            else "Authentification biométrique désactivée."
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("biometric_toggle_switch")
                            )
                        }
                        if (biometricCapability == BiometricCapability.NONE_ENROLLED) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(BiometricAuthHelper.buildBiometricEnrollIntent())
                                    } catch (_: Exception) {
                                        onShowMessage("Ouvrez les paramètres Android pour enregistrer une empreinte.")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Configurer une empreinte sur l’appareil")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Documents sensibles protégés par biométrie", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Exige l’empreinte digitale ou la reconnaissance faciale avant d’afficher un document marqué sensible.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.protectSensitiveDocsWithBiometric,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        securityPrefs.setProtectSensitiveDocsWithBiometric(enabled)
                                        onShowMessage(
                                            if (enabled) "Protection biométrique des documents sensibles activée."
                                            else "Protection biométrique des documents sensibles désactivée."
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("settings_sensitive_doc_biometric_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Code PIN à 4 chiffres (secours)", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = settings.pinEnabled,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        newPin = ""; confirmPin = ""; pinError = null
                                        showPinDialog = true
                                    } else {
                                        scope.launch {
                                            securityPrefs.disablePin()
                                            onShowMessage("Code PIN désactivé.")
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("pin_toggle_switch")
                            )
                        }
                        if (settings.pinEnabled || settings.biometricEnabled) {
                            Text("Verrouillage automatique après inactivité :", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "Immédiat", 1 to "1 min", 5 to "5 min", 15 to "15 min").forEach { (mins, lbl) ->
                                    FilterChip(
                                        selected = settings.autoLockMinutes == mins,
                                        onClick = { scope.launch { securityPrefs.setAutoLockMinutes(mins) } },
                                        label = { Text(lbl) }
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onLockNow,
                                modifier = Modifier.fillMaxWidth().testTag("test_lock_now_button")
                            ) {
                                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Verrouiller et tester maintenant")
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.settings_appearance_title), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "system" to stringResource(R.string.settings_theme_system),
                                "light" to stringResource(R.string.settings_theme_light),
                                "dark" to stringResource(R.string.settings_theme_dark)
                            ).forEach { (mode, lbl) ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { scope.launch { securityPrefs.setThemeMode(mode) } },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onReplayCoachMark,
                            modifier = Modifier.fillMaxWidth().testTag("replay_coach_mark_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.HelpOutline, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Revoir le tutoriel de découverte")
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_language_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = stringResource(R.string.settings_language_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val systemLbl = stringResource(R.string.settings_language_system)
                        val updatedTemplate = stringResource(R.string.settings_language_updated, "%1\$s")
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppLanguages.ALL.forEach { option ->
                                val label = if (option.code == "system") systemLbl else option.nativeLabel
                                FilterChip(
                                    selected = settings.appLanguage == option.code,
                                    onClick = {
                                        scope.launch {
                                            securityPrefs.setAppLanguage(option.code)
                                            LocaleHelper.applySystemAppLocale(context, option.code)
                                            onShowMessage(updatedTemplate.replace("%1\$s", label))
                                        }
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("language_chip_${option.code}")
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().testTag("room_offline_cache_settings_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.settings_room_title), style = MaterialTheme.typography.titleMedium)
                        val cacheSyncTime = remember(offlineCacheStatus.lastSyncMillis) {
                            if (offlineCacheStatus.lastSyncMillis > 0L) {
                                java.text.SimpleDateFormat("dd/MM à HH:mm", java.util.Locale.FRANCE)
                                    .format(java.util.Date(offlineCacheStatus.lastSyncMillis))
                            } else {
                                "En attente de synchronisation"
                            }
                        }
                        Text(
                            text = stringResource(
                                R.string.settings_room_summary,
                                offlineCacheStatus.cachedDocumentsCount,
                                offlineCacheStatus.cachedTasksCount,
                                offlineCacheStatus.cachedSubscriptionsCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "État réseau : ${if (offlineCacheStatus.isOnline) "En ligne" else "Hors ligne"} · Dernier cache : $cacheSyncTime",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onRefreshOfflineCache,
                                modifier = Modifier.weight(1f).testTag("refresh_room_cache_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.action_refresh))
                            }
                            OutlinedButton(
                                onClick = onClearOfflineCache,
                                modifier = Modifier.weight(1f).testTag("clear_room_cache_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.action_clear))
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Sessions actives", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("Appareil actuel connecté via Google Sign-In sécurisé.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onSignOutAll, modifier = Modifier.fillMaxWidth()) {
                            Text("Déconnecter cet appareil")
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Journal de sécurité", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (securityLogs.isEmpty()) {
                item { Text("Aucun événement enregistré.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(securityLogs.take(15), key = { it.id }) { log ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(log.action, style = MaterialTheme.typography.labelLarge)
                            Text(log.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Configurer votre code PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("Code PIN (4 chiffres)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) confirmPin = it },
                        label = { Text("Confirmer le code PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    pinError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPin.length != 4) {
                        pinError = "Le code doit comporter exactement 4 chiffres."
                    } else if (newPin != confirmPin) {
                        pinError = "Les deux codes ne correspondent pas."
                    } else {
                        showPinDialog = false
                        scope.launch {
                            securityPrefs.setPin(newPin)
                            onShowMessage("Code PIN activé (hachage SHA-256 salé).")
                        }
                    }
                }) { Text("Activer") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Annuler") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(
    settings: UserSecuritySettings,
    securityPrefs: SecurityPreferences,
    fcmSyncStatus: FcmSyncStatus = FcmSyncStatus(),
    onSyncFcmNow: () -> Unit = {},
    onRunDeadlineWorkerNow: () -> Unit = {},
    onToggleCalendarSync: (Boolean) -> Unit = {},
    onSyncCalendarNow: () -> Unit = {},
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasNotifPermission by remember {
        mutableStateOf(ReminderBroadcastReceiver.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (granted) {
            onSyncFcmNow()
            onShowMessage("Permission notifications accordée et FCM synchronisé.")
        } else {
            onShowMessage("Notifications système refusées. Vous pouvez les activer dans les paramètres Android.")
        }
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            onToggleCalendarSync(true)
        } else {
            onShowMessage("Permission Calendrier refusée. Activez-la dans les paramètres Android pour synchroniser l’agenda.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notif_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                Text(
                    "Par confidentialité, les notifications push FCM et rappels VieSimple ne révèlent jamais de données sensibles (montants, noms de contrats ou détails personnels), même sur l’écran verrouillé.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("fcm_status_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Firebase Cloud Messaging (FCM)", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text = fcmSyncStatus.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (fcmSyncStatus.isRegistered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Suivi automatique actif : ${fcmSyncStatus.upcomingDeadlinesCount} échéance(s) imminente(s) · ${fcmSyncStatus.overdueTasksCount} tâche(s) en retard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth().testTag("grant_notif_permission_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Autoriser les notifications système")
                        }
                    }
                    OutlinedButton(
                        onClick = onSyncFcmNow,
                        modifier = Modifier.fillMaxWidth().testTag("sync_fcm_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Synchroniser FCM et rappels")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("deadline_worker_status_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.notif_worker_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    val lastRunFormatted = remember(settings.lastWorkerRunMillis) {
                        if (settings.lastWorkerRunMillis > 0L) {
                            java.text.SimpleDateFormat("dd/MM à HH:mm", java.util.Locale.FRANCE)
                                .format(java.util.Date(settings.lastWorkerRunMillis))
                        } else {
                            "Pas encore exécutée"
                        }
                    }
                    Text(
                        text = "Dernière vérification : $lastRunFormatted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = settings.lastWorkerSummary.ifBlank {
                            stringResource(R.string.notif_worker_default_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            if (!ReminderBroadcastReceiver.hasNotificationPermission(context) &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onRunDeadlineWorkerNow()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("run_deadline_worker_now_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.notif_worker_run_now))
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("notif_calendar_sync_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Calendrier système (rappels natifs)",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.calendarSyncEnabled,
                            onCheckedChange = { enable ->
                                if (enable && !SystemCalendarSyncManager.hasCalendarPermissions(context)) {
                                    calendarPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                } else {
                                    onToggleCalendarSync(enable)
                                }
                            },
                            modifier = Modifier.testTag("notif_calendar_sync_switch")
                        )
                    }
                    Text(
                        text = settings.lastCalendarSyncSummary.ifBlank {
                            "Crée et met à jour vos échéances Firestore dans l’agenda natif Android."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            if (!SystemCalendarSyncManager.hasCalendarPermissions(context)) {
                                calendarPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            } else {
                                onSyncCalendarNow()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("notif_sync_calendar_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Synchroniser le calendrier maintenant")
                    }
                }
            }

            NotifSwitchRow("Activer les notifications", settings.notificationsEnabled) { checked ->
                scope.launch { securityPrefs.updateNotificationSetting { it.copy(notificationsEnabled = checked) } }
            }
            NotifSwitchRow("Échéances & renouvellements", settings.notifDeadlines) { checked ->
                scope.launch { securityPrefs.updateNotificationSetting { it.copy(notifDeadlines = checked) } }
            }
            NotifSwitchRow("Documents expirant bientôt", settings.notifDocuments) { checked ->
                scope.launch { securityPrefs.updateNotificationSetting { it.copy(notifDocuments = checked) } }
            }
            NotifSwitchRow("Prélèvements d’abonnements", settings.notifSubscriptions) { checked ->
                scope.launch { securityPrefs.updateNotificationSetting { it.copy(notifSubscriptions = checked) } }
            }
            NotifSwitchRow("Tâches assignées ou en retard", settings.notifTasks) { checked ->
                scope.launch { securityPrefs.updateNotificationSetting { it.copy(notifTasks = checked) } }
            }
            NotifSwitchRow("Activité du foyer", settings.notifHousehold) { checked ->
                scope.launch { securityPrefs.updateNotificationSetting { it.copy(notifHousehold = checked) } }
            }
            NotifSwitchRow("Résumé hebdomadaire", settings.notifWeeklySummary) { checked ->
                scope.launch { securityPrefs.updateNotificationSetting { it.copy(notifWeeklySummary = checked) } }
            }
            NotifSwitchRow("Alertes de sécurité", settings.notifSecurityAlerts) { checked ->
                scope.launch { securityPrefs.updateNotificationSetting { it.copy(notifSecurityAlerts = checked) } }
            }
            NotifSwitchRow("Mode silencieux", settings.notifQuietMode) { checked ->
                scope.launch { securityPrefs.updateNotificationSetting { it.copy(notifQuietMode = checked) } }
            }

            Text("Heure des rappels quotidiens :", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8, 9, 12, 18).forEach { h ->
                    FilterChip(
                        selected = settings.notifHour == h,
                        onClick = { scope.launch { securityPrefs.updateNotificationSetting { it.copy(notifHour = h) } } },
                        label = { Text("${h}h00") }
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val until = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
                        securityPrefs.updateNotificationSetting { it.copy(notifPausedUntilMillis = until) }
                        onShowMessage("Notifications mises en pause pendant 24h.")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mettre en pause 24h")
            }

            Button(
                onClick = {
                    if (!ReminderBroadcastReceiver.hasNotificationPermission(context) &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        ReminderScheduler.sendTestSafeNotification(context)
                        onShowMessage("Notification confidentielle de test envoyée.")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Tester une notification confidentielle")
            }
        }
    }
}

@Composable
private fun NotifSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    currentPlan: String,
    onBack: () -> Unit,
    onSelectPlan: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    var annualBilling by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VieSimple Premium") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                Text(
                    "Engagement VieSimple : le chiffrement local, le code PIN, l’export RGPD et la suppression du compte sont et resteront toujours gratuits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Facturation annuelle (-25%)", style = MaterialTheme.typography.titleSmall)
                Switch(checked = annualBilling, onCheckedChange = { annualBilling = it })
            }

            PlanCard(
                title = "Gratuit",
                price = "0 €",
                features = listOf("Jusqu’à 50 documents chiffrés", "1 foyer familial", "Rappels confidentiels et tâches", "Export JSON et suppression RGPD"),
                selected = currentPlan == "free",
                onSelect = { onSelectPlan("free") }
            )
            PlanCard(
                title = "Premium Individuel",
                price = if (annualBilling) "3,75 €/mois (facturé 45 €/an)" else "4,99 €/mois",
                features = listOf("Documents et OCR illimités", "Assistant IA avancé & courriers illimités", "Historique complet des versions", "Essai gratuit de 14 jours"),
                selected = currentPlan == "premium_individual",
                onSelect = { onSelectPlan("premium_individual") }
            )
            PlanCard(
                title = "Premium Famille (jusqu’à 5 membres)",
                price = if (annualBilling) "5,99 €/mois (facturé 71,90 €/an)" else "7,99 €/mois",
                features = listOf("Tout Premium Individuel", "Foyers multiples & jusqu’à 5 membres", "Partage sélectif et suivi d’activité", "Essai gratuit de 14 jours"),
                selected = currentPlan == "premium_family",
                onSelect = { onSelectPlan("premium_family") }
            )

            OutlinedButton(
                onClick = { onShowMessage("Votre formule active (${MainViewModel.planLabel(currentPlan)}) est synchronisée avec votre compte.") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restaurer mes achats")
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    features: List<String>,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(price, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            features.forEach { f ->
                Text("• $f", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onSelect, enabled = !selected, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (selected) "Formule active" else "Choisir cette formule")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportHelpScreen(onBack: () -> Unit) {
    var dialogType by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aide, CGU & Confidentialité") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FaqCard(
                "Mes documents sont-ils visibles par les membres de mon foyer ?",
                "Non. Tout document ajouté est strictement privé par défaut. Vous décidez au cas par cas lesquels partager avec votre foyer."
            )
            FaqCard(
                "L’application fonctionne-t-elle hors ligne ?",
                "Oui. Vos données déjà chargées restent consultables hors connexion et vos modifications locales sont automatiquement synchronisées dès le retour du réseau."
            )
            FaqCard(
                "Que se passe-t-il si un membre quitte le foyer ?",
                "Il perd l’accès aux éléments du foyer, mais ses documents privés personnels restent intacts."
            )
            OutlinedButton(onClick = { dialogType = "privacy" }, modifier = Modifier.fillMaxWidth()) {
                Text("Lire la Politique de confidentialité")
            }
            OutlinedButton(onClick = { dialogType = "cgu" }, modifier = Modifier.fillMaxWidth()) {
                Text("Lire les Conditions Générales d’Utilisation")
            }
        }
    }

    dialogType?.let { LegalDocumentDialog(type = it, onDismiss = { dialogType = null }) }
}

@Composable
private fun FaqCard(q: String, a: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(q, style = MaterialTheme.typography.titleSmall)
            Text(a, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataExportScreen(
    onBack: () -> Unit,
    onExportConfirmed: (onReady: (java.io.File) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var showConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exporter mes données") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Export complet RGPD", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Générez un fichier JSON regroupant votre profil, vos documents, vos échéances, vos abonnements et vos tâches.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { showConfirm = true },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("trigger_export_btn"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Générer et partager l’export JSON")
            }
        }
    }

    if (showConfirm) {
        ConfirmActionDialog(
            title = "Confirmer l’export de vos données ?",
            message = "Le fichier généré contiendra vos données personnelles en clair pour vous permettre de les sauvegarder ou de les transférer.",
            confirmLabel = "Exporter",
            onConfirm = {
                showConfirm = false
                onExportConfirmed { file ->
                    PdfGenerator.shareFile(context, file, "application/json", "Exporter mes données VieSimple")
                }
            },
            onDismiss = { showConfirm = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDeletionScreen(
    onBack: () -> Unit,
    onConfirmDeleteAccount: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supprimer mon compte") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                Text(
                    "Attention : cette opération supprime définitivement vos documents, échéances, abonnements, tâches, journaux de sécurité et votre coffre local chiffré.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Button(
                onClick = { showConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("delete_account_btn"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Supprimer définitivement mes données")
            }
        }
    }

    if (showConfirm) {
        ConfirmActionDialog(
            title = "Suppression définitive ?",
            message = "Toutes vos données VieSimple seront effacées de façon irréversible. Voulez-vous vraiment continuer ?",
            confirmLabel = "Tout supprimer",
            isDestructive = true,
            onConfirm = {
                showConfirm = false
                onConfirmDeleteAccount()
            },
            onDismiss = { showConfirm = false }
        )
    }
}

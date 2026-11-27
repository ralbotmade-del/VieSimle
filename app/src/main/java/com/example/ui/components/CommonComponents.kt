package com.example.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.core.BiometricAuthHelper
import com.example.core.BiometricCapability
import com.example.model.DateUtils
import com.example.model.VieSimpleCategories
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningContainer
import com.example.ui.theme.UrgentRed
import com.example.ui.theme.UrgentRedContainer

@Composable
fun CategoryChipRow(
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    includeAllOption: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (includeAllOption) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onSelectCategory(null) },
                label = { Text("Toutes") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
        VieSimpleCategories.ALL.forEach { cat ->
            FilterChip(
                selected = selectedCategory == cat,
                onClick = { onSelectCategory(if (selectedCategory == cat && includeAllOption) null else cat) },
                label = { Text(cat) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
fun VisibilityBadge(visibility: String, confidentiality: String = "standard") {
    val isShared = visibility == "household"
    val bgColor = if (isShared) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fgColor = if (isShared) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isShared) Icons.Outlined.Group else Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = fgColor
                )
                Text(
                    text = if (isShared) "Foyer" else "Privé",
                    style = MaterialTheme.typography.labelSmall,
                    color = fgColor
                )
            }
        }
        if (confidentiality == "sensitive") {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Sensible",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun DeadlineBadge(dueDate: String, status: String) {
    val days = DateUtils.daysUntil(dueDate)
    val (label, bg, fg) = when {
        status == "done" -> Triple("Traitée", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        days != null && days < 0L -> Triple("En retard (${-days}j)", UrgentRedContainer, UrgentRed)
        days == 0L -> Triple("Aujourd’hui", UrgentRedContainer, UrgentRed)
        days != null && days <= 7L -> Triple("Dans ${days}j", AmberWarningContainer, AmberWarning)
        else -> Triple(DateUtils.formatFrench(dueDate), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirmer",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("confirm_dialog_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun PinLockOverlay(
    pinEnabled: Boolean = false,
    biometricEnabled: Boolean,
    onVerifyPin: (String, (Boolean) -> Unit) -> Unit,
    onBiometricUnlocked: (String) -> Unit,
    onSetupPinAndUnlock: (String) -> Unit = {},
    onSignOutFallback: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var capability by remember { mutableStateOf(BiometricAuthHelper.checkCapability(context)) }
    var enteredPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showSetupPinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var setupPinError by remember { mutableStateOf<String?>(null) }

    val biometricCanLaunch = biometricEnabled && (
        capability == BiometricCapability.AVAILABLE_BIOMETRIC ||
            (capability == BiometricCapability.AVAILABLE_DEVICE_CREDENTIAL &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !pinEnabled)
        )

    var showPinPad by remember(pinEnabled, biometricCanLaunch) {
        mutableStateOf(pinEnabled && !biometricCanLaunch)
    }

    fun triggerBiometric(ctx: Context) {
        errorMsg = null
        BiometricAuthHelper.launchBiometricPrompt(
            context = ctx,
            hasPinFallback = pinEnabled,
            onSuccess = { method -> onBiometricUnlocked(method) },
            onUsePinFallback = {
                if (pinEnabled) showPinPad = true
            },
            onError = { msg -> errorMsg = msg }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                capability = BiometricAuthHelper.checkCapability(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(biometricEnabled, capability) {
        if (biometricCanLaunch) {
            triggerBiometric(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(76.dp)
            ) {
                Icon(
                    imageVector = if (biometricCanLaunch && !showPinPad) Icons.Outlined.Fingerprint else Icons.Outlined.Lock,
                    contentDescription = "Coffre VieSimple verrouillé",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(18.dp)
                )
            }

            Text(
                text = "Accès sécurisé VieSimple",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = when {
                    showPinPad -> "Saisissez votre code PIN à 4 chiffres pour ouvrir votre tableau de bord."
                    biometricCanLaunch -> "Confirmez votre identité par empreinte digitale ou biométrie avant d’afficher vos données."
                    else -> BiometricAuthHelper.capabilityLabel(capability)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            errorMsg?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (biometricCanLaunch && !showPinPad) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Protection biométrique active", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            "Vos documents, échéances et rappels restent masqués tant que l’authentification n’est pas validée.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { triggerBiometric(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("biometric_unlock_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Déverrouiller par biométrie")
                        }
                        if (pinEnabled) {
                            OutlinedButton(
                                onClick = { showPinPad = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("switch_to_pin_button"),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Outlined.Pin, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Utiliser le code PIN")
                            }
                        }
                    }
                }
            } else if (showPinPad && pinEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) { index ->
                        val filled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            row.forEach { digit ->
                                PinKeyButton(label = digit) {
                                    if (enteredPin.length < 4) {
                                        errorMsg = null
                                        val next = enteredPin + digit
                                        enteredPin = next
                                        if (next.length == 4) {
                                            onVerifyPin(next) { ok ->
                                                if (!ok) {
                                                    enteredPin = ""
                                                    errorMsg = "Code PIN incorrect."
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (biometricCanLaunch) {
                            Surface(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        showPinPad = false
                                        triggerBiometric(context)
                                    }
                                    .testTag("biometric_unlock_button"),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Fingerprint,
                                    contentDescription = "Déverrouiller par biométrie",
                                    modifier = Modifier.padding(20.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        } else {
                            Spacer(Modifier.size(68.dp))
                        }
                        PinKeyButton(label = "0") {
                            if (enteredPin.length < 4) {
                                errorMsg = null
                                val next = enteredPin + "0"
                                enteredPin = next
                                if (next.length == 4) {
                                    onVerifyPin(next) { ok ->
                                        if (!ok) {
                                            enteredPin = ""
                                            errorMsg = "Code PIN incorrect."
                                        }
                                    }
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Effacer",
                                modifier = Modifier.padding(22.dp)
                            )
                        }
                    }
                }
            } else {
                // Biometric hardware/enrollment not configured on device and no PIN set yet
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(BiometricAuthHelper.buildBiometricEnrollIntent())
                                } catch (_: Exception) {
                                    errorMsg = "Ouvrez Paramètres > Sécurité sur votre appareil pour enregistrer une empreinte."
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("enroll_biometric_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Enregistrer une empreinte (Android)")
                        }
                        OutlinedButton(
                            onClick = {
                                newPin = ""; confirmPin = ""; setupPinError = null
                                showSetupPinDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("lock_setup_pin_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Outlined.Pin, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Créer un code PIN VieSimple (4 chiffres)")
                        }
                        TextButton(
                            onClick = { onBiometricUnlocked("Accès sans capteur configuré") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("lock_continue_unconfigured_button")
                        ) {
                            Text("Accéder au tableau de bord pour cette session")
                        }
                    }
                }
            }

            TextButton(
                onClick = onSignOutFallback,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lock_sign_out_button")
            ) {
                Text("Se déconnecter")
            }
        }
    }

    if (showSetupPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetupPinDialog = false },
            title = { Text("Créer un code PIN (4 chiffres)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("Code PIN") },
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
                    setupPinError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPin.length != 4) {
                        setupPinError = "Le code doit contenir 4 chiffres."
                    } else if (newPin != confirmPin) {
                        setupPinError = "Les deux codes ne correspondent pas."
                    } else {
                        showSetupPinDialog = false
                        onSetupPinAndUnlock(newPin)
                    }
                }) { Text("Activer et entrer") }
            },
            dismissButton = {
                TextButton(onClick = { showSetupPinDialog = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun PinKeyButton(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun OfflineCacheBanner(
    isOnline: Boolean,
    cachedDocsCount: Int,
    cachedTasksCount: Int,
    cachedSubscriptionsCount: Int = 0,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = !isOnline) {
        Surface(
            color = AmberWarningContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .testTag("offline_room_cache_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = stringResource(R.string.offline_cache_title),
                    tint = AmberWarning,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.offline_cache_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AmberWarning
                    )
                    Text(
                        text = stringResource(
                            R.string.offline_cache_summary,
                            cachedDocsCount,
                            cachedTasksCount,
                            cachedSubscriptionsCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

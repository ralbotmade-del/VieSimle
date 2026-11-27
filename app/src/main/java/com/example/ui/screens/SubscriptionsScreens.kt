package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DateUtils
import com.example.model.SubscriptionItem
import com.example.model.VaultDocument
import com.example.model.VieSimpleCategories
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.OfflineCacheBanner
import com.example.ui.components.VisibilityBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningContainer
import com.example.ui.viewmodel.OfflineCacheStatus
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    subscriptionsState: UiState<List<SubscriptionItem>>,
    offlineCacheStatus: OfflineCacheStatus = OfflineCacheStatus(),
    onBack: (() -> Unit)? = null,
    onCreateSubscription: () -> Unit,
    onEditSubscription: (SubscriptionItem) -> Unit,
    onGenerateCancelLetter: (SubscriptionItem) -> Unit,
    onDeleteSubscription: (String) -> Unit
) {
    var statusFilter by remember { mutableStateOf("all") }
    var deleteCandidate by remember { mutableStateOf<SubscriptionItem?>(null) }

    val allSubs = (subscriptionsState as? UiState.Success)?.data ?: emptyList()
    val monthlyRecurringSubs = remember(allSubs) {
        allSubs.filter { it.frequency == "monthly" && it.status != "cancelled" }
    }
    val activeSubs = remember(allSubs) { allSubs.filter { it.status == "active" || it.status == "to_cancel" } }
    val monthlyTotal = remember(activeSubs) { activeSubs.sumOf { it.monthlyEquivalent() } }
    val annualTotal = remember(activeSubs) { activeSubs.sumOf { it.annualCost() } }

    val filtered = remember(allSubs, statusFilter) {
        allSubs.filter { sub ->
            when (statusFilter) {
                "monthly" -> sub.frequency == "monthly" && sub.status != "cancelled"
                "active" -> sub.status == "active"
                "to_cancel" -> sub.status == "to_cancel" || sub.cancellationStatus != "none"
                "cancelled" -> sub.status == "cancelled"
                "paused" -> sub.status == "paused"
                else -> true
            }
        }.sortedBy { DateUtils.daysUntil(it.nextBillingDate) ?: Long.MAX_VALUE }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(stringResource(R.string.subscriptions_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateSubscription,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("add_subscription_fab")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.subscriptions_add))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onBack == null) {
                item {
                    Text(stringResource(R.string.subscriptions_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.subscriptions_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!offlineCacheStatus.isOnline) {
                item {
                    OfflineCacheBanner(
                        isOnline = offlineCacheStatus.isOnline,
                        cachedDocsCount = offlineCacheStatus.cachedDocumentsCount,
                        cachedTasksCount = offlineCacheStatus.cachedTasksCount,
                        cachedSubscriptionsCount = offlineCacheStatus.cachedSubscriptionsCount
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.subscriptions_monthly_cost),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                )
                                Text(
                                    "${"%.2f".format(monthlyTotal)} € / mois",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(R.string.subscriptions_annual_cost),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                )
                                Text(
                                    "${"%.2f".format(annualTotal)} € / an",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("subscriptions_room_cache_badge")
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.subscriptions_room_badge,
                                    offlineCacheStatus.cachedSubscriptionsCount
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "all" to stringResource(R.string.subscriptions_filter_all, allSubs.size),
                        "monthly" to stringResource(R.string.subscriptions_filter_monthly, monthlyRecurringSubs.size),
                        "active" to stringResource(R.string.subscriptions_filter_active),
                        "to_cancel" to stringResource(R.string.subscriptions_filter_to_cancel),
                        "paused" to stringResource(R.string.subscriptions_filter_paused),
                        "cancelled" to stringResource(R.string.subscriptions_filter_cancelled)
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { statusFilter = key },
                            label = { Text(label) },
                            modifier = Modifier.testTag("subs_filter_chip_$key")
                        )
                    }
                }
            }

            when (subscriptionsState) {
                is UiState.Loading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                        Text(subscriptionsState.message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                is UiState.Success -> {
                    if (filtered.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Outlined.Subscriptions, contentDescription = null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.subscriptions_empty_title), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.subscriptions_empty_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(onClick = onCreateSubscription) {
                                        Text(stringResource(R.string.subscriptions_add))
                                    }
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { sub ->
                            val daysUntilBilling = DateUtils.daysUntil(sub.nextBillingDate)
                            val billingSoon = daysUntilBilling != null && daysUntilBilling in 0L..sub.alertDaysBefore
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                                    .clickable { onEditSubscription(sub) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sub.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "${sub.category} · ${frequencyLabel(sub.frequency)} (${"%.2f".format(sub.amount)} €)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "${"%.2f".format(sub.monthlyEquivalent())} €/mois",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                subscriptionStatusLabel(sub.status),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        VisibilityBadge(sub.visibility)
                                        if (sub.cancellationStatus != "none") {
                                            Surface(color = AmberWarningContainer, shape = RoundedCornerShape(8.dp)) {
                                                Text(
                                                    text = "Résiliation : ${cancellationStatusLabel(sub.cancellationStatus)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AmberWarning,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        if (billingSoon && sub.status == "active") {
                                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp)) {
                                                Text(
                                                    text = "Prélèvement J-$daysUntilBilling",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (sub.nextBillingDate.isNotBlank()) {
                                        Text(
                                            text = buildString {
                                                append(stringResource(R.string.subscriptions_next_renewal, DateUtils.formatFrench(sub.nextBillingDate)))
                                                if (sub.paymentMethod.isNotBlank()) append(" · ${sub.paymentMethod}")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onGenerateCancelLetter(sub) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Outlined.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(6.dp))
                                            Text(stringResource(R.string.subscriptions_cancel_letter), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick = { onEditSubscription(sub) }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit))
                                        }
                                        IconButton(onClick = { deleteCandidate = sub }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    deleteCandidate?.let { sub ->
        ConfirmActionDialog(
            title = "Supprimer l’abonnement ?",
            message = "Voulez-vous supprimer « ${sub.name} » de votre suivi ?",
            confirmLabel = "Supprimer",
            isDestructive = true,
            onConfirm = {
                deleteCandidate = null
                onDeleteSubscription(sub.id)
            },
            onDismiss = { deleteCandidate = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionEditorScreen(
    initialSub: SubscriptionItem,
    documents: List<VaultDocument>,
    hasHousehold: Boolean,
    onBack: () -> Unit,
    onSave: (SubscriptionItem) -> Unit,
    onGenerateCancelLetter: (SubscriptionItem) -> Unit
) {
    var name by remember(initialSub) { mutableStateOf(initialSub.name) }
    var category by remember(initialSub) { mutableStateOf(initialSub.category.ifBlank { "Abonnements" }) }
    var amountText by remember(initialSub) { mutableStateOf(if (initialSub.amount > 0) initialSub.amount.toString() else "") }
    var frequency by remember(initialSub) { mutableStateOf(initialSub.frequency.ifBlank { "monthly" }) }
    var nextBillingDate by remember(initialSub) { mutableStateOf(initialSub.nextBillingDate.ifBlank { DateUtils.addDaysIso(DateUtils.todayIso(), 30) }) }
    var paymentMethod by remember(initialSub) { mutableStateOf(initialSub.paymentMethod) }
    var status by remember(initialSub) { mutableStateOf(initialSub.status.ifBlank { "active" }) }
    var cancellationStatus by remember(initialSub) { mutableStateOf(initialSub.cancellationStatus.ifBlank { "none" }) }
    var alertDaysText by remember(initialSub) { mutableStateOf(initialSub.alertDaysBefore.toString()) }
    var linkedDocId by remember(initialSub) { mutableStateOf(initialSub.linkedDocumentId) }
    var notes by remember(initialSub) { mutableStateOf(initialSub.notes) }
    var shareWithHousehold by remember(initialSub) { mutableStateOf(initialSub.visibility == "household") }
    var catExpanded by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val parsedAmount = amountText.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val previewSub = remember(parsedAmount, frequency) {
        SubscriptionItem(amount = parsedAmount, frequency = frequency)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialSub.id.isBlank()) "Nouvel abonnement" else "Modifier l’abonnement") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMsg = null },
                label = { Text("Nom de l’abonnement *") },
                placeholder = { Text("Ex : Assurance habitation, Fibre, Salle de sport") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("sub_name_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorMsg = null },
                    label = { Text("Montant (€) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("sub_amount_input")
                )
                OutlinedTextField(
                    value = alertDaysText,
                    onValueChange = { alertDaysText = it },
                    label = { Text("Alerte avant (jours)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            if (parsedAmount > 0) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = "Équivalent : ${"%.2f".format(previewSub.monthlyEquivalent())} €/mois · ${"%.2f".format(previewSub.annualCost())} €/an",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Text("Fréquence", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("monthly" to "Mensuel", "quarterly" to "Trimestriel", "yearly" to "Annuel").forEach { (key, label) ->
                    FilterChip(selected = frequency == key, onClick = { frequency = key }, label = { Text(label) })
                }
            }

            ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = !catExpanded }) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Catégorie") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                    VieSimpleCategories.ALL.forEach { cat ->
                        DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; catExpanded = false })
                    }
                }
            }

            OutlinedTextField(
                value = nextBillingDate,
                onValueChange = { nextBillingDate = it; errorMsg = null },
                label = { Text("Prochaine date de prélèvement (AAAA-MM-JJ)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = paymentMethod,
                onValueChange = { paymentMethod = it },
                label = { Text("Moyen de paiement (ex : Carte Boursorama, Prélèvement SEPA)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Statut", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("active" to "Actif", "to_cancel" to "À résilier", "paused" to "En pause", "cancelled" to "Résilié").forEach { (key, label) ->
                    FilterChip(selected = status == key, onClick = { status = key }, label = { Text(label) })
                }
            }

            Text("Suivi de résiliation", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "none" to "Aucune",
                    "to_send" to "À envoyer",
                    "sent" to "Envoyée",
                    "awaiting_reply" to "En attente",
                    "confirmed" to "Confirmée",
                    "refused" to "Refusée"
                ).forEach { (key, label) ->
                    FilterChip(selected = cancellationStatus == key, onClick = { cancellationStatus = key }, label = { Text(label) })
                }
            }

            if (documents.isNotEmpty()) {
                Text("Document lié (contrat / facture)", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = linkedDocId.isBlank(), onClick = { linkedDocId = "" }, label = { Text("Aucun") })
                    documents.take(8).forEach { doc ->
                        FilterChip(
                            selected = linkedDocId == doc.id,
                            onClick = { linkedDocId = doc.id },
                            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text(doc.title.take(18)) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (n° client, préavis, conditions)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            if (hasHousehold) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Partager avec le foyer", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = shareWithHousehold, onCheckedChange = { shareWithHousehold = it })
                }
            }

            errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            if (name.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        onGenerateCancelLetter(
                            initialSub.copy(
                                name = name.trim(),
                                amount = parsedAmount,
                                frequency = frequency
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Préparer un courrier de résiliation")
                }
            }

            Button(
                onClick = {
                    if (name.trim().isBlank()) {
                        errorMsg = "Le nom de l’abonnement est obligatoire."
                        return@Button
                    }
                    if (parsedAmount <= 0.0) {
                        errorMsg = "Indiquez un montant supérieur à 0 €."
                        return@Button
                    }
                    onSave(
                        initialSub.copy(
                            name = name.trim(),
                            category = category,
                            amount = parsedAmount,
                            frequency = frequency,
                            nextBillingDate = nextBillingDate.trim(),
                            paymentMethod = paymentMethod.trim(),
                            status = status,
                            cancellationStatus = cancellationStatus,
                            alertDaysBefore = alertDaysText.toLongOrNull()?.coerceIn(0L, 60L) ?: 3L,
                            linkedDocumentId = linkedDocId,
                            notes = notes.trim(),
                            visibility = if (shareWithHousehold && hasHousehold) "household" else "private"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_subscription_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Enregistrer l’abonnement", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun frequencyLabel(freq: String): String = when (freq) {
    "yearly" -> "Annuel"
    "quarterly" -> "Trimestriel"
    else -> "Mensuel"
}

private fun subscriptionStatusLabel(status: String): String = when (status) {
    "to_cancel" -> "À résilier"
    "paused" -> "En pause"
    "cancelled" -> "Résilié"
    else -> "Actif"
}

private fun cancellationStatusLabel(status: String): String = when (status) {
    "to_send" -> "À envoyer"
    "sent" -> "Envoyée"
    "awaiting_reply" -> "En attente"
    "confirmed" -> "Confirmée"
    "refused" -> "Refusée"
    else -> "Aucune"
}

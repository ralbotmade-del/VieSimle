package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.HouseholdTask
import com.example.model.SubscriptionItem
import com.example.model.UserProfile
import com.example.model.VaultDocument
import com.example.ui.components.DeadlineBadge
import com.example.ui.components.OfflineCacheBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShimmerPulseBox
import com.example.ui.components.StaggeredEntrance
import com.example.ui.components.SubscriptionExpenseChartCard
import com.example.ui.components.VisibilityBadge
import com.example.ui.components.rememberAnimatedCount
import com.example.ui.theme.UrgentRed
import com.example.ui.theme.UrgentRedContainer
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.OfflineCacheStatus
import com.example.ui.viewmodel.UiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    profile: UserProfile?,
    dashboardUiState: DashboardUiState = DashboardUiState(isSyncing = false),
    documentsState: UiState<List<VaultDocument>>,
    deadlinesState: UiState<List<Deadline>>,
    subscriptionsState: UiState<List<SubscriptionItem>>,
    tasksState: UiState<List<HouseholdTask>>,
    onOpenScan: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenLetters: () -> Unit = {},
    onOpenSearch: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenVault: () -> Unit = {},
    onOpenDeadlines: () -> Unit,
    onCreateDeadline: () -> Unit = onOpenDeadlines,
    onOpenSubscriptions: () -> Unit,
    onCreateSubscription: () -> Unit = onOpenSubscriptions,
    onOpenTasks: () -> Unit,
    onCreateTask: () -> Unit = onOpenTasks,
    onMarkDeadlineDone: (Deadline) -> Unit,
    onPostponeDeadline: (Deadline) -> Unit,
    onToggleTaskDone: (HouseholdTask, Boolean) -> Unit,
    onScanTargetPositioned: (Rect) -> Unit = {},
    onAiTargetPositioned: (Rect) -> Unit = {},
    offlineCacheStatus: OfflineCacheStatus = OfflineCacheStatus()
) {
    val docs = (documentsState as? UiState.Success)?.data ?: emptyList()
    val deadlines = (deadlinesState as? UiState.Success)?.data ?: emptyList()
    val subs = (subscriptionsState as? UiState.Success)?.data ?: emptyList()
    val tasks = (tasksState as? UiState.Success)?.data ?: emptyList()

    val isDocsLoading = documentsState is UiState.Loading
    val isDeadlinesLoading = deadlinesState is UiState.Loading
    val isTasksLoading = tasksState is UiState.Loading
    val isSubsLoading = subscriptionsState is UiState.Loading

    val pendingDeadlines = remember(deadlines) {
        deadlines.filter { it.status == "pending" }
            .sortedBy { DateUtils.daysUntil(it.dueDate) ?: Long.MAX_VALUE }
    }
    val overdueDeadlines = remember(pendingDeadlines) {
        pendingDeadlines.filter { (DateUtils.daysUntil(it.dueDate) ?: 1L) < 0L }
    }
    val thisWeekDeadlines = remember(pendingDeadlines) {
        pendingDeadlines.filter {
            val d = DateUtils.daysUntil(it.dueDate)
            d != null && d in 0L..7L
        }
    }
    val upcomingDeadlines = remember(pendingDeadlines) {
        pendingDeadlines.filter {
            val d = DateUtils.daysUntil(it.dueDate)
            d == null || d >= 0L
        }.take(5)
    }

    val expiringDocs = remember(docs) {
        docs.filter {
            val d = DateUtils.daysUntil(it.expirationDate)
            d != null && d <= 30L
        }
    }

    val activeSubs = remember(subs) { subs.filter { it.status == "active" || it.status == "to_cancel" } }
    val monthlyTotal = remember(activeSubs) { activeSubs.sumOf { it.monthlyEquivalent() } }
    val subsToWatch = remember(activeSubs) {
        activeSubs.filter {
            val d = DateUtils.daysUntil(it.nextBillingDate)
            it.status == "to_cancel" || (d != null && d in 0L..10L)
        }
    }

    val openTasks = remember(tasks) { tasks.filter { it.status == "todo" } }
    val urgentTasks = remember(openTasks) {
        openTasks.filter { t ->
            val d = DateUtils.daysUntil(t.dueDate)
            t.priority == "urgent" || t.priority == "high" || (d != null && d <= 2L)
        }.sortedBy { DateUtils.daysUntil(it.dueDate) ?: Long.MAX_VALUE }
    }
    val regularOpenTasks = remember(openTasks, urgentTasks) {
        val urgentIds = urgentTasks.map { it.id }.toSet()
        openTasks.filterNot { it.id in urgentIds }
    }

    val hasAnyData = docs.isNotEmpty() || deadlines.isNotEmpty() || subs.isNotEmpty() || tasks.isNotEmpty()
    val firstName = profile?.displayName?.substringBefore(" ")?.ifBlank { "vous" } ?: "vous"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header with personalized greeting + date + universal search bar
        item {
            val profileBadge = when (profile?.profileType) {
                "couple" -> "Espace Couple"
                "family" -> "Espace Famille"
                "roommates" -> "Colocation"
                else -> "Espace Personnel"
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${DateUtils.formatFrench(DateUtils.todayIso())} · $profileBadge",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${DateUtils.greetingFrench()}, $firstName",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenSearch)
                        .minimumInteractiveComponentSize()
                        .testTag("home_search_button"),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.action_search),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Offline Room cache indicator
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

        // KPI Summary Strip (Document counts, Upcoming deadlines, Urgent tasks, Subscriptions)
        item {
            val heroBrush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary
                )
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(heroBrush)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val animDocsCount = rememberAnimatedCount(dashboardUiState.documentCount)
                    val animDeadlinesCount = rememberAnimatedCount(dashboardUiState.pendingDeadlinesCount)
                    val animUrgentCount = rememberAnimatedCount(dashboardUiState.urgentTasksCount + dashboardUiState.overdueDeadlinesCount)
                    val animSubsRounded = rememberAnimatedCount(dashboardUiState.monthlySubscriptionsTotal.roundToInt())

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DashboardMetricTile(
                            label = stringResource(R.string.metric_docs),
                            value = if (isDocsLoading) "—" else "$animDocsCount",
                            icon = Icons.Outlined.FolderShared,
                            onClick = onOpenVault,
                            modifier = Modifier.weight(1f).testTag("dashboard_metric_docs")
                        )
                        DashboardMetricTile(
                            label = stringResource(R.string.metric_upcoming),
                            value = if (isDeadlinesLoading) "—" else "$animDeadlinesCount",
                            icon = Icons.Outlined.CalendarToday,
                            onClick = onOpenDeadlines,
                            modifier = Modifier.weight(1f).testTag("dashboard_metric_deadlines")
                        )
                        DashboardMetricTile(
                            label = stringResource(R.string.metric_urgent),
                            value = if (isTasksLoading || isDeadlinesLoading) "—" else "$animUrgentCount",
                            icon = Icons.Outlined.PriorityHigh,
                            onClick = onOpenTasks,
                            modifier = Modifier.weight(1f).testTag("dashboard_metric_urgent")
                        )
                        DashboardMetricTile(
                            label = stringResource(R.string.metric_subs),
                            value = if (isSubsLoading) "—" else "$animSubsRounded €",
                            icon = Icons.Outlined.Subscriptions,
                            onClick = onOpenSubscriptions,
                            modifier = Modifier.weight(1f).testTag("dashboard_metric_subs")
                        )
                    }

                    val summaryText = when {
                        dashboardUiState.isSyncing -> "Synchronisation de votre coffre-fort et de vos rappels…"
                        dashboardUiState.expiringDocumentsCount > 0 -> "${dashboardUiState.expiringDocumentsCount} document(s) arrivent bientôt à expiration (sous 30 jours)."
                        dashboardUiState.documentCount == 0 -> "Coffre-fort prêt : 0 document archivé pour le moment."
                        else -> "${dashboardUiState.thisWeekDeadlinesCount} échéance(s) cette semaine · ${dashboardUiState.openTasksCount} tâche(s) en cours."
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Material 3 Quick Actions (Scan + Assistant IA + Courriers PDF)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onOpenScan,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .onGloballyPositioned { onScanTargetPositioned(it.boundsInRoot()) }
                            .testTag("home_quick_scan"),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Outlined.DocumentScanner, contentDescription = "Scanner un document", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text(stringResource(R.string.action_scan), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.action_scan_sub), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), maxLines = 1)
                        }
                    }
                    FilledTonalButton(
                        onClick = onOpenAi,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .onGloballyPositioned { onAiTargetPositioned(it.boundsInRoot()) }
                            .testTag("home_quick_ai"),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.action_ai), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text(stringResource(R.string.action_ai), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.action_ai_sub), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f), maxLines = 1)
                        }
                    }
                }
                OutlinedButton(
                    onClick = onOpenLetters,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("home_quick_letters"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.action_letters_library))
                }
            }
        }

        // Dedicated Section: Cette semaine (with staggered entrance animations on data load)
        item {
            SectionHeader(title = stringResource(R.string.this_week_title), actionLabel = stringResource(R.string.this_week_action), onActionClick = onOpenDeadlines)
            val isWeekLoading = isDeadlinesLoading || isTasksLoading || isSubsLoading
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    .testTag("home_this_week_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                AnimatedContent(
                    targetState = isWeekLoading,
                    transitionSpec = {
                        fadeIn(tween(260)) togetherWith fadeOut(tween(180))
                    },
                    label = "this_week_loading_transition"
                ) { loading ->
                    if (loading) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                repeat(3) {
                                    ShimmerPulseBox(
                                        modifier = Modifier.weight(1f).height(58.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                }
                            }
                            repeat(2) {
                                ShimmerPulseBox(
                                    modifier = Modifier.fillMaxWidth().height(34.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    } else {
                        val weekDeadlinesCountAnim = rememberAnimatedCount(thisWeekDeadlines.size)
                        val openTasksCountAnim = rememberAnimatedCount(openTasks.size)
                        val subsToWatchCountAnim = rememberAnimatedCount(subsToWatch.size)

                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StaggeredEntrance(index = 0, delayStepMs = 55L, modifier = Modifier.weight(1f)) {
                                    WeekHighlightPill(
                                        count = "$weekDeadlinesCountAnim",
                                        label = "Échéances 7j",
                                        onClick = onOpenDeadlines,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                StaggeredEntrance(index = 1, delayStepMs = 55L, modifier = Modifier.weight(1f)) {
                                    WeekHighlightPill(
                                        count = "$openTasksCountAnim",
                                        label = "Tâches actives",
                                        onClick = onOpenTasks,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                StaggeredEntrance(index = 2, delayStepMs = 55L, modifier = Modifier.weight(1f)) {
                                    WeekHighlightPill(
                                        count = "$subsToWatchCountAnim",
                                        label = "Abos proches",
                                        onClick = onOpenSubscriptions,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (thisWeekDeadlines.isNotEmpty()) {
                                thisWeekDeadlines.take(3).forEachIndexed { idx, dl ->
                                    key(dl.id) {
                                        StaggeredEntrance(index = idx + 3, delayStepMs = 75L) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable(onClick = onOpenDeadlines)
                                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.CalendarToday,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = dl.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                DeadlineBadge(dl.dueDate, dl.status)
                                            }
                                        }
                                    }
                                }
                            } else {
                                StaggeredEntrance(index = 3, delayStepMs = 60L) {
                                    Text(
                                        text = "Votre semaine est calme : aucune échéance critique dans les 7 prochains jours.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Monthly Subscription Expense Visualization (Firestore subscriptions)
        item {
            SubscriptionExpenseChartCard(
                subscriptionsState = subscriptionsState,
                onOpenSubscriptions = onOpenSubscriptions,
                onAddSubscription = onCreateSubscription
            )
        }

        // Welcome empty-state banner when brand new
        if (!isDocsLoading && !isDeadlinesLoading && !hasAnyData) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_vault_1795532519257),
                            contentDescription = "Coffre-fort vide",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text("Votre espace est prêt", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Ajoutez votre premier document (assurance, facture, carte d’identité) pour activer le suivi des échéances.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onOpenScan,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("empty_add_first_doc_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Ajouter mon premier document")
                        }
                    }
                }
            }
        }

        // Section: Overdue deadlines (if any)
        if (overdueDeadlines.isNotEmpty()) {
            item {
                SectionHeader(title = "En retard", actionLabel = "Voir tout", onActionClick = onOpenDeadlines)
            }
            items(overdueDeadlines, key = { "overdue_${it.id}" }) { dl ->
                Box(modifier = Modifier.animateItem()) {
                    OverdueDeadlineCard(
                        deadline = dl,
                        onDone = { onMarkDeadlineDone(dl) },
                        onPostpone = { onPostponeDeadline(dl) }
                    )
                }
            }
        }

        // Section: Tâches urgentes (Always visible with placeholder when empty or loading)
        item {
            SectionHeader(
                title = "Tâches urgentes",
                actionLabel = if (urgentTasks.isNotEmpty()) "Voir tout" else "Ajouter",
                onActionClick = if (urgentTasks.isNotEmpty()) onOpenTasks else onCreateTask
            )
        }
        if (isTasksLoading) {
            item {
                DashboardLoadingPlaceholder("Chargement des tâches urgentes…")
            }
        } else if (urgentTasks.isEmpty()) {
            item {
                DashboardPlaceholderCard(
                    icon = Icons.Outlined.TaskAlt,
                    title = "Aucune tâche urgente",
                    description = "Les tâches prioritaires ou à échéance proche apparaîtront ici pour vous guider.",
                    actionLabel = "Créer une tâche",
                    onAction = onCreateTask,
                    testTag = "placeholder_urgent_tasks"
                )
            }
        } else {
            items(urgentTasks.take(4), key = { "urgent_task_${it.id}" }) { task ->
                Box(modifier = Modifier.animateItem()) {
                    DashboardTaskRow(
                        task = task,
                        isUrgentHighlight = true,
                        onClick = onOpenTasks,
                        onToggleDone = { onToggleTaskDone(task, it) }
                    )
                }
            }
        }

        // Section: Échéances à venir (Always visible with placeholder when empty or loading)
        item {
            SectionHeader(
                title = "Échéances à venir",
                actionLabel = if (upcomingDeadlines.isNotEmpty()) "Échéancier" else "Planifier",
                onActionClick = if (upcomingDeadlines.isNotEmpty()) onOpenDeadlines else onCreateDeadline
            )
        }
        if (isDeadlinesLoading) {
            item {
                DashboardLoadingPlaceholder("Chargement des prochaines échéances…")
            }
        } else if (upcomingDeadlines.isEmpty()) {
            item {
                DashboardPlaceholderCard(
                    icon = Icons.Outlined.EventAvailable,
                    title = "Aucune échéance à venir",
                    description = "Ajoutez un renouvellement (assurance, passeport, impôts) pour recevoir des rappels confidentiels.",
                    actionLabel = "Planifier une échéance",
                    onAction = onCreateDeadline,
                    testTag = "placeholder_upcoming_deadlines"
                )
            }
        } else {
            items(upcomingDeadlines, key = { "upcoming_${it.id}" }) { dl ->
                Box(modifier = Modifier.animateItem()) {
                    DeadlineSummaryCard(dl = dl, onClick = onOpenDeadlines)
                }
            }
        }

        // Section: Documents récents & coffre-fort
        item {
            SectionHeader(
                title = "Documents récents",
                actionLabel = "Coffre-fort (${docs.size})",
                onActionClick = onOpenVault
            )
        }
        if (isDocsLoading) {
            item {
                DashboardLoadingPlaceholder("Chargement de vos documents…")
            }
        } else if (docs.isEmpty()) {
            item {
                DashboardPlaceholderCard(
                    icon = Icons.Outlined.Description,
                    title = "0 document dans le coffre-fort",
                    description = "Centralisez vos contrats, factures et pièces d’identité avec chiffrement local.",
                    actionLabel = "Scanner un document",
                    onAction = onOpenScan,
                    testTag = "placeholder_documents"
                )
            }
        } else {
            items(docs.take(4), key = { "doc_${it.id}" }) { doc ->
                Card(
                    modifier = Modifier.fillMaxWidth().animateItem().clickable { onOpenDocument(doc.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${doc.category} · ${doc.issuer.ifBlank { "Coffre-fort" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        VisibilityBadge(doc.visibility, doc.confidentiality)
                    }
                }
            }
        }

        // Section: Autres tâches du foyer (Swipe to complete)
        if (regularOpenTasks.isNotEmpty()) {
            item {
                SectionHeader(title = "Tâches du foyer", actionLabel = "Toutes", onActionClick = onOpenTasks)
            }
            items(regularOpenTasks.take(4), key = { "task_${it.id}" }) { task ->
                Box(modifier = Modifier.animateItem()) {
                    DashboardTaskRow(
                        task = task,
                        isUrgentHighlight = false,
                        onClick = onOpenTasks,
                        onToggleDone = { onToggleTaskDone(task, it) }
                    )
                }
            }
        }

        // Section: Abonnements à surveiller
        if (subsToWatch.isNotEmpty()) {
            item {
                SectionHeader(title = "Abonnements à surveiller", actionLabel = "Gérer", onActionClick = onOpenSubscriptions)
            }
            items(subsToWatch.take(4), key = { "sub_${it.id}" }) { sub ->
                Card(
                    modifier = Modifier.fillMaxWidth().animateItem().clickable(onClick = onOpenSubscriptions),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.Subscriptions, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Prochain prélèvement : ${DateUtils.formatFrench(sub.nextBillingDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${"%.2f".format(sub.monthlyEquivalent())} €/mois",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DashboardMetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardPlaceholderCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(11.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DashboardLoadingPlaceholder(label: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTaskRow(
    task: HouseholdTask,
    isUrgentHighlight: Boolean,
    onClick: () -> Unit,
    onToggleDone: (Boolean) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd ||
            dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
        ) {
            onToggleDone(true)
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.size(8.dp))
                    Text("Glisser pour terminer", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            colors = CardDefaults.cardColors(
                containerColor = if (isUrgentHighlight) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = task.status == "done",
                    onCheckedChange = onToggleDone
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${task.category} · ${task.assigneeName.ifBlank { "Moi" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (task.dueDate.isNotBlank()) {
                    DeadlineBadge(task.dueDate, if (task.status == "done") "done" else "pending")
                }
            }
        }
    }
}

@Composable
private fun OverdueDeadlineCard(
    deadline: Deadline,
    onDone: () -> Unit,
    onPostpone: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UrgentRedContainer),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = UrgentRed)
                Text(deadline.title, style = MaterialTheme.typography.titleMedium, color = UrgentRed, modifier = Modifier.weight(1f))
                DeadlineBadge(deadline.dueDate, deadline.status)
            }
            Text(
                "${deadline.category} · Échéance dépassée (${DateUtils.formatFrench(deadline.dueDate)})",
                style = MaterialTheme.typography.bodySmall,
                color = UrgentRed
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = UrgentRed),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Marquer traitée", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onPostpone,
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Repousser +7j", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DeadlineSummaryCard(dl: Deadline, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(dl.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dl.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DeadlineBadge(dl.dueDate, dl.status)
        }
    }
}

@Composable
private fun WeekHighlightPill(
    count: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


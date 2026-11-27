package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.HouseholdMember
import com.example.model.VaultDocument
import com.example.model.VieSimpleCategories
import com.example.notifications.SystemCalendarSyncManager
import com.example.ui.components.CategoryChipRow
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.DeadlineBadge
import com.example.ui.components.VisibilityBadge
import com.example.ui.theme.UrgentRed
import com.example.ui.theme.UrgentRedContainer
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlinesScreen(
    deadlinesState: UiState<List<Deadline>>,
    members: List<HouseholdMember>,
    calendarSyncEnabled: Boolean = false,
    calendarSyncSummary: String = "",
    syncedDeadlineEventIds: Map<String, Long> = emptyMap(),
    onToggleCalendarSync: (Boolean) -> Unit = {},
    onSyncCalendarNow: () -> Unit = {},
    onCreateDeadline: () -> Unit,
    onEditDeadline: (Deadline) -> Unit,
    onMarkDone: (Deadline) -> Unit,
    onPostpone: (Deadline) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    var viewMode by remember { mutableIntStateOf(0) } // 0=Liste, 1=Calendrier, 2=Timeline
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf("pending") } // pending, urgent, done, all
    var memberFilterUid by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<Deadline?>(null) }
    var calendarNotice by remember { mutableStateOf<String?>(null) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            calendarNotice = null
            onToggleCalendarSync(true)
        } else {
            calendarNotice = "Autorisation Calendrier refusée. Vous pouvez tout de même ouvrir une échéance dans l’Agenda via son icône calendrier."
        }
    }

    val allDeadlines = (deadlinesState as? UiState.Success)?.data ?: emptyList()
    val filtered = remember(allDeadlines, selectedCategory, statusFilter, memberFilterUid) {
        allDeadlines.filter { dl ->
            val catOk = selectedCategory == null || dl.category == selectedCategory
            val memberOk = memberFilterUid == null || dl.assigneeUid == memberFilterUid
            val days = DateUtils.daysUntil(dl.dueDate)
            val statusOk = when (statusFilter) {
                "pending" -> dl.status == "pending"
                "urgent" -> dl.status == "pending" && days != null && days <= 7L
                "done" -> dl.status == "done"
                else -> true
            }
            catOk && memberOk && statusOk
        }.sortedBy { DateUtils.daysUntil(it.dueDate) ?: Long.MAX_VALUE }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateDeadline,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("add_deadline_fab")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Nouvelle échéance")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Échéancier", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Rappels intelligents et récurrences automatiques.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                val modes = listOf("Liste", "Calendrier", "Timeline")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = viewMode == idx,
                            onClick = { viewMode = idx },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size)
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("deadlines_calendar_sync_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.EventAvailable,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text("Calendrier système & rappels natifs", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = calendarSyncSummary.ifBlank {
                                            "Ajoute automatiquement vos échéances Firestore dans l’agenda de l’appareil."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = calendarSyncEnabled,
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
                                modifier = Modifier.testTag("deadlines_calendar_sync_switch")
                            )
                        }
                        calendarNotice?.let { notice ->
                            Text(
                                text = notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (calendarSyncEnabled) {
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
                                modifier = Modifier.fillMaxWidth().testTag("sync_system_calendar_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Synchroniser maintenant (${syncedDeadlineEventIds.size} liée(s))")
                            }
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
                        "pending" to "À traiter",
                        "urgent" to "Urgentes",
                        "done" to "Traitées",
                        "all" to "Toutes"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { statusFilter = key },
                            label = { Text(label) }
                        )
                    }
                    members.forEach { m ->
                        FilterChip(
                            selected = memberFilterUid == m.userId,
                            onClick = { memberFilterUid = if (memberFilterUid == m.userId) null else m.userId },
                            label = { Text(m.displayName.substringBefore(" ")) }
                        )
                    }
                }
            }

            item {
                CategoryChipRow(selectedCategory = selectedCategory, onSelectCategory = { selectedCategory = it })
            }

            if (viewMode == 1) {
                item {
                    DeadlinesCalendarView(
                        deadlines = filtered,
                        onSelectDeadline = onEditDeadline
                    )
                }
            } else {
                if (filtered.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Aucune échéance dans cette vue", style = MaterialTheme.typography.titleMedium)
                                Button(onClick = onCreateDeadline) {
                                    Text("Créer une échéance")
                                }
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { dl ->
                        val days = DateUtils.daysUntil(dl.dueDate)
                        val isOverdue = dl.status == "pending" && days != null && days < 0L
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOverdue) UrgentRedContainer else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            dl.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isOverdue) UrgentRed else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${dl.category} · ${DateUtils.formatFrench(dl.dueDate)} · ${recurrenceLabel(dl.recurrence)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isOverdue) UrgentRed else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DeadlineBadge(dl.dueDate, dl.status)
                                }

                                VisibilityBadge(dl.visibility, dl.confidentiality)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (dl.status == "pending") {
                                        OutlinedButton(
                                            onClick = { onMarkDone(dl) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("Traitée")
                                        }
                                        OutlinedButton(
                                            onClick = { onPostpone(dl) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("+7j")
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val linkedId = syncedDeadlineEventIds[dl.id]
                                            val intent = SystemCalendarSyncManager.buildOpenInCalendarIntent(
                                                deadline = dl,
                                                linkedEventId = linkedId
                                            )
                                            runCatching { context.startActivity(intent) }
                                                .onFailure {
                                                    calendarNotice = "Aucune application Agenda trouvée sur cet appareil."
                                                }
                                        },
                                        modifier = Modifier.testTag("open_in_calendar_${dl.id}")
                                    ) {
                                        Icon(
                                            Icons.Outlined.EventAvailable,
                                            contentDescription = "Ouvrir dans l’agenda système",
                                            tint = if (syncedDeadlineEventIds.containsKey(dl.id)) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                    IconButton(onClick = { onEditDeadline(dl) }) {
                                        Icon(Icons.Outlined.Edit, contentDescription = "Modifier")
                                    }
                                    IconButton(onClick = { deleteCandidate = dl }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
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

    deleteCandidate?.let { target ->
        ConfirmActionDialog(
            title = "Supprimer l’échéance ?",
            message = "Voulez-vous supprimer « ${target.title} » ?",
            confirmLabel = "Supprimer",
            isDestructive = true,
            onConfirm = {
                deleteCandidate = null
                onDelete(target.id)
            },
            onDismiss = { deleteCandidate = null }
        )
    }
}

@Composable
fun DeadlinesCalendarView(
    deadlines: List<Deadline>,
    onSelectDeadline: (Deadline) -> Unit
) {
    var monthOffset by remember { mutableIntStateOf(0) }
    val cal = remember(monthOffset) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
    }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) // Monday = 0
    val monthTitle = SimpleDateFormat("MMMM yyyy", Locale.FRENCH).format(cal.time)
        .replaceFirstChar { it.uppercase() }

    val deadlinesByDay = remember(deadlines, year, month) {
        deadlines.mapNotNull { dl ->
            val d = DateUtils.parseIso(dl.dueDate) ?: return@mapNotNull null
            val c = Calendar.getInstance().apply { time = d }
            if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) {
                c.get(Calendar.DAY_OF_MONTH) to dl
            } else null
        }.groupBy({ it.first }, { it.second })
    }

    var selectedDay by remember(monthOffset) { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { monthOffset-- }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Mois précédent")
                }
                Text(monthTitle, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { monthOffset++ }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Mois suivant")
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach { d ->
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val totalCells = firstDayOfWeek + daysInMonth
            val rows = (totalCells + 6) / 7
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - firstDayOfWeek + 1
                        if (dayNum in 1..daysInMonth) {
                            val hasEvents = deadlinesByDay[dayNum]?.isNotEmpty() == true
                            val isSelected = selectedDay == dayNum
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            hasEvents -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .clickable { selectedDay = dayNum },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (hasEvents) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.tertiary
                                                )
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            val dayEvents = deadlinesByDay[selectedDay].orEmpty()
            Text("Échéances du $selectedDay $monthTitle :", style = MaterialTheme.typography.titleSmall)
            if (dayEvents.isEmpty()) {
                Text("Aucune échéance ce jour.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                dayEvents.forEach { dl ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectDeadline(dl) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(dl.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            DeadlineBadge(dl.dueDate, dl.status)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineEditorScreen(
    initialDeadline: Deadline,
    documents: List<VaultDocument>,
    members: List<HouseholdMember>,
    hasHousehold: Boolean,
    onBack: () -> Unit,
    onSave: (Deadline) -> Unit
) {
    var title by remember(initialDeadline) { mutableStateOf(initialDeadline.title) }
    var description by remember(initialDeadline) { mutableStateOf(initialDeadline.description) }
    var category by remember(initialDeadline) { mutableStateOf(initialDeadline.category.ifBlank { "Administratif" }) }
    var dueDate by remember(initialDeadline) { mutableStateOf(initialDeadline.dueDate.ifBlank { DateUtils.addDaysIso(DateUtils.todayIso(), 7) }) }
    var recurrence by remember(initialDeadline) { mutableStateOf(initialDeadline.recurrence.ifBlank { "none" }) }
    var customDaysText by remember(initialDeadline) { mutableStateOf(if (initialDeadline.customRecurrenceDays > 0) initialDeadline.customRecurrenceDays.toString() else "30") }
    var priority by remember(initialDeadline) { mutableStateOf(initialDeadline.priority.ifBlank { "medium" }) }
    var selectedReminders by remember(initialDeadline) { mutableStateOf(initialDeadline.reminders.toSet()) }
    var linkedDocId by remember(initialDeadline) { mutableStateOf(initialDeadline.linkedDocumentId) }
    var assigneeUid by remember(initialDeadline) { mutableStateOf(initialDeadline.assigneeUid) }
    var assigneeName by remember(initialDeadline) { mutableStateOf(initialDeadline.assigneeName) }
    var isSensitive by remember(initialDeadline) { mutableStateOf(initialDeadline.confidentiality == "sensitive") }
    var shareWithHousehold by remember(initialDeadline) { mutableStateOf(initialDeadline.visibility == "household") }
    var catExpanded by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialDeadline.id.isBlank()) "Nouvelle échéance" else "Modifier l’échéance") },
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
                value = title,
                onValueChange = { title = it; errorMsg = null },
                label = { Text("Titre de l’échéance *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("deadline_title_input")
            )
            OutlinedTextField(
                value = dueDate,
                onValueChange = { dueDate = it; errorMsg = null },
                label = { Text("Date d’échéance (AAAA-MM-JJ) *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

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

            Text("Récurrence", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "none" to "Unique",
                    "weekly" to "Hebdo",
                    "monthly" to "Mensuelle",
                    "quarterly" to "Trimestrielle",
                    "yearly" to "Annuelle",
                    "custom" to "Personnalisée"
                ).forEach { (key, label) ->
                    FilterChip(selected = recurrence == key, onClick = { recurrence = key }, label = { Text(label) })
                }
            }

            if (recurrence == "custom") {
                OutlinedTextField(
                    value = customDaysText,
                    onValueChange = { customDaysText = it },
                    label = { Text("Intervalle en jours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("Rappels intelligents", style = MaterialTheme.typography.titleSmall)
            val reminderOptions = listOf(
                60L to "60 jours avant",
                30L to "30 jours avant",
                7L to "7 jours avant",
                3L to "3 jours avant",
                1L to "1 jour avant",
                0L to "Le jour même"
            )
            reminderOptions.forEach { (days, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = days in selectedReminders,
                        onCheckedChange = { checked ->
                            selectedReminders = if (checked) selectedReminders + days else selectedReminders - days
                        }
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Priorité", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("low" to "Basse", "medium" to "Normale", "high" to "Haute", "urgent" to "Urgente").forEach { (key, label) ->
                    FilterChip(selected = priority == key, onClick = { priority = key }, label = { Text(label) })
                }
            }

            if (documents.isNotEmpty()) {
                Text("Document associé (optionnel)", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = linkedDocId.isBlank(), onClick = { linkedDocId = "" }, label = { Text("Aucun") })
                    documents.take(8).forEach { d ->
                        FilterChip(
                            selected = linkedDocId == d.id,
                            onClick = { linkedDocId = d.id },
                            label = { Text(d.title.take(18)) }
                        )
                    }
                }
            }

            if (members.isNotEmpty()) {
                Text("Responsable", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    members.forEach { m ->
                        FilterChip(
                            selected = assigneeUid == m.userId,
                            onClick = { assigneeUid = m.userId; assigneeName = m.displayName },
                            label = { Text(m.displayName) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Éléments sensibles (masqués dans les alertes)", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = isSensitive, onCheckedChange = { isSensitive = it })
            }

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

            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMsg = "Le titre est obligatoire."
                        return@Button
                    }
                    if (DateUtils.parseIso(dueDate) == null) {
                        errorMsg = "Format de date attendu : AAAA-MM-JJ (ex : 2026-12-15)."
                        return@Button
                    }
                    onSave(
                        initialDeadline.copy(
                            title = title.trim(),
                            description = description.trim(),
                            category = category,
                            dueDate = dueDate.trim(),
                            recurrence = recurrence,
                            customRecurrenceDays = customDaysText.toLongOrNull() ?: 30L,
                            reminders = selectedReminders.sortedDescending(),
                            priority = priority,
                            linkedDocumentId = linkedDocId,
                            assigneeUid = assigneeUid,
                            assigneeName = assigneeName,
                            confidentiality = if (isSensitive) "sensitive" else "standard",
                            visibility = if (shareWithHousehold && hasHousehold) "household" else "private"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_deadline_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Enregistrer l’échéance", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun recurrenceLabel(rec: String): String = when (rec) {
    "weekly" -> "Hebdomadaire"
    "monthly" -> "Mensuelle"
    "quarterly" -> "Trimestrielle"
    "yearly" -> "Annuelle"
    "custom" -> "Personnalisée"
    else -> "Unique"
}

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ChecklistTemplateDef
import com.example.model.ChecklistTemplates
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.HouseholdMember
import com.example.model.HouseholdTask
import com.example.model.VaultDocument
import com.example.model.VieSimpleCategories
import com.example.ui.components.CategoryChipRow
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.DeadlineBadge
import com.example.ui.components.OfflineCacheBanner
import com.example.ui.components.VisibilityBadge
import com.example.ui.viewmodel.OfflineCacheStatus
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    tasksState: UiState<List<HouseholdTask>>,
    members: List<HouseholdMember>,
    offlineCacheStatus: OfflineCacheStatus = OfflineCacheStatus(),
    onCreateTask: () -> Unit,
    onEditTask: (HouseholdTask) -> Unit,
    onOpenChecklists: () -> Unit,
    onToggleDone: (HouseholdTask, Boolean) -> Unit,
    onToggleSubtask: (HouseholdTask, Int) -> Unit,
    onPostponeTask: (HouseholdTask) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    var timeFilter by remember { mutableStateOf("all_todo") } // today, week, overdue, all_todo, done
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var memberFilterUid by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<HouseholdTask?>(null) }

    val allTasks = (tasksState as? UiState.Success)?.data ?: emptyList()
    val filtered = remember(allTasks, timeFilter, selectedCategory, memberFilterUid) {
        allTasks.filter { t ->
            val catOk = selectedCategory == null || t.category == selectedCategory
            val memberOk = memberFilterUid == null || t.assigneeUid == memberFilterUid
            val days = DateUtils.daysUntil(t.dueDate)
            val timeOk = when (timeFilter) {
                "today" -> t.status == "todo" && days == 0L
                "week" -> t.status == "todo" && days != null && days in 0L..7L
                "overdue" -> t.status == "todo" && days != null && days < 0L
                "done" -> t.status == "done"
                else -> t.status == "todo"
            }
            catOk && memberOk && timeOk
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FloatingActionButton(
                    onClick = onOpenChecklists,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.testTag("open_checklists_fab")
                ) {
                    Icon(Icons.Outlined.Checklist, contentDescription = "Modèles de checklists")
                }
                FloatingActionButton(
                    onClick = onCreateTask,
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("add_task_fab")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Nouvelle tâche")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tâches & Checklists", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Glissez une tâche pour la terminer rapidement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onOpenChecklists) {
                        Text("Modèles")
                    }
                }
            }

            if (!offlineCacheStatus.isOnline) {
                item {
                    OfflineCacheBanner(
                        isOnline = offlineCacheStatus.isOnline,
                        cachedDocsCount = offlineCacheStatus.cachedDocumentsCount,
                        cachedTasksCount = offlineCacheStatus.cachedTasksCount
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "all_todo" to "À faire",
                        "today" to "Aujourd’hui",
                        "week" to "Cette semaine",
                        "overdue" to "En retard",
                        "done" to "Terminées"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = timeFilter == key,
                            onClick = { timeFilter = key },
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

            when (tasksState) {
                is UiState.Loading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                        Text(tasksState.message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
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
                                    Icon(Icons.Outlined.Checklist, contentDescription = null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Aucune tâche dans ce filtre", style = MaterialTheme.typography.titleMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(onClick = onCreateTask) { Text("Créer une tâche") }
                                        OutlinedButton(onClick = onOpenChecklists) { Text("Checklists prêtes") }
                                    }
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { task ->
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd ||
                                    dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
                                ) {
                                    onToggleDone(task, task.status != "done")
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier
                                    .animateItem()
                                    .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
                                backgroundContent = {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Spacer(Modifier.size(8.dp))
                                            Text(
                                                if (task.status == "done") "Rouvrir la tâche" else "Marquer terminée",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            ) {
                                TaskCardItem(
                                    task = task,
                                    onToggleDone = { onToggleDone(task, it) },
                                    onToggleSubtask = { idx -> onToggleSubtask(task, idx) },
                                    onPostpone = { onPostponeTask(task) },
                                    onEdit = { onEditTask(task) },
                                    onDelete = { deleteCandidate = task }
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }

    deleteCandidate?.let { task ->
        ConfirmActionDialog(
            title = "Supprimer la tâche ?",
            message = "Voulez-vous supprimer « ${task.title} » ?",
            confirmLabel = "Supprimer",
            isDestructive = true,
            onConfirm = {
                deleteCandidate = null
                onDeleteTask(task.id)
            },
            onDismiss = { deleteCandidate = null }
        )
    }
}

@Composable
private fun TaskCardItem(
    task: HouseholdTask,
    onToggleDone: (Boolean) -> Unit,
    onToggleSubtask: (Int) -> Unit,
    onPostpone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = task.status == "done"
    val doneSubs = task.subtasks.count { it.startsWith("1|") }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isDone, onCheckedChange = onToggleDone)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            task.category,
                            task.assigneeName.takeIf { it.isNotBlank() },
                            priorityLabel(task.priority)
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (task.dueDate.isNotBlank()) {
                    DeadlineBadge(task.dueDate, if (isDone) "done" else "pending")
                }
            }

            if (task.subtasks.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { doneSubs.toFloat() / task.subtasks.size.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "$doneSubs / ${task.subtasks.size} étapes réalisées",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                task.subtasks.take(4).forEachIndexed { idx, raw ->
                    val checked = raw.startsWith("1|")
                    val label = raw.removePrefix("1|").removePrefix("0|")
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggleSubtask(idx) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggleSubtask(idx) })
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VisibilityBadge(task.visibility, task.confidentiality)
                Row {
                    if (!isDone) {
                        IconButton(onClick = onPostpone) {
                            Icon(Icons.Outlined.Schedule, contentDescription = "Reporter +3j")
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Modifier")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    initialTask: HouseholdTask,
    documents: List<VaultDocument>,
    deadlines: List<Deadline>,
    members: List<HouseholdMember>,
    hasHousehold: Boolean,
    onBack: () -> Unit,
    onSave: (HouseholdTask) -> Unit
) {
    var title by remember(initialTask) { mutableStateOf(initialTask.title) }
    var description by remember(initialTask) { mutableStateOf(initialTask.description) }
    var category by remember(initialTask) { mutableStateOf(initialTask.category.ifBlank { "Famille" }) }
    var dueDate by remember(initialTask) { mutableStateOf(initialTask.dueDate.ifBlank { DateUtils.addDaysIso(DateUtils.todayIso(), 3) }) }
    var priority by remember(initialTask) { mutableStateOf(initialTask.priority.ifBlank { "medium" }) }
    var assigneeUid by remember(initialTask) { mutableStateOf(initialTask.assigneeUid) }
    var assigneeName by remember(initialTask) { mutableStateOf(initialTask.assigneeName) }
    var linkedDocId by remember(initialTask) { mutableStateOf(initialTask.linkedDocumentId) }
    var linkedDeadlineId by remember(initialTask) { mutableStateOf(initialTask.linkedDeadlineId) }
    var shareWithHousehold by remember(initialTask) { mutableStateOf(initialTask.visibility == "household") }
    var isSensitive by remember(initialTask) { mutableStateOf(initialTask.confidentiality == "sensitive") }
    val subtasks = remember(initialTask) { mutableStateListOf<String>().apply { addAll(initialTask.subtasks) } }
    var newSubtaskText by remember { mutableStateOf("") }
    var catExpanded by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialTask.id.isBlank()) "Nouvelle tâche" else "Modifier la tâche") },
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
                label = { Text("Titre de la tâche *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("task_title_input")
            )

            OutlinedTextField(
                value = dueDate,
                onValueChange = { dueDate = it; errorMsg = null },
                label = { Text("Date souhaitée (AAAA-MM-JJ)") },
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

            Text("Priorité", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("low" to "Basse", "medium" to "Normale", "high" to "Haute", "urgent" to "Urgente").forEach { (key, label) ->
                    FilterChip(selected = priority == key, onClick = { priority = key }, label = { Text(label) })
                }
            }

            if (members.isNotEmpty()) {
                Text("Attribuer à", style = MaterialTheme.typography.titleSmall)
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

            Text("Sous-tâches / Étapes", style = MaterialTheme.typography.titleSmall)
            subtasks.forEachIndexed { idx, raw ->
                val checked = raw.startsWith("1|")
                val label = raw.removePrefix("1|").removePrefix("0|")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { nextChecked ->
                            subtasks[idx] = if (nextChecked) "1|$label" else "0|$label"
                        }
                    )
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { subtasks.removeAt(idx) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Retirer l’étape")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSubtaskText,
                    onValueChange = { newSubtaskText = it },
                    label = { Text("Ajouter une étape") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val trimmed = newSubtaskText.trim()
                        if (trimmed.isNotBlank()) {
                            subtasks.add("0|$trimmed")
                            newSubtaskText = ""
                        }
                    }
                ) {
                    Text("+")
                }
            }

            if (documents.isNotEmpty()) {
                Text("Document lié", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = linkedDocId.isBlank(), onClick = { linkedDocId = "" }, label = { Text("Aucun") })
                    documents.take(6).forEach { doc ->
                        FilterChip(selected = linkedDocId == doc.id, onClick = { linkedDocId = doc.id }, label = { Text(doc.title.take(18)) })
                    }
                }
            }

            if (deadlines.isNotEmpty()) {
                Text("Échéance liée", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = linkedDeadlineId.isBlank(), onClick = { linkedDeadlineId = "" }, label = { Text("Aucune") })
                    deadlines.take(6).forEach { dl ->
                        FilterChip(selected = linkedDeadlineId == dl.id, onClick = { linkedDeadlineId = dl.id }, label = { Text(dl.title.take(18)) })
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Élément sensible", style = MaterialTheme.typography.bodyMedium)
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
                    if (title.trim().isBlank()) {
                        errorMsg = "Le titre de la tâche est obligatoire."
                        return@Button
                    }
                    onSave(
                        initialTask.copy(
                            title = title.trim(),
                            description = description.trim(),
                            category = category,
                            dueDate = dueDate.trim(),
                            priority = priority,
                            assigneeUid = assigneeUid,
                            assigneeName = assigneeName,
                            subtasks = subtasks.toList(),
                            linkedDocumentId = linkedDocId,
                            linkedDeadlineId = linkedDeadlineId,
                            confidentiality = if (isSensitive) "sensitive" else "standard",
                            visibility = if (shareWithHousehold && hasHousehold) "household" else "private"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_task_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Enregistrer la tâche", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistTemplatesScreen(
    hasHousehold: Boolean,
    onBack: () -> Unit,
    onCreateFromTemplate: (ChecklistTemplateDef, Boolean) -> Unit
) {
    var shareWithHousehold by remember { mutableStateOf(hasHousehold) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checklists prêtes à l’emploi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasHousehold) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Partager les checklists créées avec le foyer", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = shareWithHousehold, onCheckedChange = { shareWithHousehold = it })
                        }
                    }
                }
            }

            items(ChecklistTemplates.ALL, key = { it.id }) { tpl ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tpl.title, style = MaterialTheme.typography.titleMedium)
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    tpl.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(tpl.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        tpl.items.take(3).forEach { raw ->
                            Text("• ${raw.removePrefix("0|")}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (tpl.items.size > 3) {
                            Text("+ ${tpl.items.size - 3} autre(s) étape(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = {
                                onCreateFromTemplate(tpl, shareWithHousehold && hasHousehold)
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Utiliser cette checklist")
                        }
                    }
                }
            }
        }
    }
}

private fun priorityLabel(priority: String): String = when (priority) {
    "urgent" -> "Urgente"
    "high" -> "Haute"
    "low" -> "Basse"
    else -> "Normale"
}

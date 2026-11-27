package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FirestoreDocumentSearchResult
import com.example.data.SearchScopeTab
import com.example.data.UniversalSearchEngine
import com.example.data.UniversalSearchResults
import com.example.model.Deadline
import com.example.model.HouseholdTask
import com.example.model.SubscriptionItem
import com.example.ui.components.CategoryChipRow
import com.example.ui.components.SearchDeadlineResultCard
import com.example.ui.components.SearchDocumentResultCard
import com.example.ui.components.SearchMemberResultCard
import com.example.ui.components.SearchSubscriptionResultCard
import com.example.ui.components.SearchTagResultCard
import com.example.ui.components.SearchTaskResultCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.rememberAnimatedCount
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UniversalSearchScreen(
    results: UniversalSearchResults,
    recentSearches: List<String>,
    firestoreDocSearchState: UiState<FirestoreDocumentSearchResult>? = null,
    onQueryChange: (String) -> Unit,
    onSelectTab: (SearchScopeTab) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onDateFiltersChange: (dateQuery: String, dateFrom: String, dateTo: String) -> Unit = { _, _, _ -> },
    onTriggerFirestoreDocSearch: () -> Unit = {},
    onToggleTag: (String) -> Unit,
    onToggleActiveOnly: () -> Unit,
    onToggleHouseholdOnly: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onResetFilters: () -> Unit,
    onSaveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenTask: (HouseholdTask) -> Unit,
    onOpenSubscription: (SubscriptionItem) -> Unit,
    onOpenDeadline: (Deadline) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val filter = results.filter
    var showCategoryChips by remember { mutableStateOf(filter.selectedCategory != null) }
    var showDateFilters by remember {
        mutableStateOf(
            filter.dateQuery.isNotBlank() || filter.dateFrom.isNotBlank() || filter.dateTo.isNotBlank()
        )
    }
    val selectedTagsNormalized = remember(filter.selectedTags) {
        filter.selectedTags.map { UniversalSearchEngine.normalize(it) }.toSet()
    }

    val availableTabs = remember(results.tabCounts) {
        val base = listOf(
            SearchScopeTab.ALL,
            SearchScopeTab.DOCUMENTS,
            SearchScopeTab.TASKS,
            SearchScopeTab.SUBSCRIPTIONS,
            SearchScopeTab.TAGS,
            SearchScopeTab.DEADLINES
        )
        if ((results.tabCounts[SearchScopeTab.MEMBERS] ?: 0) > 0) base + SearchScopeTab.MEMBERS else base
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Recherche universelle", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (results.isSyncing) {
                                "Synchronisation en cours…"
                            } else if (filter.hasActiveFilters || filter.activeTab != SearchScopeTab.ALL) {
                                "${results.visibleCount} résultat${if (results.visibleCount > 1) "s" else ""}"
                            } else {
                                "Documents, tâches, abonnements et tags"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("search_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (filter.hasActiveFilters || filter.activeTab != SearchScopeTab.ALL) {
                        TextButton(
                            onClick = onResetFilters,
                            modifier = Modifier.testTag("search_top_reset_btn")
                        ) {
                            Text("Réinitialiser")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (results.isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Real-time search input
                item {
                    OutlinedTextField(
                        value = filter.query,
                        onValueChange = onQueryChange,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (filter.query.isNotBlank()) {
                                    IconButton(
                                        onClick = { onSaveRecentSearch(filter.query) },
                                        modifier = Modifier.testTag("universal_search_save_btn")
                                    ) {
                                        Icon(
                                            Icons.Outlined.BookmarkAdd,
                                            contentDescription = "Enregistrer la recherche"
                                        )
                                    }
                                    IconButton(
                                        onClick = { onQueryChange("") },
                                        modifier = Modifier.testTag("universal_search_clear")
                                    ) {
                                        Icon(Icons.Outlined.Clear, contentDescription = "Effacer la recherche")
                                    }
                                }
                            }
                        },
                        placeholder = { Text("Document, OCR, tâche, abonnement, #tag…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (filter.query.isNotBlank()) onSaveRecentSearch(filter.query)
                                focusManager.clearFocus()
                            }
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("universal_search_input")
                    )
                }

                // Scope tabs with live counts
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableTabs, key = { it.name }) { tab ->
                            val count = results.tabCounts[tab] ?: 0
                            val selected = filter.activeTab == tab
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectTab(tab) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = tabIcon(tab),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("${tab.label} ($count)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                // Quick criteria filters + Category & Date toggles + Firestore Document Search
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = showCategoryChips || filter.selectedCategory != null,
                            onClick = { showCategoryChips = !showCategoryChips },
                            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = {
                                Text(filter.selectedCategory?.let { "Catégorie : $it" } ?: "Catégories")
                            },
                            modifier = Modifier.testTag("search_toggle_categories_chip")
                        )
                        FilterChip(
                            selected = showDateFilters || filter.dateQuery.isNotBlank() || filter.dateFrom.isNotBlank() || filter.dateTo.isNotBlank(),
                            onClick = { showDateFilters = !showDateFilters },
                            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Date") },
                            modifier = Modifier.testTag("search_toggle_date_chip")
                        )
                        AssistChip(
                            onClick = {
                                focusManager.clearFocus()
                                onTriggerFirestoreDocSearch()
                            },
                            leadingIcon = { Icon(Icons.Outlined.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(stringResource(R.string.doc_search_firestore_btn)) },
                            modifier = Modifier.testTag("search_firestore_docs_btn")
                        )
                        FilterChip(
                            selected = filter.onlyActiveOrOpen,
                            onClick = onToggleActiveOnly,
                            leadingIcon = if (filter.onlyActiveOrOpen) {
                                { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = { Text("Actifs / En cours") },
                            modifier = Modifier.testTag("search_filter_active_chip")
                        )
                        FilterChip(
                            selected = filter.onlyHouseholdShared,
                            onClick = onToggleHouseholdOnly,
                            leadingIcon = { Icon(Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Partagés Foyer") },
                            modifier = Modifier.testTag("search_filter_household_chip")
                        )
                        FilterChip(
                            selected = filter.onlyFavorites,
                            onClick = onToggleFavoritesOnly,
                            leadingIcon = { Icon(Icons.Outlined.StarBorder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Favoris") },
                            modifier = Modifier.testTag("search_filter_favorites_chip")
                        )
                    }
                }

                // Expandable category chip strip
                item {
                    AnimatedVisibility(visible = showCategoryChips || filter.selectedCategory != null) {
                        CategoryChipRow(
                            selectedCategory = filter.selectedCategory,
                            onSelectCategory = onSelectCategory
                        )
                    }
                }

                // Expandable date filter strip + Firestore search status
                item {
                    AnimatedVisibility(
                        visible = showDateFilters ||
                            filter.dateQuery.isNotBlank() ||
                            filter.dateFrom.isNotBlank() ||
                            filter.dateTo.isNotBlank() ||
                            firestoreDocSearchState != null
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (showDateFilters || filter.dateQuery.isNotBlank() || filter.dateFrom.isNotBlank() || filter.dateTo.isNotBlank()) {
                                OutlinedTextField(
                                    value = filter.dateQuery,
                                    onValueChange = { onDateFiltersChange(it, filter.dateFrom, filter.dateTo) },
                                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
                                    placeholder = { Text(stringResource(R.string.doc_search_date_placeholder)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("search_date_query_input")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = filter.dateFrom,
                                        onValueChange = { onDateFiltersChange(filter.dateQuery, it, filter.dateTo) },
                                        label = { Text(stringResource(R.string.doc_search_date_from)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f).testTag("search_date_from_input")
                                    )
                                    OutlinedTextField(
                                        value = filter.dateTo,
                                        onValueChange = { onDateFiltersChange(filter.dateQuery, filter.dateFrom, it) },
                                        label = { Text(stringResource(R.string.doc_search_date_to)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f).testTag("search_date_to_input")
                                    )
                                }
                            }

                            when (firestoreDocSearchState) {
                                is UiState.Loading -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(stringResource(R.string.doc_search_loading), style = MaterialTheme.typography.labelMedium)
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                                is UiState.Success -> {
                                    val fsRes = firestoreDocSearchState.data
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("search_firestore_status")
                                    ) {
                                        Text(
                                            text = buildString {
                                                append(
                                                    if (fsRes.usedServerQuery) {
                                                        stringResource(R.string.doc_search_results_server, fsRes.matches.size)
                                                    } else {
                                                        stringResource(R.string.doc_search_results_cache, fsRes.matches.size)
                                                    }
                                                )
                                                append(" (${fsRes.appliedFirestoreFilters.joinToString(", ")})")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                is UiState.Error -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = firestoreDocSearchState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                null -> Unit
                            }
                        }
                    }
                }

                // Interactive Tags quick-filter strip
                if (results.allTags.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Label,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (filter.selectedTags.isEmpty()) "Filtrer par tag"
                                    else "Tags sélectionnés (${filter.selectedTags.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(results.allTags, key = { "tag_chip_${it.normalizedTag}" }) { tagItem ->
                                    val selected = tagItem.normalizedTag in selectedTagsNormalized
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onToggleTag(tagItem.displayTag) },
                                        leadingIcon = if (selected) {
                                            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                        } else null,
                                        label = { Text("#${tagItem.displayTag} (${tagItem.documentCount})") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        modifier = Modifier.testTag("search_tag_chip_${tagItem.normalizedTag}")
                                    )
                                }
                            }
                        }
                    }
                }

                // Discovery state when no query, no filters, and "Tout" tab selected
                if (!filter.hasActiveFilters && filter.activeTab == SearchScopeTab.ALL) {
                    if (recentSearches.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text("Recherches récentes", style = MaterialTheme.typography.titleSmall)
                                    }
                                    TextButton(onClick = onClearRecentSearches) {
                                        Text("Effacer")
                                    }
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    recentSearches.forEach { term ->
                                        AssistChip(
                                            onClick = { onQueryChange(term) },
                                            label = { Text(term) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Explorer votre espace", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SearchScopeOverviewTile(
                                    title = "Documents",
                                    count = results.tabCounts[SearchScopeTab.DOCUMENTS] ?: 0,
                                    icon = Icons.Outlined.Description,
                                    onClick = { onSelectTab(SearchScopeTab.DOCUMENTS) },
                                    modifier = Modifier.weight(1f)
                                )
                                SearchScopeOverviewTile(
                                    title = "Tâches",
                                    count = results.tabCounts[SearchScopeTab.TASKS] ?: 0,
                                    icon = Icons.Outlined.CheckBox,
                                    onClick = { onSelectTab(SearchScopeTab.TASKS) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SearchScopeOverviewTile(
                                    title = "Abonnements",
                                    count = results.tabCounts[SearchScopeTab.SUBSCRIPTIONS] ?: 0,
                                    icon = Icons.Outlined.Subscriptions,
                                    onClick = { onSelectTab(SearchScopeTab.SUBSCRIPTIONS) },
                                    modifier = Modifier.weight(1f)
                                )
                                SearchScopeOverviewTile(
                                    title = "Tags",
                                    count = results.tabCounts[SearchScopeTab.TAGS] ?: 0,
                                    icon = Icons.Outlined.Label,
                                    onClick = { onSelectTab(SearchScopeTab.TAGS) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Live filtered results (shown whenever searching, filtering, or inside a specific tab)
                val showResultsList = filter.hasActiveFilters || filter.activeTab != SearchScopeTab.ALL
                if (showResultsList) {
                    if (results.visibleCount == 0 && !results.isSyncing) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth().testTag("search_empty_state")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.SearchOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                    Text(
                                        text = if (filter.query.isNotBlank()) "Aucun résultat pour « ${filter.query.trim()} »"
                                        else "Aucun élément ne correspond à ces filtres",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Essayez un autre mot-clé, vérifiez sans accent, ou élargissez les filtres de catégorie et de tags.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (filter.hasActiveFilters || filter.activeTab != SearchScopeTab.ALL) {
                                        OutlinedButton(
                                            onClick = onResetFilters,
                                            modifier = Modifier.testTag("search_reset_filters_btn"),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Réinitialiser les filtres")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val isAllTab = filter.activeTab == SearchScopeTab.ALL

                        // 1. Tags section
                        if ((isAllTab || filter.activeTab == SearchScopeTab.TAGS) && results.matchingTags.isNotEmpty()) {
                            val tagsToShow = if (isAllTab) results.matchingTags.take(4) else results.matchingTags
                            item {
                                SectionHeader(
                                    title = "Tags (${results.matchingTags.size})",
                                    actionLabel = if (isAllTab && results.matchingTags.size > 4) "Voir tout" else null,
                                    onActionClick = if (isAllTab && results.matchingTags.size > 4) {
                                        { onSelectTab(SearchScopeTab.TAGS) }
                                    } else null
                                )
                            }
                            items(tagsToShow, key = { "res_tag_${it.normalizedTag}" }) { tagSummary ->
                                SearchTagResultCard(
                                    tagSummary = tagSummary,
                                    query = filter.query,
                                    isSelected = tagSummary.normalizedTag in selectedTagsNormalized,
                                    onToggleTag = { onToggleTag(tagSummary.displayTag) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        // 2. Documents section
                        if ((isAllTab || filter.activeTab == SearchScopeTab.DOCUMENTS) && results.matchingDocuments.isNotEmpty()) {
                            val docsToShow = if (isAllTab) results.matchingDocuments.take(5) else results.matchingDocuments
                            item {
                                SectionHeader(
                                    title = "Documents (${results.matchingDocuments.size})",
                                    actionLabel = if (isAllTab && results.matchingDocuments.size > 5) "Voir tout" else null,
                                    onActionClick = if (isAllTab && results.matchingDocuments.size > 5) {
                                        { onSelectTab(SearchScopeTab.DOCUMENTS) }
                                    } else null
                                )
                            }
                            items(docsToShow, key = { "res_doc_${it.item.id}" }) { match ->
                                SearchDocumentResultCard(
                                    match = match,
                                    query = filter.query,
                                    selectedTagsNormalized = selectedTagsNormalized,
                                    onOpen = {
                                        if (filter.query.isNotBlank()) onSaveRecentSearch(filter.query)
                                        onOpenDocument(match.item.id)
                                    },
                                    onToggleTag = onToggleTag,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        // 3. Tasks section
                        if ((isAllTab || filter.activeTab == SearchScopeTab.TASKS) && results.matchingTasks.isNotEmpty()) {
                            val tasksToShow = if (isAllTab) results.matchingTasks.take(5) else results.matchingTasks
                            item {
                                SectionHeader(
                                    title = "Tâches (${results.matchingTasks.size})",
                                    actionLabel = if (isAllTab && results.matchingTasks.size > 5) "Voir tout" else null,
                                    onActionClick = if (isAllTab && results.matchingTasks.size > 5) {
                                        { onSelectTab(SearchScopeTab.TASKS) }
                                    } else null
                                )
                            }
                            items(tasksToShow, key = { "res_task_${it.item.id}" }) { match ->
                                SearchTaskResultCard(
                                    match = match,
                                    query = filter.query,
                                    onOpen = {
                                        if (filter.query.isNotBlank()) onSaveRecentSearch(filter.query)
                                        onOpenTask(match.item)
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        // 4. Subscriptions section
                        if ((isAllTab || filter.activeTab == SearchScopeTab.SUBSCRIPTIONS) && results.matchingSubscriptions.isNotEmpty()) {
                            val subsToShow = if (isAllTab) results.matchingSubscriptions.take(5) else results.matchingSubscriptions
                            item {
                                SectionHeader(
                                    title = "Abonnements (${results.matchingSubscriptions.size})",
                                    actionLabel = if (isAllTab && results.matchingSubscriptions.size > 5) "Voir tout" else null,
                                    onActionClick = if (isAllTab && results.matchingSubscriptions.size > 5) {
                                        { onSelectTab(SearchScopeTab.SUBSCRIPTIONS) }
                                    } else null
                                )
                            }
                            items(subsToShow, key = { "res_sub_${it.item.id}" }) { match ->
                                SearchSubscriptionResultCard(
                                    match = match,
                                    query = filter.query,
                                    onOpen = {
                                        if (filter.query.isNotBlank()) onSaveRecentSearch(filter.query)
                                        onOpenSubscription(match.item)
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        // 5. Deadlines section
                        if ((isAllTab || filter.activeTab == SearchScopeTab.DEADLINES) && results.matchingDeadlines.isNotEmpty()) {
                            val deadlinesToShow = if (isAllTab) results.matchingDeadlines.take(5) else results.matchingDeadlines
                            item {
                                SectionHeader(
                                    title = "Échéances (${results.matchingDeadlines.size})",
                                    actionLabel = if (isAllTab && results.matchingDeadlines.size > 5) "Voir tout" else null,
                                    onActionClick = if (isAllTab && results.matchingDeadlines.size > 5) {
                                        { onSelectTab(SearchScopeTab.DEADLINES) }
                                    } else null
                                )
                            }
                            items(deadlinesToShow, key = { "res_dl_${it.item.id}" }) { match ->
                                SearchDeadlineResultCard(
                                    match = match,
                                    query = filter.query,
                                    onOpen = {
                                        if (filter.query.isNotBlank()) onSaveRecentSearch(filter.query)
                                        onOpenDeadline(match.item)
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        // 6. Household Members section
                        if ((isAllTab || filter.activeTab == SearchScopeTab.MEMBERS) && results.matchingMembers.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Membres du foyer (${results.matchingMembers.size})")
                            }
                            items(results.matchingMembers, key = { "res_mem_${it.id}" }) { member ->
                                SearchMemberResultCard(
                                    member = member,
                                    query = filter.query,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SearchScopeOverviewTile(
    title: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
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
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
            val animatedCount = rememberAnimatedCount(count)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$animatedCount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun tabIcon(tab: SearchScopeTab): ImageVector = when (tab) {
    SearchScopeTab.ALL -> Icons.Outlined.FilterList
    SearchScopeTab.DOCUMENTS -> Icons.Outlined.Description
    SearchScopeTab.TASKS -> Icons.Outlined.CheckBox
    SearchScopeTab.SUBSCRIPTIONS -> Icons.Outlined.Subscriptions
    SearchScopeTab.TAGS -> Icons.Outlined.Label
    SearchScopeTab.DEADLINES -> Icons.Outlined.CalendarToday
    SearchScopeTab.MEMBERS -> Icons.Outlined.Group
}

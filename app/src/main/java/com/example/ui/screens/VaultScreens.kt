package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.BiometricAuthHelper
import com.example.core.BiometricCapability
import com.example.data.DocumentSmartSorter
import com.example.data.DocumentSortMode
import com.example.data.FirestoreDocumentSearchResult
import com.example.data.FrequentTagStat
import com.example.data.RankedVaultDocument
import com.example.data.ScoredSearchMatch
import com.example.data.UniversalSearchEngine
import com.example.export.PdfGenerator
import com.example.model.DateUtils
import com.example.model.Household
import com.example.model.VaultDocument
import com.example.model.VieSimpleCategories
import com.example.ui.components.CategoryChipRow
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.OfflineCacheBanner
import com.example.ui.components.PhotoRetouchDialog
import com.example.ui.components.VisibilityBadge
import com.example.ui.viewmodel.OfflineCacheStatus
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class VaultDatePreset {
    ALL,
    TODAY,
    THIS_MONTH,
    THIS_YEAR,
    CUSTOM
}

private data class VaultDisplayItem(
    val ranked: RankedVaultDocument,
    val searchMatch: ScoredSearchMatch<VaultDocument>? = null
)

@Composable
fun DocumentsVaultScreen(
    documentsState: UiState<List<VaultDocument>>,
    rankedDocumentsState: UiState<List<RankedVaultDocument>>? = null,
    frequentTags: List<FrequentTagStat> = emptyList(),
    sortMode: DocumentSortMode = DocumentSortMode.SMART_AUTO,
    onSortModeChange: (DocumentSortMode) -> Unit = {},
    offlineCacheStatus: OfflineCacheStatus = OfflineCacheStatus(),
    firestoreSearchState: UiState<FirestoreDocumentSearchResult>? = null,
    onSearchInFirestore: (keywords: String, category: String?, dateQuery: String, dateFrom: String, dateTo: String, onlyFavorites: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onClearFirestoreSearch: () -> Unit = {},
    onOpenDocument: (String) -> Unit,
    onOpenScan: () -> Unit,
    onAddManualDocument: () -> Unit,
    onOpenLetters: () -> Unit = {},
    onToggleFavorite: (VaultDocument) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedTagNorm by remember { mutableStateOf<String?>(null) }
    var onlyFavorites by remember { mutableStateOf(false) }

    var datePreset by remember { mutableStateOf(VaultDatePreset.ALL) }
    var customDateQuery by remember { mutableStateOf("") }
    var customDateFrom by remember { mutableStateOf("") }
    var customDateTo by remember { mutableStateOf("") }

    val todayIso = remember { DateUtils.todayIso() }
    val effectiveDateQuery = if (datePreset == VaultDatePreset.CUSTOM) customDateQuery.trim() else ""
    val effectiveDateFrom = when (datePreset) {
        VaultDatePreset.ALL -> ""
        VaultDatePreset.TODAY -> todayIso
        VaultDatePreset.THIS_MONTH -> todayIso.take(7)
        VaultDatePreset.THIS_YEAR -> todayIso.take(4)
        VaultDatePreset.CUSTOM -> customDateFrom.trim()
    }
    val effectiveDateTo = when (datePreset) {
        VaultDatePreset.ALL -> ""
        VaultDatePreset.TODAY -> todayIso
        VaultDatePreset.THIS_MONTH -> todayIso.take(7)
        VaultDatePreset.THIS_YEAR -> todayIso.take(4)
        VaultDatePreset.CUSTOM -> customDateTo.trim()
    }
    val hasSearchCriteria = searchQuery.isNotBlank() ||
        selectedCategory != null ||
        datePreset != VaultDatePreset.ALL ||
        onlyFavorites

    LaunchedEffect(
        searchQuery,
        selectedCategory,
        effectiveDateQuery,
        effectiveDateFrom,
        effectiveDateTo,
        onlyFavorites
    ) {
        if (hasSearchCriteria) {
            delay(420L)
            onSearchInFirestore(
                searchQuery,
                selectedCategory,
                effectiveDateQuery,
                effectiveDateFrom,
                effectiveDateTo,
                onlyFavorites
            )
        } else {
            onClearFirestoreSearch()
        }
    }

    val effectiveState: UiState<List<RankedVaultDocument>> = rankedDocumentsState ?: when (documentsState) {
        is UiState.Loading -> UiState.Loading
        is UiState.Error -> documentsState
        is UiState.Success -> UiState.Success(
            DocumentSmartSorter.sortDocuments(documentsState.data, emptyMap(), sortMode)
        )
    }

    val allRanked = (effectiveState as? UiState.Success)?.data ?: emptyList()
    val allDocs = remember(allRanked) { allRanked.map { it.document } }
    val rankedById = remember(allRanked) { allRanked.associateBy { it.document.id } }
    val folders = remember(allDocs) { allDocs.map { it.folder.ifBlank { "Général" } }.distinct().sorted() }
    val computedFrequentTags = remember(frequentTags, allDocs) {
        if (frequentTags.isNotEmpty()) frequentTags else DocumentSmartSorter.computeFrequentTags(allDocs, emptyMap())
    }

    val firestoreSuccess = firestoreSearchState as? UiState.Success<FirestoreDocumentSearchResult>

    val displayItems: List<VaultDisplayItem> = remember(
        allRanked,
        rankedById,
        firestoreSuccess,
        hasSearchCriteria,
        searchQuery,
        selectedCategory,
        effectiveDateQuery,
        effectiveDateFrom,
        effectiveDateTo,
        selectedFolder,
        selectedTagNorm,
        onlyFavorites
    ) {
        if (firestoreSuccess != null && hasSearchCriteria) {
            firestoreSuccess.data.matches.mapNotNull { match ->
                val doc = match.item
                val matchesFolder = selectedFolder == null || doc.folder.ifBlank { "Général" } == selectedFolder
                val matchesTag = selectedTagNorm == null || doc.tags.any {
                    UniversalSearchEngine.normalize(it.removePrefix("#")) == selectedTagNorm
                }
                if (!matchesFolder || !matchesTag) null
                else VaultDisplayItem(
                    ranked = rankedById[doc.id] ?: RankedVaultDocument(
                        document = doc,
                        score = match.score.toDouble(),
                        openCount = 0,
                        topFrequentTag = doc.tags.firstOrNull(),
                        reasonBadge = match.matchReasons.firstOrNull()
                    ),
                    searchMatch = match
                )
            }
        } else {
            val baseMatches = allRanked.mapNotNull { ranked ->
                val doc = ranked.document
                val matchesFolder = selectedFolder == null || doc.folder.ifBlank { "Général" } == selectedFolder
                val matchesTag = selectedTagNorm == null || doc.tags.any {
                    UniversalSearchEngine.normalize(it.removePrefix("#")) == selectedTagNorm
                }
                if (!matchesFolder || !matchesTag) return@mapNotNull null

                if (hasSearchCriteria) {
                    val scored = UniversalSearchEngine.scoreDocumentMatch(
                        doc = doc,
                        query = searchQuery,
                        selectedCategory = selectedCategory,
                        dateQuery = effectiveDateQuery,
                        dateFrom = effectiveDateFrom,
                        dateTo = effectiveDateTo,
                        onlyFavorites = onlyFavorites
                    ) ?: return@mapNotNull null
                    VaultDisplayItem(ranked = ranked, searchMatch = scored)
                } else {
                    VaultDisplayItem(ranked = ranked, searchMatch = null)
                }
            }
            if (searchQuery.isNotBlank()) {
                baseMatches.sortedByDescending { it.searchMatch?.score ?: 0 }
            } else {
                baseMatches
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingActionButton(
                    onClick = onOpenScan,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.testTag("vault_scan_fab")
                ) {
                    Icon(Icons.Outlined.DocumentScanner, contentDescription = "Scanner un document")
                }
                FloatingActionButton(
                    onClick = onAddManualDocument,
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("vault_add_fab")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Ajouter un document")
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
                        Text("Coffre-fort", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Vos documents chiffrés localement et protégés par contrôle d’accès.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenLetters,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("vault_letters_btn")
                    ) {
                        Icon(Icons.Outlined.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Courriers", style = MaterialTheme.typography.labelMedium)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("vault_search_clear_btn")
                                ) {
                                    Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.action_clear))
                                }
                            }
                        },
                        placeholder = { Text("Mots-clés, organisme, tag, date ou texte OCR…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                onSearchInFirestore(
                                    searchQuery,
                                    selectedCategory,
                                    effectiveDateQuery,
                                    effectiveDateFrom,
                                    effectiveDateTo,
                                    onlyFavorites
                                )
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("vault_search_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                focusManager.clearFocus()
                                onSearchInFirestore(
                                    searchQuery,
                                    selectedCategory,
                                    effectiveDateQuery,
                                    effectiveDateFrom,
                                    effectiveDateTo,
                                    onlyFavorites
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("vault_firestore_search_btn")
                        ) {
                            Icon(Icons.Outlined.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.doc_search_firestore_btn), style = MaterialTheme.typography.labelMedium)
                        }

                        FilterChip(
                            selected = datePreset == VaultDatePreset.ALL,
                            onClick = { datePreset = VaultDatePreset.ALL },
                            label = { Text(stringResource(R.string.doc_search_date_all)) },
                            modifier = Modifier.testTag("vault_date_all")
                        )
                        FilterChip(
                            selected = datePreset == VaultDatePreset.TODAY,
                            onClick = {
                                datePreset = if (datePreset == VaultDatePreset.TODAY) VaultDatePreset.ALL else VaultDatePreset.TODAY
                            },
                            leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text(stringResource(R.string.doc_search_date_today)) },
                            modifier = Modifier.testTag("vault_date_today")
                        )
                        FilterChip(
                            selected = datePreset == VaultDatePreset.THIS_MONTH,
                            onClick = {
                                datePreset = if (datePreset == VaultDatePreset.THIS_MONTH) VaultDatePreset.ALL else VaultDatePreset.THIS_MONTH
                            },
                            label = { Text(stringResource(R.string.doc_search_date_month)) },
                            modifier = Modifier.testTag("vault_date_month")
                        )
                        FilterChip(
                            selected = datePreset == VaultDatePreset.THIS_YEAR,
                            onClick = {
                                datePreset = if (datePreset == VaultDatePreset.THIS_YEAR) VaultDatePreset.ALL else VaultDatePreset.THIS_YEAR
                            },
                            label = { Text(stringResource(R.string.doc_search_date_year)) },
                            modifier = Modifier.testTag("vault_date_year")
                        )
                        FilterChip(
                            selected = datePreset == VaultDatePreset.CUSTOM,
                            onClick = {
                                datePreset = if (datePreset == VaultDatePreset.CUSTOM) VaultDatePreset.ALL else VaultDatePreset.CUSTOM
                            },
                            label = { Text(stringResource(R.string.doc_search_date_custom)) },
                            modifier = Modifier.testTag("vault_date_custom")
                        )
                    }

                    AnimatedVisibility(visible = datePreset == VaultDatePreset.CUSTOM) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customDateQuery,
                                onValueChange = { customDateQuery = it },
                                leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
                                placeholder = { Text(stringResource(R.string.doc_search_date_placeholder)) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("vault_custom_date_query")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customDateFrom,
                                    onValueChange = { customDateFrom = it },
                                    label = { Text(stringResource(R.string.doc_search_date_from)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).testTag("vault_custom_date_from")
                                )
                                OutlinedTextField(
                                    value = customDateTo,
                                    onValueChange = { customDateTo = it },
                                    label = { Text(stringResource(R.string.doc_search_date_to)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).testTag("vault_custom_date_to")
                                )
                            }
                        }
                    }

                    if (firestoreSearchState != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("vault_firestore_status_banner")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                when (firestoreSearchState) {
                                    is UiState.Loading -> {
                                        Text(
                                            text = stringResource(R.string.doc_search_loading),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                    is UiState.Error -> {
                                        Text(
                                            text = firestoreSearchState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    is UiState.Success -> {
                                        val res = firestoreSearchState.data
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (res.usedServerQuery) {
                                                        stringResource(R.string.doc_search_results_server, displayItems.size)
                                                    } else {
                                                        stringResource(R.string.doc_search_results_cache, displayItems.size)
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Text(
                                                    text = buildString {
                                                        append("Filtres : ${res.appliedFirestoreFilters.joinToString(" · ")}")
                                                        res.fallbackNote?.let { append(" ($it)") }
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    searchQuery = ""
                                                    selectedCategory = null
                                                    selectedFolder = null
                                                    selectedTagNorm = null
                                                    onlyFavorites = false
                                                    datePreset = VaultDatePreset.ALL
                                                    customDateQuery = ""
                                                    customDateFrom = ""
                                                    customDateTo = ""
                                                    onClearFirestoreSearch()
                                                },
                                                modifier = Modifier.testTag("vault_firestore_search_clear_btn")
                                            ) {
                                                Text(stringResource(R.string.doc_search_reset))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Automatic sorting mode selector (usage frequency + frequent tags)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DocumentSortMode.entries.forEach { mode ->
                            FilterChip(
                                selected = sortMode == mode,
                                onClick = { onSortModeChange(mode) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (mode == DocumentSortMode.SMART_AUTO) Icons.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.Sort,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("vault_sort_${mode.name.lowercase()}")
                            )
                        }
                    }
                    if (sortMode == DocumentSortMode.SMART_AUTO) {
                        Text(
                            text = "Tri automatique par fréquence de consultation et tags les plus fréquents",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                CategoryChipRow(
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
            }

            // Frequent tags quick navigation strip
            if (computedFrequentTags.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        computedFrequentTags.take(8).forEach { tagStat ->
                            val isSelected = selectedTagNorm == tagStat.normalizedTag
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTagNorm = if (isSelected) null else tagStat.normalizedTag
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Label, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                label = { Text("#${tagStat.displayTag} (${tagStat.documentCount})") },
                                modifier = Modifier.testTag("vault_frequent_tag_${tagStat.normalizedTag}")
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
                    FilterChip(
                        selected = onlyFavorites,
                        onClick = { onlyFavorites = !onlyFavorites },
                        leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Favoris") }
                    )
                    folders.forEach { folder ->
                        FilterChip(
                            selected = selectedFolder == folder,
                            onClick = { selectedFolder = if (selectedFolder == folder) null else folder },
                            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(folder) }
                        )
                    }
                }
            }

            when (effectiveState) {
                is UiState.Loading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                        Text(effectiveState.message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                is UiState.Success -> {
                    if (displayItems.isEmpty()) {
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
                                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Aucun document trouvé", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (hasSearchCriteria) "Essayez d’élargir vos mots-clés, la date ou la catégorie."
                                        else "Scannez ou importez votre premier contrat, facture ou justificatif.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (hasSearchCriteria) {
                                        OutlinedButton(
                                            onClick = {
                                                searchQuery = ""
                                                selectedCategory = null
                                                selectedFolder = null
                                                selectedTagNorm = null
                                                onlyFavorites = false
                                                datePreset = VaultDatePreset.ALL
                                                customDateQuery = ""
                                                customDateFrom = ""
                                                customDateTo = ""
                                                onClearFirestoreSearch()
                                            }
                                        ) {
                                            Text(stringResource(R.string.doc_search_reset))
                                        }
                                    } else {
                                        Button(onClick = onOpenScan) {
                                            Text("Scanner un document")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(displayItems, key = { it.ranked.document.id }) { displayItem ->
                            val ranked = displayItem.ranked
                            val doc = ranked.document
                            val match = displayItem.searchMatch
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .clickable { onOpenDocument(doc.id) }
                                    .testTag("doc_card_${doc.id}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(11.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(
                                                doc.category,
                                                doc.issuer.takeIf { it.isNotBlank() },
                                                doc.docDate.takeIf { it.isNotBlank() }?.let { DateUtils.formatFrench(it) },
                                                doc.expirationDate.takeIf { it.isNotBlank() }?.let { "Exp. ${DateUtils.formatFrench(it)}" }
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!match?.matchedSnippet.isNullOrBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = match?.matchedSnippet.orEmpty(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            VisibilityBadge(doc.visibility, doc.confidentiality)
                                            if (doc.confidentiality == "sensitive") {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.Fingerprint,
                                                            contentDescription = "Document protégé par biométrie",
                                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = "Biométrie",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
                                                }
                                            }
                                            val badgeText = match?.matchReasons?.takeIf { it.isNotEmpty() }
                                                ?.take(2)
                                                ?.joinToString(" · ")
                                                ?: ranked.reasonBadge
                                            badgeText?.let { badge ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = badge,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (doc.tags.isNotEmpty()) {
                                            @OptIn(ExperimentalLayoutApi::class)
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                doc.tags.take(3).forEach { tag ->
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "#$tag",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            maxLines = 1,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    IconButton(onClick = { onToggleFavorite(doc) }) {
                                        Icon(
                                            imageVector = if (doc.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Favori",
                                            tint = if (doc.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    document: VaultDocument?,
    activeHousehold: Household?,
    currentUserId: String,
    protectSensitiveDocsWithBiometric: Boolean = true,
    pinEnabled: Boolean = false,
    onVerifyPinFallback: (String, (Boolean) -> Unit) -> Unit = { _, cb -> cb(false) },
    onSensitiveUnlocked: (VaultDocument, String) -> Unit = { _, _ -> },
    onToggleSensitive: (VaultDocument, Boolean) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onEdit: (VaultDocument) -> Unit,
    onNewVersion: (VaultDocument) -> Unit,
    onToggleShareWithHousehold: (VaultDocument, Boolean) -> Unit,
    onExportPdf: (VaultDocument) -> Unit,
    onCreateDeadlineFromDoc: (VaultDocument) -> Unit,
    onGenerateLetterFromDoc: (VaultDocument) -> Unit = {},
    onRetouchPhoto: (VaultDocument, Bitmap) -> Unit = { _, _ -> },
    isAutoTagging: Boolean = false,
    onAutoCategorizeWithAi: (VaultDocument) -> Unit = {},
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRetouchDialog by remember { mutableStateOf(false) }
    var pendingShareState by remember { mutableStateOf<Boolean?>(null) }

    val biometricCapability = remember { BiometricAuthHelper.checkCapability(context) }
    val requiresBiometricGate = document != null &&
        document.confidentiality == "sensitive" &&
        protectSensitiveDocsWithBiometric
    var isSensitiveUnlocked by remember(document?.id, requiresBiometricGate) {
        mutableStateOf(!requiresBiometricGate)
    }
    var biometricStatusMessage by remember(document?.id) { mutableStateOf<String?>(null) }
    var autoPromptAttempted by remember(document?.id) { mutableStateOf(false) }
    var showPinFallbackDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun triggerSensitiveBiometricAuth() {
        val doc = document ?: return
        BiometricAuthHelper.launchSensitiveDocumentPrompt(
            context = context,
            documentTitle = doc.title,
            hasPinFallback = pinEnabled,
            onSuccess = { methodLabel ->
                isSensitiveUnlocked = true
                biometricStatusMessage = null
                onSensitiveUnlocked(doc, methodLabel)
            },
            onUsePinFallback = {
                pinInput = ""
                pinError = null
                showPinFallbackDialog = true
            },
            onError = { err ->
                biometricStatusMessage = err
            }
        )
    }

    LaunchedEffect(document?.id, requiresBiometricGate) {
        if (requiresBiometricGate && !isSensitiveUnlocked && !autoPromptAttempted) {
            autoPromptAttempted = true
            if (biometricCapability == BiometricCapability.AVAILABLE_BIOMETRIC ||
                biometricCapability == BiometricCapability.AVAILABLE_DEVICE_CREDENTIAL
            ) {
                triggerSensitiveBiometricAuth()
            }
        }
    }

    var decryptedPreviewBitmaps by remember(document?.id, document?.localFileUri, isSensitiveUnlocked) {
        mutableStateOf<List<Bitmap>>(emptyList())
    }

    LaunchedEffect(document?.id, document?.localFileUri, isSensitiveUnlocked) {
        val doc = document
        if (doc == null || doc.localFileUri.isBlank() || !isSensitiveUnlocked) {
            decryptedPreviewBitmaps = emptyList()
        } else {
            decryptedPreviewBitmaps = withContext(Dispatchers.IO) {
                runCatching { PdfGenerator.decryptVaultPdfPreviewBitmaps(context, doc, maxPages = 3) }
                    .getOrDefault(emptyList())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détail du document", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (document != null && document.userId == currentUserId && isSensitiveUnlocked) {
                        IconButton(onClick = { onEdit(document) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Modifier")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (document == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Document introuvable.")
            }
            return@Scaffold
        }

        if (requiresBiometricGate && !isSensitiveUnlocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.size(84.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Fingerprint,
                            contentDescription = "Document protégé par biométrie",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                VisibilityBadge(document.visibility, document.confidentiality)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().testTag("sensitive_doc_lock_shield")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Document sensible protégé", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "Les informations, le texte OCR et les pièces jointes de ce document sont masqués. Vérifiez votre identité par empreinte digitale ou reconnaissance faciale pour y accéder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = BiometricAuthHelper.capabilityLabel(biometricCapability),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        biometricStatusMessage?.let { msg ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        Button(
                            onClick = { triggerSensitiveBiometricAuth() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("doc_biometric_unlock_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Déverrouiller (Empreinte / Visage)")
                        }
                        if (pinEnabled) {
                            OutlinedButton(
                                onClick = {
                                    pinInput = ""
                                    pinError = null
                                    showPinFallbackDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("doc_pin_fallback_btn"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Déverrouiller avec le code PIN")
                            }
                        }
                        if (biometricCapability == BiometricCapability.NONE_ENROLLED) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(BiometricAuthHelper.buildBiometricEnrollIntent())
                                    } catch (_: Exception) {
                                        biometricStatusMessage = "Ouvrez les paramètres Android pour enregistrer une empreinte ou un visage."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Configurer la biométrie sur l’appareil")
                            }
                        }
                    }
                }
            }

            if (showPinFallbackDialog) {
                AlertDialog(
                    onDismissRequest = { showPinFallbackDialog = false },
                    title = { Text("Code PIN de secours") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Saisissez votre code PIN à 4 chiffres pour ouvrir « ${document.title} ».")
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pinInput = it; pinError = null },
                                label = { Text("Code PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("doc_pin_fallback_input")
                            )
                            pinError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onVerifyPinFallback(pinInput) { valid ->
                                    if (valid) {
                                        showPinFallbackDialog = false
                                        isSensitiveUnlocked = true
                                        biometricStatusMessage = null
                                        onSensitiveUnlocked(document, "Code PIN")
                                    } else {
                                        pinError = "Code PIN incorrect."
                                    }
                                }
                            },
                            enabled = pinInput.length == 4,
                            modifier = Modifier.testTag("doc_pin_fallback_confirm")
                        ) {
                            Text("Valider")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPinFallbackDialog = false }) {
                            Text("Annuler")
                        }
                    }
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(document.title, style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VisibilityBadge(document.visibility, document.confidentiality)
                if (requiresBiometricGate) {
                    OutlinedButton(
                        onClick = { isSensitiveUnlocked = false },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("doc_biometric_relock_btn")
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Verrouiller")
                    }
                }
            }

            if (document.ocrConfidence == "low") {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Certaines données OCR présentaient une confiance modérée/faible. Vérifiez les champs ci-dessous.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("Catégorie", document.category)
                    DetailRow("Dossier", document.folder.ifBlank { "Général" })
                    DetailRow("Type", document.docType.ifBlank { "Non précisé" })
                    DetailRow("Organisme", document.issuer.ifBlank { "Non précisé" })
                    DetailRow("Référence", document.referenceNumber.ifBlank { "Non précisée" })
                    DetailRow("Date", DateUtils.formatFrench(document.docDate))
                    DetailRow("Expiration", DateUtils.formatFrench(document.expirationDate))
                    DetailRow("Montant", if (document.amount > 0) "${"%.2f".format(document.amount)} €" else "—")
                    DetailRow("Statut", statusLabel(document.status))
                    if (document.tags.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Tags", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                document.tags.forEach { tag ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (document.userId == currentUserId) {
                        FilledTonalButton(
                            onClick = { onAutoCategorizeWithAi(document) },
                            enabled = !isAutoTagging,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("doc_ai_autotag_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isAutoTagging) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.ai_tagging_running))
                            } else {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.ai_tagging_run_btn))
                            }
                        }
                    }
                }
            }

            if (document.notes.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Notes", style = MaterialTheme.typography.titleSmall)
                        Text(document.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (document.userId == currentUserId) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Protection biométrique (sensible)", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "Exige votre empreinte digitale ou reconnaissance faciale à chaque ouverture.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = document.confidentiality == "sensitive",
                            onCheckedChange = { sensitive -> onToggleSensitive(document, sensitive) },
                            modifier = Modifier.testTag("doc_detail_sensitive_switch")
                        )
                    }
                }
            }

            if (document.userId == currentUserId && activeHousehold != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Partager avec ${activeHousehold.name}", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "Par défaut privé. Les autres membres ne voient ce document que si vous l’autorisez.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = document.visibility == "household",
                            onCheckedChange = { wantShare -> pendingShareState = wantShare }
                        )
                    }
                }
            }

            if (document.ocrText.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Texte extrait (OCR)", style = MaterialTheme.typography.titleSmall)
                        Text(document.ocrText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Historique des versions (v${document.versionNumber})", style = MaterialTheme.typography.titleSmall)
                    }
                    document.versionHistory.forEach { entry ->
                        Text("• $entry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (document.userId == currentUserId) {
                        OutlinedButton(onClick = { onNewVersion(document) }) {
                            Text("Remplacer par une nouvelle version")
                        }
                    }
                }
            }

            if (decryptedPreviewBitmaps.isNotEmpty()) {
                Card(
                    modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (document.docType.contains("PDF", ignoreCase = true)) {
                                "Aperçu du fichier PDF chiffré (${decryptedPreviewBitmaps.size} page${if (decryptedPreviewBitmaps.size > 1) "s" else ""})"
                            } else {
                                "Aperçu du fichier chiffré"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        decryptedPreviewBitmaps.forEachIndexed { idx, bmp ->
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Aperçu page ${idx + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f))
                                )
                            }
                        }
                        if (decryptedPreviewBitmaps.size == 1 &&
                            !document.docType.contains("PDF", ignoreCase = true) &&
                            document.userId == currentUserId
                        ) {
                            FilledTonalButton(
                                onClick = { showRetouchDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("doc_detail_retouch_photo_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Retoucher la photo")
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onExportPdf(document) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Exporter PDF")
                }
                OutlinedButton(
                    onClick = { onCreateDeadlineFromDoc(document) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Créer échéance")
                }
            }

            FilledTonalButton(
                onClick = { onGenerateLetterFromDoc(document) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Générer un courrier PDF lié à ce document")
            }
        }
    }

    if (showDeleteConfirm && document != null) {
        ConfirmActionDialog(
            title = "Supprimer définitivement ?",
            message = "Le document « ${document.title} » sera supprimé de votre coffre-fort. Cette action est irréversible.",
            confirmLabel = "Supprimer",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDelete(document.id)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    pendingShareState?.let { share ->
        if (document != null) {
            ConfirmActionDialog(
                title = if (share) "Partager avec le foyer ?" else "Rendre ce document privé ?",
                message = if (share) "Les membres de votre foyer pourront consulter « ${document.title} »."
                else "Ce document redeviendra strictement privé.",
                confirmLabel = "Confirmer",
                onConfirm = {
                    pendingShareState = null
                    onToggleShareWithHousehold(document, share)
                },
                onDismiss = { pendingShareState = null }
            )
        }
    }

    if (showRetouchDialog && decryptedPreviewBitmaps.size == 1 && document != null) {
        PhotoRetouchDialog(
            sourceBitmap = decryptedPreviewBitmaps.first(),
            onDismiss = { showRetouchDialog = false },
            onSaveRetouched = { retouchedBmp ->
                showRetouchDialog = false
                decryptedPreviewBitmaps = listOf(retouchedBmp)
                onRetouchPhoto(document, retouchedBmp)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    initialDoc: VaultDocument,
    isNewVersionBump: Boolean,
    hasHousehold: Boolean,
    onBack: () -> Unit,
    onSave: (VaultDocument) -> Unit
) {
    var title by remember(initialDoc) { mutableStateOf(initialDoc.title) }
    var category by remember(initialDoc) { mutableStateOf(initialDoc.category.ifBlank { "Administratif" }) }
    var docType by remember(initialDoc) { mutableStateOf(initialDoc.docType) }
    var folder by remember(initialDoc) { mutableStateOf(initialDoc.folder.ifBlank { "Général" }) }
    var docDate by remember(initialDoc) { mutableStateOf(initialDoc.docDate) }
    var expirationDate by remember(initialDoc) { mutableStateOf(initialDoc.expirationDate) }
    var issuer by remember(initialDoc) { mutableStateOf(initialDoc.issuer) }
    var refNum by remember(initialDoc) { mutableStateOf(initialDoc.referenceNumber) }
    var amountText by remember(initialDoc) { mutableStateOf(if (initialDoc.amount > 0) initialDoc.amount.toString() else "") }
    var notes by remember(initialDoc) { mutableStateOf(initialDoc.notes) }
    var tagsText by remember(initialDoc) { mutableStateOf(initialDoc.tags.joinToString(", ")) }
    var isSensitive by remember(initialDoc) { mutableStateOf(initialDoc.confidentiality == "sensitive") }
    var shareWithHousehold by remember(initialDoc) { mutableStateOf(initialDoc.visibility == "household") }
    var catExpanded by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isNewVersionBump -> "Nouvelle version"
                            initialDoc.id.isBlank() -> "Nouveau document"
                            else -> "Modifier le document"
                        }
                    )
                },
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
                onValueChange = { title = it; validationError = null },
                label = { Text("Titre du document *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("doc_title_input")
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
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                catExpanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = docType,
                    onValueChange = { docType = it },
                    label = { Text("Type (Contrat, Facture…)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Dossier") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
                label = { Text("Organisme émetteur") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = refNum,
                    onValueChange = { refNum = it },
                    label = { Text("N° contrat / réf.") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Montant (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = docDate,
                    onValueChange = { docDate = it },
                    label = { Text("Date (AAAA-MM-JJ)") },
                    placeholder = { Text("2026-11-24") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = expirationDate,
                    onValueChange = { expirationDate = it },
                    label = { Text("Expiration (AAAA-MM-JJ)") },
                    placeholder = { Text("2027-11-24") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text("Tags (séparés par des virgules)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes personnelles") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Document sensible (protection renforcée)", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = isSensitive, onCheckedChange = { isSensitive = it })
            }

            if (hasHousehold) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Partager avec le foyer", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = shareWithHousehold, onCheckedChange = { shareWithHousehold = it })
                }
            }

            validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    if (title.trim().isBlank()) {
                        validationError = "Le titre du document est obligatoire."
                        return@Button
                    }
                    val parsedAmount = amountText.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(10)
                    val nextVer = if (isNewVersionBump) initialDoc.versionNumber + 1L else initialDoc.versionNumber
                    val nextHistory = if (isNewVersionBump) {
                        initialDoc.versionHistory + "v$nextVer · ${DateUtils.todayNumeric()} · Mise à jour du fichier"
                    } else {
                        initialDoc.versionHistory
                    }
                    onSave(
                        initialDoc.copy(
                            title = title.trim(),
                            category = category,
                            docType = docType.trim(),
                            folder = folder.trim().ifBlank { "Général" },
                            docDate = docDate.trim(),
                            expirationDate = expirationDate.trim(),
                            issuer = issuer.trim(),
                            referenceNumber = refNum.trim(),
                            amount = parsedAmount,
                            notes = notes.trim(),
                            tags = tags,
                            confidentiality = if (isSensitive) "sensitive" else "standard",
                            visibility = if (shareWithHousehold && hasHousehold) "household" else "private",
                            versionNumber = nextVer,
                            versionHistory = nextHistory
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_document_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Enregistrer dans le coffre-fort", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun statusLabel(status: String): String = when (status) {
    "expired" -> "Expiré"
    "to_renew" -> "À renouveler"
    "archived" -> "Archivé"
    else -> "Actif"
}

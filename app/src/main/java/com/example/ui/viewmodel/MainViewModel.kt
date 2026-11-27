package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.CryptoVaultHelper
import com.example.core.DocumentUsageStat
import com.example.core.NetworkConnectivityMonitor
import com.example.core.SecurityPreferences
import com.example.core.UserSecuritySettings
import com.example.data.AiAssistantRepository
import com.example.data.DocumentSmartSorter
import com.example.data.DocumentSortMode
import com.example.data.ExtractedDocumentInfo
import com.example.data.FrequentTagStat
import com.example.data.HouseholdRepository
import com.example.data.RankedVaultDocument
import com.example.data.SearchScopeTab
import com.example.data.UniversalSearchEngine
import com.example.data.UniversalSearchFilterState
import com.example.data.UniversalSearchResults
import com.example.data.VieSimpleRepository
import com.example.data.local.OfflineCacheRepository
import com.example.export.PdfGenerator
import com.example.model.AiMessageRecord
import com.example.model.ChecklistTemplateDef
import com.example.model.DateUtils
import com.example.model.Deadline
import com.example.model.Household
import com.example.model.HouseholdActivity
import com.example.model.HouseholdMember
import com.example.model.HouseholdTask
import com.example.model.LetterTemplate
import com.example.model.SecurityLog
import com.example.model.SubscriptionItem
import com.example.model.UserProfile
import com.example.model.VaultDocument
import com.example.notifications.DeadlineWorkerScheduler
import com.example.notifications.FcmReminderSyncManager
import com.example.notifications.FcmSyncStatus
import com.example.notifications.ReminderScheduler
import com.example.notifications.SystemCalendarSyncManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiChatState {
    data object Idle : AiChatState
    data object Loading : AiChatState
    data class Streaming(val partialText: String) : AiChatState
    data class Error(val message: String) : AiChatState
}

sealed interface OcrScanState {
    data object Idle : OcrScanState
    data object Analyzing : OcrScanState
    data object SavingToFirestore : OcrScanState
    data class Ready(
        val info: ExtractedDocumentInfo,
        val encryptedPath: String,
        val savedDocumentId: String? = null,
        val autoSavedToFirestore: Boolean = false,
        val autoCreatedDeadline: Boolean = false,
        val isGrayscalePdf: Boolean = false,
        val pageCount: Int = 1
    ) : OcrScanState
    data class Error(val message: String) : OcrScanState
}

data class DashboardUiState(
    val documentCount: Int = 0,
    val pendingDeadlinesCount: Int = 0,
    val overdueDeadlinesCount: Int = 0,
    val thisWeekDeadlinesCount: Int = 0,
    val expiringDocumentsCount: Int = 0,
    val urgentTasksCount: Int = 0,
    val openTasksCount: Int = 0,
    val monthlySubscriptionsTotal: Double = 0.0,
    val subscriptionsToWatchCount: Int = 0,
    val isSyncing: Boolean = true
)

data class OfflineCacheStatus(
    val isOnline: Boolean = true,
    val cachedDocumentsCount: Int = 0,
    val cachedTasksCount: Int = 0,
    val cachedSubscriptionsCount: Int = 0,
    val lastSyncMillis: Long = 0L
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repo: VieSimpleRepository,
    private val householdRepo: HouseholdRepository,
    private val aiRepo: AiAssistantRepository,
    val securityPrefs: SecurityPreferences,
    val currentUserId: String,
    private val appContext: Context? = null
) : ViewModel() {

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _aiChatState = MutableStateFlow<AiChatState>(AiChatState.Idle)
    val aiChatState: StateFlow<AiChatState> = _aiChatState.asStateFlow()

    private val _ocrState = MutableStateFlow<OcrScanState>(OcrScanState.Idle)
    val ocrState: StateFlow<OcrScanState> = _ocrState.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isLockStateReady = MutableStateFlow(false)
    val isLockStateReady: StateFlow<Boolean> = _isLockStateReady.asStateFlow()

    private val _fcmSyncStatus = MutableStateFlow(FcmSyncStatus())
    val fcmSyncStatus: StateFlow<FcmSyncStatus> = _fcmSyncStatus.asStateFlow()

    private val _searchFilterState = MutableStateFlow(UniversalSearchFilterState())
    val searchFilterState: StateFlow<UniversalSearchFilterState> = _searchFilterState.asStateFlow()

    private val _documentSortMode = MutableStateFlow(DocumentSortMode.SMART_AUTO)
    val documentSortMode: StateFlow<DocumentSortMode> = _documentSortMode.asStateFlow()

    private var lastBackgroundTimestamp = 0L

    val securitySettings: StateFlow<UserSecuritySettings> = securityPrefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserSecuritySettings())

    val coachMarkPending: StateFlow<Boolean> = securityPrefs.coachMarkPendingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val documentUsageStats: StateFlow<Map<String, DocumentUsageStat>> = securityPrefs.documentUsageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    val isOnline: StateFlow<Boolean> = (appContext?.let {
        NetworkConnectivityMonitor.observeIsOnline(it.applicationContext)
    } ?: kotlinx.coroutines.flow.flowOf(true))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), appContext?.let { NetworkConnectivityMonitor.isOnline(it) } ?: true)

    private val localCacheRepo: OfflineCacheRepository? =
        repo.cacheRepo ?: appContext?.let { OfflineCacheRepository(it.applicationContext) }

    val offlineCacheStatus: StateFlow<OfflineCacheStatus> = combine(
        isOnline,
        localCacheRepo?.observeDocuments(currentUserId) ?: kotlinx.coroutines.flow.flowOf(emptyList()),
        localCacheRepo?.observeTasks(currentUserId) ?: kotlinx.coroutines.flow.flowOf(emptyList()),
        localCacheRepo?.observeSubscriptions(currentUserId) ?: kotlinx.coroutines.flow.flowOf(emptyList()),
        securitySettings
    ) { online, cachedDocs, cachedTasks, cachedSubs, settings ->
        OfflineCacheStatus(
            isOnline = online,
            cachedDocumentsCount = cachedDocs.size,
            cachedTasksCount = cachedTasks.size,
            cachedSubscriptionsCount = cachedSubs.size,
            lastSyncMillis = settings.lastRoomCacheSyncMillis
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), OfflineCacheStatus())

    val syncedCalendarEventIds: StateFlow<Map<String, Long>> = (
        localCacheRepo?.observeCalendarLinks(currentUserId)?.map { links ->
            links.associate { it.deadlineId to it.calendarEventId }
        } ?: kotlinx.coroutines.flow.flowOf(emptyMap())
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    init {
        viewModelScope.launch {
            checkInitialPinLock()
        }
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user != null && user.uid == currentUserId) {
                repo.ensureUserProfile(
                    userId = user.uid,
                    displayName = user.displayName ?: "Utilisateur VieSimple",
                    email = user.email.orEmpty()
                )
            }
        }
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.toUiState(tag: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(tag, "Stream error", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Erreur de synchronisation."))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val profileState: StateFlow<UiState<UserProfile?>> =
        repo.observeProfile(currentUserId).toUiState("ProfileVM")

    val documentsState: StateFlow<UiState<List<VaultDocument>>> =
        repo.observeDocuments(currentUserId).toUiState("DocsVM")

    val frequentVaultTags: StateFlow<List<FrequentTagStat>> = combine(
        documentsState,
        documentUsageStats
    ) { docsUi, usage ->
        val docs = (docsUi as? UiState.Success)?.data ?: emptyList()
        DocumentSmartSorter.computeFrequentTags(docs, usage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val sortedDocumentsState: StateFlow<UiState<List<RankedVaultDocument>>> = combine(
        documentsState,
        documentUsageStats,
        _documentSortMode
    ) { docsUi, usage, mode ->
        when (docsUi) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> docsUi
            is UiState.Success -> UiState.Success(
                DocumentSmartSorter.sortDocuments(docsUi.data, usage, mode)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val deadlinesState: StateFlow<UiState<List<Deadline>>> =
        repo.observeDeadlines(currentUserId).toUiState("DeadlinesVM")

    val subscriptionsState: StateFlow<UiState<List<SubscriptionItem>>> =
        repo.observeSubscriptions(currentUserId).toUiState("SubsVM")

    val tasksState: StateFlow<UiState<List<HouseholdTask>>> =
        repo.observeTasks(currentUserId).toUiState("TasksVM")

    val householdsState: StateFlow<UiState<List<Household>>> =
        householdRepo.observeHouseholds(currentUserId).toUiState("HouseholdsVM")

    private val _selectedDocumentId = MutableStateFlow<String?>(null)
    val selectedDocument: StateFlow<VaultDocument?> = combine(documentsState, _selectedDocumentId) { state, docId ->
        val docs = (state as? UiState.Success)?.data ?: emptyList()
        if (docId.isNullOrBlank()) null else docs.firstOrNull { it.id == docId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _selectedDeadlineId = MutableStateFlow<String?>(null)
    val selectedDeadline: StateFlow<Deadline?> = combine(deadlinesState, _selectedDeadlineId) { state, id ->
        val items = (state as? UiState.Success)?.data ?: emptyList()
        if (id.isNullOrBlank()) null else items.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _selectedTaskId = MutableStateFlow<String?>(null)
    val selectedTask: StateFlow<HouseholdTask?> = combine(tasksState, _selectedTaskId) { state, id ->
        val items = (state as? UiState.Success)?.data ?: emptyList()
        if (id.isNullOrBlank()) null else items.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _selectedSubscriptionId = MutableStateFlow<String?>(null)
    val selectedSubscription: StateFlow<SubscriptionItem?> = combine(subscriptionsState, _selectedSubscriptionId) { state, id ->
        val items = (state as? UiState.Success)?.data ?: emptyList()
        if (id.isNullOrBlank()) null else items.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val dashboardUiState: StateFlow<DashboardUiState> = combine(
        documentsState,
        deadlinesState,
        subscriptionsState,
        tasksState
    ) { docsUi, deadlinesUi, subsUi, tasksUi ->
        val docs = (docsUi as? UiState.Success)?.data ?: emptyList()
        val deadlines = (deadlinesUi as? UiState.Success)?.data ?: emptyList()
        val subs = (subsUi as? UiState.Success)?.data ?: emptyList()
        val tasks = (tasksUi as? UiState.Success)?.data ?: emptyList()

        val pendingDeadlines = deadlines.filter { it.status == "pending" }
        val overdueCount = pendingDeadlines.count { (DateUtils.daysUntil(it.dueDate) ?: 1L) < 0L }
        val thisWeekCount = pendingDeadlines.count {
            val d = DateUtils.daysUntil(it.dueDate)
            d != null && d in 0L..7L
        }
        val expiringDocsCount = docs.count {
            val d = DateUtils.daysUntil(it.expirationDate)
            d != null && d <= 30L
        }
        val openTasks = tasks.filter { it.status == "todo" }
        val urgentTasksCount = openTasks.count { t ->
            val d = DateUtils.daysUntil(t.dueDate)
            t.priority == "urgent" || t.priority == "high" || (d != null && d <= 2L)
        }
        val activeSubs = subs.filter { it.status == "active" || it.status == "to_cancel" }
        val monthlyTotal = activeSubs.sumOf { it.monthlyEquivalent() }
        val subsToWatchCount = activeSubs.count {
            val d = DateUtils.daysUntil(it.nextBillingDate)
            it.status == "to_cancel" || (d != null && d in 0L..10L)
        }
        val isSyncing = docsUi is UiState.Loading ||
            deadlinesUi is UiState.Loading ||
            subsUi is UiState.Loading ||
            tasksUi is UiState.Loading

        DashboardUiState(
            documentCount = docs.size,
            pendingDeadlinesCount = pendingDeadlines.size,
            overdueDeadlinesCount = overdueCount,
            thisWeekDeadlinesCount = thisWeekCount,
            expiringDocumentsCount = expiringDocsCount,
            urgentTasksCount = urgentTasksCount,
            openTasksCount = openTasks.size,
            monthlySubscriptionsTotal = monthlyTotal,
            subscriptionsToWatchCount = subsToWatchCount,
            isSyncing = isSyncing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardUiState())

    fun selectDocument(docId: String?) {
        _selectedDocumentId.value = docId?.takeIf { it.isNotBlank() }
    }

    fun recordDocumentOpened(docId: String) {
        if (docId.isBlank()) return
        viewModelScope.launch {
            securityPrefs.recordDocumentOpened(docId)
        }
    }

    fun setDocumentSortMode(mode: DocumentSortMode) {
        _documentSortMode.value = mode
    }

    fun dismissCoachMark() {
        viewModelScope.launch {
            securityPrefs.setCoachMarkSeen(true)
        }
    }

    fun replayCoachMark() {
        viewModelScope.launch {
            securityPrefs.setCoachMarkSeen(false)
        }
    }

    fun selectDeadline(deadlineId: String?) {
        _selectedDeadlineId.value = deadlineId?.takeIf { it.isNotBlank() }
    }

    fun selectTask(taskId: String?) {
        _selectedTaskId.value = taskId?.takeIf { it.isNotBlank() }
    }

    fun selectSubscription(subId: String?) {
        _selectedSubscriptionId.value = subId?.takeIf { it.isNotBlank() }
    }

    val activeHousehold: StateFlow<Household?> = combine(profileState, householdsState) { pState, hState ->
        val households = (hState as? UiState.Success)?.data ?: emptyList()
        val activeId = (pState as? UiState.Success)?.data?.activeHouseholdId.orEmpty()
        households.firstOrNull { it.id == activeId } ?: households.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val activeHouseholdMembers: StateFlow<List<HouseholdMember>> = activeHousehold
        .flatMapLatest { hh ->
            if (hh == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else householdRepo.observeHouseholdMembers(hh.id, currentUserId)
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private data class SearchableDataSnapshot(
        val docs: List<VaultDocument>,
        val tasks: List<HouseholdTask>,
        val subs: List<SubscriptionItem>,
        val deadlines: List<Deadline>,
        val isSyncing: Boolean
    )

    val universalSearchResults: StateFlow<UniversalSearchResults> = combine(
        combine(documentsState, tasksState, subscriptionsState, deadlinesState) { docsUi, tasksUi, subsUi, deadlinesUi ->
            SearchableDataSnapshot(
                docs = (docsUi as? UiState.Success)?.data ?: emptyList(),
                tasks = (tasksUi as? UiState.Success)?.data ?: emptyList(),
                subs = (subsUi as? UiState.Success)?.data ?: emptyList(),
                deadlines = (deadlinesUi as? UiState.Success)?.data ?: emptyList(),
                isSyncing = docsUi is UiState.Loading ||
                    tasksUi is UiState.Loading ||
                    subsUi is UiState.Loading ||
                    deadlinesUi is UiState.Loading
            )
        },
        activeHouseholdMembers,
        _searchFilterState
    ) { snapshot, members, filter ->
        UniversalSearchEngine.search(
            documents = snapshot.docs,
            tasks = snapshot.tasks,
            subscriptions = snapshot.subs,
            deadlines = snapshot.deadlines,
            members = members,
            filter = filter,
            isSyncing = snapshot.isSyncing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UniversalSearchResults(isSyncing = true))

    fun updateSearchQuery(query: String) {
        _searchFilterState.value = _searchFilterState.value.copy(query = query)
    }

    fun setSearchTab(tab: SearchScopeTab) {
        _searchFilterState.value = _searchFilterState.value.copy(activeTab = tab)
    }

    fun setSearchCategory(category: String?) {
        _searchFilterState.value = _searchFilterState.value.copy(selectedCategory = category)
    }

    fun toggleSearchTag(tag: String) {
        val current = _searchFilterState.value.selectedTags
        val normTarget = UniversalSearchEngine.normalize(tag)
        val existingMatch = current.firstOrNull { UniversalSearchEngine.normalize(it) == normTarget }
        val updated = if (existingMatch != null) current - existingMatch else current + tag.trim()
        _searchFilterState.value = _searchFilterState.value.copy(selectedTags = updated)
    }

    fun toggleSearchActiveOnly() {
        val cur = _searchFilterState.value
        _searchFilterState.value = cur.copy(onlyActiveOrOpen = !cur.onlyActiveOrOpen)
    }

    fun toggleSearchHouseholdOnly() {
        val cur = _searchFilterState.value
        _searchFilterState.value = cur.copy(onlyHouseholdShared = !cur.onlyHouseholdShared)
    }

    fun toggleSearchFavoritesOnly() {
        val cur = _searchFilterState.value
        _searchFilterState.value = cur.copy(onlyFavorites = !cur.onlyFavorites)
    }

    fun resetSearchFilters(keepQuery: Boolean = false) {
        val cur = _searchFilterState.value
        _searchFilterState.value = UniversalSearchFilterState(
            query = if (keepQuery) cur.query else "",
            activeTab = SearchScopeTab.ALL
        )
    }

    val householdActivities: StateFlow<List<HouseholdActivity>> =
        householdRepo.observeHouseholdActivities(currentUserId)
            .catch { error ->
                val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fe == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val securityLogs: StateFlow<List<SecurityLog>> =
        repo.observeSecurityLogs(currentUserId)
            .catch { error ->
                val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fe == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val aiMessages: StateFlow<List<AiMessageRecord>> =
        repo.observeAiMessages(currentUserId)
            .catch { error ->
                val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fe == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun showMessage(msg: String) {
        _actionMessage.value = msg
    }

    fun checkInitialPinLock() {
        if (_isLockStateReady.value) return
        viewModelScope.launch {
            val settings = try {
                securityPrefs.settingsFlow.first()
            } catch (_: Exception) {
                UserSecuritySettings()
            }
            if (!_isLockStateReady.value) {
                _isLocked.value = settings.biometricEnabled || settings.pinEnabled
                _isLockStateReady.value = true
            }
        }
    }

    fun lockNow() {
        _isLocked.value = true
    }

    fun onAppBackgrounded() {
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        val settings = securitySettings.value
        if ((settings.pinEnabled || settings.biometricEnabled) && lastBackgroundTimestamp > 0L) {
            val elapsedMinutes = (System.currentTimeMillis() - lastBackgroundTimestamp) / 60_000L
            if (elapsedMinutes >= settings.autoLockMinutes) {
                _isLocked.value = true
            }
        }
    }

    fun unlockWithBiometric(methodLabel: String = "Biométrie") {
        _isLocked.value = false
        viewModelScope.launch {
            repo.logSecurityAction("UNLOCK_APP", "Accès autorisé via $methodLabel")
        }
    }

    fun verifyAndUnlockPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = securityPrefs.verifyPin(pin)
            if (ok) {
                _isLocked.value = false
                repo.logSecurityAction("UNLOCK_APP", "Accès autorisé via code PIN")
            }
            onResult(ok)
        }
    }

    fun configurePinAndUnlock(pin: String) {
        viewModelScope.launch {
            securityPrefs.setPin(pin)
            _isLocked.value = false
            repo.logSecurityAction("PIN_CONFIGURED", "Code PIN de secours configuré et session déverrouillée")
        }
    }

    fun completeOnboarding(profileType: String, householdName: String?) {
        viewModelScope.launch {
            val profile = (profileState.value as? UiState.Success)?.data
            val displayName = profile?.displayName ?: Firebase.auth.currentUser?.displayName ?: "Utilisateur"
            repo.updateProfile(profileType = profileType, onboardingCompleted = true)
            if (!householdName.isNullOrBlank()) {
                householdRepo.createHousehold(householdName, displayName)
            }
        }
    }

    fun saveDocument(context: Context, doc: VaultDocument, onSaved: (String) -> Unit = {}) {
        viewModelScope.launch {
            val profile = (profileState.value as? UiState.Success)?.data
            val docs = (documentsState.value as? UiState.Success)?.data ?: emptyList()
            if (doc.id.isBlank() && profile?.planTier == "free" && docs.size >= 50) {
                _actionMessage.value = "Limite de 50 documents atteinte en offre Gratuite. Passez à Premium pour un stockage illimité."
                return@launch
            }
            val hh = activeHousehold.value
            val prepared = if (doc.visibility == "household" && hh != null) {
                doc.copy(householdId = hh.id, sharedWithUids = hh.memberUids)
            } else {
                doc.copy(visibility = "private", householdId = "", sharedWithUids = listOf(currentUserId))
            }
            val res = repo.saveDocument(prepared)
            res.onSuccess { docId ->
                if (prepared.visibility == "household" && hh != null) {
                    val actor = profile?.displayName ?: "Un membre"
                    householdRepo.postActivity(hh.id, hh.memberUids, actor, "$actor a partagé un document (${prepared.category}).", "SHARE_DOC")
                }
                _actionMessage.value = "Document enregistré dans le coffre-fort."
                onSaved(docId)
            }.onFailure { err ->
                _actionMessage.value = "Erreur : ${err.localizedMessage ?: "Impossible d’enregistrer le document"}"
            }
        }
    }

    fun updateDocumentPhoto(
        context: Context,
        doc: VaultDocument,
        retouchedBitmap: Bitmap,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val encryptedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val baos = ByteArrayOutputStream()
                    retouchedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                    CryptoVaultHelper.encryptBytesToVaultFile(
                        context = context.applicationContext,
                        fileName = "retouch_${doc.id.ifBlank { "doc" }}_${System.currentTimeMillis()}",
                        plainBytes = baos.toByteArray()
                    )
                }.getOrDefault("")
            }
            if (encryptedPath.isBlank()) {
                _actionMessage.value = "Impossible d’enregistrer la photo retouchée localement."
                return@launch
            }
            val updatedDoc = doc.copy(
                localFileUri = encryptedPath,
                versionNumber = doc.versionNumber + 1L,
                versionHistory = (doc.versionHistory + "${DateUtils.todayIso()} : Retouche photo appliquée").takeLast(12)
            )
            repo.saveDocument(updatedDoc)
                .onSuccess {
                    _actionMessage.value = "Photo retouchée et chiffrée (v${updatedDoc.versionNumber})."
                    onDone()
                }
                .onFailure { err ->
                    _actionMessage.value = "Erreur : ${err.localizedMessage ?: "Impossible de mettre à jour le document"}"
                }
        }
    }

    fun updateDocumentConfidentiality(doc: VaultDocument, sensitive: Boolean) {
        viewModelScope.launch {
            val nextConf = if (sensitive) "sensitive" else "standard"
            if (doc.confidentiality == nextConf) return@launch
            repo.saveDocument(doc.copy(confidentiality = nextConf))
                .onSuccess {
                    repo.logSecurityAction(
                        if (sensitive) "PROTECT_SENSITIVE_DOC" else "UNPROTECT_SENSITIVE_DOC",
                        if (sensitive) "Protection biométrique activée sur un document" else "Protection biométrique retirée d’un document"
                    )
                    _actionMessage.value = if (sensitive) {
                        "Document marqué sensible : empreinte ou reconnaissance faciale requise à l’ouverture."
                    } else {
                        "Protection sensible désactivée pour ce document."
                    }
                }
                .onFailure { err ->
                    _actionMessage.value = "Erreur : ${err.localizedMessage ?: "Mise à jour impossible"}"
                }
        }
    }

    fun logSensitiveDocumentUnlocked(doc: VaultDocument, methodLabel: String) {
        viewModelScope.launch {
            repo.logSecurityAction(
                "SENSITIVE_DOC_UNLOCKED",
                "Accès autorisé ($methodLabel) au document sensible « ${doc.title.take(40)} »"
            )
        }
    }

    fun saveSenderDetails(address: String, city: String, phone: String) {
        viewModelScope.launch {
            securityPrefs.saveSenderProfileDetails(address, city, phone)
            _actionMessage.value = "Coordonnées enregistrées pour vos prochains courriers."
        }
    }

    fun saveGeneratedLetterToVault(
        context: Context,
        template: LetterTemplate,
        subject: String,
        body: String,
        recipientOrg: String,
        referenceNumber: String,
        amount: Double,
        pdfFile: File,
        shareWithHousehold: Boolean = false,
        onSaved: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val profile = (profileState.value as? UiState.Success)?.data
            val docs = (documentsState.value as? UiState.Success)?.data ?: emptyList()
            if (profile?.planTier == "free" && docs.size >= 50) {
                val msg = "Limite de 50 documents atteinte en offre Gratuite. Passez à Premium pour un stockage illimité."
                _actionMessage.value = msg
                onError(msg)
                return@launch
            }

            val encryptedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val pdfBytes = pdfFile.readBytes()
                    CryptoVaultHelper.encryptBytesToVaultFile(
                        context = context.applicationContext,
                        fileName = "letter_${template.id}_${System.currentTimeMillis()}",
                        plainBytes = pdfBytes
                    )
                }.getOrDefault("")
            }

            if (encryptedPath.isBlank()) {
                val msg = "Impossible de chiffrer le PDF dans le coffre-fort local."
                _actionMessage.value = msg
                onError(msg)
                return@launch
            }

            val hh = activeHousehold.value
            val useHousehold = shareWithHousehold && hh != null
            val docToSave = VaultDocument(
                title = "${template.title} — ${recipientOrg.trim().ifBlank { "Courrier" }}",
                category = template.category,
                docType = "Courrier PDF",
                folder = "Courriers générés",
                docDate = DateUtils.todayIso(),
                issuer = recipientOrg.trim(),
                referenceNumber = referenceNumber.trim(),
                amount = amount.coerceAtLeast(0.0),
                notes = subject.trim(),
                tags = listOf("Courrier", "PDF", template.category).distinct(),
                ocrText = buildString {
                    if (subject.isNotBlank()) {
                        appendLine("Objet : ${subject.trim()}")
                        appendLine()
                    }
                    append(body.trim())
                },
                ocrConfidence = "high",
                localFileUri = encryptedPath,
                visibility = if (useHousehold) "household" else "private",
                householdId = if (useHousehold) hh!!.id else "",
                sharedWithUids = if (useHousehold) hh!!.memberUids else listOf(currentUserId)
            )

            repo.saveDocument(docToSave)
                .onSuccess { docId ->
                    if (useHousehold && hh != null) {
                        val actor = profile?.displayName ?: "Un membre"
                        householdRepo.postActivity(hh.id, hh.memberUids, actor, "$actor a partagé un courrier PDF (${template.category}).", "SHARE_DOC")
                    }
                    _actionMessage.value = "Courrier PDF chiffré et enregistré dans le coffre-fort."
                    onSaved(docId)
                }
                .onFailure { err ->
                    val msg = "Erreur : ${err.localizedMessage ?: "Impossible d’enregistrer le courrier"}"
                    _actionMessage.value = msg
                    onError(msg)
                }
        }
    }

    fun toggleFavorite(doc: VaultDocument) {
        viewModelScope.launch {
            repo.toggleDocumentFavorite(doc.id, !doc.favorite)
            securityPrefs.recordDocumentOpened(doc.id)
        }
    }

    fun deleteDocument(docId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteDocument(docId)
                .onSuccess {
                    _actionMessage.value = "Document supprimé définitivement."
                    onDeleted()
                }
                .onFailure { _actionMessage.value = "Suppression impossible : ${it.localizedMessage}" }
        }
    }

    fun saveDeadline(context: Context, deadline: Deadline, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val hh = activeHousehold.value
            val profile = (profileState.value as? UiState.Success)?.data
            val prepared = if (deadline.visibility == "household" && hh != null) {
                deadline.copy(householdId = hh.id, sharedWithUids = hh.memberUids)
            } else {
                deadline.copy(visibility = "private", householdId = "", sharedWithUids = listOf(currentUserId))
            }
            repo.saveDeadline(prepared)
                .onSuccess { id ->
                    ReminderScheduler.schedulePrivacySafeDeadlineReminders(
                        context,
                        prepared.copy(id = id),
                        securitySettings.value
                    )
                    if (prepared.visibility == "household" && hh != null) {
                        val actor = profile?.displayName ?: "Un membre"
                        householdRepo.postActivity(hh.id, hh.memberUids, actor, "$actor a créé l’échéance « ${prepared.title} ».", "CREATE_DEADLINE")
                    }
                    _actionMessage.value = "Échéance enregistrée."
                    onSaved()
                }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun markDeadlineDone(deadline: Deadline) {
        viewModelScope.launch {
            repo.markDeadlineDone(deadline)
                .onSuccess {
                    _actionMessage.value = if (deadline.recurrence != "none") {
                        "Échéance traitée et prochaine occurrence créée."
                    } else {
                        "Échéance marquée comme traitée."
                    }
                }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun postponeDeadline(deadline: Deadline, days: Int = 7) {
        viewModelScope.launch {
            repo.postponeDeadline(deadline, days)
                .onSuccess { _actionMessage.value = "Échéance repoussée de $days jours." }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun deleteDeadline(deadlineId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteDeadline(deadlineId)
                .onSuccess {
                    _actionMessage.value = "Échéance supprimée."
                    onDeleted()
                }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun saveSubscription(sub: SubscriptionItem, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val hh = activeHousehold.value
            val prepared = if (sub.visibility == "household" && hh != null) {
                sub.copy(householdId = hh.id, sharedWithUids = hh.memberUids)
            } else {
                sub.copy(visibility = "private", householdId = "", sharedWithUids = listOf(currentUserId))
            }
            repo.saveSubscription(prepared)
                .onSuccess {
                    _actionMessage.value = "Abonnement enregistré."
                    onSaved()
                }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun deleteSubscription(subId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteSubscription(subId)
                .onSuccess {
                    _actionMessage.value = "Abonnement supprimé."
                    onDeleted()
                }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun saveTask(task: HouseholdTask, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val hh = activeHousehold.value
            val profile = (profileState.value as? UiState.Success)?.data
            val prepared = if (task.visibility == "household" && hh != null) {
                task.copy(householdId = hh.id, sharedWithUids = hh.memberUids)
            } else {
                task.copy(visibility = "private", householdId = "", sharedWithUids = listOf(currentUserId))
            }
            repo.saveTask(prepared)
                .onSuccess {
                    if (prepared.visibility == "household" && hh != null) {
                        val actor = profile?.displayName ?: "Un membre"
                        householdRepo.postActivity(hh.id, hh.memberUids, actor, "$actor a ajouté la tâche « ${prepared.title} ».", "CREATE_TASK")
                    }
                    _actionMessage.value = "Tâche enregistrée."
                    onSaved()
                }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun createTaskFromChecklistTemplate(template: ChecklistTemplateDef, shareWithHousehold: Boolean) {
        viewModelScope.launch {
            val hh = activeHousehold.value
            val profile = (profileState.value as? UiState.Success)?.data
            val task = HouseholdTask(
                title = "Checklist : ${template.title}",
                description = template.description,
                category = template.category,
                dueDate = DateUtils.addDaysIso(DateUtils.todayIso(), 14),
                priority = "medium",
                assigneeUid = currentUserId,
                assigneeName = profile?.displayName ?: "Moi",
                status = "todo",
                subtasks = template.items,
                checklistTemplate = template.title,
                visibility = if (shareWithHousehold && hh != null) "household" else "private",
                householdId = if (shareWithHousehold && hh != null) hh.id else "",
                sharedWithUids = if (shareWithHousehold && hh != null) hh.memberUids else listOf(currentUserId)
            )
            repo.saveTask(task)
                .onSuccess { _actionMessage.value = "Checklist « ${template.title} » ajoutée aux tâches." }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun toggleTaskDone(task: HouseholdTask, done: Boolean) {
        viewModelScope.launch {
            val hh = activeHousehold.value
            val profile = (profileState.value as? UiState.Success)?.data
            repo.toggleTaskDone(task, done)
                .onSuccess {
                    if (done && task.visibility == "household" && hh != null) {
                        val actor = profile?.displayName ?: "Un membre"
                        householdRepo.postActivity(hh.id, hh.memberUids, actor, "$actor a terminé la tâche « ${task.title} ».", "COMPLETE_TASK")
                    }
                }
        }
    }

    fun toggleSubtask(task: HouseholdTask, index: Int) {
        viewModelScope.launch {
            val next = task.subtasks.mapIndexed { i, raw ->
                if (i != index) raw
                else if (raw.startsWith("1|")) "0|" + raw.removePrefix("1|")
                else "1|" + raw.removePrefix("0|")
            }
            repo.updateTaskSubtasks(task.id, next)
        }
    }

    fun postponeTask(task: HouseholdTask, days: Int = 3) {
        viewModelScope.launch {
            repo.postponeTask(task, days)
                .onSuccess { _actionMessage.value = "Tâche reportée de $days jours." }
        }
    }

    fun deleteTask(taskId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteTask(taskId)
                .onSuccess {
                    _actionMessage.value = "Tâche supprimée."
                    onDeleted()
                }
        }
    }

    fun createHousehold(name: String) {
        viewModelScope.launch {
            val profile = (profileState.value as? UiState.Success)?.data
            val households = (householdsState.value as? UiState.Success)?.data ?: emptyList()
            if (profile?.planTier == "free" && households.isNotEmpty()) {
                _actionMessage.value = "L’offre Gratuite inclut 1 foyer. Passez à Premium Famille pour en gérer plusieurs."
                return@launch
            }
            householdRepo.createHousehold(name, profile?.displayName ?: "Propriétaire")
                .onSuccess { _actionMessage.value = "Foyer créé avec succès." }
                .onFailure { _actionMessage.value = "Impossible de créer le foyer : ${it.localizedMessage}" }
        }
    }

    fun joinHousehold(code: String) {
        viewModelScope.launch {
            val profile = (profileState.value as? UiState.Success)?.data
            householdRepo.joinHouseholdByCode(code, profile?.displayName ?: "Membre")
                .onSuccess { _actionMessage.value = "Vous avez rejoint le foyer." }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Code d’invitation invalide." }
        }
    }

    fun switchActiveHousehold(householdId: String) {
        viewModelScope.launch {
            repo.updateProfile(activeHouseholdId = householdId)
        }
    }

    fun updateMemberRole(household: Household, memberUid: String, newRole: String) {
        viewModelScope.launch {
            householdRepo.updateMemberRole(household, memberUid, newRole)
                .onSuccess { _actionMessage.value = "Rôle mis à jour." }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun removeMember(household: Household, memberUid: String) {
        viewModelScope.launch {
            householdRepo.removeMemberFromHousehold(household, memberUid)
                .onSuccess { _actionMessage.value = "Membre retiré du foyer (ses documents privés restent intacts)." }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun leaveHousehold(household: Household) {
        viewModelScope.launch {
            householdRepo.leaveHousehold(household)
                .onSuccess { _actionMessage.value = "Vous avez quitté le foyer." }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun deleteHousehold(household: Household) {
        viewModelScope.launch {
            householdRepo.deleteHousehold(household)
                .onSuccess { _actionMessage.value = "Foyer supprimé." }
                .onFailure { _actionMessage.value = "Erreur : ${it.localizedMessage}" }
        }
    }

    fun updatePlanTier(tier: String) {
        viewModelScope.launch {
            repo.updateProfile(planTier = tier)
                .onSuccess {
                    repo.logSecurityAction("PLAN_CHANGE", "Formule mise à jour : $tier")
                    _actionMessage.value = "Formule mise à jour : ${planLabel(tier)}."
                }
        }
    }

    fun analyzeScannedBitmap(
        context: Context,
        bitmap: Bitmap,
        autoSaveToFirestore: Boolean = true,
        grayscalePdfFile: File? = null,
        pageCount: Int = 1,
        isSensitive: Boolean = false
    ) {
        viewModelScope.launch {
            _ocrState.value = OcrScanState.Analyzing
            val hasGrayscalePdf = grayscalePdfFile != null && grayscalePdfFile.exists()
            val encryptedPath = withContext(Dispatchers.IO) {
                try {
                    if (hasGrayscalePdf) {
                        val pdfBytes = grayscalePdfFile!!.readBytes()
                        CryptoVaultHelper.encryptBytesToVaultFile(
                            context = context,
                            fileName = "scan_pdf_${System.currentTimeMillis()}",
                            plainBytes = pdfBytes
                        )
                    } else {
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                        CryptoVaultHelper.encryptBytesToVaultFile(
                            context = context,
                            fileName = "scan_${System.currentTimeMillis()}",
                            plainBytes = baos.toByteArray()
                        )
                    }
                } catch (_: Exception) {
                    ""
                }
            }
            aiRepo.analyzeDocumentImage(bitmap)
                .onSuccess { info ->
                    val effectiveDocType = if (hasGrayscalePdf) {
                        val base = info.docType.trim()
                        when {
                            base.isBlank() -> "PDF N&B numérisé"
                            base.contains("PDF", ignoreCase = true) -> base
                            else -> "$base (PDF N&B)"
                        }
                    } else {
                        info.docType.trim()
                    }
                    val enrichedInfo = info.copy(docType = effectiveDocType)

                    if (!autoSaveToFirestore) {
                        _ocrState.value = OcrScanState.Ready(
                            info = enrichedInfo,
                            encryptedPath = encryptedPath,
                            isGrayscalePdf = hasGrayscalePdf,
                            pageCount = pageCount
                        )
                        return@onSuccess
                    }

                    val profile = (profileState.value as? UiState.Success)?.data
                    val docs = (documentsState.value as? UiState.Success)?.data ?: emptyList()
                    if (profile?.planTier == "free" && docs.size >= 50) {
                        _actionMessage.value = "Limite de 50 documents atteinte en offre Gratuite."
                        _ocrState.value = OcrScanState.Ready(
                            info = enrichedInfo,
                            encryptedPath = encryptedPath,
                            isGrayscalePdf = hasGrayscalePdf,
                            pageCount = pageCount
                        )
                        return@onSuccess
                    }

                    _ocrState.value = OcrScanState.SavingToFirestore
                    val docToSave = VaultDocument(
                        title = enrichedInfo.title.ifBlank { "Document administratif" },
                        category = enrichedInfo.category,
                        docType = enrichedInfo.docType,
                        docDate = enrichedInfo.docDate.trim(),
                        expirationDate = enrichedInfo.expirationDate.trim(),
                        issuer = enrichedInfo.issuer.trim(),
                        referenceNumber = enrichedInfo.referenceNumber.trim(),
                        amount = enrichedInfo.amount,
                        tags = (enrichedInfo.tags + if (hasGrayscalePdf) listOf("PDF N&B") else emptyList()).distinct(),
                        confidentiality = if (isSensitive) "sensitive" else "standard",
                        ocrText = enrichedInfo.ocrText,
                        ocrConfidence = enrichedInfo.confidence,
                        localFileUri = encryptedPath,
                        visibility = "private",
                        sharedWithUids = listOf(currentUserId)
                    )
                    repo.saveDocument(docToSave)
                        .onSuccess { savedId ->
                            var createdDeadline = false
                            if (enrichedInfo.expirationDate.isNotBlank() && DateUtils.daysUntil(enrichedInfo.expirationDate) != null) {
                                val autoDl = Deadline(
                                    title = "Échéance : ${docToSave.title}",
                                    category = docToSave.category,
                                    dueDate = enrichedInfo.expirationDate.trim(),
                                    priority = "high",
                                    linkedDocumentId = savedId,
                                    visibility = "private",
                                    sharedWithUids = listOf(currentUserId)
                                )
                                repo.saveDeadline(autoDl).onSuccess { dlId ->
                                    createdDeadline = true
                                    ReminderScheduler.schedulePrivacySafeDeadlineReminders(
                                        context,
                                        autoDl.copy(id = dlId),
                                        securitySettings.value
                                    )
                                }
                            }
                            _actionMessage.value = if (hasGrayscalePdf) {
                                "PDF niveaux de gris archivé (${docToSave.category}) et enregistré sur Firestore."
                            } else {
                                "Document classé (${docToSave.category}) et enregistré sur Firestore."
                            }
                            _ocrState.value = OcrScanState.Ready(
                                info = enrichedInfo,
                                encryptedPath = encryptedPath,
                                savedDocumentId = savedId,
                                autoSavedToFirestore = true,
                                autoCreatedDeadline = createdDeadline,
                                isGrayscalePdf = hasGrayscalePdf,
                                pageCount = pageCount
                            )
                        }
                        .onFailure { err ->
                            _actionMessage.value = "Enregistrement auto impossible : ${err.localizedMessage ?: "vérifiez votre connexion"}"
                            _ocrState.value = OcrScanState.Ready(
                                info = enrichedInfo,
                                encryptedPath = encryptedPath,
                                isGrayscalePdf = hasGrayscalePdf,
                                pageCount = pageCount
                            )
                        }
                }
                .onFailure { err ->
                    _ocrState.value = OcrScanState.Error(err.localizedMessage ?: "Échec de l’analyse OCR.")
                }
        }
    }

    fun reanalyzeOcrText(
        rawText: String,
        existingEncryptedPath: String = "",
        existingSavedId: String? = null
    ) {
        val text = rawText.trim()
        if (text.isBlank()) {
            _actionMessage.value = "Saisissez ou numérisez du texte avant de relancer l’extraction."
            return
        }
        viewModelScope.launch {
            _ocrState.value = OcrScanState.Analyzing
            aiRepo.analyzeDocumentText(text)
                .onSuccess { info ->
                    _ocrState.value = OcrScanState.Ready(
                        info = info,
                        encryptedPath = existingEncryptedPath,
                        savedDocumentId = existingSavedId
                    )
                }
                .onFailure { err ->
                    _ocrState.value = OcrScanState.Error(err.localizedMessage ?: "Échec de l’extraction depuis le texte.")
                }
        }
    }

    fun resetOcrState() {
        _ocrState.value = OcrScanState.Idle
    }

    fun sendAiMessage(userPrompt: String) {
        val prompt = userPrompt.trim()
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _aiChatState.value = AiChatState.Loading
            repo.saveAiMessage("user", prompt)

            val contextSummary = buildAccessibleAiContext()
            val history = aiMessages.value
            val accumulated = StringBuilder()

            try {
                aiRepo.streamAssistantReply(prompt, contextSummary, history).collect { partial ->
                    accumulated.setLength(0)
                    accumulated.append(partial)
                    _aiChatState.value = AiChatState.Streaming(stripPropositionTag(partial))
                }
                val fullReply = accumulated.toString()
                val proposition = extractPropositionTag(fullReply)
                val cleanText = stripPropositionTag(fullReply)
                repo.saveAiMessage("assistant", cleanText, proposition)
                _aiChatState.value = AiChatState.Idle
            } catch (e: TimeoutCancellationException) {
                _aiChatState.value = AiChatState.Error("L’assistant a mis trop de temps à répondre (60s). Réessayez.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainVM", "AI chat error", e)
                _aiChatState.value = AiChatState.Error(e.localizedMessage ?: "Impossible de joindre l’assistant IA.")
            }
        }
    }

    fun clearAiConversation() {
        viewModelScope.launch {
            repo.clearAiHistory(currentUserId)
        }
    }

    private fun buildAccessibleAiContext(): String {
        val docs = (documentsState.value as? UiState.Success)?.data ?: emptyList()
        val deadlines = (deadlinesState.value as? UiState.Success)?.data ?: emptyList()
        val subs = (subscriptionsState.value as? UiState.Success)?.data ?: emptyList()
        val tasks = (tasksState.value as? UiState.Success)?.data ?: emptyList()

        return buildString {
            appendLine("Date du jour : ${DateUtils.todayIso()}")
            appendLine("Documents accessibles (${docs.size}) :")
            docs.take(25).forEach {
                appendLine("- [${it.category}] ${it.title} | Organisme: ${it.issuer} | Réf: ${it.referenceNumber} | Expire: ${it.expirationDate} | Montant: ${it.amount}€ | Statut: ${it.status}")
            }
            appendLine("Échéances accessibles (${deadlines.size}) :")
            deadlines.take(25).forEach {
                appendLine("- [${it.category}] ${it.title} | Date: ${it.dueDate} | Statut: ${it.status} | Priorité: ${it.priority}")
            }
            appendLine("Abonnements (${subs.size}) :")
            subs.take(20).forEach {
                appendLine("- ${it.name} | ${it.amount}${it.currency} (${it.frequency}) | Prochain: ${it.nextBillingDate} | Statut: ${it.status}")
            }
            appendLine("Tâches (${tasks.size}) :")
            tasks.take(20).forEach {
                appendLine("- ${it.title} | Date: ${it.dueDate} | Statut: ${it.status} | Responsable: ${it.assigneeName}")
            }
        }
    }

    private fun extractPropositionTag(text: String): String {
        val line = text.lines().lastOrNull { it.trim().startsWith("[PROPOSITION:") } ?: return ""
        return line.trim().removePrefix("[PROPOSITION:").removeSuffix("]")
    }

    private fun stripPropositionTag(text: String): String {
        return text.lines()
            .filterNot { it.trim().startsWith("[PROPOSITION:") }
            .joinToString("\n")
            .trim()
    }

    fun syncFcmAndAutomaticReminders(context: Context, showFeedback: Boolean = false) {
        viewModelScope.launch {
            val deadlines = (deadlinesState.value as? UiState.Success)?.data ?: emptyList()
            val tasks = (tasksState.value as? UiState.Success)?.data ?: emptyList()
            val settings = securitySettings.value
            DeadlineWorkerScheduler.syncPeriodicSchedule(context.applicationContext, settings)
            FcmReminderSyncManager.syncFcmTokenAndReminderSummary(
                context = context.applicationContext,
                repo = repo,
                securityPrefs = securityPrefs,
                deadlines = deadlines,
                tasks = tasks,
                settings = settings
            ).onSuccess { status ->
                _fcmSyncStatus.value = status
                if (showFeedback) {
                    _actionMessage.value = "Notifications FCM, worker et rappels confidentiels synchronisés."
                }
            }.onFailure { err ->
                _fcmSyncStatus.value = _fcmSyncStatus.value.copy(
                    isRegistered = false,
                    statusMessage = "Mode local actif (FCM en attente : ${err.localizedMessage ?: "hors ligne"})"
                )
                if (showFeedback) {
                    _actionMessage.value = "Synchronisation FCM impossible pour le moment : ${err.localizedMessage}"
                }
            }
        }
    }

    fun syncDeadlineBackgroundWorker(context: Context) {
        DeadlineWorkerScheduler.syncPeriodicSchedule(context.applicationContext, securitySettings.value)
    }

    fun runDeadlineWorkerNow(context: Context) {
        DeadlineWorkerScheduler.triggerImmediateCheck(context.applicationContext)
        _actionMessage.value = "Vérification en arrière-plan des échéances Firestore lancée."
    }

    fun setCalendarSyncEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            val ctx = context.applicationContext
            val cache = localCacheRepo ?: OfflineCacheRepository(ctx)
            securityPrefs.setCalendarSyncEnabled(enabled)
            if (enabled) {
                syncDeadlinesToSystemCalendar(ctx, showFeedback = true)
            } else {
                SystemCalendarSyncManager.clearAllSyncedEvents(ctx, currentUserId, cache, securityPrefs)
                _actionMessage.value = "Synchronisation avec le calendrier système désactivée."
            }
        }
    }

    fun syncDeadlinesToSystemCalendar(context: Context, showFeedback: Boolean = false) {
        viewModelScope.launch {
            val ctx = context.applicationContext
            val cache = localCacheRepo ?: OfflineCacheRepository(ctx)
            val deadlines = (deadlinesState.value as? UiState.Success)?.data ?: emptyList()
            val result = SystemCalendarSyncManager.syncDeadlinesToSystemCalendar(
                context = ctx,
                userId = currentUserId,
                deadlines = deadlines,
                settings = securitySettings.value,
                cacheRepo = cache,
                securityPrefs = securityPrefs
            )
            if (showFeedback) {
                _actionMessage.value = result.summary
            }
        }
    }

    fun forceRefreshOfflineRoomCache() {
        viewModelScope.launch {
            val cache = localCacheRepo ?: return@launch
            val docs = (documentsState.value as? UiState.Success)?.data
            val tasks = (tasksState.value as? UiState.Success)?.data
            val subs = (subscriptionsState.value as? UiState.Success)?.data
            if (docs != null) cache.replaceDocuments(currentUserId, docs)
            if (tasks != null) cache.replaceTasks(currentUserId, tasks)
            if (subs != null) cache.replaceSubscriptions(currentUserId, subs)
            securityPrefs.setLastRoomCacheSyncMillis(System.currentTimeMillis())
            _actionMessage.value = "Cache local Room actualisé (${docs?.size ?: 0} docs, ${tasks?.size ?: 0} tâches, ${subs?.size ?: 0} abos)."
        }
    }

    fun clearOfflineRoomCache() {
        viewModelScope.launch {
            repo.clearLocalCache(currentUserId)
            securityPrefs.setLastRoomCacheSyncMillis(0L)
            _actionMessage.value = "Cache local Room vidé."
        }
    }

    fun clearSessionCachesBeforeSignOut(context: Context, onReadyToSignOut: () -> Unit) {
        viewModelScope.launch {
            val ctx = context.applicationContext
            val cache = localCacheRepo ?: OfflineCacheRepository(ctx)
            runCatching {
                SystemCalendarSyncManager.clearAllSyncedEvents(ctx, currentUserId, cache, securityPrefs)
                repo.clearLocalCache(currentUserId)
            }
            onReadyToSignOut()
        }
    }

    fun exportAllUserData(context: Context, onReady: (File) -> Unit) {
        viewModelScope.launch {
            householdRepo.exportUserDataToJsonFile(context)
                .onSuccess { file ->
                    repo.logSecurityAction("EXPORT_DATA", "Export JSON généré")
                    onReady(file)
                }
                .onFailure { _actionMessage.value = "Échec de l’export : ${it.localizedMessage}" }
        }
    }

    fun exportDocumentPdf(context: Context, doc: VaultDocument) {
        viewModelScope.launch {
            try {
                securityPrefs.recordDocumentOpened(doc.id)
                val file = withContext(Dispatchers.IO) {
                    PdfGenerator.decryptVaultFileToShareablePdf(context, doc)
                        ?: PdfGenerator.generateDocumentPdf(context, doc)
                }
                PdfGenerator.shareFile(context, file, "application/pdf", "Exporter le document en PDF")
            } catch (e: Exception) {
                _actionMessage.value = "Erreur export PDF : ${e.localizedMessage}"
            }
        }
    }

    fun deleteAccountDataAndSignOut(context: Context, onComplete: () -> Unit) {
        viewModelScope.launch {
            val ctx = context.applicationContext
            val cache = localCacheRepo ?: OfflineCacheRepository(ctx)
            DeadlineWorkerScheduler.cancelAll(ctx)
            runCatching {
                SystemCalendarSyncManager.clearAllSyncedEvents(ctx, currentUserId, cache, securityPrefs)
            }
            householdRepo.deleteAllOwnedUserData(context)
                .onSuccess {
                    repo.clearLocalCache(currentUserId)
                    securityPrefs.clearAllLocalPreferences()
                    onComplete()
                }
                .onFailure { _actionMessage.value = "Erreur lors de la suppression : ${it.localizedMessage}" }
        }
    }

    companion object {
        fun planLabel(tier: String): String = when (tier) {
            "premium_individual" -> "Premium Individuel"
            "premium_family" -> "Premium Famille"
            else -> "Gratuit"
        }
    }
}

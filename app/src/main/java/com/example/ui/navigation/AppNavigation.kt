package com.example.ui.navigation

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.core.LocaleHelper
import com.example.core.SecurityPreferences
import com.example.core.UserSecuritySettings
import com.example.data.AiAssistantRepository
import com.example.data.HouseholdRepository
import com.example.data.VieSimpleRepository
import com.example.model.Deadline
import com.example.model.HouseholdTask
import com.example.model.SubscriptionItem
import com.example.model.VaultDocument
import com.example.ui.auth.AuthWelcomeScreen
import com.example.ui.auth.signOut
import com.example.ui.components.CoachMarkOverlay
import com.example.ui.components.CoachMarkStep
import com.example.ui.components.PinLockOverlay
import com.example.ui.screens.AccountDeletionScreen
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.ChecklistTemplatesScreen
import com.example.ui.screens.DataExportScreen
import com.example.ui.screens.DeadlineEditorScreen
import com.example.ui.screens.DeadlinesScreen
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.DocumentEditorScreen
import com.example.ui.screens.DocumentsVaultScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.HouseholdInviteScreen
import com.example.ui.screens.HouseholdMembersScreen
import com.example.ui.screens.HouseholdScreen
import com.example.ui.screens.LetterTemplatesScreen
import com.example.ui.screens.MoreHubScreen
import com.example.ui.screens.NotificationsSettingsScreen
import com.example.ui.screens.OnboardingWizardScreen
import com.example.ui.screens.PremiumScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ScanDocumentScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubscriptionEditorScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.screens.SupportHelpScreen
import com.example.ui.screens.TaskEditorScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.UniversalSearchScreen
import com.example.notifications.ReminderBroadcastReceiver
import com.example.ui.theme.VieSimpleTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

object VieSimpleRoutes {
    const val HOME = "home"
    const val VAULT = "vault"
    const val DEADLINES = "deadlines"
    const val TASKS = "tasks"
    const val MORE = "more"

    const val DOC_DETAIL = "doc_detail"
    const val DOC_EDITOR = "doc_editor"
    const val SCAN = "scan"
    const val DEADLINE_EDITOR = "deadline_editor"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SUB_EDITOR = "sub_editor"
    const val TASK_EDITOR = "task_editor"
    const val CHECKLISTS = "checklists"
    const val HOUSEHOLD = "household"
    const val HOUSEHOLD_MEMBERS = "household_members"
    const val HOUSEHOLD_INVITE = "household_invite"
    const val AI_ASSISTANT = "ai_assistant"
    const val LETTERS = "letters"
    const val SEARCH = "search"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
    const val PREMIUM = "premium"
    const val SUPPORT = "support"
    const val EXPORT = "export"
    const val DELETE_ACCOUNT = "delete_account"
}

private data class BottomTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

private val MAIN_TABS = listOf(
    BottomTab(VieSimpleRoutes.HOME, R.string.nav_home, Icons.Outlined.Home, "tab_home"),
    BottomTab(VieSimpleRoutes.VAULT, R.string.nav_vault, Icons.Outlined.FolderShared, "tab_vault"),
    BottomTab(VieSimpleRoutes.DEADLINES, R.string.nav_deadlines, Icons.Outlined.CalendarMonth, "tab_deadlines"),
    BottomTab(VieSimpleRoutes.TASKS, R.string.nav_tasks, Icons.Outlined.CheckBox, "tab_tasks"),
    BottomTab(VieSimpleRoutes.MORE, R.string.nav_more, Icons.Outlined.Menu, "tab_more")
)

@Composable
fun VieSimpleRootApp(
    notificationTargetRoute: String? = null,
    onNotificationRouteConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val securityPrefs = remember { SecurityPreferences(appContext) }
    val globalSettings by securityPrefs.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSecuritySettings())
    val scope = rememberCoroutineScope()

    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val (localizedContext, localizedConfig) = remember(context, globalSettings.appLanguage) {
        LocaleHelper.wrapContextWithLocale(context, globalSettings.appLanguage)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfig
    ) {
        VieSimpleTheme(themeMode = globalSettings.themeMode) {
            AnimatedContent(
                targetState = currentUser?.uid,
                transitionSpec = {
                    (fadeIn(tween(260)) + scaleIn(initialScale = 0.98f, animationSpec = tween(260))) togetherWith
                        fadeOut(tween(180))
                },
                label = "auth_root_transition"
            ) { activeUid ->
                if (activeUid == null) {
                    AuthWelcomeScreen(
                        initialAcceptedTerms = globalSettings.acceptedTerms,
                        onAcceptedTermsChange = { accepted ->
                            scope.launch { securityPrefs.setAcceptedTerms(accepted) }
                        },
                        selectedLanguage = globalSettings.appLanguage,
                        onLanguageSelected = { lang ->
                            scope.launch {
                                securityPrefs.setAppLanguage(lang)
                                LocaleHelper.applySystemAppLocale(context, lang)
                            }
                        },
                        onAuthSuccess = {
                            currentUser = Firebase.auth.currentUser
                        }
                    )
                } else {
                    key(activeUid) {
                        AuthenticatedAppContent(
                            uid = activeUid,
                            securityPrefs = securityPrefs,
                            notificationTargetRoute = notificationTargetRoute,
                            onNotificationRouteConsumed = onNotificationRouteConsumed,
                            onSignedOut = {
                                currentUser = Firebase.auth.currentUser
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedAppContent(
    uid: String,
    securityPrefs: SecurityPreferences,
    notificationTargetRoute: String?,
    onNotificationRouteConsumed: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val vm: MainViewModel = viewModel(
        key = uid,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = VieSimpleRepository(appContext)
                return MainViewModel(
                    repo = repo,
                    householdRepo = HouseholdRepository(repo.db),
                    aiRepo = AiAssistantRepository(),
                    securityPrefs = securityPrefs,
                    currentUserId = uid,
                    appContext = appContext
                ) as T
            }
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> vm.onAppBackgrounded()
                Lifecycle.Event.ON_START -> vm.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uid) {
        vm.checkInitialPinLock()
    }

    val profileState by vm.profileState.collectAsStateWithLifecycle()
    val dashboardUiState by vm.dashboardUiState.collectAsStateWithLifecycle()
    val docsState by vm.documentsState.collectAsStateWithLifecycle()
    val deadlinesState by vm.deadlinesState.collectAsStateWithLifecycle()
    val subsState by vm.subscriptionsState.collectAsStateWithLifecycle()
    val tasksState by vm.tasksState.collectAsStateWithLifecycle()
    val selectedDocument by vm.selectedDocument.collectAsStateWithLifecycle()
    val selectedDeadline by vm.selectedDeadline.collectAsStateWithLifecycle()
    val selectedTask by vm.selectedTask.collectAsStateWithLifecycle()
    val selectedSubscription by vm.selectedSubscription.collectAsStateWithLifecycle()
    val householdsState by vm.householdsState.collectAsStateWithLifecycle()
    val activeHousehold by vm.activeHousehold.collectAsStateWithLifecycle()
    val members by vm.activeHouseholdMembers.collectAsStateWithLifecycle()
    val activities by vm.householdActivities.collectAsStateWithLifecycle()
    val securityLogs by vm.securityLogs.collectAsStateWithLifecycle()
    val aiMessages by vm.aiMessages.collectAsStateWithLifecycle()
    val aiChatState by vm.aiChatState.collectAsStateWithLifecycle()
    val ocrState by vm.ocrState.collectAsStateWithLifecycle()
    val securitySettings by vm.securitySettings.collectAsStateWithLifecycle()
    val fcmSyncStatus by vm.fcmSyncStatus.collectAsStateWithLifecycle()
    val universalSearchResults by vm.universalSearchResults.collectAsStateWithLifecycle()
    val coachMarkPending by vm.coachMarkPending.collectAsStateWithLifecycle()
    val sortedDocumentsState by vm.sortedDocumentsState.collectAsStateWithLifecycle()
    val frequentVaultTags by vm.frequentVaultTags.collectAsStateWithLifecycle()
    val documentSortMode by vm.documentSortMode.collectAsStateWithLifecycle()
    val offlineCacheStatus by vm.offlineCacheStatus.collectAsStateWithLifecycle()
    val syncedCalendarEventIds by vm.syncedCalendarEventIds.collectAsStateWithLifecycle()
    val isLocked by vm.isLocked.collectAsStateWithLifecycle()
    val isLockStateReady by vm.isLockStateReady.collectAsStateWithLifecycle()
    val actionMessage by vm.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearActionMessage()
        }
    }

    val profile = (profileState as? UiState.Success)?.data
    val gateStage = when {
        !isLockStateReady -> "lock_loading"
        isLocked -> "locked"
        profileState is UiState.Loading || profile == null -> "profile_loading"
        !profile.onboardingCompleted -> "onboarding"
        else -> "ready"
    }

    var startRouteAfterOnboarding by remember { mutableStateOf(VieSimpleRoutes.HOME) }

    AnimatedContent(
        targetState = gateStage,
        transitionSpec = {
            (fadeIn(tween(240)) + scaleIn(initialScale = 0.985f, animationSpec = tween(240))) togetherWith
                fadeOut(tween(180))
        },
        label = "app_gate_transition"
    ) { stage ->
        when (stage) {
            "lock_loading" -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("Vérification de la sécurité…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            "locked" -> {
                PinLockOverlay(
                    pinEnabled = securitySettings.pinEnabled,
                    biometricEnabled = securitySettings.biometricEnabled,
                    onVerifyPin = { pin, callback -> vm.verifyAndUnlockPin(pin, callback) },
                    onBiometricUnlocked = { method -> vm.unlockWithBiometric(method) },
                    onSetupPinAndUnlock = { pin -> vm.configurePinAndUnlock(pin) },
                    onSignOutFallback = {
                        vm.clearSessionCachesBeforeSignOut(context) {
                            signOut(credentialManager, scope) { onSignedOut() }
                        }
                    }
                )
            }
            "profile_loading" -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("Chargement de votre espace sécurisé…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            "onboarding" -> {
                val safeProfile = profile!!
                OnboardingWizardScreen(
                    displayName = safeProfile.displayName,
                    onComplete = { profileType, hhName, goToScan ->
                        startRouteAfterOnboarding = if (goToScan) VieSimpleRoutes.SCAN else VieSimpleRoutes.HOME
                        vm.completeOnboarding(profileType, hhName)
                    }
                )
            }
            else -> {
                val readyProfile = profile!!

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
    val showBottomOrRail = currentRoute in MAIN_TABS.map { it.route }

    val docs = (docsState as? UiState.Success)?.data ?: emptyList()
    val deadlines = (deadlinesState as? UiState.Success)?.data ?: emptyList()
    val subs = (subsState as? UiState.Success)?.data ?: emptyList()
    val tasks = (tasksState as? UiState.Success)?.data ?: emptyList()

    var requestedNotifPermThisSession by remember(uid) { mutableStateOf(false) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.syncFcmAndAutomaticReminders(context)
        }
    }

    LaunchedEffect(uid, readyProfile.onboardingCompleted) {
        if (readyProfile.onboardingCompleted &&
            !requestedNotifPermThisSession &&
            securitySettings.notificationsEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ReminderBroadcastReceiver.hasNotificationPermission(context)
        ) {
            requestedNotifPermThisSession = true
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uid, readyProfile.onboardingCompleted, deadlines, tasks, securitySettings) {
        if (readyProfile.onboardingCompleted &&
            deadlinesState is UiState.Success &&
            tasksState is UiState.Success
        ) {
            vm.syncFcmAndAutomaticReminders(context)
        }
    }

    LaunchedEffect(
        uid,
        readyProfile.onboardingCompleted,
        securitySettings.notificationsEnabled,
        securitySettings.notifDeadlines,
        securitySettings.notifQuietMode,
        securitySettings.notifPausedUntilMillis
    ) {
        if (readyProfile.onboardingCompleted) {
            vm.syncDeadlineBackgroundWorker(context)
        }
    }

    LaunchedEffect(
        uid,
        readyProfile.onboardingCompleted,
        deadlines,
        securitySettings.calendarSyncEnabled,
        securitySettings.notifHour,
        securitySettings.notifMinute
    ) {
        if (readyProfile.onboardingCompleted &&
            securitySettings.calendarSyncEnabled &&
            deadlinesState is UiState.Success &&
            com.example.notifications.SystemCalendarSyncManager.hasCalendarPermissions(context)
        ) {
            vm.syncDeadlinesToSystemCalendar(context, showFeedback = false)
        }
    }

    LaunchedEffect(notificationTargetRoute) {
        val target = notificationTargetRoute?.trim().orEmpty()
        if (target.isNotBlank()) {
            when (target) {
                "deadlines" -> navController.navigate(VieSimpleRoutes.DEADLINES) { launchSingleTop = true }
                "tasks" -> navController.navigate(VieSimpleRoutes.TASKS) { launchSingleTop = true }
                "household" -> navController.navigate(VieSimpleRoutes.HOUSEHOLD) { launchSingleTop = true }
                "settings" -> navController.navigate(VieSimpleRoutes.SETTINGS) { launchSingleTop = true }
            }
            onNotificationRouteConsumed()
        }
    }

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openLetters(
        templateId: String? = null,
        org: String = "",
        contract: String = "",
        amount: String = "",
        ref: String = ""
    ) {
        val route = buildString {
            append("${VieSimpleRoutes.LETTERS}?")
            append("templateId=${Uri.encode(templateId.orEmpty())}")
            append("&org=${Uri.encode(org)}")
            append("&contract=${Uri.encode(contract)}")
            append("&amount=${Uri.encode(amount)}")
            append("&ref=${Uri.encode(ref)}")
        }
        navController.navigate(route)
    }

    var scanTargetBounds by remember { mutableStateOf<Rect?>(null) }
    var aiTargetBounds by remember { mutableStateOf<Rect?>(null) }
    var overlayOffsetInRoot by remember { mutableStateOf(Offset.Zero) }
    var coachStep by remember { mutableStateOf(CoachMarkStep.SCAN) }

    LaunchedEffect(coachMarkPending) {
        if (coachMarkPending) {
            coachStep = CoachMarkStep.SCAN
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOffsetInRoot = it.boundsInRoot().topLeft }
    ) {
        val isExpanded = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (showBottomOrRail && !isExpanded) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MAIN_TABS.forEach { tab ->
                            val tabLabel = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTopTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tabLabel) },
                                label = { Text(tabLabel, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (showBottomOrRail && isExpanded) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MAIN_TABS.forEach { tab ->
                            val tabLabel = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTopTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tabLabel) },
                                label = { Text(tabLabel) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = startRouteAfterOnboarding,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    enterTransition = {
                        fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 14 }
                    },
                    exitTransition = {
                        fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 16 }
                    },
                    popEnterTransition = {
                        fadeIn(tween(220)) + slideInHorizontally(tween(240)) { -it / 14 }
                    },
                    popExitTransition = {
                        fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 14 }
                    }
                ) {
                    composable(VieSimpleRoutes.HOME) {
                        HomeDashboardScreen(
                            profile = readyProfile,
                            dashboardUiState = dashboardUiState,
                            documentsState = docsState,
                            deadlinesState = deadlinesState,
                            subscriptionsState = subsState,
                            tasksState = tasksState,
                            onOpenScan = { navController.navigate(VieSimpleRoutes.SCAN) },
                            onOpenAi = { navController.navigate(VieSimpleRoutes.AI_ASSISTANT) },
                            onOpenLetters = { openLetters() },
                            onOpenSearch = { navController.navigate(VieSimpleRoutes.SEARCH) },
                            onOpenDocument = { id ->
                                vm.recordDocumentOpened(id)
                                vm.selectDocument(id)
                                navController.navigate("${VieSimpleRoutes.DOC_DETAIL}/$id")
                            },
                            onOpenVault = { navigateTopTab(VieSimpleRoutes.VAULT) },
                            onOpenDeadlines = { navigateTopTab(VieSimpleRoutes.DEADLINES) },
                            onCreateDeadline = { navController.navigate("${VieSimpleRoutes.DEADLINE_EDITOR}?deadlineId=&linkedDocId=") },
                            onOpenSubscriptions = { navController.navigate(VieSimpleRoutes.SUBSCRIPTIONS) },
                            onCreateSubscription = { navController.navigate("${VieSimpleRoutes.SUB_EDITOR}?subId=") },
                            onOpenTasks = { navigateTopTab(VieSimpleRoutes.TASKS) },
                            onCreateTask = { navController.navigate("${VieSimpleRoutes.TASK_EDITOR}?taskId=") },
                            onMarkDeadlineDone = { vm.markDeadlineDone(it) },
                            onPostponeDeadline = { vm.postponeDeadline(it) },
                            onToggleTaskDone = { task, done -> vm.toggleTaskDone(task, done) },
                            onScanTargetPositioned = { scanTargetBounds = it },
                            onAiTargetPositioned = { aiTargetBounds = it },
                            offlineCacheStatus = offlineCacheStatus
                        )
                    }

                    composable(VieSimpleRoutes.VAULT) {
                        DocumentsVaultScreen(
                            documentsState = docsState,
                            rankedDocumentsState = sortedDocumentsState,
                            frequentTags = frequentVaultTags,
                            sortMode = documentSortMode,
                            onSortModeChange = vm::setDocumentSortMode,
                            offlineCacheStatus = offlineCacheStatus,
                            onOpenDocument = { id ->
                                vm.recordDocumentOpened(id)
                                vm.selectDocument(id)
                                navController.navigate("${VieSimpleRoutes.DOC_DETAIL}/$id")
                            },
                            onOpenScan = { navController.navigate(VieSimpleRoutes.SCAN) },
                            onAddManualDocument = { navController.navigate("${VieSimpleRoutes.DOC_EDITOR}?docId=&newVer=false") },
                            onOpenLetters = { openLetters() },
                            onToggleFavorite = { vm.toggleFavorite(it) }
                        )
                    }

                    composable(
                        route = "${VieSimpleRoutes.DOC_DETAIL}/{docId}",
                        arguments = listOf(navArgument("docId") { type = NavType.StringType })
                    ) { backStack ->
                        val docId = backStack.arguments?.getString("docId").orEmpty()
                        LaunchedEffect(docId) { vm.selectDocument(docId) }
                        val doc = selectedDocument?.takeIf { it.id == docId } ?: docs.firstOrNull { it.id == docId }
                        DocumentDetailScreen(
                            document = doc,
                            activeHousehold = activeHousehold,
                            currentUserId = uid,
                            protectSensitiveDocsWithBiometric = securitySettings.protectSensitiveDocsWithBiometric,
                            pinEnabled = securitySettings.pinEnabled,
                            onVerifyPinFallback = { pin, callback -> vm.verifyAndUnlockPin(pin, callback) },
                            onSensitiveUnlocked = { d, method -> vm.logSensitiveDocumentUnlocked(d, method) },
                            onToggleSensitive = { d, sensitive -> vm.updateDocumentConfidentiality(d, sensitive) },
                            onBack = { navController.popBackStack() },
                            onEdit = { d -> navController.navigate("${VieSimpleRoutes.DOC_EDITOR}?docId=${d.id}&newVer=false") },
                            onNewVersion = { d -> navController.navigate("${VieSimpleRoutes.DOC_EDITOR}?docId=${d.id}&newVer=true") },
                            onToggleShareWithHousehold = { d, share ->
                                vm.saveDocument(context, d.copy(visibility = if (share) "household" else "private"))
                            },
                            onExportPdf = { d -> vm.exportDocumentPdf(context, d) },
                            onCreateDeadlineFromDoc = { d ->
                                navController.navigate("${VieSimpleRoutes.DEADLINE_EDITOR}?deadlineId=&linkedDocId=${d.id}")
                            },
                            onGenerateLetterFromDoc = { d ->
                                openLetters(
                                    org = d.issuer.ifBlank { d.title },
                                    contract = d.title,
                                    amount = if (d.amount > 0) "%.2f".format(d.amount) else "",
                                    ref = d.referenceNumber
                                )
                            },
                            onRetouchPhoto = { d, bmp ->
                                vm.updateDocumentPhoto(context, d, bmp)
                            },
                            onDelete = { id ->
                                vm.deleteDocument(id) { navController.popBackStack() }
                            }
                        )
                    }

                    composable(
                        route = "${VieSimpleRoutes.DOC_EDITOR}?docId={docId}&newVer={newVer}",
                        arguments = listOf(
                            navArgument("docId") { type = NavType.StringType; defaultValue = "" },
                            navArgument("newVer") { type = NavType.BoolType; defaultValue = false }
                        )
                    ) { backStack ->
                        val docId = backStack.arguments?.getString("docId").orEmpty()
                        val newVer = backStack.arguments?.getBoolean("newVer") ?: false
                        LaunchedEffect(docId) { vm.selectDocument(docId.ifBlank { null }) }
                        val initialDoc = if (docId.isBlank()) {
                            VaultDocument()
                        } else {
                            selectedDocument?.takeIf { it.id == docId } ?: docs.firstOrNull { it.id == docId } ?: VaultDocument()
                        }
                        DocumentEditorScreen(
                            initialDoc = initialDoc,
                            isNewVersionBump = newVer,
                            hasHousehold = activeHousehold != null,
                            onBack = { navController.popBackStack() },
                            onSave = { updated ->
                                vm.saveDocument(context, updated) { navController.popBackStack() }
                            }
                        )
                    }

                    composable(VieSimpleRoutes.SCAN) {
                        ScanDocumentScreen(
                            ocrState = ocrState,
                            onAnalyzeBitmap = { bmp, autoSave, pdfFile, pageCount, isSensitive ->
                                vm.analyzeScannedBitmap(context, bmp, autoSave, pdfFile, pageCount, isSensitive)
                            },
                            onReanalyzeText = { rawText, path, savedId -> vm.reanalyzeOcrText(rawText, path, savedId) },
                            onResetScan = { vm.resetOcrState() },
                            onSaveScannedDocument = { doc, autoDeadline ->
                                vm.saveDocument(context, doc) { savedId ->
                                    if (autoDeadline != null) {
                                        vm.saveDeadline(context, autoDeadline.copy(linkedDocumentId = savedId))
                                    }
                                    vm.resetOcrState()
                                    navController.popBackStack()
                                }
                            },
                            onOpenSavedDocument = { savedId ->
                                vm.resetOcrState()
                                vm.recordDocumentOpened(savedId)
                                vm.selectDocument(savedId)
                                navController.navigate("${VieSimpleRoutes.DOC_DETAIL}/$savedId") {
                                    popUpTo(VieSimpleRoutes.HOME)
                                }
                            },
                            onManualEntryFallback = {
                                vm.resetOcrState()
                                navController.navigate("${VieSimpleRoutes.DOC_EDITOR}?docId=&newVer=false")
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(VieSimpleRoutes.DEADLINES) {
                        DeadlinesScreen(
                            deadlinesState = deadlinesState,
                            members = members,
                            calendarSyncEnabled = securitySettings.calendarSyncEnabled,
                            calendarSyncSummary = securitySettings.lastCalendarSyncSummary,
                            syncedDeadlineEventIds = syncedCalendarEventIds,
                            onToggleCalendarSync = { enabled -> vm.setCalendarSyncEnabled(context, enabled) },
                            onSyncCalendarNow = { vm.syncDeadlinesToSystemCalendar(context, showFeedback = true) },
                            onCreateDeadline = { navController.navigate("${VieSimpleRoutes.DEADLINE_EDITOR}?deadlineId=&linkedDocId=") },
                            onEditDeadline = { dl -> navController.navigate("${VieSimpleRoutes.DEADLINE_EDITOR}?deadlineId=${dl.id}&linkedDocId=") },
                            onMarkDone = { vm.markDeadlineDone(it) },
                            onPostpone = { vm.postponeDeadline(it) },
                            onDelete = { id -> vm.deleteDeadline(id) }
                        )
                    }

                    composable(
                        route = "${VieSimpleRoutes.DEADLINE_EDITOR}?deadlineId={deadlineId}&linkedDocId={linkedDocId}",
                        arguments = listOf(
                            navArgument("deadlineId") { type = NavType.StringType; defaultValue = "" },
                            navArgument("linkedDocId") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { backStack ->
                        val deadlineId = backStack.arguments?.getString("deadlineId").orEmpty()
                        val linkedDocId = backStack.arguments?.getString("linkedDocId").orEmpty()
                        LaunchedEffect(deadlineId) { vm.selectDeadline(deadlineId.ifBlank { null }) }
                        val linkedDoc = docs.firstOrNull { it.id == linkedDocId }
                        val initialDeadline = (if (deadlineId.isNotBlank()) selectedDeadline?.takeIf { it.id == deadlineId } ?: deadlines.firstOrNull { it.id == deadlineId } else null)
                            ?: Deadline(
                                title = linkedDoc?.let { "Renouvellement : ${it.title}" }.orEmpty(),
                                category = linkedDoc?.category ?: "Administratif",
                                dueDate = linkedDoc?.expirationDate.orEmpty(),
                                linkedDocumentId = linkedDocId
                            )
                        DeadlineEditorScreen(
                            initialDeadline = initialDeadline,
                            documents = docs,
                            members = members,
                            hasHousehold = activeHousehold != null,
                            onBack = { navController.popBackStack() },
                            onSave = { updated ->
                                vm.saveDeadline(context, updated) { navController.popBackStack() }
                            }
                        )
                    }

                    composable(VieSimpleRoutes.TASKS) {
                        TasksScreen(
                            tasksState = tasksState,
                            members = members,
                            offlineCacheStatus = offlineCacheStatus,
                            onCreateTask = { navController.navigate("${VieSimpleRoutes.TASK_EDITOR}?taskId=") },
                            onEditTask = { t ->
                                vm.selectTask(t.id)
                                navController.navigate("${VieSimpleRoutes.TASK_EDITOR}?taskId=${t.id}")
                            },
                            onOpenChecklists = { navController.navigate(VieSimpleRoutes.CHECKLISTS) },
                            onToggleDone = { t, done -> vm.toggleTaskDone(t, done) },
                            onToggleSubtask = { t, idx -> vm.toggleSubtask(t, idx) },
                            onPostponeTask = { t -> vm.postponeTask(t) },
                            onDeleteTask = { id -> vm.deleteTask(id) }
                        )
                    }

                    composable(
                        route = "${VieSimpleRoutes.TASK_EDITOR}?taskId={taskId}",
                        arguments = listOf(navArgument("taskId") { type = NavType.StringType; defaultValue = "" })
                    ) { backStack ->
                        val taskId = backStack.arguments?.getString("taskId").orEmpty()
                        LaunchedEffect(taskId) { vm.selectTask(taskId.ifBlank { null }) }
                        val initialTask = if (taskId.isBlank()) {
                            HouseholdTask()
                        } else {
                            selectedTask?.takeIf { it.id == taskId } ?: tasks.firstOrNull { it.id == taskId } ?: HouseholdTask()
                        }
                        TaskEditorScreen(
                            initialTask = initialTask,
                            documents = docs,
                            deadlines = deadlines,
                            members = members,
                            hasHousehold = activeHousehold != null,
                            onBack = { navController.popBackStack() },
                            onSave = { updated ->
                                vm.saveTask(updated) { navController.popBackStack() }
                            }
                        )
                    }

                    composable(VieSimpleRoutes.CHECKLISTS) {
                        ChecklistTemplatesScreen(
                            hasHousehold = activeHousehold != null,
                            onBack = { navController.popBackStack() },
                            onCreateFromTemplate = { tpl, share -> vm.createTaskFromChecklistTemplate(tpl, share) }
                        )
                    }

                    composable(VieSimpleRoutes.MORE) {
                        MoreHubScreen(
                            profile = readyProfile,
                            onOpenSubscriptions = { navController.navigate(VieSimpleRoutes.SUBSCRIPTIONS) },
                            onOpenHousehold = { navController.navigate(VieSimpleRoutes.HOUSEHOLD) },
                            onOpenAi = { navController.navigate(VieSimpleRoutes.AI_ASSISTANT) },
                            onOpenLetters = { openLetters() },
                            onOpenSearch = { navController.navigate(VieSimpleRoutes.SEARCH) },
                            onOpenProfile = { navController.navigate(VieSimpleRoutes.PROFILE) },
                            onOpenSettings = { navController.navigate(VieSimpleRoutes.SETTINGS) },
                            onOpenNotifications = { navController.navigate(VieSimpleRoutes.NOTIFICATIONS) },
                            onOpenPremium = { navController.navigate(VieSimpleRoutes.PREMIUM) },
                            onOpenSupport = { navController.navigate(VieSimpleRoutes.SUPPORT) },
                            onOpenExport = { navController.navigate(VieSimpleRoutes.EXPORT) },
                            onOpenDeleteAccount = { navController.navigate(VieSimpleRoutes.DELETE_ACCOUNT) }
                        )
                    }

                    composable(VieSimpleRoutes.SUBSCRIPTIONS) {
                        SubscriptionsScreen(
                            subscriptionsState = subsState,
                            offlineCacheStatus = offlineCacheStatus,
                            onBack = { navController.popBackStack() },
                            onCreateSubscription = { navController.navigate("${VieSimpleRoutes.SUB_EDITOR}?subId=") },
                            onEditSubscription = { s -> navController.navigate("${VieSimpleRoutes.SUB_EDITOR}?subId=${s.id}") },
                            onGenerateCancelLetter = { s ->
                                openLetters(
                                    templateId = "resiliation_abonnement",
                                    org = s.name,
                                    contract = s.name,
                                    amount = "%.2f".format(s.amount)
                                )
                            },
                            onDeleteSubscription = { id -> vm.deleteSubscription(id) }
                        )
                    }

                    composable(
                        route = "${VieSimpleRoutes.SUB_EDITOR}?subId={subId}",
                        arguments = listOf(navArgument("subId") { type = NavType.StringType; defaultValue = "" })
                    ) { backStack ->
                        val subId = backStack.arguments?.getString("subId").orEmpty()
                        LaunchedEffect(subId) { vm.selectSubscription(subId.ifBlank { null }) }
                        val initialSub = if (subId.isBlank()) {
                            SubscriptionItem()
                        } else {
                            selectedSubscription?.takeIf { it.id == subId } ?: subs.firstOrNull { it.id == subId } ?: SubscriptionItem()
                        }
                        SubscriptionEditorScreen(
                            initialSub = initialSub,
                            documents = docs,
                            hasHousehold = activeHousehold != null,
                            onBack = { navController.popBackStack() },
                            onSave = { updated ->
                                vm.saveSubscription(updated) { navController.popBackStack() }
                            },
                            onGenerateCancelLetter = { s ->
                                openLetters(
                                    templateId = "resiliation_abonnement",
                                    org = s.name,
                                    contract = s.name,
                                    amount = "%.2f".format(s.amount)
                                )
                            }
                        )
                    }

                    composable(VieSimpleRoutes.HOUSEHOLD) {
                        HouseholdScreen(
                            householdsState = householdsState,
                            activeHousehold = activeHousehold,
                            members = members,
                            activities = activities,
                            currentUserId = uid,
                            onBack = { navController.popBackStack() },
                            onOpenMembers = { navController.navigate(VieSimpleRoutes.HOUSEHOLD_MEMBERS) },
                            onOpenInvite = { navController.navigate(VieSimpleRoutes.HOUSEHOLD_INVITE) },
                            onCreateHousehold = { vm.createHousehold(it) },
                            onSwitchHousehold = { vm.switchActiveHousehold(it) },
                            onLeaveHousehold = { vm.leaveHousehold(it) },
                            onDeleteHousehold = { vm.deleteHousehold(it) }
                        )
                    }

                    composable(VieSimpleRoutes.HOUSEHOLD_MEMBERS) {
                        HouseholdMembersScreen(
                            household = activeHousehold,
                            members = members,
                            currentUserId = uid,
                            onBack = { navController.popBackStack() },
                            onUpdateRole = { hh, mUid, role -> vm.updateMemberRole(hh, mUid, role) },
                            onRemoveMember = { hh, mUid -> vm.removeMember(hh, mUid) }
                        )
                    }

                    composable(VieSimpleRoutes.HOUSEHOLD_INVITE) {
                        HouseholdInviteScreen(
                            household = activeHousehold,
                            onBack = { navController.popBackStack() },
                            onJoinWithCode = { vm.joinHousehold(it) },
                            onShowMessage = { vm.showMessage(it) }
                        )
                    }

                    composable(VieSimpleRoutes.AI_ASSISTANT) {
                        AiAssistantScreen(
                            messages = aiMessages,
                            chatState = aiChatState,
                            onBack = { navController.popBackStack() },
                            onSendPrompt = { vm.sendAiMessage(it) },
                            onClearHistory = { vm.clearAiConversation() },
                            onConfirmCreateDeadline = { dl -> vm.saveDeadline(context, dl) },
                            onConfirmCreateTask = { t -> vm.saveTask(t) },
                            onOpenLetterTemplate = { tplId -> openLetters(templateId = tplId) }
                        )
                    }

                    composable(
                        route = "${VieSimpleRoutes.LETTERS}?templateId={templateId}&org={org}&contract={contract}&amount={amount}&ref={ref}",
                        arguments = listOf(
                            navArgument("templateId") { type = NavType.StringType; defaultValue = "" },
                            navArgument("org") { type = NavType.StringType; defaultValue = "" },
                            navArgument("contract") { type = NavType.StringType; defaultValue = "" },
                            navArgument("amount") { type = NavType.StringType; defaultValue = "" },
                            navArgument("ref") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { backStack ->
                        val tplId = backStack.arguments?.getString("templateId")?.takeIf { it.isNotBlank() }
                        val org = backStack.arguments?.getString("org").orEmpty()
                        val contract = backStack.arguments?.getString("contract").orEmpty()
                        val amount = backStack.arguments?.getString("amount").orEmpty()
                        val ref = backStack.arguments?.getString("ref").orEmpty()
                        LetterTemplatesScreen(
                            initialTemplateId = tplId,
                            prefillOrg = org,
                            prefillContract = contract,
                            prefillAmount = amount,
                            prefillRef = ref,
                            profile = readyProfile,
                            securitySettings = securitySettings,
                            documents = docs,
                            subscriptions = subs,
                            deadlines = deadlines,
                            hasHousehold = activeHousehold != null,
                            onBack = { navController.popBackStack() },
                            onSaveSenderDefaults = { addr, city, phone -> vm.saveSenderDetails(addr, city, phone) },
                            onSaveGeneratedLetterToVault = { tpl, subject, body, recipientOrg, refNumber, parsedAmount, pdfFile, shareWithHousehold, onSaved, onError ->
                                vm.saveGeneratedLetterToVault(
                                    context = context,
                                    template = tpl,
                                    subject = subject,
                                    body = body,
                                    recipientOrg = recipientOrg,
                                    referenceNumber = refNumber,
                                    amount = parsedAmount,
                                    pdfFile = pdfFile,
                                    shareWithHousehold = shareWithHousehold,
                                    onSaved = onSaved,
                                    onError = onError
                                )
                            },
                            onOpenSavedDocument = { docId ->
                                vm.recordDocumentOpened(docId)
                                vm.selectDocument(docId)
                                navController.navigate("${VieSimpleRoutes.DOC_DETAIL}/$docId")
                            },
                            onShowMessage = { vm.showMessage(it) }
                        )
                    }

                    composable(VieSimpleRoutes.SEARCH) {
                        UniversalSearchScreen(
                            results = universalSearchResults,
                            recentSearches = securitySettings.recentSearches,
                            onQueryChange = vm::updateSearchQuery,
                            onSelectTab = vm::setSearchTab,
                            onSelectCategory = vm::setSearchCategory,
                            onToggleTag = vm::toggleSearchTag,
                            onToggleActiveOnly = vm::toggleSearchActiveOnly,
                            onToggleHouseholdOnly = vm::toggleSearchHouseholdOnly,
                            onToggleFavoritesOnly = vm::toggleSearchFavoritesOnly,
                            onResetFilters = { vm.resetSearchFilters(keepQuery = false) },
                            onSaveRecentSearch = { q -> scope.launch { securityPrefs.addRecentSearch(q) } },
                            onClearRecentSearches = { scope.launch { securityPrefs.clearRecentSearches() } },
                            onBack = { navController.popBackStack() },
                            onOpenDocument = { id ->
                                vm.recordDocumentOpened(id)
                                vm.selectDocument(id)
                                navController.navigate("${VieSimpleRoutes.DOC_DETAIL}/$id")
                            },
                            onOpenTask = { t ->
                                vm.selectTask(t.id)
                                navController.navigate("${VieSimpleRoutes.TASK_EDITOR}?taskId=${t.id}")
                            },
                            onOpenSubscription = { s ->
                                vm.selectSubscription(s.id)
                                navController.navigate("${VieSimpleRoutes.SUB_EDITOR}?subId=${s.id}")
                            },
                            onOpenDeadline = { dl ->
                                vm.selectDeadline(dl.id)
                                navController.navigate("${VieSimpleRoutes.DEADLINE_EDITOR}?deadlineId=${dl.id}&linkedDocId=")
                            }
                        )
                    }

                    composable(VieSimpleRoutes.PROFILE) {
                        ProfileScreen(
                            profile = readyProfile,
                            onBack = { navController.popBackStack() },
                            onSignOut = {
                                vm.clearSessionCachesBeforeSignOut(context) {
                                    signOut(credentialManager, scope) { onSignedOut() }
                                }
                            }
                        )
                    }

                    composable(VieSimpleRoutes.SETTINGS) {
                        SettingsScreen(
                            settings = securitySettings,
                            securityPrefs = securityPrefs,
                            securityLogs = securityLogs,
                            offlineCacheStatus = offlineCacheStatus,
                            onRefreshOfflineCache = { vm.forceRefreshOfflineRoomCache() },
                            onClearOfflineCache = { vm.clearOfflineRoomCache() },
                            onBack = { navController.popBackStack() },
                            onLockNow = { vm.lockNow() },
                            onReplayCoachMark = {
                                vm.replayCoachMark()
                                navigateTopTab(VieSimpleRoutes.HOME)
                            },
                            onSignOutAll = {
                                vm.clearSessionCachesBeforeSignOut(context) {
                                    signOut(credentialManager, scope) { onSignedOut() }
                                }
                            },
                            onShowMessage = { vm.showMessage(it) }
                        )
                    }

                    composable(VieSimpleRoutes.NOTIFICATIONS) {
                        NotificationsSettingsScreen(
                            settings = securitySettings,
                            securityPrefs = securityPrefs,
                            fcmSyncStatus = fcmSyncStatus,
                            onSyncFcmNow = { vm.syncFcmAndAutomaticReminders(context, showFeedback = true) },
                            onRunDeadlineWorkerNow = { vm.runDeadlineWorkerNow(context) },
                            onToggleCalendarSync = { enabled -> vm.setCalendarSyncEnabled(context, enabled) },
                            onSyncCalendarNow = { vm.syncDeadlinesToSystemCalendar(context, showFeedback = true) },
                            onBack = { navController.popBackStack() },
                            onShowMessage = { vm.showMessage(it) }
                        )
                    }

                    composable(VieSimpleRoutes.PREMIUM) {
                        PremiumScreen(
                            currentPlan = readyProfile.planTier,
                            onBack = { navController.popBackStack() },
                            onSelectPlan = { vm.updatePlanTier(it) },
                            onShowMessage = { vm.showMessage(it) }
                        )
                    }

                    composable(VieSimpleRoutes.SUPPORT) {
                        SupportHelpScreen(onBack = { navController.popBackStack() })
                    }

                    composable(VieSimpleRoutes.EXPORT) {
                        DataExportScreen(
                            onBack = { navController.popBackStack() },
                            onExportConfirmed = { onReady -> vm.exportAllUserData(context, onReady) }
                        )
                    }

                    composable(VieSimpleRoutes.DELETE_ACCOUNT) {
                        AccountDeletionScreen(
                            onBack = { navController.popBackStack() },
                            onConfirmDeleteAccount = {
                                vm.deleteAccountDataAndSignOut(context) {
                                    signOut(credentialManager, scope) { onSignedOut() }
                                }
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = coachMarkPending && currentRoute == VieSimpleRoutes.HOME,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(180))
        ) {
            CoachMarkOverlay(
                step = coachStep,
                scanTargetBounds = scanTargetBounds?.translate(-overlayOffsetInRoot),
                aiTargetBounds = aiTargetBounds?.translate(-overlayOffsetInRoot),
                onStepChange = { coachStep = it },
                onTryScan = {
                    vm.dismissCoachMark()
                    navController.navigate(VieSimpleRoutes.SCAN)
                },
                onTryAi = {
                    vm.dismissCoachMark()
                    navController.navigate(VieSimpleRoutes.AI_ASSISTANT)
                },
                onDismiss = { vm.dismissCoachMark() }
            )
        }
    }
            }
        }
    }
}

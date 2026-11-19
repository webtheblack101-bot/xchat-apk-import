package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.UiState
import com.example.data.repository.AuthRepository
import com.example.data.repository.BattleRepository
import com.example.data.repository.DiscoveryRepository
import com.example.data.repository.GroupRepository
import com.example.data.repository.MessagingRepository
import com.example.data.repository.SafetyNotificationRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.LoadingStateView
import com.example.ui.screens.AdminModerationScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BattlesScreen
import com.example.ui.screens.ChatsScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.GroupDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OnboardingWizardScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.state.AdminModerationViewModel
import com.example.ui.state.AppSessionViewModel
import com.example.ui.state.BattlesChatsViewModel
import com.example.ui.state.DiscoverViewModel
import com.example.ui.state.HomeGroupsViewModel
import com.example.ui.state.SessionBundle
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CoolAsh
import com.example.ui.theme.DeepVelvetRed
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoseWhite
import com.example.ui.theme.SmokedOnyx
import com.example.ui.theme.XCrimson
import com.google.firebase.firestore.FirebaseFirestore

enum class XChatTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
) {
    HOME("home", R.string.nav_home, Icons.Rounded.Home, "nav_tab_home"),
    DISCOVER("discover", R.string.nav_discover, Icons.Rounded.Favorite, "nav_tab_discover"),
    BATTLES("battles", R.string.nav_battles, Icons.Rounded.LocalFireDepartment, "nav_tab_battles"),
    CHATS("chats", R.string.nav_chats, Icons.Rounded.ChatBubble, "nav_tab_chats"),
    PROFILE("profile", R.string.nav_profile, Icons.Rounded.Person, "nav_tab_profile")
}

sealed interface XChatOverlay {
    data object Notifications : XChatOverlay
    data object AdminModeration : XChatOverlay
    data class GroupDetail(val groupId: String) : XChatOverlay
}

@Composable
fun XChatRoot(
    authRepository: AuthRepository
) {
    val authUser by authRepository.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = authRepository.currentUser)

    val user = authUser
    if (user == null) {
        AuthScreen(
            authRepository = authRepository,
            onAuthSuccess = { }
        )
    } else {
        key(user.uid) {
            AuthenticatedXChatApp(
                currentUserId = user.uid,
                defaultDisplayName = user.displayName.orEmpty(),
                authRepository = authRepository
            )
        }
    }
}

@Composable
private fun AuthenticatedXChatApp(
    currentUserId: String,
    defaultDisplayName: String,
    authRepository: AuthRepository
) {
    val context = LocalContext.current.applicationContext
    val db = remember(context) {
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    }

    val userRepo = remember(db) { UserRepository(db) }
    val groupRepo = remember(db) { GroupRepository(db) }
    val messagingRepo = remember(db) { MessagingRepository(db) }
    val discoveryRepo = remember(db) { DiscoveryRepository(db) }
    val battleRepo = remember(db) { BattleRepository(db) }
    val safetyRepo = remember(db) { SafetyNotificationRepository(db) }

    val sessionViewModel: AppSessionViewModel = viewModel(
        key = "session_$currentUserId",
        factory = viewModelFactory {
            initializer {
                AppSessionViewModel(currentUserId, authRepository, userRepo, safetyRepo)
            }
        }
    )
    val homeGroupsViewModel: HomeGroupsViewModel = viewModel(
        key = "home_$currentUserId",
        factory = viewModelFactory {
            initializer {
                HomeGroupsViewModel(currentUserId, userRepo, groupRepo, messagingRepo)
            }
        }
    )
    val discoverViewModel: DiscoverViewModel = viewModel(
        key = "discover_$currentUserId",
        factory = viewModelFactory {
            initializer {
                DiscoverViewModel(currentUserId, userRepo, discoveryRepo)
            }
        }
    )
    val battlesChatsViewModel: BattlesChatsViewModel = viewModel(
        key = "battles_chats_$currentUserId",
        factory = viewModelFactory {
            initializer {
                BattlesChatsViewModel(currentUserId, battleRepo, messagingRepo)
            }
        }
    )
    val adminModerationViewModel: AdminModerationViewModel = viewModel(
        key = "admin_$currentUserId",
        factory = viewModelFactory {
            initializer {
                AdminModerationViewModel(safetyRepo)
            }
        }
    )

    val sessionState by sessionViewModel.sessionState.collectAsStateWithLifecycle()
    val isAdmin by sessionViewModel.isAdmin.collectAsStateWithLifecycle()
    val isBusy by sessionViewModel.isBusy.collectAsStateWithLifecycle()
    val sessionMessage by sessionViewModel.actionMessage.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        is UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack),
                contentAlignment = Alignment.Center
            ) {
                LoadingStateView("Loading your X Chat account...")
            }
        }

        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                ErrorStateBanner(message = state.message)
            }
        }

        is UiState.Success -> {
            val session = state.data
            if (session.needsOnboarding) {
                OnboardingWizardScreen(
                    defaultDisplayName = defaultDisplayName,
                    sessionViewModel = sessionViewModel,
                    isBusy = isBusy,
                    feedbackMessage = sessionMessage,
                    onDismissFeedback = { sessionViewModel.clearMessage() }
                )
            } else {
                XChatMainShell(
                    session = session,
                    isAdmin = isAdmin,
                    sessionViewModel = sessionViewModel,
                    homeGroupsViewModel = homeGroupsViewModel,
                    discoverViewModel = discoverViewModel,
                    battlesChatsViewModel = battlesChatsViewModel,
                    adminModerationViewModel = adminModerationViewModel
                )
            }
        }
    }
}

@Composable
private fun XChatMainShell(
    session: SessionBundle,
    isAdmin: Boolean,
    sessionViewModel: AppSessionViewModel,
    homeGroupsViewModel: HomeGroupsViewModel,
    discoverViewModel: DiscoverViewModel,
    battlesChatsViewModel: BattlesChatsViewModel,
    adminModerationViewModel: AdminModerationViewModel
) {
    val me = session.publicUser ?: return
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by remember { mutableStateOf(XChatTab.HOME) }
    var activeOverlay by remember { mutableStateOf<XChatOverlay?>(null) }
    var profileInitialTab by remember { mutableIntStateOf(0) }

    val homeState by homeGroupsViewModel.homeFeedState.collectAsStateWithLifecycle()
    val groupDetailState by homeGroupsViewModel.selectedGroupState.collectAsStateWithLifecycle()
    val discoverState by discoverViewModel.discoverState.collectAsStateWithLifecycle()
    val matchCelebration by discoverViewModel.matchCelebration.collectAsStateWithLifecycle()
    val battlesState by battlesChatsViewModel.battlesState.collectAsStateWithLifecycle()
    val activeBattleState by battlesChatsViewModel.activeBattleState.collectAsStateWithLifecycle()
    val selectedBattleId by battlesChatsViewModel.selectedBattleId.collectAsStateWithLifecycle()
    val conversationsState by battlesChatsViewModel.conversationsState.collectAsStateWithLifecycle()
    val activeChatState by battlesChatsViewModel.activeChatState.collectAsStateWithLifecycle()
    val selectedConversationId by battlesChatsViewModel.selectedConversationId.collectAsStateWithLifecycle()
    val adminState by adminModerationViewModel.dashboardState.collectAsStateWithLifecycle()

    val sessionMsg by sessionViewModel.actionMessage.collectAsStateWithLifecycle()
    val homeMsg by homeGroupsViewModel.feedback.collectAsStateWithLifecycle()
    val discoverMsg by discoverViewModel.feedback.collectAsStateWithLifecycle()
    val bcMsg by battlesChatsViewModel.feedback.collectAsStateWithLifecycle()
    val adminMsg by adminModerationViewModel.feedback.collectAsStateWithLifecycle()

    LaunchedEffect(sessionMsg) {
        if (sessionMsg != null) {
            snackbarHostState.showSnackbar(sessionMsg!!)
            sessionViewModel.clearMessage()
        }
    }
    LaunchedEffect(homeMsg) {
        if (homeMsg != null) {
            snackbarHostState.showSnackbar(homeMsg!!)
            homeGroupsViewModel.clearFeedback()
        }
    }
    LaunchedEffect(discoverMsg) {
        if (discoverMsg != null) {
            snackbarHostState.showSnackbar(discoverMsg!!)
            discoverViewModel.clearFeedback()
        }
    }
    LaunchedEffect(bcMsg) {
        if (bcMsg != null) {
            snackbarHostState.showSnackbar(bcMsg!!)
            battlesChatsViewModel.clearFeedback()
        }
    }
    LaunchedEffect(adminMsg) {
        if (adminMsg != null) {
            snackbarHostState.showSnackbar(adminMsg!!)
            adminModerationViewModel.clearFeedback()
        }
    }

    if (activeOverlay == null && currentTab != XChatTab.HOME && selectedBattleId == null && selectedConversationId == null) {
        BackHandler { currentTab = XChatTab.HOME }
    }

    val openConversation: (String) -> Unit = { convId ->
        activeOverlay = null
        battlesChatsViewModel.selectBattle(null)
        battlesChatsViewModel.selectConversation(convId)
        currentTab = XChatTab.CHATS
    }

    val totalUnreadChats = when (val cState = conversationsState) {
        is UiState.Success -> cState.data.sumOf { (it.unreadCounts[me.userId] ?: 0L).toInt() }
        else -> 0
    }

    when (val overlay = activeOverlay) {
        is XChatOverlay.Notifications -> {
            NotificationsScreen(
                session = session,
                sessionViewModel = sessionViewModel,
                onBack = { activeOverlay = null }
            )
            return
        }

        is XChatOverlay.AdminModeration -> {
            if (isAdmin) {
                AdminModerationScreen(
                    dashboardState = adminState,
                    viewModel = adminModerationViewModel,
                    onBack = { activeOverlay = null }
                )
            } else {
                activeOverlay = null
            }
            return
        }

        is XChatOverlay.GroupDetail -> {
            GroupDetailScreen(
                me = me,
                groupId = overlay.groupId,
                groupState = groupDetailState,
                homeGroupsViewModel = homeGroupsViewModel,
                sessionViewModel = sessionViewModel,
                onOpenGroupChat = openConversation,
                onBack = { activeOverlay = null }
            )
            return
        }

        null -> Unit
    }

    val hideBottomNav = (currentTab == XChatTab.BATTLES && selectedBattleId != null) ||
        (currentTab == XChatTab.CHATS && selectedConversationId != null)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBlack,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = SmokedOnyx,
                        contentColor = MoonWhite
                    )
                }
            },
            bottomBar = {
                if (!useRail && !hideBottomNav) {
                    NavigationBar(containerColor = CharcoalSurface) {
                        XChatTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (tab != XChatTab.PROFILE) profileInitialTab = 0
                                    currentTab = tab
                                },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        unreadChats = totalUnreadChats
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MoonWhite,
                                    selectedTextColor = MoonWhite,
                                    indicatorColor = DeepVelvetRed,
                                    unselectedIconColor = CoolAsh,
                                    unselectedTextColor = CoolAsh
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !hideBottomNav) {
                    NavigationRail(
                        containerColor = CharcoalSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        XChatTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (tab != XChatTab.PROFILE) profileInitialTab = 0
                                    currentTab = tab
                                },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        unreadChats = totalUnreadChats
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MoonWhite,
                                    selectedTextColor = MoonWhite,
                                    indicatorColor = DeepVelvetRed,
                                    unselectedIconColor = CoolAsh,
                                    unselectedTextColor = CoolAsh
                                )
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        if (me.accountStatus != "active") {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                ErrorStateBanner(
                                    message = "Account status: ${me.accountStatus.uppercase()}. Some social actions are limited by moderation."
                                )
                            }
                        }

                        when (currentTab) {
                            XChatTab.HOME -> HomeScreen(
                                session = session,
                                homeState = homeState,
                                isAdmin = isAdmin,
                                sessionViewModel = sessionViewModel,
                                homeGroupsViewModel = homeGroupsViewModel,
                                onOpenNotifications = { activeOverlay = XChatOverlay.Notifications },
                                onOpenAdminConsole = { activeOverlay = XChatOverlay.AdminModeration },
                                onOpenGroup = { groupId ->
                                    homeGroupsViewModel.selectGroup(groupId)
                                    activeOverlay = XChatOverlay.GroupDetail(groupId)
                                },
                                onOpenConversation = openConversation,
                                onNavigateToDiscover = { currentTab = XChatTab.DISCOVER },
                                onNavigateToBattles = { currentTab = XChatTab.BATTLES }
                            )

                            XChatTab.DISCOVER -> DiscoverScreen(
                                session = session,
                                discoverState = discoverState,
                                matchCelebration = matchCelebration,
                                discoverViewModel = discoverViewModel,
                                sessionViewModel = sessionViewModel,
                                onOpenConversation = openConversation,
                                onGoToDatingSettings = {
                                    profileInitialTab = 1
                                    currentTab = XChatTab.PROFILE
                                }
                            )

                            XChatTab.BATTLES -> BattlesScreen(
                                me = me,
                                battlesState = battlesState,
                                activeBattleState = activeBattleState,
                                selectedBattleId = selectedBattleId,
                                viewModel = battlesChatsViewModel
                            )

                            XChatTab.CHATS -> ChatsScreen(
                                session = session,
                                conversationsState = conversationsState,
                                activeChatState = activeChatState,
                                selectedConversationId = selectedConversationId,
                                viewModel = battlesChatsViewModel,
                                sessionViewModel = sessionViewModel
                            )

                            XChatTab.PROFILE -> ProfileScreen(
                                session = session,
                                initialTab = profileInitialTab,
                                sessionViewModel = sessionViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: XChatTab,
    unreadChats: Int
) {
    if (tab == XChatTab.CHATS && unreadChats > 0) {
        BadgedBox(
            badge = {
                Badge(containerColor = XCrimson) {
                    Text(
                        text = if (unreadChats > 99) "99+" else "$unreadChats",
                        color = RoseWhite,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        ) {
            Icon(tab.icon, contentDescription = stringResource(tab.labelRes))
        }
    } else {
        Icon(tab.icon, contentDescription = stringResource(tab.labelRes))
    }
}

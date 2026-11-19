package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DatingProfile
import com.example.data.model.UiState
import com.example.ui.components.AvatarDisplay
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.LoadingStateView
import com.example.ui.components.StatusPill
import com.example.ui.components.XChatCard
import com.example.ui.state.AdminDashboardBundle
import com.example.ui.state.AdminModerationViewModel
import com.example.ui.state.AppSessionViewModel
import com.example.ui.state.SessionBundle
import com.example.ui.theme.CoolAsh
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.DeepVelvetRed
import com.example.ui.theme.ElectricScarlet
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoseWhite
import com.example.ui.theme.SmokedOnyx
import com.example.ui.theme.XCrimson
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    session: SessionBundle,
    initialTab: Int = 0,
    sessionViewModel: AppSessionViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = session.publicUser ?: return
    val priv = session.privateAccount
    val dating = session.datingProfile

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Social fields
    var displayName by remember(me) { mutableStateOf(me.displayName) }
    var username by remember(me) { mutableStateOf(me.username) }
    var bio by remember(me) { mutableStateOf(me.bio) }
    var avatarData by remember(me) { mutableStateOf(me.avatarData) }

    val socialPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val compressed = sessionViewModel.compressImage(context, uri)
                if (compressed != null) avatarData = compressed
            }
        }
    }

    // Dating fields
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val age = ((currentYear - (priv?.birthYear ?: 2000L))).coerceAtLeast(18L)
    val isAdult = priv?.ageVerified == true
    var datingEnabled by remember(dating) { mutableStateOf(dating?.datingEnabled ?: false) }
    var datingBio by remember(dating) { mutableStateOf(dating?.datingBio ?: me.bio) }
    var city by remember(dating) { mutableStateOf(dating?.city ?: "Seattle") }
    var intent by remember(dating) { mutableStateOf(dating?.relationshipIntent ?: "dating") }
    var style by remember(dating) { mutableStateOf(dating?.conversationStyle ?: "playful") }
    var availability by remember(dating) { mutableStateOf(dating?.availabilityStatus ?: "tonight") }

    // Privacy & Notifications
    var showOnline by remember(priv) { mutableStateOf(priv?.showOnlineStatus ?: true) }
    var allowFriends by remember(priv) { mutableStateOf(priv?.allowFriendRequests ?: true) }
    var notifyMsg by remember(priv) { mutableStateOf(priv?.notifyMessages ?: true) }
    var notifyMatch by remember(priv) { mutableStateOf(priv?.notifyMatches ?: true) }
    var notifyGroup by remember(priv) { mutableStateOf(priv?.notifyGroups ?: true) }
    var notifyBattle by remember(priv) { mutableStateOf(priv?.notifyBattles ?: true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AvatarDisplay(name = displayName, imageData = avatarData, size = 68.dp, showOnlineDot = showOnline)
                Column(Modifier.weight(1f)) {
                    Text(me.displayName, style = MaterialTheme.typography.headlineMedium, color = MoonWhite)
                    Text("@${me.username}", style = MaterialTheme.typography.labelLarge, color = ElectricScarlet)
                    Text("Account: ${me.accountStatus}", style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                }
                OutlinedButton(
                    onClick = { socialPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Change avatar", tint = XCrimson)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Social Profile", "Dating Profile", "Privacy & Safety").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        label = { Text(label) },
                        modifier = Modifier.testTag("profile_tab_$idx"),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = XCrimson, selectedLabelColor = MoonWhite)
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                item {
                    XChatCard {
                        Text("Social Identity", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(40) },
                            label = { Text("Display Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(24) },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it.take(300) },
                            label = { Text("Social Bio") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                sessionViewModel.updateProfile(
                                    username = username,
                                    displayName = displayName,
                                    bio = bio,
                                    avatarData = avatarData,
                                    interests = me.interests,
                                    languages = me.languages,
                                    onlineVisible = showOnline
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_social_profile_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                        ) {
                            Text("Save Social Profile")
                        }
                    }
                }
            }

            1 -> {
                item {
                    XChatCard {
                        if (!isAdult) {
                            Text(
                                "Dating discovery is restricted to verified 18+ accounts.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoralAlert
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Dating Discovery Active", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                                    Text("Toggle off to pause dating without deleting your social account", style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                                }
                                Switch(
                                    checked = datingEnabled,
                                    onCheckedChange = { datingEnabled = it },
                                    modifier = Modifier.testTag("profile_dating_toggle"),
                                    colors = SwitchDefaults.colors(checkedThumbColor = MoonWhite, checkedTrackColor = XCrimson)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = city,
                                onValueChange = { city = it.take(80) },
                                label = { Text("City / Approximate Region") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = datingBio,
                                onValueChange = { datingBio = it.take(400) },
                                label = { Text("Dating Bio") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Relationship Intent", style = MaterialTheme.typography.labelLarge, color = MoonWhite)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("dating", "serious", "casual", "friendship").forEach { item ->
                                    FilterChip(
                                        selected = intent == item,
                                        onClick = { intent = item },
                                        label = { Text(item.replaceFirstChar { it.uppercase() }) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DeepVelvetRed, selectedLabelColor = RoseWhite)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Availability", style = MaterialTheme.typography.labelLarge, color = MoonWhite)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("available_now", "tonight", "weekend", "offline").forEach { item ->
                                    FilterChip(
                                        selected = availability == item,
                                        onClick = { availability = item },
                                        label = { Text(item.replace('_', ' ')) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DeepVelvetRed, selectedLabelColor = RoseWhite)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val updated = DatingProfile(
                                        userId = me.userId,
                                        displayName = me.displayName,
                                        datingEnabled = datingEnabled,
                                        datingBio = datingBio,
                                        age = age,
                                        city = city.ifBlank { "Global" },
                                        relationshipIntent = intent,
                                        conversationStyle = style,
                                        availabilityStatus = availability,
                                        interests = me.interests,
                                        languages = me.languages,
                                        photos = listOfNotNull(avatarData)
                                    )
                                    sessionViewModel.saveDatingProfile(updated, existsAlready = dating != null)
                                },
                                modifier = Modifier.fillMaxWidth().testTag("save_dating_profile_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                            ) {
                                Text("Save Dating Profile")
                            }
                        }
                    }
                }
            }

            else -> {
                item {
                    XChatCard {
                        Text("Privacy & Notification Preferences", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                        Spacer(Modifier.height(8.dp))
                        PrefSwitch("Show Online Status", showOnline) { showOnline = it }
                        PrefSwitch("Allow Friend Connections", allowFriends) { allowFriends = it }
                        PrefSwitch("Message Notifications", notifyMsg) { notifyMsg = it }
                        PrefSwitch("Match Notifications", notifyMatch) { notifyMatch = it }
                        PrefSwitch("Group Notifications", notifyGroup) { notifyGroup = it }
                        PrefSwitch("Battle Notifications", notifyBattle) { notifyBattle = it }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                sessionViewModel.updatePrivacyAndNotifications(
                                    showOnline, allowFriends, notifyMsg, notifyMatch, notifyGroup, notifyBattle
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                        ) {
                            Text("Save Preferences")
                        }
                    }
                }

                if (session.blockedByMe.isNotEmpty()) {
                    item {
                        XChatCard {
                            Text("Blocked Users (${session.blockedByMe.size})", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                            Spacer(Modifier.height(8.dp))
                            session.blockedByMe.forEach { block ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(block.blockedId.take(16), style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                                    OutlinedButton(onClick = { sessionViewModel.unblockUser(block.blockedId) }) {
                                        Text("Unblock", color = MoonWhite)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { sessionViewModel.signOut(context) },
                            modifier = Modifier.weight(1f).testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = MoonWhite)
                            Spacer(Modifier.width(6.dp))
                            Text("Sign Out", color = MoonWhite)
                        }
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f).testTag("delete_account_button")
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = CoralAlert)
                            Spacer(Modifier.width(6.dp))
                            Text("Delete Account", color = CoralAlert)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SmokedOnyx,
            title = { Text("Delete Account Data?", color = MoonWhite) },
            text = { Text("This deletes your public profile, private PII, and dating profile from Firestore and signs you out.", color = CoolAsh) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        sessionViewModel.deleteAccountAndSignOut(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                ) {
                    Text("Delete & Sign Out")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = MoonWhite) }
            }
        )
    }
}

@Composable
private fun PrefSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MoonWhite)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MoonWhite, checkedTrackColor = XCrimson)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    session: SessionBundle,
    sessionViewModel: AppSessionViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBlack),
                title = { Text("Notifications", color = MoonWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MoonWhite)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (session.notifications.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Rounded.Notifications,
                        title = "All Caught Up",
                        subtitle = "New messages, mutual matches, group invites, and battle updates appear here."
                    )
                }
            } else {
                items(session.notifications, key = { it.id }) { notif ->
                    XChatCard(
                        borderColor = if (!notif.readFlag) XCrimson else com.example.ui.theme.HairlineSlate
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(notif.title, style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                                Text(notif.body, style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                            }
                            if (!notif.readFlag) {
                                OutlinedButton(onClick = { sessionViewModel.markNotificationRead(notif.id) }) {
                                    Text("Mark Read", color = ElectricScarlet)
                                }
                            } else {
                                StatusPill("Read")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminModerationScreen(
    dashboardState: UiState<AdminDashboardBundle>,
    viewModel: AdminModerationViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBlack),
                title = { Text("Moderation Console", color = MoonWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MoonWhite)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusPill("Restricted Admin Role • Audit Logged", containerColor = DeepVelvetRed, contentColor = RoseWhite)
            }
            when (dashboardState) {
                is UiState.Loading -> item { LoadingStateView("Loading moderation queue...") }
                is UiState.Error -> item { ErrorStateBanner(dashboardState.message) }
                is UiState.Success -> {
                    val data = dashboardState.data
                    item {
                        Text("Safety Reports (${data.reports.size})", style = MaterialTheme.typography.titleLarge, color = MoonWhite)
                    }
                    if (data.reports.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = Icons.Rounded.Security,
                                title = "No Reports Pending",
                                subtitle = "User, group, and message safety reports appear here for admin review."
                            )
                        }
                    } else {
                        items(data.reports, key = { it.id }) { report ->
                            XChatCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${report.targetType.uppercase()}: ${report.reason}", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                                    StatusPill(report.status.uppercase())
                                }
                                if (report.description.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(report.description, style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                                }
                                if (report.status == "open") {
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (report.reportedUserId.isNotBlank()) {
                                            Button(
                                                onClick = { viewModel.takeAction(report.id, report.reportedUserId, "suspend", report.reason) },
                                                colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                                            ) {
                                                Text("Suspend User")
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.takeAction(report.id, report.reportedUserId, "restrict", report.reason) }
                                            ) {
                                                Text("Restrict", color = MoonWhite)
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.takeAction(report.id, report.reportedUserId.ifBlank { report.targetId }, "dismiss_report", "Reviewed") }
                                        ) {
                                            Text("Dismiss", color = CoolAsh)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Audit Log (${data.actions.size})", style = MaterialTheme.typography.titleLarge, color = MoonWhite)
                    }
                    items(data.actions, key = { it.id }) { act ->
                        XChatCard {
                            Text("${act.actionType.uppercase()} -> ${act.targetUserId.take(14)}", style = MaterialTheme.typography.titleMedium, color = EmeraldOnline)
                            Text("Reason: ${act.reason}", style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                        }
                    }
                }
            }
        }
    }
}

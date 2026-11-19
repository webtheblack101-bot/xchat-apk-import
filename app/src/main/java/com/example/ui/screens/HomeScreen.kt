package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PublicUser
import com.example.data.model.UiState
import com.example.ui.components.AvatarDisplay
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.LoadingStateView
import com.example.ui.components.StatusPill
import com.example.ui.components.XChatBrandWordmark
import com.example.ui.components.XChatCard
import com.example.ui.state.AppSessionViewModel
import com.example.ui.state.HomeFeedData
import com.example.ui.state.HomeGroupsViewModel
import com.example.ui.state.SessionBundle
import com.example.ui.theme.CoolAsh
import com.example.ui.theme.DeepVelvetRed
import com.example.ui.theme.ElectricScarlet
import com.example.ui.theme.ElevatedOnyx
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.RoseWhite
import com.example.ui.theme.XCrimson
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    session: SessionBundle,
    homeState: UiState<HomeFeedData>,
    isAdmin: Boolean,
    sessionViewModel: AppSessionViewModel,
    homeGroupsViewModel: HomeGroupsViewModel,
    onOpenNotifications: () -> Unit,
    onOpenAdminConsole: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToBattles: () -> Unit
) {
    val me = session.publicUser ?: return
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showRedeemInviteDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                XChatBrandWordmark()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAdmin) {
                        IconButton(
                            onClick = onOpenAdminConsole,
                            modifier = Modifier.testTag("home_admin_button")
                        ) {
                            Icon(
                                Icons.Rounded.AdminPanelSettings,
                                contentDescription = "Admin moderation console",
                                tint = ElectricScarlet
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("home_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (session.unreadNotificationCount > 0) {
                                    Badge(containerColor = XCrimson) {
                                        Text("${session.unreadNotificationCount}", color = MoonWhite)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Notifications,
                                contentDescription = "Notifications",
                                tint = MoonWhite
                            )
                        }
                    }
                }
            }
        }

        // Quick Shortcuts Row
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedButton(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier.testTag("home_create_group_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = XCrimson)
                        Spacer(Modifier.width(6.dp))
                        Text("Create Group", color = MoonWhite)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { showRedeemInviteDialog = true },
                        modifier = Modifier.testTag("home_redeem_invite_button")
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null, tint = XCrimson)
                        Spacer(Modifier.width(6.dp))
                        Text("Redeem Invite", color = MoonWhite)
                    }
                }
                item {
                    OutlinedButton(onClick = onNavigateToBattles) {
                        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = XCrimson)
                        Spacer(Modifier.width(6.dp))
                        Text("Live Battles", color = MoonWhite)
                    }
                }
                item {
                    OutlinedButton(onClick = onNavigateToDiscover) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = XCrimson)
                        Spacer(Modifier.width(6.dp))
                        Text("Discover", color = MoonWhite)
                    }
                }
            }
        }

        when (homeState) {
            is UiState.Loading -> item { LoadingStateView("Loading social home...") }
            is UiState.Error -> item { ErrorStateBanner(homeState.message) }
            is UiState.Success -> {
                val data = homeState.data
                val blockedIds = session.allBlockedUserIds
                val friendUids = session.friends.map { it.friendUid }.toSet()

                // Recent Conversations
                item {
                    Text("Recent Conversations", style = MaterialTheme.typography.titleLarge, color = MoonWhite)
                }
                if (data.conversations.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.ChatBubble,
                            title = "No conversations yet",
                            subtitle = "Message a friend below or join a community group to start chatting."
                        )
                    }
                } else {
                    items(data.conversations.take(4), key = { it.id }) { conv ->
                        val unread = conv.unreadCounts[me.userId] ?: 0L
                        XChatCard(
                            modifier = Modifier
                                .clickable { onOpenConversation(conv.id) }
                                .testTag("home_conv_${conv.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = conv.title?.takeIf { it.isNotBlank() } ?: "Direct Chat",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MoonWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (conv.type == "group") StatusPill("Group")
                                    }
                                    Text(
                                        text = conv.lastMessagePreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CoolAsh,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (unread > 0L) {
                                    StatusPill("$unread new", containerColor = XCrimson, contentColor = MoonWhite)
                                }
                            }
                        }
                    }
                }

                // My Joined Groups
                if (data.myMemberships.isNotEmpty()) {
                    item {
                        Text("Your Communities", style = MaterialTheme.typography.titleLarge, color = MoonWhite)
                    }
                    items(data.myMemberships, key = { it.groupId }) { membership ->
                        XChatCard(
                            modifier = Modifier
                                .clickable { onOpenGroup(membership.groupId) }
                                .testTag("my_group_${membership.groupId}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(membership.groupName, style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                                    Text("Role: ${membership.role} • ${membership.visibility}", style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                                }
                                StatusPill("Open")
                            }
                        }
                    }
                }

                // Suggested Public Groups
                item {
                    Text("Suggested Public Groups", style = MaterialTheme.typography.titleLarge, color = MoonWhite)
                }
                val joinedGroupIds = data.myMemberships.map { it.groupId }.toSet()
                val publicSuggestions = data.publicGroups.filter { it.id !in joinedGroupIds }
                if (publicSuggestions.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.Groups,
                            title = "No new public groups yet",
                            subtitle = "Create a public or private community room for your interests.",
                            actionLabel = "Create Group",
                            onAction = { showCreateGroupDialog = true }
                        )
                    }
                } else {
                    items(publicSuggestions, key = { it.id }) { group ->
                        XChatCard(modifier = Modifier.clickable { onOpenGroup(group.id) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarDisplay(name = group.name, imageData = group.imageData, size = 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                                    Text(
                                        group.description.ifBlank { "${group.memberCount} members" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CoolAsh,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { homeGroupsViewModel.joinPublicGroup(group, me) },
                                    colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                                ) {
                                    Text("Join")
                                }
                            }
                        }
                    }
                }

                // People & Friends
                item {
                    Text("People on X Chat", style = MaterialTheme.typography.titleLarge, color = MoonWhite)
                }
                val visiblePeople = data.communityUsers.filter { it.userId !in blockedIds }
                if (visiblePeople.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.PersonAdd,
                            title = "Waiting for more members",
                            subtitle = "Other signed-in accounts will appear here so you can connect and chat."
                        )
                    }
                } else {
                    items(visiblePeople, key = { it.userId }) { person ->
                        val isFriend = person.userId in friendUids
                        XChatCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AvatarDisplay(
                                    name = person.displayName,
                                    imageData = person.avatarData,
                                    size = 48.dp,
                                    showOnlineDot = person.onlineVisible
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(person.displayName, style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                                    Text("@${person.username}", style = MaterialTheme.typography.labelSmall, color = ElectricScarlet)
                                    if (person.bio.isNotBlank()) {
                                        Text(person.bio, style = MaterialTheme.typography.bodySmall, color = CoolAsh, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (!isFriend) {
                                    OutlinedButton(onClick = { sessionViewModel.addFriend(me, person) }) {
                                        Text("Add", color = MoonWhite)
                                    }
                                } else {
                                    StatusPill("Friend", containerColor = ElevatedOnyx)
                                }
                                Button(
                                    onClick = {
                                        homeGroupsViewModel.startDirectChat(me, person) { convId ->
                                            onOpenConversation(convId)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                                ) {
                                    Text("Chat")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            sessionViewModel = sessionViewModel,
            onCreate = { name, desc, vis, img ->
                showCreateGroupDialog = false
                homeGroupsViewModel.createGroup(me, name, desc, vis, img) { groupId ->
                    onOpenGroup(groupId)
                }
            }
        )
    }

    if (showRedeemInviteDialog) {
        RedeemInviteDialog(
            onDismiss = { showRedeemInviteDialog = false },
            onRedeem = { code ->
                showRedeemInviteDialog = false
                homeGroupsViewModel.redeemInvite(code, me) { groupId ->
                    onOpenGroup(groupId)
                }
            }
        )
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    sessionViewModel: AppSessionViewModel,
    onCreate: (String, String, String, String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("public") }
    var imageData by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch { imageData = sessionViewModel.compressImage(context, uri) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.example.ui.theme.SmokedOnyx,
        title = { Text("Create Community Group", color = MoonWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_group_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(300) },
                    label = { Text("Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = visibility == "public",
                        onClick = { visibility = "public" },
                        label = { Text("Public") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DeepVelvetRed, selectedLabelColor = RoseWhite)
                    )
                    FilterChip(
                        selected = visibility == "private",
                        onClick = { visibility = "private" },
                        label = { Text("Private") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DeepVelvetRed, selectedLabelColor = RoseWhite)
                    )
                }
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = XCrimson)
                    Spacer(Modifier.width(6.dp))
                    Text(if (imageData == null) "Pick Group Cover" else "Cover Selected", color = MoonWhite)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, visibility, imageData) },
                enabled = name.trim().length >= 2,
                modifier = Modifier.testTag("confirm_create_group_button"),
                colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel", color = MoonWhite) }
        }
    )
}

@Composable
private fun RedeemInviteDialog(
    onDismiss: () -> Unit,
    onRedeem: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.example.ui.theme.SmokedOnyx,
        title = { Text("Redeem Group Invite", color = MoonWhite) },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(32) },
                label = { Text("Invite Code (e.g. XC-1A2B3C)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("redeem_invite_code_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onRedeem(code) },
                enabled = code.trim().length >= 6,
                colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
            ) {
                Text("Join Group")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel", color = MoonWhite) }
        }
    )
}

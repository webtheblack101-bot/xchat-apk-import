package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.PublicUser
import com.example.data.model.UiState
import com.example.ui.components.AvatarDisplay
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.LoadingStateView
import com.example.ui.components.ReportAbuseDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.XChatCard
import com.example.ui.state.AppSessionViewModel
import com.example.ui.state.GroupDetailBundle
import com.example.ui.state.HomeGroupsViewModel
import com.example.ui.theme.CoolAsh
import com.example.ui.theme.DeepVelvetRed
import com.example.ui.theme.ElectricScarlet
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.XCrimson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    me: PublicUser,
    groupId: String,
    groupState: UiState<GroupDetailBundle>,
    homeGroupsViewModel: HomeGroupsViewModel,
    sessionViewModel: AppSessionViewModel,
    onOpenGroupChat: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var showReportDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBlack),
                title = { Text("Community Group", color = MoonWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MoonWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { showReportDialog = true }) {
                        Icon(Icons.Rounded.Flag, contentDescription = "Report group", tint = CoolAsh)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (groupState) {
                is UiState.Loading -> item { LoadingStateView("Loading group...") }
                is UiState.Error -> item { ErrorStateBanner(groupState.message) }
                is UiState.Success -> {
                    val bundle = groupState.data
                    val group = bundle.group
                    if (group == null) {
                        item {
                            ErrorStateBanner("Group is private or unavailable to non-members.")
                        }
                        item {
                            Button(
                                onClick = { homeGroupsViewModel.requestPrivateAccess(groupId, me) },
                                colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                            ) {
                                Text("Request Private Access")
                            }
                        }
                    } else {
                        val myMembership = bundle.members.firstOrNull { it.userId == me.userId }
                        val isActiveMember = myMembership?.status == "active"
                        val isManager = myMembership?.role in setOf("owner", "admin", "moderator") || group.ownerId == me.userId

                        item {
                            XChatCard {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    AvatarDisplay(name = group.name, imageData = group.imageData, size = 60.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(group.name, style = MaterialTheme.typography.headlineMedium, color = MoonWhite)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            StatusPill(group.visibility.uppercase())
                                            StatusPill("${group.memberCount} members", containerColor = DeepVelvetRed)
                                        }
                                    }
                                }
                                if (group.description.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(group.description, style = MaterialTheme.typography.bodyMedium, color = CoolAsh)
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (isActiveMember) {
                                        Button(
                                            onClick = { onOpenGroupChat("grp_${group.id}") },
                                            colors = ButtonDefaults.buttonColors(containerColor = XCrimson),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.ChatBubble, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Open Group Chat")
                                        }
                                        if (group.ownerId != me.userId) {
                                            OutlinedButton(
                                                onClick = { homeGroupsViewModel.removeOrLeaveGroup(group.id, me.userId, onBack) }
                                            ) {
                                                Text("Leave", color = MoonWhite)
                                            }
                                        }
                                    } else if (myMembership?.status == "pending") {
                                        StatusPill("Join request pending admin approval")
                                    } else if (group.visibility == "public") {
                                        Button(
                                            onClick = { homeGroupsViewModel.joinPublicGroup(group, me) },
                                            colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                                        ) {
                                            Text("Join Public Group")
                                        }
                                    } else {
                                        Button(
                                            onClick = { homeGroupsViewModel.requestPrivateAccess(group.id, me) },
                                            colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                                        ) {
                                            Text("Request Private Access")
                                        }
                                    }
                                }
                            }
                        }

                        if (isManager) {
                            item {
                                XChatCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Group Invitations", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                                        Button(
                                            onClick = { homeGroupsViewModel.createInvite(group.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                                        ) {
                                            Icon(Icons.Rounded.Key, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("New Invite")
                                        }
                                    }
                                    bundle.myInvites.forEach { invite ->
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(invite.inviteCode, style = MaterialTheme.typography.labelLarge, color = ElectricScarlet)
                                                Text(
                                                    if (invite.revoked) "Revoked" else "Active (7-day expiry)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = CoolAsh
                                                )
                                            }
                                            if (!invite.revoked) {
                                                OutlinedButton(onClick = { homeGroupsViewModel.revokeInvite(invite.id) }) {
                                                    Text("Revoke", color = MoonWhite)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("Members (${bundle.members.size})", style = MaterialTheme.typography.titleLarge, color = MoonWhite)
                        }

                        items(bundle.members, key = { it.userId }) { member ->
                            XChatCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(member.displayName, style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                                        Text("@${member.username} • ${member.role} (${member.status})", style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                                    }
                                    if (isManager && member.userId != me.userId && member.role != "owner") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (member.status == "pending") {
                                                Button(
                                                    onClick = { homeGroupsViewModel.approveMember(group, member.userId) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                                                ) {
                                                    Text("Approve")
                                                }
                                            } else if (group.ownerId == me.userId) {
                                                val nextRole = if (member.role == "member") "moderator" else "member"
                                                OutlinedButton(onClick = { homeGroupsViewModel.updateRole(group.id, member.userId, nextRole) }) {
                                                    Text(if (nextRole == "moderator") "Make Mod" else "Set Member", color = MoonWhite)
                                                }
                                            }
                                            OutlinedButton(onClick = { homeGroupsViewModel.removeOrLeaveGroup(group.id, member.userId) }) {
                                                Text("Remove", color = ElectricScarlet)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        ReportAbuseDialog(
            targetLabel = "Group",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, desc ->
                showReportDialog = false
                sessionViewModel.submitReport("", "group", groupId, reason, desc)
            }
        )
    }
}

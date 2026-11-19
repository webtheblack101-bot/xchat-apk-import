package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Conversation
import com.example.data.model.PublicUser
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.LoadingStateView
import com.example.ui.components.MediaImageView
import com.example.ui.components.ReportAbuseDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.XChatCard
import com.example.ui.state.ActiveChatBundle
import com.example.ui.state.AppSessionViewModel
import com.example.ui.state.BattlesChatsViewModel
import com.example.ui.state.SessionBundle
import com.example.ui.theme.CoolAsh
import com.example.ui.theme.DeepVelvetRed
import com.example.ui.theme.ElectricScarlet
import com.example.ui.theme.ElevatedOnyx
import com.example.ui.theme.HairlineSlate
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoseWhite
import com.example.ui.theme.SmokedOnyx
import com.example.ui.theme.XCrimson
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ChatsScreen(
    session: SessionBundle,
    conversationsState: UiState<List<Conversation>>,
    activeChatState: UiState<ActiveChatBundle>,
    selectedConversationId: String?,
    viewModel: BattlesChatsViewModel,
    sessionViewModel: AppSessionViewModel
) {
    val me = session.publicUser ?: return

    if (selectedConversationId != null) {
        ChatRoomScreen(
            me = me,
            chatState = activeChatState,
            viewModel = viewModel,
            sessionViewModel = sessionViewModel,
            onBack = { viewModel.selectConversation(null) }
        )
        return
    }

    var searchQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Chats", style = MaterialTheme.typography.headlineLarge, color = MoonWhite)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search conversations...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("chats_search_input")
            )
        }

        when (conversationsState) {
            is UiState.Loading -> item { LoadingStateView("Syncing conversations...") }
            is UiState.Error -> item { ErrorStateBanner(conversationsState.message) }
            is UiState.Success -> {
                val blockedIds = session.allBlockedUserIds
                val filtered = conversationsState.data.filter { conv ->
                    val otherBlocked = conv.type == "direct" && conv.memberUids.any { it in blockedIds }
                    val matchesSearch = searchQuery.isBlank() ||
                        (conv.title?.contains(searchQuery, ignoreCase = true) == true) ||
                        conv.lastMessagePreview.contains(searchQuery, ignoreCase = true)
                    !otherBlocked && matchesSearch
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.ChatBubble,
                            title = "No messages yet",
                            subtitle = "Connect with people on Home or match in Discover to open a crimson-accented chat."
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { conv ->
                        val unread = conv.unreadCounts[me.userId] ?: 0L
                        XChatCard(
                            modifier = Modifier
                                .clickable { viewModel.selectConversation(conv.id) }
                                .testTag("chat_item_${conv.id}"),
                            borderColor = if (unread > 0L) XCrimson else HairlineSlate
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            conv.title?.takeIf { it.isNotBlank() } ?: "Direct Conversation",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MoonWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (conv.type == "group") StatusPill("Group")
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        conv.lastMessagePreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (unread > 0L) RoseWhite else CoolAsh,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (unread > 0L) {
                                    StatusPill("$unread", containerColor = XCrimson, contentColor = MoonWhite)
                                }
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
fun ChatRoomScreen(
    me: PublicUser,
    chatState: UiState<ActiveChatBundle>,
    viewModel: BattlesChatsViewModel,
    sessionViewModel: AppSessionViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messageInput by remember { mutableStateOf("") }
    var showReportDialog by remember { mutableStateOf(false) }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && chatState is UiState.Success) {
            val conv = chatState.data.conversation ?: return@rememberLauncherForActivityResult
            scope.launch {
                val mediaData = sessionViewModel.compressImage(context, uri)
                if (mediaData != null) {
                    viewModel.sendMessage(conv, me, messageInput, mediaData)
                    messageInput = ""
                }
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            val conv = (chatState as? UiState.Success)?.data?.conversation
            val peerUid = conv?.memberUids?.firstOrNull { it != me.userId }
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepVelvetRed.copy(alpha = 0.7f)),
                title = {
                    Column {
                        Text(
                            conv?.title?.takeIf { it.isNotBlank() } ?: "X Chat",
                            color = MoonWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (conv?.type == "group") "Group conversation" else "Private conversation",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoseWhite
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MoonWhite)
                    }
                },
                actions = {
                    if (conv?.type == "direct" && peerUid != null) {
                        IconButton(onClick = {
                            sessionViewModel.blockUser(peerUid, conv.title ?: "User")
                            onBack()
                        }) {
                            Icon(Icons.Rounded.Block, contentDescription = "Block user", tint = RoseWhite)
                        }
                    }
                    IconButton(onClick = { showReportDialog = true }) {
                        Icon(Icons.Rounded.Flag, contentDescription = "Report conversation", tint = RoseWhite)
                    }
                }
            )
        }
    ) { innerPadding ->
        when (chatState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding)) { LoadingStateView() }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) { ErrorStateBanner(chatState.message) }
            is UiState.Success -> {
                val conv = chatState.data.conversation
                val messages = chatState.data.messages

                LaunchedEffect(conv?.id, messages.size) {
                    if (conv != null) {
                        viewModel.markConversationRead(conv, messages)
                    }
                }

                if (conv == null) {
                    Box(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                        ErrorStateBanner("Conversation unavailable.")
                    }
                    return@Scaffold
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (messages.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = Icons.Rounded.ChatBubble,
                                    title = "Say Hello",
                                    subtitle = "Messages are persisted securely in real time."
                                )
                            }
                        } else {
                            items(messages, key = { it.id }) { msg ->
                                val isMine = msg.senderId == me.userId
                                val readByOther = msg.readBy.any { it != me.userId }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        modifier = Modifier.widthIn(max = 290.dp),
                                        shape = RoundedCornerShape(
                                            topStart = 18.dp,
                                            topEnd = 18.dp,
                                            bottomStart = if (isMine) 18.dp else 4.dp,
                                            bottomEnd = if (isMine) 4.dp else 18.dp
                                        ),
                                        color = if (isMine) XCrimson else SmokedOnyx,
                                        border = if (isMine) null else BorderStroke(1.dp, HairlineSlate)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .then(
                                                    if (isMine) {
                                                        Modifier.background(
                                                            Brush.linearGradient(listOf(XCrimson, DeepVelvetRed))
                                                        )
                                                    } else Modifier
                                                )
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (!isMine && conv.type == "group") {
                                                Text(msg.senderName, style = MaterialTheme.typography.labelSmall, color = ElectricScarlet)
                                            }
                                            if (!msg.deleted && !msg.mediaData.isNullOrBlank()) {
                                                MediaImageView(
                                                    imageData = msg.mediaData,
                                                    contentDescription = "Shared photo",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(180.dp)
                                                        .clip(MaterialTheme.shapes.small),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            if (msg.content.isNotBlank()) {
                                                Text(
                                                    text = if (msg.deleted) "Message deleted" else msg.content,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (msg.deleted) CoolAsh else MoonWhite
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val tsText = msg.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Now"
                                                Text(tsText, style = MaterialTheme.typography.labelSmall, color = RoseWhite.copy(alpha = 0.75f))
                                                if (isMine && !msg.deleted) {
                                                    Spacer(Modifier.size(4.dp))
                                                    Icon(
                                                        Icons.Rounded.DoneAll,
                                                        contentDescription = if (readByOther) "Read" else "Delivered",
                                                        tint = if (readByOther) MoonWhite else RoseWhite.copy(alpha = 0.55f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.size(4.dp))
                                                    Icon(
                                                        Icons.Rounded.DeleteOutline,
                                                        contentDescription = "Delete message",
                                                        tint = RoseWhite.copy(alpha = 0.75f),
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clickable { viewModel.deleteMessage(conv.id, msg.id) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Composer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SmokedOnyx)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.testTag("chat_attach_image_button")
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Attach photo", tint = ElectricScarlet)
                        }
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it.take(2000) },
                            placeholder = { Text("Message...") },
                            maxLines = 3,
                            modifier = Modifier.weight(1f).testTag("chat_message_input")
                        )
                        IconButton(
                            onClick = {
                                val text = messageInput
                                if (text.isNotBlank()) {
                                    messageInput = ""
                                    viewModel.sendMessage(conv, me, text)
                                }
                            },
                            modifier = Modifier.testTag("chat_send_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send message", tint = XCrimson)
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog && chatState is UiState.Success) {
        val conv = chatState.data.conversation
        val peerUid = conv?.memberUids?.firstOrNull { it != me.userId }.orEmpty()
        ReportAbuseDialog(
            targetLabel = "Conversation",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, desc ->
                showReportDialog = false
                sessionViewModel.submitReport(peerUid, "message", conv?.id.orEmpty(), reason, desc)
            }
        )
    }
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Battle
import com.example.data.model.BattleGiftType
import com.example.data.model.PublicUser
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.LoadingStateView
import com.example.ui.components.StatusPill
import com.example.ui.components.XChatCard
import com.example.ui.state.ActiveBattleBundle
import com.example.ui.state.BattlesChatsViewModel
import com.example.ui.theme.AmberCrown
import com.example.ui.theme.CoolAsh
import com.example.ui.theme.DeepVelvetRed
import com.example.ui.theme.ElectricScarlet
import com.example.ui.theme.ElevatedOnyx
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoseWhite
import com.example.ui.theme.SmokedOnyx
import com.example.ui.theme.XCrimson
import kotlinx.coroutines.delay

@Composable
fun BattlesScreen(
    me: PublicUser,
    battlesState: UiState<List<Battle>>,
    activeBattleState: UiState<ActiveBattleBundle>,
    selectedBattleId: String?,
    viewModel: BattlesChatsViewModel
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (selectedBattleId != null) {
        BattleArenaScreen(
            me = me,
            battleState = activeBattleState,
            viewModel = viewModel,
            onBack = { viewModel.selectBattle(null) }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Live Battles", style = MaterialTheme.typography.headlineLarge, color = MoonWhite)
                    Text(
                        stringResource(R.string.battle_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoolAsh
                    )
                }
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_battle_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Host")
                }
            }
        }

        when (battlesState) {
            is UiState.Loading -> item { LoadingStateView("Loading battle rooms...") }
            is UiState.Error -> item { ErrorStateBanner(battlesState.message) }
            is UiState.Success -> {
                val battles = battlesState.data
                if (battles.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.LocalFireDepartment,
                            title = "No active battles yet",
                            subtitle = "Host a real-time interactive battle and invite challengers or spectators to react with virtual gifts.",
                            actionLabel = "Host First Battle",
                            onAction = { showCreateDialog = true }
                        )
                    }
                } else {
                    items(battles, key = { it.id }) { battle ->
                        val statusColor = when (battle.status) {
                            "live" -> XCrimson
                            "waiting" -> DeepVelvetRed
                            else -> ElevatedOnyx
                        }
                        XChatCard(
                            modifier = Modifier
                                .clickable { viewModel.selectBattle(battle.id) }
                                .testTag("battle_card_${battle.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(battle.title, style = MaterialTheme.typography.titleLarge, color = MoonWhite)
                                StatusPill(battle.status.uppercase(), containerColor = statusColor, contentColor = MoonWhite)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Topic: ${battle.topic} • ${battle.durationSeconds}s", style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${battle.challengerName}: ${battle.challengerScore}", style = MaterialTheme.typography.titleMedium, color = ElectricScarlet)
                                Text("VS", style = MaterialTheme.typography.labelLarge, color = CoolAsh)
                                Text(
                                    if (battle.opponentName.isBlank()) "Open Slot" else "${battle.opponentName}: ${battle.opponentScore}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MoonWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateBattleDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, topic, duration ->
                showCreateDialog = false
                viewModel.createBattle(me, title, topic, duration) { battleId ->
                    viewModel.selectBattle(battleId)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BattleArenaScreen(
    me: PublicUser,
    battleState: UiState<ActiveBattleBundle>,
    viewModel: BattlesChatsViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var commentText by remember { mutableStateOf("") }
    var targetChallenger by remember { mutableStateOf(true) }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBlack),
                title = { Text("Battle Arena", color = MoonWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MoonWhite)
                    }
                }
            )
        }
    ) { innerPadding ->
        when (battleState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding)) { LoadingStateView() }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) { ErrorStateBanner(battleState.message) }
            is UiState.Success -> {
                val bundle = battleState.data
                val battle = bundle.battle
                if (battle == null) {
                    Box(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                        ErrorStateBanner("Battle not found.")
                    }
                    return@Scaffold
                }

                var remainingSec by remember(battle.id, battle.endsAt, battle.status) {
                    mutableLongStateOf(
                        battle.endsAt?.toDate()?.time?.let { endMs ->
                            ((endMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                        } ?: battle.durationSeconds
                    )
                }

                LaunchedEffect(battle.id, battle.status, battle.endsAt) {
                    while (battle.status == "live" && remainingSec > 0L) {
                        delay(1000L)
                        val endMs = battle.endsAt?.toDate()?.time
                        if (endMs != null) {
                            remainingSec = ((endMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                        } else {
                            remainingSec = (remainingSec - 1L).coerceAtLeast(0L)
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            StatusPill(stringResource(R.string.battle_disclaimer), containerColor = SmokedOnyx, contentColor = CoolAsh)
                        }

                        // Scoreboard Card
                        item {
                            val totalScore = (battle.challengerScore + battle.opponentScore).coerceAtLeast(1L)
                            val ratio = battle.challengerScore.toFloat() / totalScore.toFloat()
                            XChatCard(borderColor = XCrimson.copy(alpha = 0.6f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(battle.title, style = MaterialTheme.typography.headlineMedium, color = MoonWhite)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = ElectricScarlet, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = if (battle.status == "completed") "FINAL" else "${remainingSec}s",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ElectricScarlet
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(battle.challengerName, style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                                        Text("${battle.challengerScore} pts", style = MaterialTheme.typography.headlineMedium, color = ElectricScarlet)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            battle.opponentName.ifBlank { "Waiting for Challenger" },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MoonWhite
                                        )
                                        Text("${battle.opponentScore} pts", style = MaterialTheme.typography.headlineMedium, color = MoonWhite)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { ratio.coerceIn(0.05f, 0.95f) },
                                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)),
                                    color = XCrimson,
                                    trackColor = ElevatedOnyx
                                )

                                Spacer(Modifier.height(12.dp))
                                when (battle.status) {
                                    "waiting" -> {
                                        if (battle.challengerId != me.userId) {
                                            Button(
                                                onClick = { viewModel.joinBattle(battle, me) },
                                                modifier = Modifier.fillMaxWidth().testTag("join_battle_button"),
                                                colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                                            ) {
                                                Text("Join as Opponent & Start Timer")
                                            }
                                        } else {
                                            Text("Waiting for another user to join as your opponent...", style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                                        }
                                    }
                                    "live" -> {
                                        if (remainingSec == 0L || battle.creatorId == me.userId) {
                                            OutlinedButton(
                                                onClick = { viewModel.completeBattle(battle) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = AmberCrown)
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    if (remainingSec == 0L) "Finalize Battle & Crown Winner" else "End Battle Now",
                                                    color = MoonWhite
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        val winnerLabel = when (battle.winnerId) {
                                            battle.challengerId -> "Winner: ${battle.challengerName}"
                                            battle.opponentId -> "Winner: ${battle.opponentName}"
                                            else -> "Result: Tie Match"
                                        }
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = DeepVelvetRed,
                                            border = BorderStroke(1.dp, AmberCrown)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = AmberCrown)
                                                Spacer(Modifier.width(8.dp))
                                                Text(winnerLabel, style = MaterialTheme.typography.titleMedium, color = RoseWhite)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Virtual Gifts Bar (non-monetary)
                        if (battle.status == "live") {
                            item {
                                XChatCard {
                                    Text("Send Virtual Gift (Non-Monetary)", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = targetChallenger,
                                            onClick = { targetChallenger = true },
                                            label = { Text("Boost ${battle.challengerName}") },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DeepVelvetRed, selectedLabelColor = RoseWhite)
                                        )
                                        FilterChip(
                                            selected = !targetChallenger,
                                            onClick = { targetChallenger = false },
                                            label = { Text("Boost ${battle.opponentName}") },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DeepVelvetRed, selectedLabelColor = RoseWhite)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        BattleGiftType.entries.forEach { gift ->
                                            OutlinedButton(
                                                onClick = { viewModel.sendBattleGift(battle, me, targetChallenger, gift) },
                                                modifier = Modifier.weight(1f).testTag("gift_${gift.key}"),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        when (gift) {
                                                            BattleGiftType.HEART -> Icons.Rounded.Favorite
                                                            BattleGiftType.FIRE -> Icons.Rounded.LocalFireDepartment
                                                            BattleGiftType.ROSE -> Icons.Rounded.Favorite
                                                            BattleGiftType.CROWN -> Icons.Rounded.WorkspacePremium
                                                        },
                                                        contentDescription = gift.label,
                                                        tint = if (gift == BattleGiftType.CROWN) AmberCrown else ElectricScarlet,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text("${gift.label}\n+${gift.points}", style = MaterialTheme.typography.labelSmall, color = MoonWhite)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (bundle.gifts.isNotEmpty()) {
                            item {
                                Text("Recent Gifts", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                            }
                            items(bundle.gifts.take(4), key = { it.id }) { g ->
                                Text(
                                    "${g.senderName} sent ${g.giftType.uppercase()} (+${g.points})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AmberCrown
                                )
                            }
                        }

                        item {
                            Text("Spectator Comments (${bundle.comments.size})", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                        }
                        if (bundle.comments.isEmpty()) {
                            item {
                                Text("Be the first spectator to cheer!", style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                            }
                        } else {
                            items(bundle.comments, key = { it.id }) { c ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = SmokedOnyx,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(c.authorName, style = MaterialTheme.typography.labelLarge, color = ElectricScarlet)
                                        Text(c.content, style = MaterialTheme.typography.bodyMedium, color = MoonWhite)
                                    }
                                }
                            }
                        }
                    }

                    // Comment Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SmokedOnyx)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it.take(300) },
                            placeholder = { Text("React or cheer...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("battle_comment_input")
                        )
                        IconButton(
                            onClick = {
                                val text = commentText
                                if (text.isNotBlank()) {
                                    commentText = ""
                                    viewModel.postBattleComment(battle.id, me, text)
                                }
                            },
                            modifier = Modifier.testTag("battle_send_comment_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send comment", tint = XCrimson)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateBattleDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("Freestyle") }
    var duration by remember { mutableLongStateOf(180L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SmokedOnyx,
        title = { Text("Host Interactive Battle", color = MoonWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Battle Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("battle_title_input")
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it.take(60) },
                    label = { Text("Topic / Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60L, 180L, 300L).forEach { sec ->
                        FilterChip(
                            selected = duration == sec,
                            onClick = { duration = sec },
                            label = { Text("${sec / 60}m") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DeepVelvetRed, selectedLabelColor = RoseWhite)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, topic, duration) },
                enabled = title.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
            ) {
                Text("Open Room")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel", color = MoonWhite) }
        }
    )
}

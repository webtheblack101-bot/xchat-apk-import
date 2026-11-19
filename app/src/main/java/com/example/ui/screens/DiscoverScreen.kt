package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PublicUser
import com.example.data.model.RankedCandidate
import com.example.data.model.UiState
import com.example.domain.recommendation.DiscoveryFilter
import com.example.domain.recommendation.DiscoveryMode
import com.example.domain.recommendation.HybridRecommendationEngine
import com.example.ui.components.AvatarDisplay
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.LoadingStateView
import com.example.ui.components.MediaImageView
import com.example.ui.components.ReportAbuseDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.XChatCard
import com.example.ui.state.AppSessionViewModel
import com.example.ui.state.DiscoverBundle
import com.example.ui.state.DiscoverViewModel
import com.example.ui.state.MatchCelebration
import com.example.ui.state.SessionBundle
import com.example.ui.theme.AmberCrown
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    session: SessionBundle,
    discoverState: UiState<DiscoverBundle>,
    matchCelebration: MatchCelebration?,
    discoverViewModel: DiscoverViewModel,
    sessionViewModel: AppSessionViewModel,
    onOpenConversation: (String) -> Unit,
    onGoToDatingSettings: () -> Unit
) {
    val me = session.publicUser ?: return
    val isAdult = session.privateAccount?.ageVerified == true
    var showFilterDialog by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<RankedCandidate?>(null) }

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
                Column {
                    Text("Discover", style = MaterialTheme.typography.headlineLarge, color = MoonWhite)
                    Text(
                        "Hybrid engine • ${HybridRecommendationEngine.MODEL_VERSION}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoolAsh
                    )
                }
                IconButton(
                    onClick = { showFilterDialog = true },
                    modifier = Modifier.testTag("discover_filter_button")
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Discovery preferences", tint = MoonWhite)
                }
            }
        }

        when (discoverState) {
            is UiState.Loading -> item { LoadingStateView("Ranking compatible profiles...") }
            is UiState.Error -> item { ErrorStateBanner(discoverState.message) }
            is UiState.Success -> {
                val bundle = discoverState.data
                val filter = bundle.filter

                // Mode Switcher
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DiscoveryMode.entries.forEach { mode ->
                            FilterChip(
                                selected = filter.mode == mode,
                                onClick = { discoverViewModel.updateFilter { it.copy(mode = mode) } },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("discover_mode_${mode.key}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = XCrimson,
                                    selectedLabelColor = MoonWhite
                                )
                            )
                        }
                    }
                }

                // Age Gate & Opt-In Gate for Dating Mode
                val datingBlockedByAge = filter.mode == DiscoveryMode.DATING && !isAdult
                val datingNotOptedIn = filter.mode == DiscoveryMode.DATING && isAdult &&
                    (bundle.viewerDatingProfile == null || !bundle.viewerDatingProfile.datingEnabled)

                if (datingBlockedByAge) {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.Favorite,
                            title = "18+ Verification Required",
                            subtitle = "Dating discovery is strictly limited to verified adults (18+). Switch to Friends & Casual mode for social discovery.",
                            actionLabel = "Switch to Friends & Casual",
                            onAction = { discoverViewModel.updateFilter { it.copy(mode = DiscoveryMode.FRIENDSHIP) } }
                        )
                    }
                } else if (datingNotOptedIn) {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.Favorite,
                            title = "Dating Discovery Is Paused",
                            subtitle = "Enable your separate 18+ Dating Profile in Profile settings whenever you want to browse and match.",
                            actionLabel = "Set Up Dating Profile",
                            onAction = onGoToDatingSettings
                        )
                    }
                } else {
                    val topCandidate = bundle.rankedCandidates.firstOrNull()
                    if (topCandidate == null) {
                        item {
                            EmptyStateView(
                                icon = Icons.Rounded.AutoAwesome,
                                title = "You're Caught Up",
                                subtitle = "No more eligible profiles match your current filters. Adjust preferences or check back soon.",
                                actionLabel = "Adjust Filters",
                                onAction = { showFilterDialog = true }
                            )
                        }
                    } else {
                        item(key = topCandidate.profile.userId) {
                            LaunchedEffect(topCandidate.profile.userId) {
                                discoverViewModel.recordImpressionIfNew(topCandidate)
                            }
                            CandidateDiscoveryCard(
                                candidate = topCandidate,
                                onPass = { discoverViewModel.interact(me, topCandidate, "pass") },
                                onLike = { discoverViewModel.interact(me, topCandidate, "like") },
                                onSuperlike = { discoverViewModel.interact(me, topCandidate, "superlike") },
                                onMoreLikeThis = { discoverViewModel.applyFeedback(bundle.preferences, topCandidate, true) },
                                onLessLikeThis = { discoverViewModel.applyFeedback(bundle.preferences, topCandidate, false) },
                                onBlock = { sessionViewModel.blockUser(topCandidate.profile.userId, topCandidate.profile.displayName) },
                                onReport = { reportTarget = topCandidate }
                            )
                        }
                    }
                }

                // Mutual Matches Section
                item {
                    Text(
                        "Mutual Matches (${bundle.activeMatches.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MoonWhite
                    )
                }

                if (bundle.activeMatches.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.Favorite,
                            title = "No mutual matches yet",
                            subtitle = "When two people like each other, an exclusive match and direct conversation are created."
                        )
                    }
                } else {
                    items(bundle.activeMatches, key = { it.id }) { match ->
                        val partnerUid = if (match.userAId == me.userId) match.userBId else match.userAId
                        val partner = bundle.publicUsersById[partnerUid]
                        val partnerName = partner?.displayName ?: "X Chat Match"
                        XChatCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AvatarDisplay(name = partnerName, imageData = partner?.avatarData, size = 48.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(partnerName, style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                                    Text(
                                        partner?.let { "@${it.username}" } ?: "Matched on X Chat",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CoolAsh
                                    )
                                }
                                Button(
                                    onClick = { onOpenConversation(match.conversationId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                                ) {
                                    Text("Chat")
                                }
                                OutlinedButton(
                                    onClick = { discoverViewModel.unmatch(match.id) }
                                ) {
                                    Text("Unmatch", color = CoolAsh)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog && discoverState is UiState.Success) {
        DiscoveryFilterDialog(
            currentFilter = discoverState.data.filter,
            personalizedEnabled = discoverState.data.preferences?.personalizedEnabled ?: true,
            onDismiss = { showFilterDialog = false },
            onApply = { updated ->
                discoverViewModel.updateFilter { updated }
                showFilterDialog = false
            },
            onTogglePersonalization = { enabled ->
                discoverViewModel.resetPersonalization(enabled)
            }
        )
    }

    if (matchCelebration != null) {
        AlertDialog(
            onDismissRequest = { discoverViewModel.dismissMatchCelebration() },
            containerColor = SmokedOnyx,
            icon = { Icon(Icons.Rounded.Favorite, contentDescription = null, tint = ElectricScarlet, modifier = Modifier.size(40.dp)) },
            title = { Text("It's a Match!", color = MoonWhite) },
            text = {
                Text(
                    "You and ${matchCelebration.targetName} liked each other. A private chat is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolAsh
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val convId = matchCelebration.conversationId
                        discoverViewModel.dismissMatchCelebration()
                        onOpenConversation(convId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
                ) {
                    Text("Send Message")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { discoverViewModel.dismissMatchCelebration() }) {
                    Text("Keep Browsing", color = MoonWhite)
                }
            }
        )
    }

    reportTarget?.let { target ->
        ReportAbuseDialog(
            targetLabel = target.profile.displayName,
            onDismiss = { reportTarget = null },
            onSubmit = { reason, desc ->
                sessionViewModel.submitReport(target.profile.userId, "user", target.profile.userId, reason, desc)
                reportTarget = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateDiscoveryCard(
    candidate: RankedCandidate,
    onPass: () -> Unit,
    onLike: () -> Unit,
    onSuperlike: () -> Unit,
    onMoreLikeThis: () -> Unit,
    onLessLikeThis: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    val p = candidate.profile
    val photo = p.photos.firstOrNull() ?: candidate.publicUser?.avatarData

    XChatCard(borderColor = XCrimson.copy(alpha = 0.45f)) {
        // Hero Photo / Avatar Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    Brush.linearGradient(listOf(DeepVelvetRed, ElevatedOnyx, ObsidianBlack))
                )
        ) {
            if (!photo.isNullOrBlank()) {
                MediaImageView(
                    imageData = photo,
                    contentDescription = "${p.displayName} photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AvatarDisplay(name = p.displayName, imageData = null, size = 96.dp)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(ObsidianBlack.copy(alpha = 0.1f), ObsidianBlack.copy(alpha = 0.88f))
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill("${candidate.matchPercent}% Fit", containerColor = XCrimson, contentColor = MoonWhite)
                StatusPill(p.relationshipIntent.replaceFirstChar { it.uppercase() })
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                IconButton(onClick = onBlock) {
                    Icon(Icons.Rounded.Block, contentDescription = "Block user", tint = MoonWhite)
                }
                IconButton(onClick = onReport) {
                    Icon(Icons.Rounded.Flag, contentDescription = "Report user", tint = MoonWhite)
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text("${p.displayName}, ${p.age}", style = MaterialTheme.typography.headlineMedium, color = MoonWhite)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = ElectricScarlet, modifier = Modifier.size(16.dp))
                    Text(
                        "${p.city} • ${p.conversationStyle.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoseWhite
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (p.datingBio.isNotBlank()) {
            Text(p.datingBio, style = MaterialTheme.typography.bodyMedium, color = MoonWhite)
            Spacer(Modifier.height(8.dp))
        }

        if (candidate.breakdown.explainabilityTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                candidate.breakdown.explainabilityTags.forEach { reason ->
                    StatusPill(reason, containerColor = DeepVelvetRed, contentColor = RoseWhite)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (p.interests.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.interests.take(6).forEach { tag ->
                    StatusPill(tag, containerColor = ElevatedOnyx, contentColor = CoolAsh)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Recommendation Feedback Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onLessLikeThis,
                modifier = Modifier.weight(1f).testTag("discover_less_like_this")
            ) {
                Icon(Icons.Rounded.ThumbDown, contentDescription = null, tint = CoolAsh, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Fewer like this", color = CoolAsh, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = onMoreLikeThis,
                modifier = Modifier.weight(1f).testTag("discover_more_like_this")
            ) {
                Icon(Icons.Rounded.ThumbUp, contentDescription = null, tint = ElectricScarlet, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("More like this", color = MoonWhite, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(14.dp))
        // Primary Swipe Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onPass,
                shape = CircleShape,
                color = ElevatedOnyx,
                border = BorderStroke(1.dp, HairlineSlate),
                modifier = Modifier.size(60.dp).testTag("discover_pass_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = "Pass", tint = CoolAsh, modifier = Modifier.size(28.dp))
                }
            }
            Surface(
                onClick = onSuperlike,
                shape = CircleShape,
                color = DeepVelvetRed,
                border = BorderStroke(1.dp, AmberCrown),
                modifier = Modifier.size(54.dp).testTag("discover_superlike_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Star, contentDescription = "Superlike", tint = AmberCrown, modifier = Modifier.size(24.dp))
                }
            }
            Surface(
                onClick = onLike,
                shape = CircleShape,
                color = XCrimson,
                modifier = Modifier.size(64.dp).testTag("discover_like_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Favorite, contentDescription = "Like", tint = MoonWhite, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoveryFilterDialog(
    currentFilter: DiscoveryFilter,
    personalizedEnabled: Boolean,
    onDismiss: () -> Unit,
    onApply: (DiscoveryFilter) -> Unit,
    onTogglePersonalization: (Boolean) -> Unit
) {
    var minAge by remember { mutableStateOf(currentFilter.minAge.toString()) }
    var maxAge by remember { mutableStateOf(currentFilter.maxAge.toString()) }
    var city by remember { mutableStateOf(currentFilter.cityFilter) }
    var intent by remember { mutableStateOf(currentFilter.selectedIntent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SmokedOnyx,
        title = { Text("Discovery Preferences", color = MoonWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minAge,
                        onValueChange = { minAge = it.filter(Char::isDigit).take(2) },
                        label = { Text("Min Age") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxAge,
                        onValueChange = { maxAge = it.filter(Char::isDigit).take(3) },
                        label = { Text("Max Age") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it.take(60) },
                    label = { Text("City filter (blank = all)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Intent Filter", style = MaterialTheme.typography.labelLarge, color = MoonWhite)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all", "dating", "serious", "casual", "friendship").forEach { item ->
                        FilterChip(
                            selected = intent == item,
                            onClick = { intent = item },
                            label = { Text(item.replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DeepVelvetRed, selectedLabelColor = RoseWhite)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Personalized Hybrid Ranking", color = MoonWhite, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = personalizedEnabled,
                        onCheckedChange = onTogglePersonalization,
                        colors = SwitchDefaults.colors(checkedThumbColor = MoonWhite, checkedTrackColor = XCrimson)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val min = (minAge.toIntOrNull() ?: 18).coerceIn(18, 99)
                    val max = (maxAge.toIntOrNull() ?: 60).coerceIn(min, 100)
                    onApply(currentFilter.copy(minAge = min, maxAge = max, cityFilter = city, selectedIntent = intent))
                },
                colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel", color = MoonWhite) }
        }
    )
}

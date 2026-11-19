package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.AuthRepository
import com.example.data.repository.GoogleSignInResult
import com.example.ui.components.AvatarDisplay
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.StatusPill
import com.example.ui.components.XChatBrandWordmark
import com.example.ui.components.XChatCard
import com.example.ui.state.AppSessionViewModel
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CoolAsh
import com.example.ui.theme.DeepVelvetRed
import com.example.ui.theme.ElectricScarlet
import com.example.ui.theme.HairlineSlate
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoseWhite
import com.example.ui.theme.SmokedOnyx
import com.example.ui.theme.XCrimson
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_xchat_hero_1795071924792),
                    contentDescription = "X Chat futuristic crimson backdrop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ObsidianBlack.copy(alpha = 0.25f),
                                    ObsidianBlack.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    XChatBrandWordmark()
                    Text(
                        text = stringResource(R.string.tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoseWhite
                    )
                }
            }

            Text(
                text = "One unified network for real-time messaging, communities, live battles, and mindful discovery.",
                style = MaterialTheme.typography.headlineMedium,
                color = MoonWhite
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeaturePillarRow(Icons.Rounded.Forum, "Crimson Real-Time Chat", "1:1 & group messaging with read receipts and photo sharing.")
                FeaturePillarRow(Icons.Rounded.Groups, "Public & Private Groups", "Role-based communities with revocable invite tokens.")
                FeaturePillarRow(Icons.Rounded.LocalFireDepartment, "Live Interactive Battles", "Real-time scoreboards, spectator chats, and virtual gifts.")
                FeaturePillarRow(Icons.Rounded.Favorite, "Optional 18+ Discovery", "Separate dating profile with city-only privacy & hybrid ranking.")
            }

            if (errorMessage != null) {
                ErrorStateBanner(
                    message = errorMessage!!,
                    onDismiss = { errorMessage = null }
                )
            }

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        when (val res = authRepository.signInInteractive(context)) {
                            is GoogleSignInResult.Success -> {
                                isLoading = false
                                onAuthSuccess()
                            }
                            is GoogleSignInResult.Cancelled -> {
                                isLoading = false
                            }
                            is GoogleSignInResult.Error -> {
                                isLoading = false
                                errorMessage = res.message
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = XCrimson, contentColor = MoonWhite)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MoonWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sign_in_with_google),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = CharcoalSurface,
                border = BorderStroke(1.dp, HairlineSlate)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = CoolAsh, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Secured via Firebase Auth & Zero-Trust Rules. Additional providers (Email/Password or Phone SMS) can be enabled in your Firebase Console.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoolAsh
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePillarRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = DeepVelvetRed
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = ElectricScarlet, modifier = Modifier.size(20.dp))
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MoonWhite)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CoolAsh)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingWizardScreen(
    defaultDisplayName: String,
    sessionViewModel: AppSessionViewModel,
    isBusy: Boolean,
    feedbackMessage: String?,
    onDismissFeedback: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf(defaultDisplayName.ifBlank { "" }) }
    var bio by remember { mutableStateOf("") }
    var avatarData by remember { mutableStateOf<String?>(null) }

    val availableInterests = listOf(
        "Music", "Tech", "Gaming", "Fitness", "Art", "Coffee",
        "Travel", "Cinema", "Photography", "Startups", "Nightlife", "Books"
    )
    val selectedInterests = remember { mutableStateListOf("Music", "Tech") }

    val availableLanguages = listOf("English", "Spanish", "French", "German", "Japanese", "Arabic", "Hindi")
    val selectedLanguages = remember { mutableStateListOf("English") }

    var birthYearInput by remember { mutableStateOf("1999") }
    var city by remember { mutableStateOf("") }
    var enableDating by remember { mutableStateOf(false) }
    var datingBio by remember { mutableStateOf("") }
    var selectedIntent by remember { mutableStateOf("dating") }
    var showOnlineStatus by remember { mutableStateOf(true) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val compressed = sessionViewModel.compressImage(context, uri)
                if (compressed != null) avatarData = compressed
            }
        }
    }

    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val parsedBirthYear = birthYearInput.toIntOrNull() ?: 2000
    val isAdult = (currentYear - parsedBirthYear) >= 18

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            XChatBrandWordmark()
            StatusPill("Step ${step + 1} of 3")
        }

        LinearProgressIndicator(
            progress = { (step + 1) / 3f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
            color = XCrimson,
            trackColor = SmokedOnyx
        )

        if (feedbackMessage != null) {
            ErrorStateBanner(message = feedbackMessage, onDismiss = onDismissFeedback)
        }

        when (step) {
            0 -> {
                Text("Create Your Identity", style = MaterialTheme.typography.headlineLarge, color = MoonWhite)
                Text(
                    "Choose how friends and communities see you. Private details stay isolated.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolAsh
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AvatarDisplay(name = displayName.ifBlank { "X" }, imageData = avatarData, size = 72.dp)
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("onboarding_pick_photo_button")
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = XCrimson)
                        Spacer(Modifier.size(8.dp))
                        Text("Pick Profile Photo", color = MoonWhite)
                    }
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(24) },
                    label = { Text("Username (lowercase, 3-24 chars)") },
                    prefix = { Text("@", color = XCrimson) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_username_input")
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(40) },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_display_name_input")
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(300) },
                    label = { Text("Short Social Bio") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_bio_input")
                )
            }

            1 -> {
                Text("Interests & Languages", style = MaterialTheme.typography.headlineLarge, color = MoonWhite)
                Text(
                    "Signals power community suggestions and hybrid recommendations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolAsh
                )
                XChatCard {
                    Text("Pick Interests (up to 15)", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableInterests.forEach { tag ->
                            val selected = tag in selectedInterests
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) selectedInterests.remove(tag)
                                    else if (selectedInterests.size < 15) selectedInterests.add(tag)
                                },
                                label = { Text(tag) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DeepVelvetRed,
                                    selectedLabelColor = RoseWhite
                                )
                            )
                        }
                    }
                }
                XChatCard {
                    Text("Languages You Speak", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableLanguages.forEach { lang ->
                            val selected = lang in selectedLanguages
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected && selectedLanguages.size > 1) selectedLanguages.remove(lang)
                                    else if (!selected && selectedLanguages.size < 10) selectedLanguages.add(lang)
                                },
                                label = { Text(lang) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DeepVelvetRed,
                                    selectedLabelColor = RoseWhite
                                )
                            )
                        }
                    }
                }
            }

            else -> {
                Text("Privacy & Discovery", style = MaterialTheme.typography.headlineLarge, color = MoonWhite)
                Text(
                    stringResource(R.string.age_gate_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolAsh
                )
                XChatCard {
                    OutlinedTextField(
                        value = birthYearInput,
                        onValueChange = { birthYearInput = it.filter(Char::isDigit).take(4) },
                        label = { Text("Birth Year (e.g. 1998)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_birth_year_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it.take(80) },
                        label = { Text("City / Approximate Region (never exact GPS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_city_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Show Online Status", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                            Text("Let connections know when you are active", style = MaterialTheme.typography.bodySmall, color = CoolAsh)
                        }
                        Switch(
                            checked = showOnlineStatus,
                            onCheckedChange = { showOnlineStatus = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MoonWhite, checkedTrackColor = XCrimson)
                        )
                    }
                }

                XChatCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable 18+ Dating Discovery", style = MaterialTheme.typography.titleMedium, color = MoonWhite)
                            Text(
                                if (isAdult) "Optional — you can pause this anytime" else "Unavailable under 18",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAdult) CoolAsh else ElectricScarlet
                            )
                        }
                        Switch(
                            checked = enableDating && isAdult,
                            onCheckedChange = { if (isAdult) enableDating = it },
                            enabled = isAdult,
                            modifier = Modifier.testTag("onboarding_dating_switch"),
                            colors = SwitchDefaults.colors(checkedThumbColor = MoonWhite, checkedTrackColor = XCrimson)
                        )
                    }

                    if (enableDating && isAdult) {
                        Spacer(Modifier.height(10.dp))
                        Text("Relationship Intent", style = MaterialTheme.typography.labelLarge, color = MoonWhite)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("dating", "serious", "casual", "friendship").forEach { intent ->
                                FilterChip(
                                    selected = selectedIntent == intent,
                                    onClick = { selectedIntent = intent },
                                    label = { Text(intent.replaceFirstChar { it.uppercase() }) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = DeepVelvetRed,
                                        selectedLabelColor = RoseWhite
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = datingBio,
                            onValueChange = { datingBio = it.take(400) },
                            label = { Text("Dating Bio (separate from social bio)") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { step-- },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Back", color = MoonWhite)
                }
            }
            Button(
                onClick = {
                    if (step < 2) {
                        if (step == 0 && (username.length < 3 || displayName.isBlank())) {
                            sessionViewModel.setMessage("Enter a valid username (3+ chars) and display name.")
                        } else {
                            step++
                        }
                    } else {
                        sessionViewModel.completeOnboarding(
                            username = username,
                            displayName = displayName,
                            bio = bio,
                            avatarData = avatarData,
                            interests = selectedInterests.toList(),
                            languages = selectedLanguages.toList(),
                            birthYear = parsedBirthYear.coerceIn(1920, 2015),
                            city = city,
                            enableDating = enableDating && isAdult,
                            datingBio = datingBio,
                            intent = selectedIntent,
                            showOnlineStatus = showOnlineStatus
                        )
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.weight(1f).height(50.dp).testTag("onboarding_next_button"),
                colors = ButtonDefaults.buttonColors(containerColor = XCrimson, contentColor = MoonWhite)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MoonWhite, strokeWidth = 2.dp)
                } else {
                    if (step == 2) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Finish Setup")
                    } else {
                        Text("Continue")
                    }
                }
            }
        }
    }
}

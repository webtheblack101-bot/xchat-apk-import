package com.example.ui.state

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.media.ImageCompressor
import com.example.data.model.BlockRecord
import com.example.data.model.DatingProfile
import com.example.data.model.FriendConnection
import com.example.data.model.NotificationItem
import com.example.data.model.PrivateAccount
import com.example.data.model.PublicUser
import com.example.data.model.UiState
import com.example.data.model.userFriendlyMessage
import com.example.data.repository.AuthRepository
import com.example.data.repository.SafetyNotificationRepository
import com.example.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun <T> Flow<T>.asUiState(tag: String): Flow<UiState<T>> =
    map<T, UiState<T>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(tag, "Stream failure", rootError)
            emit(UiState.Error(rootError.userFriendlyMessage()))
        }

data class SessionBundle(
    val publicUser: PublicUser?,
    val privateAccount: PrivateAccount?,
    val datingProfile: DatingProfile?,
    val friends: List<FriendConnection>,
    val blockedByMe: List<BlockRecord>,
    val blockedMe: List<BlockRecord>,
    val notifications: List<NotificationItem>
) {
    val allBlockedUserIds: Set<String>
        get() = (blockedByMe.map { it.blockedId } + blockedMe.map { it.blockerId }).toSet()

    val unreadNotificationCount: Int
        get() = notifications.count { !it.readFlag }

    val needsOnboarding: Boolean
        get() = publicUser == null || privateAccount == null
}

class AppSessionViewModel(
    private val currentUserId: String,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val safetyRepository: SafetyNotificationRepository
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            _isAdmin.value = safetyRepository.checkIsAdmin(currentUserId)
        }
    }

    val sessionState: StateFlow<UiState<SessionBundle>> = combine(
        userRepository.observePublicUser(currentUserId),
        userRepository.observePrivateAccount(currentUserId),
        userRepository.observeDatingProfile(currentUserId),
        userRepository.observeFriends(currentUserId),
        userRepository.observeBlockedByMe(currentUserId),
        userRepository.observeBlockedMe(currentUserId),
        safetyRepository.observeNotifications(currentUserId)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SessionBundle(
            publicUser = values[0] as PublicUser?,
            privateAccount = values[1] as PrivateAccount?,
            datingProfile = values[2] as DatingProfile?,
            friends = values[3] as List<FriendConnection>,
            blockedByMe = values[4] as List<BlockRecord>,
            blockedMe = values[5] as List<BlockRecord>,
            notifications = values[6] as List<NotificationItem>
        )
    }
        .asUiState("AppSessionVM")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun setMessage(msg: String) {
        _actionMessage.value = msg
    }

    fun completeOnboarding(
        username: String,
        displayName: String,
        bio: String,
        avatarData: String?,
        interests: List<String>,
        languages: List<String>,
        birthYear: Int,
        city: String,
        enableDating: Boolean,
        datingBio: String,
        intent: String,
        showOnlineStatus: Boolean,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val computedAge = (currentYear - birthYear).toLong()
            val available = userRepository.isUsernameAvailable(username, currentUserId).getOrElse { true }
            if (!available) {
                _actionMessage.value = "Username '@${username.lowercase().trim()}' is taken or invalid."
                _isBusy.value = false
                return@launch
            }
            if (enableDating && computedAge < 18L) {
                _actionMessage.value = "Dating discovery requires 18+ age verification."
                _isBusy.value = false
                return@launch
            }

            val email = authRepository.currentUser?.email.orEmpty()
            val pub = PublicUser(
                userId = currentUserId,
                username = username.lowercase().trim(),
                displayName = displayName.trim().ifBlank { "X Chat Member" },
                bio = bio.trim(),
                avatarData = avatarData,
                interests = interests,
                languages = languages.ifEmpty { listOf("English") },
                onlineVisible = showOnlineStatus
            )
            val priv = PrivateAccount(
                userId = currentUserId,
                email = email,
                birthYear = birthYear.toLong(),
                ageVerified = computedAge >= 18L,
                showOnlineStatus = showOnlineStatus
            )
            val dating = if (computedAge >= 18L) {
                DatingProfile(
                    userId = currentUserId,
                    displayName = pub.displayName,
                    datingEnabled = enableDating,
                    datingBio = datingBio.ifBlank { bio },
                    age = computedAge.coerceIn(18L, 100L),
                    city = city.ifBlank { "Global" },
                    relationshipIntent = intent,
                    interests = interests,
                    languages = languages.ifEmpty { listOf("English") },
                    photos = listOfNotNull(avatarData)
                )
            } else null

            val result = userRepository.completeOnboarding(pub, priv, dating)
            _isBusy.value = false
            result.onSuccess {
                _actionMessage.value = "Welcome to X Chat, ${pub.displayName}!"
                onComplete()
            }.onFailure {
                _actionMessage.value = it.userFriendlyMessage("Failed to save profile.")
            }
        }
    }

    fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarData: String?,
        interests: List<String>,
        languages: List<String>,
        onlineVisible: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = userRepository.updatePublicProfile(
                username, displayName, bio, avatarData, interests, languages, onlineVisible
            )
            _isBusy.value = false
            _actionMessage.value = res.fold(
                onSuccess = { "Social profile updated." },
                onFailure = { it.userFriendlyMessage("Could not update profile.") }
            )
        }
    }

    fun saveDatingProfile(profile: DatingProfile, existsAlready: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = userRepository.saveDatingProfile(profile, existsAlready)
            _isBusy.value = false
            _actionMessage.value = res.fold(
                onSuccess = {
                    if (profile.datingEnabled) "Dating profile active." else "Dating discovery paused."
                },
                onFailure = { it.userFriendlyMessage("Could not save dating profile.") }
            )
        }
    }

    fun updatePrivacyAndNotifications(
        showOnlineStatus: Boolean,
        allowFriendRequests: Boolean,
        notifyMessages: Boolean,
        notifyMatches: Boolean,
        notifyGroups: Boolean,
        notifyBattles: Boolean
    ) {
        viewModelScope.launch {
            val res = userRepository.updatePrivacyAndNotifications(
                showOnlineStatus, allowFriendRequests, notifyMessages, notifyMatches, notifyGroups, notifyBattles
            )
            _actionMessage.value = res.fold(
                onSuccess = { "Preferences saved." },
                onFailure = { it.userFriendlyMessage("Could not update preferences.") }
            )
        }
    }

    fun addFriend(me: PublicUser, target: PublicUser) {
        viewModelScope.launch {
            val res = userRepository.addFriend(me, target)
            _actionMessage.value = res.fold(
                onSuccess = { "Connected with ${target.displayName}." },
                onFailure = { it.userFriendlyMessage("Could not add connection.") }
            )
        }
    }

    fun blockUser(targetUid: String, targetName: String) {
        viewModelScope.launch {
            val res = userRepository.blockUser(targetUid)
            _actionMessage.value = res.fold(
                onSuccess = { "$targetName blocked." },
                onFailure = { it.userFriendlyMessage("Failed to block user.") }
            )
        }
    }

    fun unblockUser(targetUid: String) {
        viewModelScope.launch {
            val res = userRepository.unblockUser(targetUid)
            _actionMessage.value = res.fold(
                onSuccess = { "User unblocked." },
                onFailure = { it.userFriendlyMessage("Failed to unblock user.") }
            )
        }
    }

    fun submitReport(
        reportedUserId: String,
        targetType: String,
        targetId: String,
        reason: String,
        description: String
    ) {
        viewModelScope.launch {
            val res = safetyRepository.submitReport(reportedUserId, targetType, targetId, reason, description)
            _actionMessage.value = res.fold(
                onSuccess = { "Report submitted to safety moderators." },
                onFailure = { it.userFriendlyMessage("Could not submit report.") }
            )
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch {
            safetyRepository.markNotificationRead(notifId)
        }
    }

    suspend fun compressImage(context: Context, uri: Uri): String? {
        return ImageCompressor.compressUriToDataUri(context, uri).getOrElse {
            _actionMessage.value = "Could not process selected photo."
            null
        }
    }

    fun deleteAccountAndSignOut(context: Context) {
        viewModelScope.launch {
            _isBusy.value = true
            userRepository.deleteAccountData()
            authRepository.signOut(context)
            _isBusy.value = false
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authRepository.signOut(context)
        }
    }
}

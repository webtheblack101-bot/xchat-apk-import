package com.example.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Conversation
import com.example.data.model.Group
import com.example.data.model.GroupInvite
import com.example.data.model.GroupMember
import com.example.data.model.GroupMembershipIndex
import com.example.data.model.PublicUser
import com.example.data.model.UiState
import com.example.data.model.userFriendlyMessage
import com.example.data.repository.GroupRepository
import com.example.data.repository.MessagingRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeFeedData(
    val publicGroups: List<Group>,
    val myMemberships: List<GroupMembershipIndex>,
    val communityUsers: List<PublicUser>,
    val conversations: List<Conversation>
)

data class GroupDetailBundle(
    val group: Group?,
    val members: List<GroupMember>,
    val myInvites: List<GroupInvite>
)

class HomeGroupsViewModel(
    private val currentUserId: String,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val messagingRepository: MessagingRepository
) : ViewModel() {

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    val homeFeedState: StateFlow<UiState<HomeFeedData>> = combine(
        groupRepository.observePublicGroups(),
        groupRepository.observeMyGroupMemberships(currentUserId),
        userRepository.observeActiveUsers(50L),
        messagingRepository.observeConversations(currentUserId)
    ) { publicGroups, memberships, users, convs ->
        HomeFeedData(
            publicGroups = publicGroups,
            myMemberships = memberships,
            communityUsers = users.filter { it.userId != currentUserId },
            conversations = convs
        )
    }
        .asUiState("HomeGroupsVM")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedGroupState: StateFlow<UiState<GroupDetailBundle>> = _selectedGroupId
        .flatMapLatest { groupId ->
            if (groupId.isNullOrBlank()) {
                flowOf(GroupDetailBundle(null, emptyList(), emptyList()))
            } else {
                combine(
                    groupRepository.observeGroup(groupId),
                    groupRepository.observeGroupMembers(groupId),
                    groupRepository.observeMyInvites(currentUserId)
                ) { group, members, invites ->
                    GroupDetailBundle(
                        group = group,
                        members = members,
                        myInvites = invites.filter { it.groupId == groupId }
                    )
                }
            }
        }
        .asUiState("GroupDetailVM")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun selectGroup(groupId: String) {
        _selectedGroupId.value = groupId
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun createGroup(
        creator: PublicUser,
        name: String,
        description: String,
        visibility: String,
        imageData: String?,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val res = groupRepository.createGroup(creator, name, description, visibility, imageData)
            res.onSuccess { groupId ->
                _feedback.value = "Group '$name' created."
                onCreated(groupId)
            }.onFailure {
                _feedback.value = it.userFriendlyMessage("Failed to create group.")
            }
        }
    }

    fun joinPublicGroup(group: Group, user: PublicUser) {
        viewModelScope.launch {
            val res = groupRepository.joinPublicGroup(group, user)
            _feedback.value = res.fold(
                onSuccess = { "Joined ${group.name}." },
                onFailure = { it.userFriendlyMessage("Could not join group.") }
            )
        }
    }

    fun requestPrivateAccess(groupId: String, user: PublicUser) {
        viewModelScope.launch {
            val res = groupRepository.requestPrivateGroupAccess(groupId, user)
            _feedback.value = res.fold(
                onSuccess = { "Access request sent to group admins." },
                onFailure = { it.userFriendlyMessage("Could not request access.") }
            )
        }
    }

    fun approveMember(group: Group, memberUid: String) {
        viewModelScope.launch {
            val res = groupRepository.approveMember(group, memberUid)
            _feedback.value = res.fold(
                onSuccess = { "Member approved." },
                onFailure = { it.userFriendlyMessage("Approval failed.") }
            )
        }
    }

    fun updateRole(groupId: String, memberUid: String, role: String) {
        viewModelScope.launch {
            val res = groupRepository.updateMemberRole(groupId, memberUid, role)
            _feedback.value = res.fold(
                onSuccess = { "Updated role to $role." },
                onFailure = { it.userFriendlyMessage("Role update failed.") }
            )
        }
    }

    fun removeOrLeaveGroup(groupId: String, targetUid: String, onLeft: () -> Unit = {}) {
        viewModelScope.launch {
            val res = groupRepository.removeOrLeaveGroup(groupId, targetUid)
            res.onSuccess {
                _feedback.value = if (targetUid == currentUserId) "Left group." else "Member removed."
                if (targetUid == currentUserId) onLeft()
            }.onFailure {
                _feedback.value = it.userFriendlyMessage("Could not update membership.")
            }
        }
    }

    fun createInvite(groupId: String) {
        viewModelScope.launch {
            val res = groupRepository.createInvite(groupId)
            _feedback.value = res.fold(
                onSuccess = { "Invite code generated: ${it.inviteCode}" },
                onFailure = { it.userFriendlyMessage("Could not create invite.") }
            )
        }
    }

    fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            val res = groupRepository.revokeInvite(inviteId)
            _feedback.value = res.fold(
                onSuccess = { "Invite revoked." },
                onFailure = { it.userFriendlyMessage("Failed to revoke invite.") }
            )
        }
    }

    fun redeemInvite(code: String, user: PublicUser, onJoined: (String) -> Unit) {
        viewModelScope.launch {
            val res = groupRepository.redeemInvite(code, user)
            res.onSuccess { groupId ->
                _feedback.value = "Invite redeemed!"
                onJoined(groupId)
            }.onFailure {
                _feedback.value = it.userFriendlyMessage("Invalid or revoked invite code.")
            }
        }
    }

    fun startDirectChat(me: PublicUser, other: PublicUser, onOpenConversation: (String) -> Unit) {
        viewModelScope.launch {
            val res = messagingRepository.getOrCreateDirectConversation(me, other)
            res.onSuccess { convId ->
                onOpenConversation(convId)
            }.onFailure {
                _feedback.value = it.userFriendlyMessage("Could not open conversation.")
            }
        }
    }
}

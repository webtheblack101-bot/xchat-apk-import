package com.example.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Battle
import com.example.data.model.BattleComment
import com.example.data.model.BattleGift
import com.example.data.model.BattleGiftType
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.PublicUser
import com.example.data.model.UiState
import com.example.data.model.userFriendlyMessage
import com.example.data.repository.BattleRepository
import com.example.data.repository.MessagingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveBattleBundle(
    val battle: Battle?,
    val comments: List<BattleComment>,
    val gifts: List<BattleGift>
)

data class ActiveChatBundle(
    val conversation: Conversation?,
    val messages: List<ChatMessage>
)

class BattlesChatsViewModel(
    private val currentUserId: String,
    private val battleRepository: BattleRepository,
    private val messagingRepository: MessagingRepository
) : ViewModel() {

    private val _selectedBattleId = MutableStateFlow<String?>(null)
    val selectedBattleId: StateFlow<String?> = _selectedBattleId.asStateFlow()

    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    val battlesState: StateFlow<UiState<List<Battle>>> = battleRepository.observeBattles()
        .asUiState("BattlesVM")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeBattleState: StateFlow<UiState<ActiveBattleBundle>> = _selectedBattleId
        .flatMapLatest { battleId ->
            if (battleId.isNullOrBlank()) {
                flowOf(ActiveBattleBundle(null, emptyList(), emptyList()))
            } else {
                combine(
                    battleRepository.observeBattle(battleId),
                    battleRepository.observeComments(battleId),
                    battleRepository.observeGifts(battleId)
                ) { battle, comments, gifts ->
                    ActiveBattleBundle(battle, comments, gifts)
                }
            }
        }
        .asUiState("ActiveBattleVM")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        messagingRepository.observeConversations(currentUserId)
            .asUiState("ChatsListVM")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatState: StateFlow<UiState<ActiveChatBundle>> = _selectedConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) {
                flowOf(ActiveChatBundle(null, emptyList()))
            } else {
                combine(
                    messagingRepository.observeConversation(convId),
                    messagingRepository.observeMessages(convId, currentUserId)
                ) { conv, msgs ->
                    ActiveChatBundle(conv, msgs)
                }
            }
        }
        .asUiState("ActiveChatVM")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun clearFeedback() {
        _feedback.value = null
    }

    fun selectBattle(battleId: String?) {
        _selectedBattleId.value = battleId
    }

    fun selectConversation(convId: String?) {
        _selectedConversationId.value = convId
    }

    fun createBattle(creator: PublicUser, title: String, topic: String, durationSeconds: Long, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val res = battleRepository.createBattle(creator, title, topic, durationSeconds)
            res.onSuccess { battleId ->
                _selectedBattleId.value = battleId
                onCreated(battleId)
            }.onFailure {
                _feedback.value = it.userFriendlyMessage("Could not create battle.")
            }
        }
    }

    fun joinBattle(battle: Battle, opponent: PublicUser) {
        viewModelScope.launch {
            val res = battleRepository.joinBattle(battle, opponent)
            _feedback.value = res.fold(
                onSuccess = { "Battle is now LIVE!" },
                onFailure = { it.userFriendlyMessage("Could not join battle.") }
            )
        }
    }

    fun postBattleComment(battleId: String, author: PublicUser, content: String) {
        viewModelScope.launch {
            val res = battleRepository.postComment(battleId, author, content)
            res.onFailure {
                _feedback.value = it.userFriendlyMessage("Comment failed.")
            }
        }
    }

    fun sendBattleGift(battle: Battle, sender: PublicUser, forChallenger: Boolean, giftType: BattleGiftType) {
        viewModelScope.launch {
            val res = battleRepository.sendGift(battle, sender, forChallenger, giftType)
            res.onFailure {
                _feedback.value = it.userFriendlyMessage("Could not send ${giftType.label}.")
            }
        }
    }

    fun completeBattle(battle: Battle) {
        viewModelScope.launch {
            val res = battleRepository.completeBattle(battle)
            res.onFailure {
                _feedback.value = it.userFriendlyMessage("Could not finalize battle.")
            }
        }
    }

    fun sendMessage(conversation: Conversation, sender: PublicUser, text: String, mediaData: String? = null) {
        viewModelScope.launch {
            val res = messagingRepository.sendMessage(conversation, sender, text, mediaData)
            res.onFailure {
                _feedback.value = it.userFriendlyMessage("Message could not be sent.")
            }
        }
    }

    fun markConversationRead(conversation: Conversation, messages: List<ChatMessage>) {
        viewModelScope.launch {
            messagingRepository.markConversationRead(conversation, messages)
        }
    }

    fun deleteMessage(conversationId: String, messageId: String) {
        viewModelScope.launch {
            val res = messagingRepository.deleteOwnMessage(conversationId, messageId)
            res.onFailure {
                _feedback.value = it.userFriendlyMessage("Could not delete message.")
            }
        }
    }
}

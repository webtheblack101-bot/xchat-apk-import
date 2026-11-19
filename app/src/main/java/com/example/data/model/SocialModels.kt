package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class Group(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val visibility: String = "public",
    val ownerId: String = "",
    val imageData: String? = null,
    val memberCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "name" to name.trim().take(60),
            "description" to description.trim().take(300),
            "visibility" to if (visibility == "private") "private" else "public",
            "ownerId" to ownerId,
            "imageData" to imageData?.takeIf { it.isNotBlank() },
            "memberCount" to 1L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class GroupMember(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val role: String = "member",
    val status: String = "active",
    @ServerTimestamp val joinedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupInvite(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val inviterId: String = "",
    val inviteCode: String = "",
    val revoked: Boolean = false,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val type: String = "direct",
    val groupId: String? = null,
    val title: String? = null,
    val memberUids: List<String> = emptyList(),
    val lastMessagePreview: String = "",
    val lastSenderId: String? = null,
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val unreadCounts: Map<String, Long> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val messageType: String = "text",
    val content: String = "",
    val mediaData: String? = null,
    val readBy: List<String> = emptyList(),
    val deleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class BattleGiftType(val key: String, val label: String, val points: Long) {
    HEART("heart", "Heart", 1L),
    FIRE("fire", "Fire", 3L),
    ROSE("rose", "Rose", 5L),
    CROWN("crown", "Crown", 10L);

    companion object {
        fun fromKey(key: String): BattleGiftType =
            entries.firstOrNull { it.key == key } ?: HEART
    }
}

@Keep
@IgnoreExtraProperties
data class Battle(
    @DocumentId val id: String = "",
    val title: String = "",
    val topic: String = "",
    val creatorId: String = "",
    val challengerId: String = "",
    val challengerName: String = "",
    val opponentId: String = "",
    val opponentName: String = "",
    val challengerScore: Long = 0L,
    val opponentScore: Long = 0L,
    val status: String = "waiting",
    val durationSeconds: Long = 180L,
    val winnerId: String = "",
    @ServerTimestamp val startedAt: Timestamp? = null,
    val endsAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BattleComment(
    @DocumentId val id: String = "",
    val battleId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BattleGift(
    @DocumentId val id: String = "",
    val battleId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val recipientId: String = "",
    val giftType: String = "heart",
    val points: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "system",
    val title: String = "",
    val body: String = "",
    val entityId: String = "",
    val readFlag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReportItem(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reportedUserId: String = "",
    val targetType: String = "user",
    val targetId: String = "",
    val reason: String = "",
    val description: String = "",
    val status: String = "open",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ModerationAction(
    @DocumentId val id: String = "",
    val moderatorId: String = "",
    val targetUserId: String = "",
    val actionType: String = "warn",
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class PublicUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarData: String? = null,
    val interests: List<String> = emptyList(),
    val languages: List<String> = listOf("English"),
    val accountStatus: String = "active",
    val onlineVisible: Boolean = true,
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to userId,
            "username" to username.lowercase().trim(),
            "displayName" to displayName.trim().take(40),
            "bio" to bio.trim().take(300),
            "avatarData" to avatarData?.takeIf { it.isNotBlank() },
            "interests" to interests.take(15),
            "languages" to languages.take(10),
            "accountStatus" to "active",
            "onlineVisible" to onlineVisible,
            "lastActiveAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class PrivateAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val birthYear: Long = 2000L,
    val ageVerified: Boolean = false,
    val showOnlineStatus: Boolean = true,
    val allowFriendRequests: Boolean = true,
    val notifyMessages: Boolean = true,
    val notifyMatches: Boolean = true,
    val notifyGroups: Boolean = true,
    val notifyBattles: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "email" to email.take(160),
        "birthYear" to birthYear,
        "ageVerified" to ageVerified,
        "showOnlineStatus" to showOnlineStatus,
        "allowFriendRequests" to allowFriendRequests,
        "notifyMessages" to notifyMessages,
        "notifyMatches" to notifyMatches,
        "notifyGroups" to notifyGroups,
        "notifyBattles" to notifyBattles,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FriendConnection(
    @DocumentId val id: String = "",
    val userId: String = "",
    val friendUid: String = "",
    val friendName: String = "",
    val friendUsername: String = "",
    val status: String = "connected",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupMembershipIndex(
    @DocumentId val id: String = "",
    val userId: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val visibility: String = "public",
    val role: String = "member",
    @ServerTimestamp val joinedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DatingProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val datingEnabled: Boolean = false,
    val datingBio: String = "",
    val age: Long = 18L,
    val city: String = "",
    val relationshipIntent: String = "dating",
    val conversationStyle: String = "playful",
    val availabilityStatus: String = "tonight",
    val interests: List<String> = emptyList(),
    val languages: List<String> = listOf("English"),
    val preferredAgeMin: Long = 18L,
    val preferredAgeMax: Long = 45L,
    val preferredCity: String = "",
    val preferredIntents: List<String> = listOf("dating", "casual", "serious"),
    val preferredLanguages: List<String> = listOf("English"),
    val photos: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.trim().take(40),
        "datingEnabled" to datingEnabled,
        "datingBio" to datingBio.trim().take(400),
        "age" to age.coerceIn(18L, 100L),
        "city" to city.trim().ifBlank { "Global" }.take(80),
        "relationshipIntent" to relationshipIntent,
        "conversationStyle" to conversationStyle,
        "availabilityStatus" to availabilityStatus,
        "interests" to interests.take(15),
        "languages" to languages.take(10),
        "preferredAgeMin" to preferredAgeMin.coerceIn(18L, 99L),
        "preferredAgeMax" to preferredAgeMax.coerceIn(preferredAgeMin.coerceIn(18L, 99L), 100L),
        "preferredCity" to preferredCity.trim().take(80),
        "preferredIntents" to preferredIntents.take(5),
        "preferredLanguages" to preferredLanguages.take(10),
        "photos" to photos.take(6),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class BlockRecord(
    @DocumentId val id: String = "",
    val blockerId: String = "",
    val blockedId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

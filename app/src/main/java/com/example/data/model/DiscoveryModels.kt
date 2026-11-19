package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class LikeInteraction(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val interactionType: String = "like",
    val intentContext: String = "dating",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MatchRecord(
    @DocumentId val id: String = "",
    val userAId: String = "",
    val userBId: String = "",
    val memberUids: List<String> = emptyList(),
    val status: String = "active",
    val conversationId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RecommendationEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetUserId: String = "",
    val eventType: String = "profile_impression",
    val intentContext: String = "dating",
    val hourOfDay: Long = 0L,
    val dayOfWeek: Long = 1L,
    val position: Long = 0L,
    val modelVersion: String = "hybrid-v1.0",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RecommendationPreference(
    @DocumentId val id: String = "",
    val userId: String = "",
    val personalizedEnabled: Boolean = true,
    val boostedTags: List<String> = emptyList(),
    val suppressedTags: List<String> = emptyList(),
    val preferredHourBucket: String = "balanced",
    val modelVersion: String = "hybrid-v1.0",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class RecommendationScoreBreakdown(
    val profileSimilarity: Double,
    val mutualCompatibility: Double,
    val behavioralAffinity: Double,
    val intentAndStyle: Double,
    val timeContext: Double,
    val availability: Double,
    val diversityFreshness: Double,
    val totalScore: Double,
    val explainabilityTags: List<String>
)

data class RankedCandidate(
    val profile: DatingProfile,
    val publicUser: PublicUser?,
    val breakdown: RecommendationScoreBreakdown,
    val position: Int
) {
    val matchPercent: Int
        get() = (breakdown.totalScore * 100.0).toInt().coerceIn(5, 99)
}

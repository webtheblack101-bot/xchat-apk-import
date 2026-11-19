package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DatingProfile
import com.example.data.model.LikeInteraction
import com.example.data.model.MatchRecord
import com.example.data.model.OperationType
import com.example.data.model.PublicUser
import com.example.data.model.RecommendationEvent
import com.example.data.model.RecommendationPreference
import com.example.data.model.handleFirestoreError
import com.example.domain.recommendation.HybridRecommendationEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Calendar

data class InteractionOutcome(
    val matched: Boolean,
    val matchId: String? = null,
    val conversationId: String? = null
)

class DiscoveryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeEligibleDatingProfiles(): Flow<List<DatingProfile>> =
        db.collection("dating_profiles")
            .whereEqualTo("datingEnabled", true)
            .limit(60)
            .snapshots()
            .map { snap -> snap.toObjects(DatingProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeMyLikes(uid: String): Flow<List<LikeInteraction>> =
        db.collection("likes")
            .whereEqualTo("fromUserId", uid)
            .snapshots()
            .map { snap -> snap.toObjects(LikeInteraction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeMyMatches(uid: String): Flow<List<MatchRecord>> =
        db.collection("matches")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap -> snap.toObjects(MatchRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeRecommendationPreferences(uid: String): Flow<RecommendationPreference?> =
        db.collection("recommendation_preferences").document(uid)
            .snapshots()
            .map { it.toObject(RecommendationPreference::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeRecommendationEvents(uid: String): Flow<List<RecommendationEvent>> =
        db.collection("recommendation_events")
            .whereEqualTo("userId", uid)
            .limit(100)
            .snapshots()
            .map { snap -> snap.toObjects(RecommendationEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun logRecommendationEvent(
        targetUserId: String,
        eventType: String,
        intentContext: String,
        position: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY).toLong()
        val day = cal.get(Calendar.DAY_OF_WEEK).toLong().coerceIn(1L, 7L)
        val docRef = db.collection("recommendation_events").document()
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "targetUserId" to targetUserId,
                    "eventType" to eventType,
                    "intentContext" to intentContext.take(32),
                    "hourOfDay" to hour,
                    "dayOfWeek" to day,
                    "position" to position.coerceIn(0, 500).toLong(),
                    "modelVersion" to HybridRecommendationEngine.MODEL_VERSION,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitSwipeInteraction(
        me: PublicUser,
        targetProfile: DatingProfile,
        interactionType: String,
        intentContext: String,
        position: Int
    ): Result<InteractionOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val targetUid = targetProfile.userId
        if (targetUid == uid) return Result.failure(IllegalArgumentException("Invalid self-interaction"))
        val likeId = "${uid}_$targetUid"
        val reciprocalId = "${targetUid}_$uid"
        val minUid = minOf(uid, targetUid)
        val maxUid = maxOf(uid, targetUid)
        val matchId = "${minUid}_$maxUid"
        val convId = "dm_${minUid}_$maxUid"

        return try {
            val reciprocalSnap = if (interactionType in setOf("like", "superlike")) {
                db.collection("likes").document(reciprocalId).get().await()
            } else null
            val reciprocalType = reciprocalSnap?.getString("interactionType")
            val isMutual = reciprocalType in setOf("like", "superlike")

            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY).toLong()
            val day = cal.get(Calendar.DAY_OF_WEEK).toLong().coerceIn(1L, 7L)

            val batch = db.batch()
            batch.set(
                db.collection("likes").document(likeId),
                mapOf(
                    "fromUserId" to uid,
                    "toUserId" to targetUid,
                    "interactionType" to interactionType,
                    "intentContext" to intentContext.take(32),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("recommendation_events").document(),
                mapOf(
                    "userId" to uid,
                    "targetUserId" to targetUid,
                    "eventType" to interactionType,
                    "intentContext" to intentContext.take(32),
                    "hourOfDay" to hour,
                    "dayOfWeek" to day,
                    "position" to position.coerceIn(0, 500).toLong(),
                    "modelVersion" to HybridRecommendationEngine.MODEL_VERSION,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            if (isMutual) {
                val existingMatch = db.collection("matches").document(matchId).get().await()
                if (!existingMatch.exists()) {
                    batch.set(
                        db.collection("matches").document(matchId),
                        mapOf(
                            "userAId" to minUid,
                            "userBId" to maxUid,
                            "memberUids" to listOf(minUid, maxUid),
                            "status" to "active",
                            "conversationId" to convId,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.set(
                        db.collection("conversations").document(convId),
                        mapOf(
                            "type" to "direct",
                            "title" to targetProfile.displayName.take(80),
                            "memberUids" to listOf(minUid, maxUid),
                            "lastMessagePreview" to "It's a match! Break the ice.",
                            "lastSenderId" to uid,
                            "lastMessageAt" to FieldValue.serverTimestamp(),
                            "unreadCounts" to mapOf(minUid to 0L, maxUid to 1L),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.set(
                    db.collection("notifications").document(),
                    mapOf(
                        "userId" to targetUid,
                        "type" to "match",
                        "title" to "New Match with ${me.displayName}",
                        "body" to "You and ${me.displayName} liked each other on X Chat!",
                        "entityId" to convId,
                        "readFlag" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else if (interactionType in setOf("like", "superlike")) {
                batch.set(
                    db.collection("notifications").document(),
                    mapOf(
                        "userId" to targetUid,
                        "type" to "like",
                        "title" to if (interactionType == "superlike") "New Superlike" else "Someone liked you",
                        "body" to "${me.displayName} showed interest in your profile.",
                        "entityId" to uid,
                        "readFlag" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(
                InteractionOutcome(
                    matched = isMutual,
                    matchId = if (isMutual) matchId else null,
                    conversationId = if (isMutual) convId else null
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "likes/$likeId")
            Result.failure(e)
        }
    }

    suspend fun unmatch(matchId: String): Result<Unit> {
        val path = "matches/$matchId"
        return try {
            db.document(path).update(
                mapOf(
                    "status" to "unmatched",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun applyFeedbackTags(
        currentPrefs: RecommendationPreference?,
        targetProfile: DatingProfile,
        moreLikeThis: Boolean,
        position: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val targetTags = targetProfile.interests.map { it.lowercase() }
        val currentBoost = currentPrefs?.boostedTags?.toMutableSet() ?: mutableSetOf()
        val currentSuppress = currentPrefs?.suppressedTags?.toMutableSet() ?: mutableSetOf()
        if (moreLikeThis) {
            currentBoost.addAll(targetTags)
            currentSuppress.removeAll(targetTags.toSet())
        } else {
            currentSuppress.addAll(targetTags)
            currentBoost.removeAll(targetTags.toSet())
        }
        val path = "recommendation_preferences/$uid"
        return try {
            db.document(path).set(
                mapOf(
                    "userId" to uid,
                    "personalizedEnabled" to (currentPrefs?.personalizedEnabled ?: true),
                    "boostedTags" to currentBoost.take(20),
                    "suppressedTags" to currentSuppress.take(20),
                    "preferredHourBucket" to (currentPrefs?.preferredHourBucket ?: "balanced"),
                    "modelVersion" to HybridRecommendationEngine.MODEL_VERSION,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logRecommendationEvent(
                targetUserId = targetProfile.userId,
                eventType = if (moreLikeThis) "more_like_this" else "less_like_this",
                intentContext = targetProfile.relationshipIntent,
                position = position
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun setPersonalizedEnabled(enabled: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "recommendation_preferences/$uid"
        return try {
            db.document(path).set(
                mapOf(
                    "userId" to uid,
                    "personalizedEnabled" to enabled,
                    "boostedTags" to emptyList<String>(),
                    "suppressedTags" to emptyList<String>(),
                    "preferredHourBucket" to "balanced",
                    "modelVersion" to HybridRecommendationEngine.MODEL_VERSION,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}

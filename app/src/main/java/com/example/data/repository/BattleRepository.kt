package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Battle
import com.example.data.model.BattleComment
import com.example.data.model.BattleGift
import com.example.data.model.BattleGiftType
import com.example.data.model.OperationType
import com.example.data.model.PublicUser
import com.example.data.model.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
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
import java.util.Date

class BattleRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeBattles(): Flow<List<Battle>> =
        db.collection("battles")
            .limit(40)
            .snapshots()
            .map { snap ->
                snap.toObjects(Battle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<Battle> {
                            when (it.status) {
                                "live" -> 0
                                "waiting" -> 1
                                else -> 2
                            }
                        }.thenByDescending { it.createdAt?.toDate()?.time ?: 0L }
                    )
            }

    fun observeBattle(battleId: String): Flow<Battle?> =
        db.collection("battles").document(battleId)
            .snapshots()
            .map { it.toObject(Battle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeComments(battleId: String): Flow<List<BattleComment>> =
        db.collection("battles").document(battleId).collection("comments")
            .limit(80)
            .snapshots()
            .map { snap ->
                snap.toObjects(BattleComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }

    fun observeGifts(battleId: String): Flow<List<BattleGift>> =
        db.collection("battles").document(battleId).collection("gifts")
            .limit(60)
            .snapshots()
            .map { snap ->
                snap.toObjects(BattleGift::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    suspend fun createBattle(
        creator: PublicUser,
        title: String,
        topic: String,
        durationSeconds: Long
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("battles").document()
        return try {
            val cleanTitle = title.trim().take(80)
            val cleanTopic = topic.trim().ifBlank { "Freestyle" }.take(60)
            require(cleanTitle.length >= 3) { "Battle title must be at least 3 characters." }
            docRef.set(
                mapOf(
                    "title" to cleanTitle,
                    "topic" to cleanTopic,
                    "creatorId" to uid,
                    "challengerId" to uid,
                    "challengerName" to creator.displayName.take(40),
                    "opponentId" to "",
                    "opponentName" to "",
                    "challengerScore" to 0L,
                    "opponentScore" to 0L,
                    "status" to "waiting",
                    "durationSeconds" to durationSeconds.coerceIn(60L, 600L),
                    "winnerId" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinBattle(battle: Battle, opponent: PublicUser): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (battle.challengerId == uid) return Result.failure(IllegalArgumentException("You are already the challenger."))
        if (battle.status != "waiting" || battle.opponentId.isNotBlank()) {
            return Result.failure(IllegalStateException("Battle already has an opponent."))
        }
        val path = "battles/${battle.id}"
        return try {
            val endsAt = Timestamp(Date(System.currentTimeMillis() + battle.durationSeconds * 1000L))
            db.document(path).update(
                mapOf(
                    "opponentId" to uid,
                    "opponentName" to opponent.displayName.take(40),
                    "status" to "live",
                    "startedAt" to FieldValue.serverTimestamp(),
                    "endsAt" to endsAt,
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

    suspend fun postComment(battleId: String, author: PublicUser, content: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val clean = content.trim().take(300)
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Comment cannot be empty"))
        val commentRef = db.collection("battles").document(battleId).collection("comments").document()
        return try {
            commentRef.set(
                mapOf(
                    "battleId" to battleId,
                    "userId" to uid,
                    "authorName" to author.displayName.take(40),
                    "content" to clean,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendGift(
        battle: Battle,
        sender: PublicUser,
        forChallenger: Boolean,
        giftType: BattleGiftType
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (battle.status != "live") return Result.failure(IllegalStateException("Battle is not live."))
        val recipientId = if (forChallenger) battle.challengerId else battle.opponentId
        if (recipientId.isBlank()) return Result.failure(IllegalStateException("Participant missing."))

        val battleRef = db.collection("battles").document(battle.id)
        val giftRef = battleRef.collection("gifts").document()
        return try {
            val batch = db.batch()
            batch.set(
                giftRef,
                mapOf(
                    "battleId" to battle.id,
                    "senderId" to uid,
                    "senderName" to sender.displayName.take(40),
                    "recipientId" to recipientId,
                    "giftType" to giftType.key,
                    "points" to giftType.points,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            val scoreField = if (forChallenger) "challengerScore" else "opponentScore"
            batch.update(
                battleRef,
                mapOf(
                    scoreField to FieldValue.increment(giftType.points),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, giftRef.path)
            Result.failure(e)
        }
    }

    suspend fun completeBattle(battle: Battle): Result<Unit> {
        if (battle.status == "completed") return Result.success(Unit)
        val winnerId = when {
            battle.challengerScore > battle.opponentScore -> battle.challengerId
            battle.opponentScore > battle.challengerScore -> battle.opponentId
            else -> "tie"
        }
        val path = "battles/${battle.id}"
        return try {
            val batch = db.batch()
            batch.update(
                db.document(path),
                mapOf(
                    "status" to "completed",
                    "winnerId" to winnerId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (battle.challengerId.isNotBlank()) {
                batch.set(
                    db.collection("notifications").document(),
                    mapOf(
                        "userId" to battle.challengerId,
                        "type" to "battle",
                        "title" to "Battle Completed: ${battle.title.take(40)}",
                        "body" to "Final Score ${battle.challengerScore} - ${battle.opponentScore}",
                        "entityId" to battle.id,
                        "readFlag" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}

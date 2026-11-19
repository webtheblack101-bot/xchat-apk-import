package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BlockRecord
import com.example.data.model.DatingProfile
import com.example.data.model.FriendConnection
import com.example.data.model.OperationType
import com.example.data.model.PrivateAccount
import com.example.data.model.PublicUser
import com.example.data.model.handleFirestoreError
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

class UserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observePublicUser(uid: String): Flow<PublicUser?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { it.toObject(PublicUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observePrivateAccount(uid: String): Flow<PrivateAccount?> =
        db.collection("users").document(uid).collection("private").document("account")
            .snapshots()
            .map { it.toObject(PrivateAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeActiveUsers(limit: Long = 50L): Flow<List<PublicUser>> =
        db.collection("users")
            .whereEqualTo("accountStatus", "active")
            .limit(limit)
            .snapshots()
            .map { snap -> snap.toObjects(PublicUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeFriends(uid: String): Flow<List<FriendConnection>> =
        db.collection("users").document(uid).collection("friends")
            .snapshots()
            .map { snap -> snap.toObjects(FriendConnection::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeDatingProfile(uid: String): Flow<DatingProfile?> =
        db.collection("dating_profiles").document(uid)
            .snapshots()
            .map { it.toObject(DatingProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeBlockedByMe(uid: String): Flow<List<BlockRecord>> =
        db.collection("blocks")
            .whereEqualTo("blockerId", uid)
            .snapshots()
            .map { snap -> snap.toObjects(BlockRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeBlockedMe(uid: String): Flow<List<BlockRecord>> =
        db.collection("blocks")
            .whereEqualTo("blockedId", uid)
            .snapshots()
            .map { snap -> snap.toObjects(BlockRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun isUsernameAvailable(username: String, excludeUid: String): Result<Boolean> {
        val clean = username.lowercase().trim()
        if (!clean.matches(Regex("^[a-z0-9_]{3,24}$"))) {
            return Result.success(false)
        }
        return try {
            val snap = db.collection("users")
                .whereEqualTo("accountStatus", "active")
                .whereEqualTo("username", clean)
                .limit(2)
                .get()
                .await()
            val takenByOther = snap.documents.any { it.id != excludeUid }
            Result.success(!takenByOther)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users")
            Result.failure(e)
        }
    }

    suspend fun completeOnboarding(
        publicUser: PublicUser,
        privateAccount: PrivateAccount,
        datingProfile: DatingProfile?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(publicUser.userId == uid && privateAccount.userId == uid)
            require(publicUser.username.lowercase().matches(Regex("^[a-z0-9_]{3,24}$"))) {
                "Username must be 3-24 lowercase letters, digits, or underscores."
            }
            val batch = db.batch()
            val pubRef = db.collection("users").document(uid)
            val privRef = pubRef.collection("private").document("account")
            batch.set(pubRef, publicUser.toCreateMap())
            batch.set(privRef, privateAccount.toCreateMap())
            if (datingProfile != null) {
                require(datingProfile.age >= 18L) { "Dating requires 18+ age verification." }
                val datingRef = db.collection("dating_profiles").document(uid)
                batch.set(datingRef, datingProfile.toCreateMap())
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updatePublicProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarData: String?,
        interests: List<String>,
        languages: List<String>,
        onlineVisible: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val cleanUsername = username.lowercase().trim()
            require(cleanUsername.matches(Regex("^[a-z0-9_]{3,24}$"))) {
                "Username must be 3-24 lowercase characters (a-z, 0-9, _)."
            }
            require(displayName.trim().length in 1..40) { "Display name must be 1-40 characters." }
            val payload = mutableMapOf<String, Any>(
                "username" to cleanUsername,
                "displayName" to displayName.trim().take(40),
                "bio" to bio.trim().take(300),
                "interests" to interests.take(15),
                "languages" to languages.take(10),
                "onlineVisible" to onlineVisible,
                "lastActiveAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarData.isNullOrBlank()) {
                payload["avatarData"] = avatarData
            }
            db.document(path).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacyAndNotifications(
        showOnlineStatus: Boolean,
        allowFriendRequests: Boolean,
        notifyMessages: Boolean,
        notifyMatches: Boolean,
        notifyGroups: Boolean,
        notifyBattles: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/private/account"
        return try {
            val batch = db.batch()
            batch.update(
                db.document(path),
                mapOf(
                    "showOnlineStatus" to showOnlineStatus,
                    "allowFriendRequests" to allowFriendRequests,
                    "notifyMessages" to notifyMessages,
                    "notifyMatches" to notifyMatches,
                    "notifyGroups" to notifyGroups,
                    "notifyBattles" to notifyBattles,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "onlineVisible" to showOnlineStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveDatingProfile(profile: DatingProfile, existsAlready: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "dating_profiles/$uid"
        return try {
            require(profile.age >= 18L) { "You must be at least 18 to activate dating discovery." }
            val docRef = db.document(path)
            if (existsAlready) {
                docRef.update(
                    mapOf(
                        "displayName" to profile.displayName.trim().take(40),
                        "datingEnabled" to profile.datingEnabled,
                        "datingBio" to profile.datingBio.trim().take(400),
                        "age" to profile.age.coerceIn(18L, 100L),
                        "city" to profile.city.trim().ifBlank { "Global" }.take(80),
                        "relationshipIntent" to profile.relationshipIntent,
                        "conversationStyle" to profile.conversationStyle,
                        "availabilityStatus" to profile.availabilityStatus,
                        "interests" to profile.interests.take(15),
                        "languages" to profile.languages.take(10),
                        "preferredAgeMin" to profile.preferredAgeMin.coerceIn(18L, 99L),
                        "preferredAgeMax" to profile.preferredAgeMax.coerceIn(profile.preferredAgeMin.coerceIn(18L, 99L), 100L),
                        "preferredCity" to profile.preferredCity.trim().take(80),
                        "preferredIntents" to profile.preferredIntents.take(5),
                        "preferredLanguages" to profile.preferredLanguages.take(10),
                        "photos" to profile.photos.take(6),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(profile.copy(userId = uid).toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addFriend(me: PublicUser, target: PublicUser): Result<Unit> {
        val uid = requireUserId()
        if (target.userId == uid) return Result.failure(IllegalArgumentException("Cannot add yourself"))
        val path = "users/$uid/friends/${target.userId}"
        return try {
            val batch = db.batch()
            batch.set(
                db.collection("users").document(uid).collection("friends").document(target.userId),
                mapOf(
                    "userId" to uid,
                    "friendUid" to target.userId,
                    "friendName" to target.displayName.take(40),
                    "friendUsername" to target.username.take(24),
                    "status" to "connected",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("users").document(target.userId).collection("friends").document(uid),
                mapOf(
                    "userId" to target.userId,
                    "friendUid" to uid,
                    "friendName" to me.displayName.take(40),
                    "friendUsername" to me.username.take(24),
                    "status" to "connected",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUid: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetUid == uid) return Result.failure(IllegalArgumentException("Cannot block yourself"))
        val blockId = "${uid}_$targetUid"
        val path = "blocks/$blockId"
        return try {
            db.document(path).set(
                mapOf(
                    "blockerId" to uid,
                    "blockedId" to targetUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(targetUid: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "blocks/${uid}_$targetUid"
        return try {
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAccountData(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            batch.delete(db.collection("users").document(uid).collection("private").document("account"))
            batch.delete(db.collection("dating_profiles").document(uid))
            batch.delete(db.collection("recommendation_preferences").document(uid))
            batch.delete(db.collection("users").document(uid))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }
}

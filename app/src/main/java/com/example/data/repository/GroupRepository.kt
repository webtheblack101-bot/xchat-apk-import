package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Group
import com.example.data.model.GroupInvite
import com.example.data.model.GroupMember
import com.example.data.model.GroupMembershipIndex
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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class GroupRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observePublicGroups(): Flow<List<Group>> =
        db.collection("groups")
            .whereEqualTo("visibility", "public")
            .limit(40)
            .snapshots()
            .map { snap -> snap.toObjects(Group::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeMyGroupMemberships(uid: String): Flow<List<GroupMembershipIndex>> =
        db.collection("users").document(uid).collection("group_memberships")
            .snapshots()
            .map { snap -> snap.toObjects(GroupMembershipIndex::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeGroup(groupId: String): Flow<Group?> =
        db.collection("groups").document(groupId)
            .snapshots()
            .map { it.toObject(Group::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    emit(null)
                } else {
                    throw error
                }
            }

    fun observeGroupMembers(groupId: String): Flow<List<GroupMember>> =
        db.collection("groups").document(groupId).collection("members")
            .snapshots()
            .map { snap -> snap.toObjects(GroupMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    emit(emptyList())
                } else {
                    throw error
                }
            }

    fun observeMyInvites(uid: String): Flow<List<GroupInvite>> =
        db.collection("group_invites")
            .whereEqualTo("inviterId", uid)
            .snapshots()
            .map { snap -> snap.toObjects(GroupInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun createGroup(
        creator: PublicUser,
        name: String,
        description: String,
        visibility: String,
        imageData: String?
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val groupRef = db.collection("groups").document()
        val groupId = groupRef.id
        return try {
            val cleanName = name.trim().take(60)
            require(cleanName.length >= 2) { "Group name must be at least 2 characters." }
            val vis = if (visibility == "private") "private" else "public"
            val groupPayload = Group(
                name = cleanName,
                description = description.trim().take(300),
                visibility = vis,
                ownerId = uid,
                imageData = imageData,
                memberCount = 1L
            ).toCreateMap()

            // Step 1: Create group document so isGroupOwner(groupId) resolves cleanly in rules
            groupRef.set(groupPayload).await()

            // Step 2: Create owner member record + user membership index + group conversation
            val convRef = db.collection("conversations").document("grp_$groupId")
            val batch = db.batch()
            batch.set(
                groupRef.collection("members").document(uid),
                mapOf(
                    "groupId" to groupId,
                    "userId" to uid,
                    "displayName" to creator.displayName.take(40),
                    "username" to creator.username.take(24),
                    "role" to "owner",
                    "status" to "active",
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("users").document(uid).collection("group_memberships").document(groupId),
                mapOf(
                    "userId" to uid,
                    "groupId" to groupId,
                    "groupName" to cleanName,
                    "visibility" to vis,
                    "role" to "owner",
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                convRef,
                mapOf(
                    "type" to "group",
                    "groupId" to groupId,
                    "title" to cleanName,
                    "memberUids" to listOf(uid),
                    "lastMessagePreview" to "Group created",
                    "lastSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(groupId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "groups/$groupId")
            Result.failure(e)
        }
    }

    suspend fun joinPublicGroup(group: Group, user: PublicUser): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val groupId = group.id
        return try {
            val batch = db.batch()
            val groupRef = db.collection("groups").document(groupId)
            batch.set(
                groupRef.collection("members").document(uid),
                mapOf(
                    "groupId" to groupId,
                    "userId" to uid,
                    "displayName" to user.displayName.take(40),
                    "username" to user.username.take(24),
                    "role" to "member",
                    "status" to "active",
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                groupRef,
                mapOf(
                    "memberCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("users").document(uid).collection("group_memberships").document(groupId),
                mapOf(
                    "userId" to uid,
                    "groupId" to groupId,
                    "groupName" to group.name.take(60),
                    "visibility" to group.visibility,
                    "role" to "member",
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            // Also ensure user is added to the group conversation memberUids
            val convRef = db.collection("conversations").document("grp_$groupId")
            try {
                convRef.update(
                    mapOf(
                        "memberUids" to FieldValue.arrayUnion(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
                // Group conversation might not exist for legacy groups
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "groups/$groupId/members/$uid")
            Result.failure(e)
        }
    }

    suspend fun requestPrivateGroupAccess(groupId: String, user: PublicUser): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "groups/$groupId/members/$uid"
        return try {
            db.document(path).set(
                mapOf(
                    "groupId" to groupId,
                    "userId" to uid,
                    "displayName" to user.displayName.take(40),
                    "username" to user.username.take(24),
                    "role" to "member",
                    "status" to "pending",
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
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

    suspend fun approveMember(group: Group, memberUid: String): Result<Unit> {
        val path = "groups/${group.id}/members/$memberUid"
        return try {
            val batch = db.batch()
            batch.update(
                db.document(path),
                mapOf(
                    "status" to "active",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("groups").document(group.id),
                mapOf(
                    "memberCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            try {
                db.collection("conversations").document("grp_${group.id}")
                    .update(
                        mapOf(
                            "memberUids" to FieldValue.arrayUnion(memberUid),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            } catch (_: Exception) {
                // Ignore if conversation doc was deleted or unavailable
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMemberRole(groupId: String, memberUid: String, role: String): Result<Unit> {
        val path = "groups/$groupId/members/$memberUid"
        return try {
            require(role in setOf("admin", "moderator", "member"))
            db.document(path).update(
                mapOf(
                    "role" to role,
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

    suspend fun removeOrLeaveGroup(groupId: String, targetUid: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "groups/$groupId/members/$targetUid"
        return try {
            val batch = db.batch()
            batch.delete(db.document(path))
            batch.update(
                db.collection("groups").document(groupId),
                mapOf(
                    "memberCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (targetUid == uid) {
                batch.delete(db.collection("users").document(uid).collection("group_memberships").document(groupId))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createInvite(groupId: String): Result<GroupInvite> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val code = "XC-${UUID.randomUUID().toString().take(6).uppercase()}"
        val path = "group_invites/$code"
        return try {
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
            db.document(path).set(
                mapOf(
                    "groupId" to groupId,
                    "inviterId" to uid,
                    "inviteCode" to code,
                    "revoked" to false,
                    "expiresAt" to expiresAt,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(
                GroupInvite(
                    id = code,
                    groupId = groupId,
                    inviterId = uid,
                    inviteCode = code,
                    revoked = false,
                    expiresAt = expiresAt
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeInvite(inviteId: String): Result<Unit> {
        val path = "group_invites/$inviteId"
        return try {
            db.document(path).update("revoked", true).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun redeemInvite(inviteCode: String, user: PublicUser): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanCode = inviteCode.trim().uppercase()
        val path = "group_invites/$cleanCode"
        return try {
            val snap = db.document(path).get().await()
            val invite = snap.toObject(GroupInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalArgumentException("Invite code not found."))
            if (invite.revoked) {
                return Result.failure(IllegalStateException("This invite link has been revoked."))
            }
            if (invite.expiresAt != null && invite.expiresAt.toDate().before(Date())) {
                return Result.failure(IllegalStateException("This invite link has expired."))
            }
            val group = try {
                val groupSnap = db.collection("groups").document(invite.groupId).get().await()
                groupSnap.toObject(Group::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } catch (_: FirebaseFirestoreException) {
                null
            }
            if (group != null && group.visibility == "public") {
                joinPublicGroup(group, user).getOrThrow()
            } else {
                requestPrivateGroupAccess(invite.groupId, user).getOrThrow()
            }
            Result.success(invite.groupId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }
}

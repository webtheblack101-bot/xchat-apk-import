package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ModerationAction
import com.example.data.model.NotificationItem
import com.example.data.model.OperationType
import com.example.data.model.ReportItem
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

class SafetyNotificationRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> =
        db.collection("notifications")
            .whereEqualTo("userId", uid)
            .limit(60)
            .snapshots()
            .map { snap ->
                snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    suspend fun markNotificationRead(notifId: String): Result<Unit> {
        val path = "notifications/$notifId"
        return try {
            db.document(path).update(
                mapOf(
                    "readFlag" to true,
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

    suspend fun submitReport(
        reportedUserId: String,
        targetType: String,
        targetId: String,
        reason: String,
        description: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("reports").document()
        return try {
            docRef.set(
                mapOf(
                    "reporterId" to uid,
                    "reportedUserId" to reportedUserId.take(128),
                    "targetType" to targetType,
                    "targetId" to targetId.take(128),
                    "reason" to reason.trim().take(80),
                    "description" to description.trim().take(500),
                    "status" to "open",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
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

    suspend fun checkIsAdmin(uid: String): Boolean {
        val email = auth.currentUser?.email
        val emailVerified = auth.currentUser?.isEmailVerified == true
        if (email == "webtheblack101@gmail.com" && emailVerified) return true
        return try {
            db.collection("admins").document(uid).get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    fun observeReportsForAdmin(): Flow<List<ReportItem>> =
        db.collection("reports")
            .limit(50)
            .snapshots()
            .map { snap ->
                snap.toObjects(ReportItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeModerationActionsForAdmin(): Flow<List<ModerationAction>> =
        db.collection("moderation_actions")
            .limit(50)
            .snapshots()
            .map { snap ->
                snap.toObjects(ModerationAction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    suspend fun executeModerationAction(
        reportId: String?,
        targetUserId: String,
        actionType: String,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            val actionRef = db.collection("moderation_actions").document()
            batch.set(
                actionRef,
                mapOf(
                    "moderatorId" to uid,
                    "targetUserId" to targetUserId,
                    "actionType" to actionType,
                    "reason" to reason.trim().take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            if (targetUserId.isNotBlank() && actionType in setOf("restrict", "suspend", "restore")) {
                val newStatus = when (actionType) {
                    "suspend" -> "suspended"
                    "restrict" -> "restricted"
                    else -> "active"
                }
                batch.update(
                    db.collection("users").document(targetUserId),
                    mapOf(
                        "accountStatus" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            if (!reportId.isNullOrBlank()) {
                val newReportStatus = if (actionType == "dismiss_report") "dismissed" else "resolved"
                batch.update(
                    db.collection("reports").document(reportId),
                    mapOf(
                        "status" to newReportStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "moderation_actions")
            Result.failure(e)
        }
    }
}

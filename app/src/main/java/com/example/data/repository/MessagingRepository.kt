package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.OperationType
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

class MessagingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeConversations(uid: String): Flow<List<Conversation>> =
        db.collection("conversations")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: it.createdAt }
            }

    fun observeConversation(convId: String): Flow<Conversation?> =
        db.collection("conversations").document(convId)
            .snapshots()
            .map { it.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeMessages(convId: String, uid: String): Flow<List<ChatMessage>> =
        db.collection("conversations").document(convId).collection("messages")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }

    suspend fun getOrCreateDirectConversation(
        me: PublicUser,
        other: PublicUser
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (other.userId == uid) return Result.failure(IllegalArgumentException("Cannot message yourself"))
        val minUid = minOf(uid, other.userId)
        val maxUid = maxOf(uid, other.userId)
        val convId = "dm_${minUid}_$maxUid"
        val path = "conversations/$convId"
        return try {
            val docRef = db.document(path)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "type" to "direct",
                        "title" to other.displayName.take(80),
                        "memberUids" to listOf(minUid, maxUid),
                        "lastMessagePreview" to "Conversation started",
                        "lastSenderId" to uid,
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "unreadCounts" to mapOf(minUid to 0L, maxUid to 0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversation: Conversation,
        sender: PublicUser,
        content: String,
        mediaData: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanText = content.trim().take(2000)
        if (cleanText.isEmpty() && mediaData.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty."))
        }
        val convRef = db.collection("conversations").document(conversation.id)
        val msgRef = convRef.collection("messages").document()
        val msgType = if (!mediaData.isNullOrBlank()) "image" else "text"
        val preview = when {
            msgType == "image" && cleanText.isEmpty() -> "Sent a photo"
            msgType == "image" -> "Photo: ${cleanText.take(120)}"
            else -> cleanText.take(180)
        }

        val updatedUnread = conversation.unreadCounts.toMutableMap()
        conversation.memberUids.forEach { memberUid ->
            if (memberUid == uid) {
                updatedUnread[memberUid] = 0L
            } else {
                updatedUnread[memberUid] = (updatedUnread[memberUid] ?: 0L) + 1L
            }
        }

        val msgPayload = mutableMapOf<String, Any?>(
            "conversationId" to conversation.id,
            "senderId" to uid,
            "senderName" to sender.displayName.take(40),
            "memberUids" to conversation.memberUids.take(50),
            "messageType" to msgType,
            "content" to cleanText,
            "mediaData" to mediaData?.takeIf { it.isNotBlank() },
            "readBy" to listOf(uid),
            "deleted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        return try {
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                convRef,
                mapOf(
                    "lastMessagePreview" to preview,
                    "lastSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "unreadCounts" to updatedUnread,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            // Notify direct recipients
            if (conversation.type == "direct") {
                conversation.memberUids.filter { it != uid }.forEach { recipientUid ->
                    val notifRef = db.collection("notifications").document()
                    batch.set(
                        notifRef,
                        mapOf(
                            "userId" to recipientUid,
                            "type" to "message",
                            "title" to sender.displayName.take(80),
                            "body" to preview.take(240),
                            "entityId" to conversation.id,
                            "readFlag" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun markConversationRead(conversation: Conversation, messages: List<ChatMessage>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            var hasUpdates = false
            if ((conversation.unreadCounts[uid] ?: 0L) > 0L) {
                val updated = conversation.unreadCounts.toMutableMap()
                updated[uid] = 0L
                batch.update(
                    db.collection("conversations").document(conversation.id),
                    mapOf(
                        "unreadCounts" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                hasUpdates = true
            }
            messages.filter { uid !in it.readBy && it.id.isNotBlank() }.takeLast(15).forEach { msg ->
                batch.update(
                    db.collection("conversations").document(conversation.id)
                        .collection("messages").document(msg.id),
                    mapOf(
                        "readBy" to FieldValue.arrayUnion(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                hasUpdates = true
            }
            if (hasUpdates) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/${conversation.id}")
            Result.failure(e)
        }
    }

    suspend fun deleteOwnMessage(conversationId: String, messageId: String): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            db.document(path).update(
                mapOf(
                    "content" to "Message deleted",
                    "deleted" to true,
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
}

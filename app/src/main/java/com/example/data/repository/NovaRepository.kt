package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.VoiceRecording
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NovaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // Always initialize with R.string.firestore_database_id (never zero-arg default)
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun generateSafeId(prefix: String): String {
        return "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
    }

    // --- Conversations ---

    fun observeConversations(userId: String): Flow<List<Conversation>> {
        val path = "users/$userId/conversations"
        return db.collection("users").document(userId).collection("conversations")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Conversation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun getConversationById(userId: String, conversationId: String): Conversation? {
        val docRef = db.collection("users").document(userId)
            .collection("conversations").document(conversationId)
        return try {
            docRef.get().await().toObject(
                Conversation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun createConversation(
        title: String = "New Chat",
        mode: String = "chat",
        previewText: String = ""
    ): String {
        val uid = requireUserId()
        val conversationId = generateSafeId("conv")
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
        val conversation = Conversation(
            id = conversationId,
            userId = uid,
            title = title,
            mode = mode,
            previewText = previewText,
            messageCount = 0L
        )
        try {
            docRef.set(conversation.toCreatePayload()).await()
            return conversationId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateConversationMetadata(
        conversationId: String,
        title: String? = null,
        mode: String? = null,
        previewText: String? = null,
        messageCount: Long? = null
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (title != null && title.isNotBlank()) {
            updates["title"] = title.take(200)
        }
        if (mode != null && mode in listOf("chat", "image", "voice")) {
            updates["mode"] = mode
        }
        if (previewText != null) {
            updates["previewText"] = previewText.take(500)
        }
        if (messageCount != null) {
            updates["messageCount"] = messageCount
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        val uid = requireUserId()
        val convRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
        try {
            val messagesSnap = convRef.collection("messages")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val docs = messagesSnap.documents
            for (chunk in docs.chunked(400)) {
                val batch = db.batch()
                for (doc in chunk) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }
            convRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convRef.path)
            throw e
        }
    }

    // --- Messages ---

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> {
        val path = "users/$userId/conversations/$conversationId/messages"
        return db.collection("users").document(userId)
            .collection("conversations").document(conversationId)
            .collection("messages")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { (it.createdAt ?: Timestamp(0, 0)).toDate().time }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        text: String,
        messageType: String = "text",
        modelUsed: String = com.example.data.model.FREE_GEMINI_MODEL_ID,
        thoughtSummary: String? = null,
        mediaLocalPath: String? = null,
        mediaThumbBase64: String? = null,
        audioDurationSec: Long? = null
    ): String {
        val uid = requireUserId()
        val messageId = generateSafeId("msg")
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        val message = ChatMessage(
            id = messageId,
            userId = uid,
            conversationId = conversationId,
            role = role,
            text = text,
            thoughtSummary = thoughtSummary,
            messageType = messageType,
            modelUsed = modelUsed,
            mediaLocalPath = mediaLocalPath,
            mediaThumbBase64 = mediaThumbBase64,
            audioDurationSec = audioDurationSec
        )
        try {
            docRef.set(message.toCreatePayload()).await()
            return messageId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateMessage(
        conversationId: String,
        messageId: String,
        text: String,
        modelUsed: String,
        thoughtSummary: String? = null,
        mediaLocalPath: String? = null,
        mediaThumbBase64: String? = null
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        val updates = mutableMapOf<String, Any>(
            "text" to text.take(50000),
            "modelUsed" to modelUsed.take(100),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!thoughtSummary.isNullOrBlank()) {
            updates["thoughtSummary"] = thoughtSummary.take(30000)
        }
        if (!mediaLocalPath.isNullOrBlank()) {
            updates["mediaLocalPath"] = mediaLocalPath.take(1000)
        }
        if (!mediaThumbBase64.isNullOrBlank()) {
            updates["mediaThumbBase64"] = mediaThumbBase64.take(200000)
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteMessage(conversationId: String, messageId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Voice Recordings ---

    fun observeVoiceRecordings(userId: String): Flow<List<VoiceRecording>> {
        val path = "users/$userId/voice_recordings"
        return db.collection("users").document(userId)
            .collection("voice_recordings")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    VoiceRecording::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun saveVoiceRecording(
        conversationId: String,
        title: String,
        userTranscript: String,
        aiTranscript: String,
        durationSeconds: Long,
        audioFilePath: String? = null
    ): String {
        val uid = requireUserId()
        val recordingId = generateSafeId("rec")
        val docRef = db.collection("users").document(uid)
            .collection("voice_recordings").document(recordingId)
        val recording = VoiceRecording(
            id = recordingId,
            userId = uid,
            conversationId = conversationId,
            title = title,
            userTranscript = userTranscript,
            aiTranscript = aiTranscript,
            durationSeconds = durationSeconds,
            audioFilePath = audioFilePath
        )
        try {
            docRef.set(recording.toCreatePayload()).await()
            return recordingId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteVoiceRecording(recordingId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("voice_recordings").document(recordingId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}

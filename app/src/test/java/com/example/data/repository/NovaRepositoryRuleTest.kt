package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NovaRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createConversationAndMessage_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = NovaRepository(firestore, auth)

        val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createConversation(
                title = "App Architecture",
                mode = "chat",
                previewText = "How to create an app?"
            )
        }
        assertTrue(convId.isNotBlank())

        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addMessage(
                conversationId = convId,
                role = "user",
                text = "How to create an app?",
                messageType = "text",
                modelUsed = "gemini-3.5-flash-lite"
            )
        }
        assertTrue(msgId.isNotBlank())

        val conversations = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeConversations(aliceUid).first { list -> list.any { it.id == convId } }
        }
        assertTrue(conversations.any { it.id == convId && it.title == "App Architecture" })

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMessages(aliceUid, convId).first { list -> list.any { it.id == msgId } }
        }
        assertTrue(messages.any { it.id == msgId && it.text == "How to create an app?" })
    }

    @Test
    fun saveVoiceRecording_authenticatedOwner_persistsAndStreams() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = NovaRepository(firestore, auth)

        val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createConversation(title = "Voice Session", mode = "voice")
        }
        val recId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveVoiceRecording(
                conversationId = convId,
                title = "Ideas Brainstorm",
                userTranscript = "Find ways to earn.",
                aiTranscript = "Here are three creative ways to build sustainable income.",
                durationSeconds = 28L
            )
        }
        assertTrue(recId.isNotBlank())

        val recordings = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeVoiceRecordings(aliceUid).first { list -> list.any { it.id == recId } }
        }
        assertTrue(recordings.any { it.id == recId && it.durationSeconds == 28L })
    }

    @Test
    fun getConversation_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = NovaRepository(firestore, auth)
        val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createConversation(title = "Alice Private Chat")
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = NovaRepository(firestore, auth)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getConversationById(aliceUid, convId)
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED for cross-user read")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun observeConversations_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = NovaRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeConversations(aliceUid).first()
            }
            fail("Expected exception for unauthenticated Flow observation")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
        }
    }

    @Test
    fun deleteConversation_authenticatedOwner_deletesMessagesAndConversationIdempotently() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = NovaRepository(firestore, auth)

        val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createConversation(title = "To Be Deleted", mode = "chat")
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addMessage(
                conversationId = convId,
                role = "user",
                text = "Delete me cleanly",
                messageType = "text",
                modelUsed = "gemini-3.5-flash-lite"
            )
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteConversation(convId)
            // Calling delete a second time on a removed conversation should succeed without PERMISSION_DENIED
            repository.deleteConversation(convId)
        }

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getConversationById(aliceUid, convId)
        }
        assertEquals(null, fetched)
    }

    private companion object {
        const val ALICE_EMAIL = "alice.nova@test.com"
        const val BOB_EMAIL = "bob.nova@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}

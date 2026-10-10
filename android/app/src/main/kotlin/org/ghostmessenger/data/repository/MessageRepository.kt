package org.ghostmessenger.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ghostmessenger.core.crypto.SignalCryptoManager
import org.ghostmessenger.core.model.EncryptedEnvelope
import org.ghostmessenger.core.model.Identity
import org.ghostmessenger.data.local.dao.ConversationDao
import org.ghostmessenger.data.local.dao.MessageDao
import org.ghostmessenger.data.local.entities.ConversationEntity
import org.ghostmessenger.data.local.entities.MessageEntity
import org.ghostmessenger.data.local.prefs.SecurePreferences
import org.ghostmessenger.data.network.api.PreKeyApiClient
import org.ghostmessenger.data.network.model.SignalingAnswer
import org.ghostmessenger.data.network.model.SignalingConnectionState
import org.ghostmessenger.data.network.model.SignalingIceCandidate
import org.ghostmessenger.data.network.model.SignalingOffer
import org.ghostmessenger.data.network.socket.SignalingClient
import org.ghostmessenger.data.webrtc.WebRtcManager
import org.ghostmessenger.BuildConfig
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.webrtc.IceCandidate
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Security events emitted when cryptographic properties of a contact change.
 * The UI layer must observe and surface these to the user.
 */
sealed class SecurityEvent {
    /**
     * A peer's identity key does not match the previously trusted key (TOFU violation).
     * The message was rejected. The user must verify the contact out-of-band.
     */
    data class IdentityChanged(val peerUserCode: String) : SecurityEvent()
}

/**
 * Unified repository managing conversations, messages, Signal Protocol encryption,
 * WebRTC P2P DataChannels, and fallback ephemeral signaling relay.
 */
@Singleton
class MessageRepository @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val signalCryptoManager: SignalCryptoManager,
    private val preKeyApiClient: PreKeyApiClient,
    private val signalingClient: SignalingClient,
    private val webRtcManager: WebRtcManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _currentIdentity = MutableStateFlow<Identity?>(null)
    val currentIdentity: StateFlow<Identity?> = _currentIdentity.asStateFlow()

    // Security (F4): emits when a peer's identity key changes unexpectedly.
    // Subscribers (e.g. ChatViewModel) must surface this to the user as a visible warning.
    private val _securityEvents = MutableSharedFlow<SecurityEvent>(extraBufferCapacity = 16)
    val securityEvents: SharedFlow<SecurityEvent> = _securityEvents.asSharedFlow()

    val conversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()
    val signalingState: StateFlow<SignalingConnectionState> = signalingClient.connectionState

    init {
        // 1. Observe incoming WebRTC signaling events
        scope.launch {
            signalingClient.incomingOffers.collect { offer ->
                handleIncomingOffer(offer)
            }
        }

        scope.launch {
            signalingClient.incomingAnswers.collect { answer ->
                handleIncomingAnswer(answer)
            }
        }

        scope.launch {
            signalingClient.incomingIceCandidates.collect { candidate ->
                handleIncomingIceCandidate(candidate)
            }
        }

        // 2. Observe incoming fallback relay envelopes
        scope.launch {
            signalingClient.incomingEnvelopes.collect { signalingEnvelope ->
                handleIncomingEnvelopeString(signalingEnvelope.envelope)
            }
        }

        // 3. Observe direct P2P DataChannel incoming messages
        scope.launch {
            webRtcManager.incomingMessages.collect { (_, rawBytes) ->
                val envelopeJson = String(rawBytes, StandardCharsets.UTF_8)
                handleIncomingEnvelopeString(envelopeJson)
            }
        }

        // 4. Auto-load existing identity if present
        val existing = securePreferences.getIdentity()
        if (existing != null) {
            _currentIdentity.value = existing
            val serverUrl = securePreferences.getSignalingUrl()
            signalingClient.connect(serverUrl, existing.userCode)
            // Security (F10): check prekey replenishment and signed prekey rotation on startup
            scope.launch {
                replenishPreKeysIfNeeded(serverUrl, existing.userCode)
                rotateSignedPreKeyIfNeeded(serverUrl, existing.userCode)
            }
        }
    }

    /**
     * Initializes and persists the user's active identity, publishes PreKey bundle,
     * and connects to the signaling server.
     */
    suspend fun initializeIdentity(
        identity: Identity,
        serverUrl: String = securePreferences.getSignalingUrl()
    ): Result<Boolean> {
        return try {
            securePreferences.saveIdentity(identity)
            _currentIdentity.value = identity

            // Generate PreKey bundle
            val bundleDto = signalCryptoManager.generateAndStorePreKeys(startId = 1, count = 50)

            // Upload PreKey bundle to signaling server
            val uploadResult = preKeyApiClient.uploadPreKeyBundle(serverUrl, identity.userCode, bundleDto)
            if (uploadResult.isFailure) {
                return uploadResult
            }

            // Connect to Socket.IO signaling server
            signalingClient.connect(serverUrl, identity.userCode)

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Security (F10): Checks remaining prekey count on the server for [userCode].
     * If fewer than [threshold] one-time prekeys remain, generates and uploads a fresh batch.
     * Called on startup and can be called after any successful bundle fetch.
     */
    private suspend fun replenishPreKeysIfNeeded(
        serverUrl: String,
        userCode: String,
        threshold: Int = 10
    ) {
        try {
            val countResult = preKeyApiClient.fetchPreKeyCount(serverUrl, userCode)
            val remainingCount = countResult.getOrNull() ?: return
            if (remainingCount < threshold) {
                // Find the highest stored prekey ID to avoid ID collision on re-upload
                val currentMaxId = signalCryptoManager.signalProtocolStore
                    .let { store -> (1..1000).lastOrNull { store.containsPreKey(it) } ?: 0 }
                val newStartId = currentMaxId + 1
                val bundleDto = signalCryptoManager.generateAndStorePreKeys(
                    startId = newStartId,
                    count = 50
                )
                preKeyApiClient.uploadPreKeyBundle(serverUrl, userCode, bundleDto)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.w("Calypso", "PreKey replenishment failed: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Checks if the local Signed PreKey has expired (> 7 days) and rotates it if needed.
     */
    private suspend fun rotateSignedPreKeyIfNeeded(serverUrl: String, userCode: String) {
        try {
            if (signalCryptoManager.shouldRotateSignedPreKey()) {
                val newSignedPreKey = signalCryptoManager.rotateSignedPreKey()
                val identity = _currentIdentity.value ?: return
                val identityKeyB64 = java.util.Base64.getEncoder().encodeToString(identity.publicKeyBytes)
                preKeyApiClient.updateSignedPreKey(serverUrl, userCode, identityKeyB64, newSignedPreKey)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.w("Calypso", "Signed PreKey rotation failed: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Observes message history for a given conversation.
     */
    fun getMessages(userCode: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(userCode)

    /**
     * Observes metadata for a single conversation.
     */
    fun getConversation(userCode: String): Flow<ConversationEntity?> =
        conversationDao.getConversationFlow(userCode)

    /**
     * Sends an encrypted message to [recipientUserCode].
     * Routes over direct WebRTC DataChannel if open, or falls back to ephemeral signaling relay.
     */
    suspend fun sendMessage(recipientUserCode: String, content: String): Result<MessageEntity> {
        val myIdentity = _currentIdentity.value
            ?: return Result.failure(IllegalStateException("No active identity initialized"))

        val serverUrl = securePreferences.getSignalingUrl()

        return try {
            // 1. Ensure a Signal Double Ratchet session exists
            if (!signalCryptoManager.hasSession(recipientUserCode)) {
                val fetchResult = preKeyApiClient.fetchPreKeyBundle(serverUrl, recipientUserCode)
                if (fetchResult.isFailure) {
                    return Result.failure(
                        fetchResult.exceptionOrNull()
                            ?: Exception("Failed to fetch PreKey bundle for $recipientUserCode")
                    )
                }
                val remoteBundle = fetchResult.getOrThrow()
                signalCryptoManager.buildSession(recipientUserCode, remoteBundle)
            }

            // 2. Encrypt plaintext into EncryptedEnvelope
            val messageId = UUID.randomUUID().toString()
            val envelope = signalCryptoManager.encryptMessage(
                senderUserCode = myIdentity.userCode,
                recipientUserCode = recipientUserCode,
                messageId = messageId,
                plaintext = content
            )
            val envelopeJson = json.encodeToString(envelope)

            // 3. Dispatch message: WebRTC DataChannel (Primary) -> Signaling Relay (Fallback)
            val sentDirectly = if (webRtcManager.isDataChannelOpen(recipientUserCode)) {
                webRtcManager.sendData(recipientUserCode, envelopeJson.toByteArray(StandardCharsets.UTF_8))
            } else {
                false
            }

            if (!sentDirectly) {
                // Fallback: Ephemeral encrypted relay
                signalingClient.sendEncryptedEnvelope(recipientUserCode, envelopeJson)
                // Proactively attempt to initiate WebRTC P2P DataChannel for subsequent messages
                initiateP2PConnection(recipientUserCode)
            }

            // 4. Save to local SQLCipher Room database
            val messageEntity = MessageEntity(
                id = messageId,
                conversationUserCode = recipientUserCode,
                senderUserCode = myIdentity.userCode,
                recipientUserCode = recipientUserCode,
                content = content,
                timestamp = envelope.timestamp,
                status = MessageEntity.STATUS_SENT,
                isOutgoing = true
            )
            messageDao.insertMessage(messageEntity)

            // 5. Update or insert conversation record
            val existingConv = conversationDao.getConversation(recipientUserCode)
            if (existingConv == null) {
                conversationDao.insertOrUpdate(
                    ConversationEntity(
                        userCode = recipientUserCode,
                        lastMessage = content,
                        lastMessageTimestamp = envelope.timestamp,
                        unreadCount = 0
                    )
                )
            } else {
                conversationDao.updateLastMessage(
                    userCode = recipientUserCode,
                    lastMessage = content,
                    timestamp = envelope.timestamp
                )
            }

            Result.success(messageEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Handles incoming serialized envelope payload.
     */
    suspend fun handleIncomingEnvelopeString(envelopeJson: String) {
        val myIdentity = _currentIdentity.value ?: return

        try {
            val envelope = json.decodeFromString<EncryptedEnvelope>(envelopeJson)

            if (envelope.type == EncryptedEnvelope.TYPE_DELIVERY_ACK) {
                // Sender received our message ACK
                val targetMsgId = if (envelope.ciphertext.isNotBlank()) {
                    try {
                        signalCryptoManager.decryptMessage(envelope)
                    } catch (_: Exception) {
                        envelope.messageId
                    }
                } else {
                    envelope.messageId
                }
                messageDao.updateStatus(targetMsgId, MessageEntity.STATUS_DELIVERED)
                return
            }

            if (envelope.type == EncryptedEnvelope.TYPE_READ_RECEIPT) {
                // Peer read our message
                val targetMsgId = if (envelope.ciphertext.isNotBlank()) {
                    try {
                        signalCryptoManager.decryptMessage(envelope)
                    } catch (_: Exception) {
                        envelope.messageId
                    }
                } else {
                    envelope.messageId
                }
                messageDao.updateStatus(targetMsgId, MessageEntity.STATUS_READ)
                return
            }

            // Decrypt message
            val decryptedPlaintext = signalCryptoManager.decryptMessage(envelope)

            // Save decrypted message to local Room database
            val messageEntity = MessageEntity(
                id = envelope.messageId,
                conversationUserCode = envelope.senderUserCode,
                senderUserCode = envelope.senderUserCode,
                recipientUserCode = myIdentity.userCode,
                content = decryptedPlaintext,
                timestamp = envelope.timestamp,
                status = MessageEntity.STATUS_DELIVERED,
                isOutgoing = false
            )
            messageDao.insertMessage(messageEntity)

            // Update conversation entry
            val existingConv = conversationDao.getConversation(envelope.senderUserCode)
            if (existingConv == null) {
                conversationDao.insertOrUpdate(
                    ConversationEntity(
                        userCode = envelope.senderUserCode,
                        lastMessage = decryptedPlaintext,
                        lastMessageTimestamp = envelope.timestamp,
                        unreadCount = 1
                    )
                )
            } else {
                conversationDao.updateLastMessage(
                    userCode = envelope.senderUserCode,
                    lastMessage = decryptedPlaintext,
                    timestamp = envelope.timestamp,
                    unreadIncrement = 1
                )
            }

            // Send delivery ACK back to sender
            sendDeliveryAck(envelope.senderUserCode, envelope.messageId)
        } catch (e: UntrustedIdentityException) {
            // Security (F4): a peer's identity key changed. Reject the message and alert the UI.
            // Do NOT log the exception details — they may contain key bytes.
            // The peer's userCode is obtained from the envelope, not from the exception.
            try {
                val envelope = json.decodeFromString<EncryptedEnvelope>(envelopeJson)
                _securityEvents.tryEmit(SecurityEvent.IdentityChanged(envelope.senderUserCode))
            } catch (_: Exception) { /* envelope unparseable — emit a generic event */ }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.w("Calypso", "Incoming envelope handling failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun sendDeliveryAck(targetUserCode: String, messageId: String) {
        val myIdentity = _currentIdentity.value ?: return
        try {
            val ackEnvelope = if (signalCryptoManager.hasSession(targetUserCode)) {
                signalCryptoManager.encryptControlMessage(
                    senderUserCode = myIdentity.userCode,
                    recipientUserCode = targetUserCode,
                    envelopeMessageId = UUID.randomUUID().toString(),
                    payload = messageId,
                    type = EncryptedEnvelope.TYPE_DELIVERY_ACK
                )
            } else {
                EncryptedEnvelope(
                    type = EncryptedEnvelope.TYPE_DELIVERY_ACK,
                    senderUserCode = myIdentity.userCode,
                    recipientUserCode = targetUserCode,
                    messageId = messageId,
                    ciphertext = "",
                    timestamp = System.currentTimeMillis()
                )
            }
            val ackJson = json.encodeToString(ackEnvelope)

            if (webRtcManager.isDataChannelOpen(targetUserCode)) {
                webRtcManager.sendData(targetUserCode, ackJson.toByteArray(StandardCharsets.UTF_8))
            } else {
                signalingClient.sendEncryptedEnvelope(targetUserCode, ackJson)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.w("Calypso", "sendDeliveryAck failed: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Sends an encrypted read receipt for a delivered message.
     */
    fun sendReadReceipt(targetUserCode: String, messageId: String) {
        val myIdentity = _currentIdentity.value ?: return
        scope.launch {
            try {
                if (!signalCryptoManager.hasSession(targetUserCode)) return@launch
                val readEnvelope = signalCryptoManager.encryptControlMessage(
                    senderUserCode = myIdentity.userCode,
                    recipientUserCode = targetUserCode,
                    envelopeMessageId = UUID.randomUUID().toString(),
                    payload = messageId,
                    type = EncryptedEnvelope.TYPE_READ_RECEIPT
                )
                val readJson = json.encodeToString(readEnvelope)

                if (webRtcManager.isDataChannelOpen(targetUserCode)) {
                    webRtcManager.sendData(targetUserCode, readJson.toByteArray(StandardCharsets.UTF_8))
                } else {
                    signalingClient.sendEncryptedEnvelope(targetUserCode, readJson)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.w("Calypso", "sendReadReceipt failed: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    /**
     * Initiates WebRTC SDP offer creation and signaling dispatch.
     */
    fun initiateP2PConnection(targetUserCode: String) {
        webRtcManager.setReconnectCallback(targetUserCode) {
            initiateP2PConnection(targetUserCode)
        }
        scope.launch {
            try {
                val offer = webRtcManager.createOffer(targetUserCode) { candidate ->
                    signalingClient.sendIceCandidate(
                        targetUserCode = targetUserCode,
                        candidate = candidate.sdp,
                        sdpMid = candidate.sdpMid ?: "0",
                        sdpMLineIndex = candidate.sdpMLineIndex
                    )
                }
                signalingClient.sendOffer(targetUserCode, offer.description, "offer")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.w("Calypso", "initiateP2PConnection failed: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    private fun handleIncomingOffer(offer: SignalingOffer) {
        scope.launch {
            try {
                val answer = webRtcManager.createAnswer(offer.fromUserCode, offer.sdp) { candidate ->
                    signalingClient.sendIceCandidate(
                        targetUserCode = offer.fromUserCode,
                        candidate = candidate.sdp,
                        sdpMid = candidate.sdpMid ?: "0",
                        sdpMLineIndex = candidate.sdpMLineIndex
                    )
                }
                signalingClient.sendAnswer(offer.fromUserCode, answer.description, "answer")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.w("Calypso", "handleIncomingOffer failed: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    private fun handleIncomingAnswer(answer: SignalingAnswer) {
        scope.launch {
            try {
                webRtcManager.setRemoteAnswer(answer.fromUserCode, answer.sdp)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.w("Calypso", "handleIncomingAnswer failed: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    private fun handleIncomingIceCandidate(cand: SignalingIceCandidate) {
        val iceCandidate = IceCandidate(cand.sdpMid, cand.sdpMLineIndex, cand.candidate)
        webRtcManager.addIceCandidate(cand.fromUserCode, iceCandidate)
    }

    /**
     * Marks all unread messages in a conversation as read.
     */
    suspend fun markConversationAsRead(userCode: String) {
        conversationDao.markAsRead(userCode)
        val recentMessages = messageDao.getRecentMessages(userCode, limit = 10)
        val latestIncoming = recentMessages.firstOrNull { !it.isOutgoing }
        if (latestIncoming != null) {
            sendReadReceipt(userCode, latestIncoming.id)
        }
    }

    /**
     * Deletes a conversation, its message history, and closes any open P2P channel.
     */
    suspend fun deleteConversation(userCode: String) {
        conversationDao.deleteConversation(userCode)
        messageDao.deleteMessagesForConversation(userCode)
        webRtcManager.closePeerConnection(userCode)
    }

    /**
     * Destroys all local session state and clears identity.
     */
    suspend fun resetAll() {
        webRtcManager.closeAll()
        signalingClient.disconnect()
        conversationDao.clearAllConversations()
        messageDao.clearAllMessages()
        securePreferences.clearIdentity()
        _currentIdentity.value = null
    }
}

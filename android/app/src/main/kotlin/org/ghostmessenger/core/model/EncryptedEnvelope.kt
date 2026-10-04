package org.ghostmessenger.core.model

import kotlinx.serialization.Serializable

/**
 * Universal wire format for messages transmitted over WebRTC DataChannels
 * and Ephemeral Signaling Relays.
 *
 * PRIVACY NOTE:
 * - [ciphertext]: Contains Signal Protocol end-to-end encrypted payload (content, ACK, or Read Receipt).
 *   The server NEVER has access to the plaintext or cryptographic keys.
 * - [senderUserCode] & [recipientUserCode]: Required for routing and for WebRTC DataChannels
 *   which operate peer-to-peer without server context.
 * - [messageId]: Random UUID used solely for deduplication and status tracking.
 * - [timestamp]: Monotonic timestamp used for causal ordering of message history.
 */
@Serializable
data class EncryptedEnvelope(
    val type: Int,
    val senderUserCode: String,
    val recipientUserCode: String,
    val messageId: String,
    val ciphertext: String, // Base64 encoded Signal-encrypted payload
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_PREKEY_SIGNAL_MESSAGE = 1 // X3DH Initial Handshake Message
        const val TYPE_SIGNAL_MESSAGE = 2        // Double Ratchet Message
        const val TYPE_DELIVERY_ACK = 3          // Delivery confirmation
        const val TYPE_READ_RECEIPT = 4          // Read confirmation
    }
}

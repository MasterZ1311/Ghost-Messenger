package org.ghostmessenger.core.model

import kotlinx.serialization.Serializable

/**
 * Universal wire format for messages transmitted over WebRTC DataChannels
 * and Ephemeral Signaling Relays.
 */
@Serializable
data class EncryptedEnvelope(
    val type: Int,
    val senderUserCode: String,
    val recipientUserCode: String,
    val messageId: String,
    val ciphertext: String, // Base64 encoded payload / messageId for ACKs
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_PREKEY_SIGNAL_MESSAGE = 1 // X3DH Initial Handshake Message
        const val TYPE_SIGNAL_MESSAGE = 2        // Double Ratchet Message
        const val TYPE_DELIVERY_ACK = 3          // Delivery confirmation
        const val TYPE_READ_RECEIPT = 4          // Read confirmation
    }
}

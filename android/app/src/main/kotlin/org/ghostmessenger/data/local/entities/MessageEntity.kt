package org.ghostmessenger.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single encrypted/decrypted message in a conversation.
 * Note: Stored in plaintext inside SQLCipher AES-256 encrypted database.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationUserCode"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationUserCode: String,
    val senderUserCode: String,
    val recipientUserCode: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = STATUS_SENT,
    val isOutgoing: Boolean
) {
    companion object {
        const val STATUS_SENDING = "SENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_DELIVERED = "DELIVERED"
        const val STATUS_READ = "READ"
        const val STATUS_FAILED = "FAILED"
    }
}

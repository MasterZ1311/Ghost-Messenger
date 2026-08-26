package org.ghostmessenger.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a direct peer-to-peer conversation with another user.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val userCode: String,
    val lastMessage: String? = null,
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

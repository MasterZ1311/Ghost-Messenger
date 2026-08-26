package org.ghostmessenger.data.local

import org.ghostmessenger.data.local.entities.ConversationEntity
import org.ghostmessenger.data.local.entities.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ConversationAndMessageTest {

    @Test
    fun testConversationEntityCreationAndDefaults() {
        val now = System.currentTimeMillis()
        val conv = ConversationEntity(
            userCode = "5JKL-2P4X",
            lastMessage = "Hello Ghost",
            lastMessageTimestamp = now,
            unreadCount = 2,
            isVerified = true
        )

        assertEquals("5JKL-2P4X", conv.userCode)
        assertEquals("Hello Ghost", conv.lastMessage)
        assertEquals(now, conv.lastMessageTimestamp)
        assertEquals(2, conv.unreadCount)
        assertTrue(conv.isVerified)

        val defaultConv = ConversationEntity(userCode = "WXYZ-2345")
        assertNull(defaultConv.lastMessage)
        assertEquals(0, defaultConv.unreadCount)
        assertFalse(defaultConv.isVerified)
    }

    @Test
    fun testMessageEntityStatuses() {
        val id = UUID.randomUUID().toString()
        val message = MessageEntity(
            id = id,
            conversationUserCode = "5JKL-2P4X",
            senderUserCode = "AAAA-BBBB",
            recipientUserCode = "5JKL-2P4X",
            content = "Encrypted message content",
            status = MessageEntity.STATUS_SENT,
            isOutgoing = true
        )

        assertEquals(id, message.id)
        assertEquals("5JKL-2P4X", message.conversationUserCode)
        assertEquals("AAAA-BBBB", message.senderUserCode)
        assertEquals("5JKL-2P4X", message.recipientUserCode)
        assertEquals("Encrypted message content", message.content)
        assertEquals(MessageEntity.STATUS_SENT, message.status)
        assertTrue(message.isOutgoing)

        val updated = message.copy(status = MessageEntity.STATUS_READ)
        assertEquals(MessageEntity.STATUS_READ, updated.status)
    }
}

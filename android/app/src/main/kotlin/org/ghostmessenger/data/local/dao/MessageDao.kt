package org.ghostmessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.ghostmessenger.data.local.entities.MessageEntity

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationUserCode = :userCode ORDER BY timestamp ASC")
    fun getMessagesForConversation(userCode: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationUserCode = :userCode ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(userCode: String, limit: Int = 50): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE conversationUserCode = :userCode")
    suspend fun deleteMessagesForConversation(userCode: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}

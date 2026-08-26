package org.ghostmessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.ghostmessenger.data.local.entities.ConversationEntity

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE userCode = :userCode LIMIT 1")
    suspend fun getConversation(userCode: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE userCode = :userCode LIMIT 1")
    fun getConversationFlow(userCode: String): Flow<ConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET lastMessage = :lastMessage, lastMessageTimestamp = :timestamp, unreadCount = unreadCount + :unreadIncrement WHERE userCode = :userCode")
    suspend fun updateLastMessage(
        userCode: String,
        lastMessage: String,
        timestamp: Long = System.currentTimeMillis(),
        unreadIncrement: Int = 0
    )

    @Query("UPDATE conversations SET unreadCount = 0 WHERE userCode = :userCode")
    suspend fun markAsRead(userCode: String)

    @Query("UPDATE conversations SET isVerified = :isVerified WHERE userCode = :userCode")
    suspend fun setVerified(userCode: String, isVerified: Boolean)

    @Query("DELETE FROM conversations WHERE userCode = :userCode")
    suspend fun deleteConversation(userCode: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()
}

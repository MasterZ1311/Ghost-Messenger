package org.ghostmessenger.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.ghostmessenger.data.local.dao.ConversationDao
import org.ghostmessenger.data.local.dao.MessageDao
import org.ghostmessenger.data.local.dao.SignalIdentityDao
import org.ghostmessenger.data.local.dao.SignalKyberPreKeyDao
import org.ghostmessenger.data.local.dao.SignalPreKeyDao
import org.ghostmessenger.data.local.dao.SignalSessionDao
import org.ghostmessenger.data.local.dao.SignalSignedPreKeyDao
import org.ghostmessenger.data.local.entities.ConversationEntity
import org.ghostmessenger.data.local.entities.MessageEntity
import org.ghostmessenger.data.local.entities.SignalIdentityEntity
import org.ghostmessenger.data.local.entities.SignalKyberPreKeyEntity
import org.ghostmessenger.data.local.entities.SignalPreKeyEntity
import org.ghostmessenger.data.local.entities.SignalSessionEntity
import org.ghostmessenger.data.local.entities.SignalSignedPreKeyEntity

/**
 * Main Room Database for Ghost Messenger.
 * Fully encrypted at rest via SQLCipher (AES-256-CBC).
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        SignalIdentityEntity::class,
        SignalPreKeyEntity::class,
        SignalSignedPreKeyEntity::class,
        SignalSessionEntity::class,
        SignalKyberPreKeyEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun signalIdentityDao(): SignalIdentityDao
    abstract fun signalPreKeyDao(): SignalPreKeyDao
    abstract fun signalSignedPreKeyDao(): SignalSignedPreKeyDao
    abstract fun signalSessionDao(): SignalSessionDao
    abstract fun signalKyberPreKeyDao(): SignalKyberPreKeyDao

    companion object {
        const val DATABASE_NAME = "ghost_messenger.db"

        /**
         * Creates an encrypted instance of [AppDatabase] using SQLCipher.
         */
        fun buildEncrypted(context: Context, passphrase: ByteArray): AppDatabase {
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Creates an in-memory database instance (primarily for unit and integration testing).
         */
        fun buildInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }
}

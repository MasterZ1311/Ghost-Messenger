package org.ghostmessenger.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.ghostmessenger.data.local.dao.ConversationDao
import org.ghostmessenger.data.local.dao.MessageDao
import org.ghostmessenger.data.local.dao.SignalIdentityDao
import org.ghostmessenger.data.local.dao.SignalKyberPreKeyDao
import org.ghostmessenger.data.local.dao.SignalPreKeyDao
import org.ghostmessenger.data.local.dao.SignalSenderKeyDao
import org.ghostmessenger.data.local.dao.SignalSessionDao
import org.ghostmessenger.data.local.dao.SignalSignedPreKeyDao
import org.ghostmessenger.data.local.entities.ConversationEntity
import org.ghostmessenger.data.local.entities.MessageEntity
import org.ghostmessenger.data.local.entities.SignalIdentityEntity
import org.ghostmessenger.data.local.entities.SignalKyberPreKeyEntity
import org.ghostmessenger.data.local.entities.SignalPreKeyEntity
import org.ghostmessenger.data.local.entities.SignalSenderKeyEntity
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
        SignalKyberPreKeyEntity::class,
        SignalSenderKeyEntity::class   // F8: persisted SenderKeys (was in-memory only)
    ],
    version = 2,   // Bumped: added signal_sender_keys table. Add MIGRATION_1_2 before releasing.
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
    abstract fun signalSenderKeyDao(): SignalSenderKeyDao

    companion object {
        const val DATABASE_NAME = "ghost_messenger.db"

        init {
            try {
                System.loadLibrary("sqlcipher")
            } catch (_: Throwable) {
                // Already loaded by GhostMessengerApp or testing environment
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `signal_sender_keys` (
                        `senderName` TEXT NOT NULL,
                        `deviceId` INTEGER NOT NULL,
                        `distributionId` TEXT NOT NULL,
                        `recordBytes` BLOB NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`senderName`, `deviceId`, `distributionId`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Creates an encrypted instance of [AppDatabase] using SQLCipher.
         */
        fun buildEncrypted(context: Context, passphrase: ByteArray): AppDatabase {
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                // Security (F6): Never use fallbackToDestructiveMigration() in production.
                // A destructive migration silently wipes all Signal sessions, trust anchors,
                // and message history. All future schema changes MUST provide an explicit
                // Migration object added here via .addMigrations(MIGRATION_X_Y).
                .build()
        }

        /**
         * Creates an in-memory database instance (primarily for unit and integration testing).
         */
        fun buildInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}

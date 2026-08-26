package org.ghostmessenger.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.ghostmessenger.data.local.dao.ConversationDao
import org.ghostmessenger.data.local.dao.MessageDao
import org.ghostmessenger.data.local.dao.SignalIdentityDao
import org.ghostmessenger.data.local.dao.SignalKyberPreKeyDao
import org.ghostmessenger.data.local.dao.SignalPreKeyDao
import org.ghostmessenger.data.local.dao.SignalSessionDao
import org.ghostmessenger.data.local.dao.SignalSignedPreKeyDao
import org.ghostmessenger.data.local.db.AppDatabase
import org.ghostmessenger.data.local.prefs.SecurePreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        securePreferences: SecurePreferences
    ): AppDatabase {
        val passphrase = securePreferences.getOrGenerateDbPassphrase()
        return AppDatabase.buildEncrypted(context, passphrase)
    }

    @Provides
    @Singleton
    fun provideConversationDao(database: AppDatabase): ConversationDao =
        database.conversationDao()

    @Provides
    @Singleton
    fun provideMessageDao(database: AppDatabase): MessageDao =
        database.messageDao()

    @Provides
    @Singleton
    fun provideSignalIdentityDao(database: AppDatabase): SignalIdentityDao =
        database.signalIdentityDao()

    @Provides
    @Singleton
    fun provideSignalPreKeyDao(database: AppDatabase): SignalPreKeyDao =
        database.signalPreKeyDao()

    @Provides
    @Singleton
    fun provideSignalSignedPreKeyDao(database: AppDatabase): SignalSignedPreKeyDao =
        database.signalSignedPreKeyDao()

    @Provides
    @Singleton
    fun provideSignalSessionDao(database: AppDatabase): SignalSessionDao =
        database.signalSessionDao()

    @Provides
    @Singleton
    fun provideSignalKyberPreKeyDao(database: AppDatabase): SignalKyberPreKeyDao =
        database.signalKyberPreKeyDao()
}

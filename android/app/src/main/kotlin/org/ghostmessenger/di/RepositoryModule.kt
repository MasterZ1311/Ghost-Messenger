package org.ghostmessenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ghostmessenger.core.model.Identity
import org.ghostmessenger.data.crypto.SqliteSignalProtocolStore
import org.ghostmessenger.data.local.dao.SignalIdentityDao
import org.ghostmessenger.data.local.dao.SignalKyberPreKeyDao
import org.ghostmessenger.data.local.dao.SignalPreKeyDao
import org.ghostmessenger.data.local.dao.SignalSenderKeyDao
import org.ghostmessenger.data.local.dao.SignalSessionDao
import org.ghostmessenger.data.local.dao.SignalSignedPreKeyDao
import org.ghostmessenger.data.local.prefs.SecurePreferences
import org.signal.libsignal.protocol.state.SignalProtocolStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSignalProtocolStore(
        securePreferences: SecurePreferences,
        identityDao: SignalIdentityDao,
        preKeyDao: SignalPreKeyDao,
        signedPreKeyDao: SignalSignedPreKeyDao,
        sessionDao: SignalSessionDao,
        kyberPreKeyDao: SignalKyberPreKeyDao,
        senderKeyDao: SignalSenderKeyDao
    ): SignalProtocolStore {
        // Security: never silently create an ephemeral identity.
        // If getIdentity() returns null, the user has not completed onboarding yet.
        // The caller must ensure onboarding is finished before any crypto operations.
        val identityProvider: () -> Identity = {
            securePreferences.getIdentity()
                ?: throw IllegalStateException(
                    "No persisted identity found. Complete onboarding before performing crypto operations."
                )
        }

        return SqliteSignalProtocolStore(
            localIdentityProvider = identityProvider,
            identityDao = identityDao,
            preKeyDao = preKeyDao,
            signedPreKeyDao = signedPreKeyDao,
            sessionDao = sessionDao,
            kyberPreKeyDao = kyberPreKeyDao,
            senderKeyDao = senderKeyDao
        )
    }
}

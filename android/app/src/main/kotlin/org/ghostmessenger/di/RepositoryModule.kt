package org.ghostmessenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ghostmessenger.core.crypto.KeyManager
import org.ghostmessenger.core.model.Identity
import org.ghostmessenger.data.crypto.SqliteSignalProtocolStore
import org.ghostmessenger.data.local.dao.SignalIdentityDao
import org.ghostmessenger.data.local.dao.SignalKyberPreKeyDao
import org.ghostmessenger.data.local.dao.SignalPreKeyDao
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
        kyberPreKeyDao: SignalKyberPreKeyDao
    ): SignalProtocolStore {
        var fallbackIdentity: Identity? = null
        val identityProvider: () -> Identity = {
            securePreferences.getIdentity() ?: run {
                if (fallbackIdentity == null) {
                    fallbackIdentity = KeyManager.createRandomIdentity()
                }
                fallbackIdentity!!
            }
        }

        return SqliteSignalProtocolStore(
            localIdentityProvider = identityProvider,
            identityDao = identityDao,
            preKeyDao = preKeyDao,
            signedPreKeyDao = signedPreKeyDao,
            sessionDao = sessionDao,
            kyberPreKeyDao = kyberPreKeyDao
        )
    }
}

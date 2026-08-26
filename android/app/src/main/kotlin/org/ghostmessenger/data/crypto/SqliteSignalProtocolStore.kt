package org.ghostmessenger.data.crypto

import org.ghostmessenger.core.model.Identity
import org.ghostmessenger.data.local.dao.SignalIdentityDao
import org.ghostmessenger.data.local.dao.SignalKyberPreKeyDao
import org.ghostmessenger.data.local.dao.SignalPreKeyDao
import org.ghostmessenger.data.local.dao.SignalSessionDao
import org.ghostmessenger.data.local.dao.SignalSignedPreKeyDao
import org.ghostmessenger.data.local.entities.SignalIdentityEntity
import org.ghostmessenger.data.local.entities.SignalKyberPreKeyEntity
import org.ghostmessenger.data.local.entities.SignalPreKeyEntity
import org.ghostmessenger.data.local.entities.SignalSessionEntity
import org.ghostmessenger.data.local.entities.SignalSignedPreKeyEntity
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Room-backed, encrypted implementation of the official [SignalProtocolStore].
 *
 * Persists:
 * - Local identity keypair & registration ID
 * - Contact identity keys (TOFU - Trust On First Use)
 * - Curve25519 one-time PreKeys
 * - Curve25519 Signed PreKeys
 * - Double Ratchet Session Records
 * - Post-Quantum Kyber PreKeys
 */
class SqliteSignalProtocolStore(
    private val localIdentityProvider: () -> Identity,
    private val identityDao: SignalIdentityDao,
    private val preKeyDao: SignalPreKeyDao,
    private val signedPreKeyDao: SignalSignedPreKeyDao,
    private val sessionDao: SignalSessionDao,
    private val kyberPreKeyDao: SignalKyberPreKeyDao
) : SignalProtocolStore {

    constructor(
        localIdentity: Identity,
        identityDao: SignalIdentityDao,
        preKeyDao: SignalPreKeyDao,
        signedPreKeyDao: SignalSignedPreKeyDao,
        sessionDao: SignalSessionDao,
        kyberPreKeyDao: SignalKyberPreKeyDao
    ) : this(
        localIdentityProvider = { localIdentity },
        identityDao = identityDao,
        preKeyDao = preKeyDao,
        signedPreKeyDao = signedPreKeyDao,
        sessionDao = sessionDao,
        kyberPreKeyDao = kyberPreKeyDao
    )

    private val senderKeys = ConcurrentHashMap<String, SenderKeyRecord>()

    // ==========================================
    // IdentityKeyStore Implementation
    // ==========================================

    override fun getIdentityKeyPair(): IdentityKeyPair = localIdentityProvider().identityKeyPair

    override fun getLocalRegistrationId(): Int = localIdentityProvider().registrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val existing = identityDao.getIdentity(address.name)
        return if (existing == null || !existing.publicKeyBytes.contentEquals(identityKey.serialize())) {
            identityDao.insertIdentity(SignalIdentityEntity(address.name, identityKey.serialize()))
            true
        } else {
            false
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        val existing = identityDao.getIdentity(address.name) ?: return true // Trust On First Use (TOFU)
        return existing.publicKeyBytes.contentEquals(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val entity = identityDao.getIdentity(address.name) ?: return null
        return try {
            IdentityKey(entity.publicKeyBytes)
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // PreKeyStore Implementation
    // ==========================================

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val entity = preKeyDao.getPreKey(preKeyId)
            ?: throw InvalidKeyIdException("PreKey with ID $preKeyId not found")
        return PreKeyRecord(entity.recordBytes)
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeyDao.insertPreKey(SignalPreKeyEntity(preKeyId, record.serialize()))
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return preKeyDao.containsPreKey(preKeyId)
    }

    override fun removePreKey(preKeyId: Int) {
        preKeyDao.deletePreKey(preKeyId)
    }

    // ==========================================
    // SignedPreKeyStore Implementation
    // ==========================================

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val entity = signedPreKeyDao.getSignedPreKey(signedPreKeyId)
            ?: throw InvalidKeyIdException("SignedPreKey with ID $signedPreKeyId not found")
        return SignedPreKeyRecord(entity.recordBytes)
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return signedPreKeyDao.getAllSignedPreKeys().map { SignedPreKeyRecord(it.recordBytes) }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeyDao.insertSignedPreKey(
            SignalSignedPreKeyEntity(signedPreKeyId, record.serialize(), record.timestamp)
        )
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return signedPreKeyDao.containsSignedPreKey(signedPreKeyId)
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeyDao.deleteSignedPreKey(signedPreKeyId)
    }

    // ==========================================
    // SessionStore Implementation
    // ==========================================

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val entity = sessionDao.getSession(address.name, address.deviceId)
        return if (entity != null) {
            try {
                SessionRecord(entity.recordBytes)
            } catch (_: Exception) {
                SessionRecord()
            }
        } else {
            SessionRecord()
        }
    }

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> {
        return addresses.map { loadSession(it) }
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        return sessionDao.getSubDeviceSessions(name)
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sessionDao.insertSession(
            SignalSessionEntity(
                addressName = address.name,
                deviceId = address.deviceId,
                recordBytes = record.serialize()
            )
        )
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return sessionDao.containsSession(address.name, address.deviceId)
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        sessionDao.deleteSession(address.name, address.deviceId)
    }

    override fun deleteAllSessions(name: String) {
        sessionDao.deleteAllSessionsForAddress(name)
    }

    // ==========================================
    // KyberPreKeyStore Implementation
    // ==========================================

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        val entity = kyberPreKeyDao.getKyberPreKey(kyberPreKeyId)
            ?: throw InvalidKeyIdException("KyberPreKey with ID $kyberPreKeyId not found")
        return KyberPreKeyRecord(entity.recordBytes)
    }

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
        return kyberPreKeyDao.getAllKyberPreKeys().map { KyberPreKeyRecord(it.recordBytes) }
    }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
        kyberPreKeyDao.insertKyberPreKey(
            SignalKyberPreKeyEntity(
                keyId = kyberPreKeyId,
                recordBytes = record.serialize(),
                timestamp = record.timestamp
            )
        )
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
        return kyberPreKeyDao.containsKyberPreKey(kyberPreKeyId)
    }

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int) {
        kyberPreKeyDao.markKyberPreKeyUsed(kyberPreKeyId)
    }

    // ==========================================
    // SenderKeyStore Implementation
    // ==========================================

    override fun storeSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID,
        record: SenderKeyRecord
    ) {
        val key = "${sender.name}_${sender.deviceId}_$distributionId"
        senderKeys[key] = record
    }

    override fun loadSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID
    ): SenderKeyRecord? {
        val key = "${sender.name}_${sender.deviceId}_$distributionId"
        return senderKeys[key]
    }
}

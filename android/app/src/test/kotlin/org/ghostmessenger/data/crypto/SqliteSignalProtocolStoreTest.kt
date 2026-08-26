package org.ghostmessenger.data.crypto

import org.ghostmessenger.core.crypto.KeyManager
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.nio.charset.StandardCharsets

class SqliteSignalProtocolStoreTest {

    private class MockIdentityDao : SignalIdentityDao {
        val map = mutableMapOf<String, SignalIdentityEntity>()
        override fun getIdentity(addressName: String) = map[addressName]
        override fun insertIdentity(identity: SignalIdentityEntity) { map[identity.addressName] = identity }
        override fun deleteIdentity(addressName: String) { map.remove(addressName) }
        override fun clearAll() { map.clear() }
    }

    private class MockPreKeyDao : SignalPreKeyDao {
        val map = mutableMapOf<Int, SignalPreKeyEntity>()
        override fun getPreKey(keyId: Int) = map[keyId]
        override fun insertPreKey(preKey: SignalPreKeyEntity) { map[preKey.keyId] = preKey }
        override fun insertPreKeys(preKeys: List<SignalPreKeyEntity>) { preKeys.forEach { insertPreKey(it) } }
        override fun containsPreKey(keyId: Int) = map.containsKey(keyId)
        override fun deletePreKey(keyId: Int) { map.remove(keyId) }
        override fun clearAll() { map.clear() }
    }

    private class MockSignedPreKeyDao : SignalSignedPreKeyDao {
        val map = mutableMapOf<Int, SignalSignedPreKeyEntity>()
        override fun getSignedPreKey(keyId: Int) = map[keyId]
        override fun getAllSignedPreKeys() = map.values.toList()
        override fun insertSignedPreKey(signedPreKey: SignalSignedPreKeyEntity) { map[signedPreKey.keyId] = signedPreKey }
        override fun containsSignedPreKey(keyId: Int) = map.containsKey(keyId)
        override fun deleteSignedPreKey(keyId: Int) { map.remove(keyId) }
        override fun clearAll() { map.clear() }
    }

    private class MockSessionDao : SignalSessionDao {
        val map = mutableMapOf<String, SignalSessionEntity>()
        private fun key(addressName: String, deviceId: Int) = "${addressName}_$deviceId"
        override fun getSession(addressName: String, deviceId: Int) = map[key(addressName, deviceId)]
        override fun getSessionsForAddress(addressName: String) = map.values.filter { it.addressName == addressName }
        override fun getSubDeviceSessions(addressName: String) = map.values.filter { it.addressName == addressName }.map { it.deviceId }
        override fun insertSession(session: SignalSessionEntity) { map[key(session.addressName, session.deviceId)] = session }
        override fun containsSession(addressName: String, deviceId: Int) = map.containsKey(key(addressName, deviceId))
        override fun deleteSession(addressName: String, deviceId: Int) { map.remove(key(addressName, deviceId)) }
        override fun deleteAllSessionsForAddress(addressName: String) {
            val toRemove = map.filter { it.value.addressName == addressName }.keys
            toRemove.forEach { map.remove(it) }
        }
        override fun clearAll() { map.clear() }
    }

    private class MockKyberPreKeyDao : SignalKyberPreKeyDao {
        val map = mutableMapOf<Int, SignalKyberPreKeyEntity>()
        override fun getKyberPreKey(keyId: Int) = map[keyId]
        override fun getAllKyberPreKeys() = map.values.toList()
        override fun insertKyberPreKey(kyberPreKey: SignalKyberPreKeyEntity) { map[kyberPreKey.keyId] = kyberPreKey }
        override fun insertKyberPreKeys(kyberPreKeys: List<SignalKyberPreKeyEntity>) { kyberPreKeys.forEach { insertKyberPreKey(it) } }
        override fun containsKyberPreKey(keyId: Int) = map.containsKey(keyId)
        override fun markKyberPreKeyUsed(keyId: Int) {
            map[keyId]?.let { map[keyId] = it.copy(isUsed = true) }
        }
        override fun deleteKyberPreKey(keyId: Int) { map.remove(keyId) }
        override fun clearAll() { map.clear() }
    }

    private fun createStore(): SqliteSignalProtocolStore {
        val identity = KeyManager.createRandomIdentity()
        return SqliteSignalProtocolStore(
            localIdentity = identity,
            identityDao = MockIdentityDao(),
            preKeyDao = MockPreKeyDao(),
            signedPreKeyDao = MockSignedPreKeyDao(),
            sessionDao = MockSessionDao(),
            kyberPreKeyDao = MockKyberPreKeyDao()
        )
    }

    @Test
    fun testIdentityKeyStoreOperations() {
        val store = createStore()
        val contactAddress = SignalProtocolAddress("5JKL-2P4X", 1)
        val contactIdentity = KeyManager.createRandomIdentity().publicKey

        // TOFU check initially trusted
        assertTrue(store.isTrustedIdentity(contactAddress, contactIdentity, IdentityKeyStore.Direction.RECEIVING))

        // Save identity
        assertTrue(store.saveIdentity(contactAddress, contactIdentity))
        assertFalse("Saving same identity again should return false", store.saveIdentity(contactAddress, contactIdentity))

        // Retrieved identity matches
        val loaded = store.getIdentity(contactAddress)
        assertNotNull(loaded)
        assertEquals(contactIdentity, loaded)
    }

    @Test
    fun testPreKeyOperations() {
        val store = createStore()
        val keyPair = Curve.generateKeyPair()
        val record = PreKeyRecord(101, keyPair)

        store.storePreKey(101, record)
        assertTrue(store.containsPreKey(101))

        val loaded = store.loadPreKey(101)
        assertEquals(101, loaded.id)
        assertArrayEquals(keyPair.publicKey.serialize(), loaded.keyPair.publicKey.serialize())

        store.removePreKey(101)
        assertFalse(store.containsPreKey(101))
        assertThrows(InvalidKeyIdException::class.java) {
            store.loadPreKey(101)
        }
    }

    @Test
    fun testSignedPreKeyOperations() {
        val store = createStore()
        val keyPair = Curve.generateKeyPair()
        val signature = Curve.calculateSignature(store.identityKeyPair.privateKey, keyPair.publicKey.serialize())
        val record = SignedPreKeyRecord(202, System.currentTimeMillis(), keyPair, signature)

        store.storeSignedPreKey(202, record)
        assertTrue(store.containsSignedPreKey(202))

        val loaded = store.loadSignedPreKey(202)
        assertEquals(202, loaded.id)
        assertArrayEquals(signature, loaded.signature)
        assertEquals(1, store.loadSignedPreKeys().size)

        store.removeSignedPreKey(202)
        assertFalse(store.containsSignedPreKey(202))
    }

    @Test
    fun testEndToEndSignalProtocolHandshakeWithSqliteStore() {
        // 1. Create Alice and Bob identities and stores
        val aliceIdentity = KeyManager.createRandomIdentity()
        val bobIdentity = KeyManager.createRandomIdentity()

        val aliceAddress = SignalProtocolAddress(aliceIdentity.userCode, 1)
        val bobAddress = SignalProtocolAddress(bobIdentity.userCode, 1)

        val aliceStore = SqliteSignalProtocolStore(
            localIdentity = aliceIdentity,
            identityDao = MockIdentityDao(),
            preKeyDao = MockPreKeyDao(),
            signedPreKeyDao = MockSignedPreKeyDao(),
            sessionDao = MockSessionDao(),
            kyberPreKeyDao = MockKyberPreKeyDao()
        )

        val bobStore = SqliteSignalProtocolStore(
            localIdentity = bobIdentity,
            identityDao = MockIdentityDao(),
            preKeyDao = MockPreKeyDao(),
            signedPreKeyDao = MockSignedPreKeyDao(),
            sessionDao = MockSessionDao(),
            kyberPreKeyDao = MockKyberPreKeyDao()
        )

        // 2. Bob publishes PreKey & SignedPreKey
        val preKeyId = 1
        val preKeyPair = Curve.generateKeyPair()
        val preKeyRecord = PreKeyRecord(preKeyId, preKeyPair)
        bobStore.storePreKey(preKeyId, preKeyRecord)

        val signedPreKeyId = 1
        val signedPreKeyPair = Curve.generateKeyPair()
        val signedPreKeySig = Curve.calculateSignature(bobIdentity.privateKey, signedPreKeyPair.publicKey.serialize())
        val signedPreKeyRecord = SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), signedPreKeyPair, signedPreKeySig)
        bobStore.storeSignedPreKey(signedPreKeyId, signedPreKeyRecord)

        // 3. Alice receives Bob's PreKeyBundle and builds a Double Ratchet session
        val bobBundle = PreKeyBundle(
            bobIdentity.registrationId,
            1, // deviceId
            preKeyId,
            preKeyPair.publicKey,
            signedPreKeyId,
            signedPreKeyPair.publicKey,
            signedPreKeySig,
            bobIdentity.publicKey
        )

        val aliceSessionBuilder = SessionBuilder(aliceStore, bobAddress)
        aliceSessionBuilder.process(bobBundle)

        assertTrue("Alice should now have an active session for Bob", aliceStore.containsSession(bobAddress))

        // 4. Alice encrypts initial message (X3DH handshake)
        val aliceCipher = SessionCipher(aliceStore, bobAddress)
        val secretMessage = "Ghost in the Wire: Zero Knowledge P2P Message"
        val ciphertext = aliceCipher.encrypt(secretMessage.toByteArray(StandardCharsets.UTF_8))

        assertTrue("First message must be a PreKeySignalMessage", ciphertext is PreKeySignalMessage)

        // 5. Bob receives and decrypts initial message
        val bobCipher = SessionCipher(bobStore, aliceAddress)
        val preKeySignalMessage = PreKeySignalMessage(ciphertext.serialize())
        val decryptedBytes = bobCipher.decrypt(preKeySignalMessage)
        val decryptedText = String(decryptedBytes, StandardCharsets.UTF_8)

        assertEquals("Decrypted message must match original plaintext exactly", secretMessage, decryptedText)
        assertTrue("Bob should now have an active session for Alice", bobStore.containsSession(aliceAddress))

        // 6. Bob replies using standard Double Ratchet message
        val replyMessage = "Echo from the Shadows: Received loud and clear!"
        val replyCiphertext = bobCipher.encrypt(replyMessage.toByteArray(StandardCharsets.UTF_8))

        assertTrue("Subsequent messages are standard SignalMessages", replyCiphertext is SignalMessage)

        // 7. Alice receives and decrypts Bob's reply
        val aliceDecryptedBytes = aliceCipher.decrypt(SignalMessage(replyCiphertext.serialize()))
        val aliceDecryptedText = String(aliceDecryptedBytes, StandardCharsets.UTF_8)

        assertEquals("Alice decrypted Bob's reply exactly", replyMessage, aliceDecryptedText)
    }
}

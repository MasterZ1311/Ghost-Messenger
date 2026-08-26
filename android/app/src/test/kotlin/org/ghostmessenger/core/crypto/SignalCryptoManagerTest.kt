package org.ghostmessenger.core.crypto

import org.ghostmessenger.core.model.EncryptedEnvelope
import org.ghostmessenger.data.crypto.SqliteSignalProtocolStore
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
import org.ghostmessenger.data.network.model.PreKeyFetchBundleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SignalCryptoManagerTest {

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

    private fun createManager(): Pair<SignalCryptoManager, org.ghostmessenger.core.model.Identity> {
        val identity = KeyManager.createRandomIdentity()
        val store = SqliteSignalProtocolStore(
            localIdentity = identity,
            identityDao = MockIdentityDao(),
            preKeyDao = MockPreKeyDao(),
            signedPreKeyDao = MockSignedPreKeyDao(),
            sessionDao = MockSessionDao(),
            kyberPreKeyDao = MockKyberPreKeyDao()
        )
        return SignalCryptoManager(store) to identity
    }

    @Test
    fun testPreKeyBatchGenerationAndPackaging() {
        val (manager, identity) = createManager()
        val bundleDto = manager.generateAndStorePreKeys(startId = 1, count = 25)

        assertNotNull(bundleDto.identityKey)
        assertEquals(identity.registrationId, bundleDto.registrationId)
        assertEquals(1, bundleDto.signedPreKey.keyId)
        assertNotNull(bundleDto.signedPreKey.publicKey)
        assertNotNull(bundleDto.signedPreKey.signature)
        assertEquals(25, bundleDto.preKeys.size)
        assertEquals(1, bundleDto.preKeys.first().keyId)
        assertEquals(25, bundleDto.preKeys.last().keyId)
    }

    @Test
    fun testFullX3DHAndDoubleRatchetRoundtrip() {
        val (aliceManager, aliceIdentity) = createManager()
        val (bobManager, bobIdentity) = createManager()

        // 1. Bob generates PreKeys and signed prekey
        val bobBundleDto = bobManager.generateAndStorePreKeys(startId = 1, count = 10)

        // 2. Alice fetches Bob's bundle (simulate server response with 1 one-time prekey)
        val bobFetchBundle = PreKeyFetchBundleDto(
            identityKey = bobBundleDto.identityKey,
            registrationId = bobBundleDto.registrationId,
            signedPreKey = bobBundleDto.signedPreKey,
            preKey = bobBundleDto.preKeys.first(),
            remainingPreKeys = 9
        )

        assertFalse(aliceManager.hasSession(bobIdentity.userCode))
        aliceManager.buildSession(bobIdentity.userCode, bobFetchBundle)
        assertTrue(aliceManager.hasSession(bobIdentity.userCode))

        // 3. Alice encrypts initial message
        val msg1 = "Ghost Protocol: Message 1 from Alice"
        val msg1Id = UUID.randomUUID().toString()
        val envelope1 = aliceManager.encryptMessage(
            senderUserCode = aliceIdentity.userCode,
            recipientUserCode = bobIdentity.userCode,
            messageId = msg1Id,
            plaintext = msg1
        )

        assertEquals(EncryptedEnvelope.TYPE_PREKEY_SIGNAL_MESSAGE, envelope1.type)
        assertEquals(aliceIdentity.userCode, envelope1.senderUserCode)
        assertEquals(bobIdentity.userCode, envelope1.recipientUserCode)
        assertEquals(msg1Id, envelope1.messageId)

        // 4. Bob decrypts initial message
        val decrypted1 = bobManager.decryptMessage(envelope1)
        assertEquals(msg1, decrypted1)
        assertTrue("Bob should now have active session for Alice", bobManager.hasSession(aliceIdentity.userCode))

        // 5. Bob replies (Double Ratchet)
        val msg2 = "Ghost Protocol: Message 2 from Bob (Ratchet response)"
        val msg2Id = UUID.randomUUID().toString()
        val envelope2 = bobManager.encryptMessage(
            senderUserCode = bobIdentity.userCode,
            recipientUserCode = aliceIdentity.userCode,
            messageId = msg2Id,
            plaintext = msg2
        )

        assertEquals(EncryptedEnvelope.TYPE_SIGNAL_MESSAGE, envelope2.type)

        // 6. Alice decrypts Bob's reply
        val decrypted2 = aliceManager.decryptMessage(envelope2)
        assertEquals(msg2, decrypted2)

        // 7. Test sequential 5-message exchange
        for (i in 3..7) {
            val isAliceSender = (i % 2 == 1)
            val senderMgr = if (isAliceSender) aliceManager else bobManager
            val receiverMgr = if (isAliceSender) bobManager else aliceManager
            val senderCode = if (isAliceSender) aliceIdentity.userCode else bobIdentity.userCode
            val receiverCode = if (isAliceSender) bobIdentity.userCode else aliceIdentity.userCode

            val text = "Sequential Ratchet message #$i from $senderCode"
            val id = UUID.randomUUID().toString()
            val env = senderMgr.encryptMessage(senderCode, receiverCode, id, text)
            val dec = receiverMgr.decryptMessage(env)
            assertEquals(text, dec)
        }
    }
}

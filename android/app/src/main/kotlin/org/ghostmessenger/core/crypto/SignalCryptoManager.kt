package org.ghostmessenger.core.crypto

import org.ghostmessenger.core.model.EncryptedEnvelope
import org.ghostmessenger.data.network.model.OneTimePreKeyDto
import org.ghostmessenger.data.network.model.PreKeyBundleDto
import org.ghostmessenger.data.network.model.PreKeyFetchBundleDto
import org.ghostmessenger.data.network.model.SignedPreKeyDto
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level coordinator for Signal Protocol operations:
 * - One-time PreKey & Signed PreKey batch generation
 * - X3DH handshake session initiation from server PreKey bundles
 * - Double Ratchet end-to-end encryption & decryption
 */
@Singleton
class SignalCryptoManager @Inject constructor(
    val signalProtocolStore: SignalProtocolStore
) {

    /**
     * Generates a batch of one-time PreKeys and one Signed PreKey, persists them into
     * local storage, and returns a [PreKeyBundleDto] ready for uploading to the signaling server.
     */
    fun generateAndStorePreKeys(startId: Int = 1, count: Int = 50): PreKeyBundleDto {
        val preKeyDtos = mutableListOf<OneTimePreKeyDto>()

        // 1. Generate and store one-time Curve25519 prekeys
        for (i in startId until (startId + count)) {
            val keyPair = Curve.generateKeyPair()
            val record = PreKeyRecord(i, keyPair)
            signalProtocolStore.storePreKey(i, record)

            preKeyDtos.add(
                OneTimePreKeyDto(
                    keyId = i,
                    publicKey = base64Encode(keyPair.publicKey.serialize())
                )
            )
        }

        // 2. Generate and store Signed PreKey
        val signedPreKeyId = 1
        val signedKeyPair = Curve.generateKeyPair()
        val signature = Curve.calculateSignature(
            signalProtocolStore.identityKeyPair.privateKey,
            signedKeyPair.publicKey.serialize()
        )
        val signedRecord = SignedPreKeyRecord(
            signedPreKeyId,
            System.currentTimeMillis(),
            signedKeyPair,
            signature
        )
        signalProtocolStore.storeSignedPreKey(signedPreKeyId, signedRecord)

        return PreKeyBundleDto(
            identityKey = base64Encode(signalProtocolStore.identityKeyPair.publicKey.serialize()),
            registrationId = signalProtocolStore.localRegistrationId,
            signedPreKey = SignedPreKeyDto(
                keyId = signedPreKeyId,
                publicKey = base64Encode(signedKeyPair.publicKey.serialize()),
                signature = base64Encode(signature)
            ),
            preKeys = preKeyDtos
        )
    }

    /**
     * Builds an active Double Ratchet session using a remote contact's fetched PreKey bundle.
     */
    fun buildSession(remoteUserCode: String, bundleDto: PreKeyFetchBundleDto) {
        val identityKey = IdentityKey(base64Decode(bundleDto.identityKey))
        val signedPreKeyPublic = Curve.decodePoint(base64Decode(bundleDto.signedPreKey.publicKey), 0)
        val signedPreKeySig = base64Decode(bundleDto.signedPreKey.signature)

        val preKeyPublic = bundleDto.preKey?.let {
            Curve.decodePoint(base64Decode(it.publicKey), 0)
        }
        val preKeyId = bundleDto.preKey?.keyId ?: PreKeyBundle.NULL_PRE_KEY_ID

        val preKeyBundle = PreKeyBundle(
            bundleDto.registrationId,
            1, // deviceId
            preKeyId,
            preKeyPublic,
            bundleDto.signedPreKey.keyId,
            signedPreKeyPublic,
            signedPreKeySig,
            identityKey
        )

        val remoteAddress = SignalProtocolAddress(remoteUserCode, 1)
        val sessionBuilder = SessionBuilder(signalProtocolStore, remoteAddress)
        sessionBuilder.process(preKeyBundle)
    }

    /**
     * Checks whether an active Double Ratchet session already exists with [remoteUserCode].
     */
    fun hasSession(remoteUserCode: String): Boolean {
        val address = SignalProtocolAddress(remoteUserCode, 1)
        return signalProtocolStore.containsSession(address)
    }

    /**
     * Encrypts plaintext message into an [EncryptedEnvelope].
     */
    fun encryptMessage(
        senderUserCode: String,
        recipientUserCode: String,
        messageId: String,
        plaintext: String
    ): EncryptedEnvelope {
        val address = SignalProtocolAddress(recipientUserCode, 1)
        val cipher = SessionCipher(signalProtocolStore, address)

        val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        val ciphertextMessage = cipher.encrypt(plaintextBytes)

        val type = if (ciphertextMessage is PreKeySignalMessage) {
            EncryptedEnvelope.TYPE_PREKEY_SIGNAL_MESSAGE
        } else {
            EncryptedEnvelope.TYPE_SIGNAL_MESSAGE
        }

        return EncryptedEnvelope(
            type = type,
            senderUserCode = senderUserCode,
            recipientUserCode = recipientUserCode,
            messageId = messageId,
            ciphertext = base64Encode(ciphertextMessage.serialize()),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Decrypts an incoming [EncryptedEnvelope] into plaintext.
     */
    fun decryptMessage(envelope: EncryptedEnvelope): String {
        val address = SignalProtocolAddress(envelope.senderUserCode, 1)
        val cipher = SessionCipher(signalProtocolStore, address)
        val ciphertextBytes = base64Decode(envelope.ciphertext)

        val decryptedBytes = when (envelope.type) {
            EncryptedEnvelope.TYPE_PREKEY_SIGNAL_MESSAGE -> {
                cipher.decrypt(PreKeySignalMessage(ciphertextBytes))
            }
            EncryptedEnvelope.TYPE_SIGNAL_MESSAGE -> {
                cipher.decrypt(SignalMessage(ciphertextBytes))
            }
            else -> throw IllegalArgumentException("Unsupported envelope message type: ${envelope.type}")
        }

        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    private fun base64Encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun base64Decode(str: String): ByteArray =
        Base64.getDecoder().decode(str)
}

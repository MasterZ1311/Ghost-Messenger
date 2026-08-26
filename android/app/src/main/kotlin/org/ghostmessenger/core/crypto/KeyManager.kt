package org.ghostmessenger.core.crypto

import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toSeed
import org.ghostmessenger.core.model.Identity
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Core cryptographic key manager for Ghost Messenger.
 *
 * Responsibilities:
 * 1. BIP-39 mnemonic generation (12 words) and validation.
 * 2. Deterministic seed derivation via PBKDF2-HMAC-SHA512.
 * 3. HKDF-SHA256 key derivation for Curve25519 identity keypairs.
 * 4. HKDF-SHA256 derivation for clamped 14-bit Registration IDs.
 * 5. Deterministic Identity generation.
 */
object KeyManager {
    const val IDENTITY_INFO = "GhostMessenger/IdentityKey/v1"
    const val REGISTRATION_ID_INFO = "GhostMessenger/RegistrationId/v1"

    /**
     * Generates a new cryptographically secure 12-word BIP-39 mnemonic.
     */
    fun generateMnemonic(): List<String> {
        val mnemonicCode = Mnemonics.MnemonicCode(Mnemonics.WordCount.COUNT_12)
        return mnemonicCode.words.map { String(it) }
    }

    /**
     * Validates a list of mnemonic words against the BIP-39 dictionary and checksum.
     */
    fun validateMnemonic(words: List<String>): Boolean {
        if (words.size !in listOf(12, 15, 18, 21, 24)) return false
        val normalized = words.joinToString(" ") { it.trim().lowercase() }
        return validateMnemonic(normalized)
    }

    /**
     * Validates a space-separated mnemonic string against the BIP-39 dictionary and checksum.
     */
    fun validateMnemonic(mnemonic: String): Boolean {
        return try {
            val code = Mnemonics.MnemonicCode(mnemonic.trim().lowercase())
            code.validate()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Converts a 12-word mnemonic into a 64-byte binary seed via BIP-39 (PBKDF2-HMAC-SHA512).
     */
    fun deriveSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        val code = Mnemonics.MnemonicCode(mnemonic.trim().lowercase())
        return code.toSeed(passphrase.toCharArray())
    }

    /**
     * Converts a list of mnemonic words into a 64-byte binary seed.
     */
    fun deriveSeed(words: List<String>, passphrase: String = ""): ByteArray {
        return deriveSeed(words.joinToString(" ") { it.trim().lowercase() }, passphrase)
    }

    /**
     * Derives a full deterministic [Identity] from a 12-word mnemonic.
     */
    fun deriveIdentity(words: List<String>): Identity {
        require(validateMnemonic(words)) { "Invalid BIP-39 mnemonic phrase" }
        val cleanWords = words.map { it.trim().lowercase() }
        val seed = deriveSeed(cleanWords)

        val keyPair = deriveIdentityKeyPair(seed)
        val registrationId = deriveRegistrationId(seed)
        val userCode = UserCodeUtils.generateUserCode(keyPair.publicKey.publicKey.serialize())

        return Identity(
            mnemonicWords = cleanWords,
            userCode = userCode,
            identityKeyPair = keyPair,
            registrationId = registrationId
        )
    }

    /**
     * Derives a full deterministic [Identity] from a space-separated mnemonic string.
     */
    fun deriveIdentity(mnemonic: String): Identity {
        val words = mnemonic.trim().lowercase().split(Regex("\\s+"))
        return deriveIdentity(words)
    }

    /**
     * Generates a brand new random [Identity].
     */
    fun createRandomIdentity(): Identity {
        val mnemonic = generateMnemonic()
        return deriveIdentity(mnemonic)
    }

    /**
     * Derives the Curve25519 IdentityKeyPair from a 64-byte seed using HKDF-SHA256.
     */
    fun deriveIdentityKeyPair(seed: ByteArray): IdentityKeyPair {
        val salt = ByteArray(32) // 32 zero bytes
        val prk = hkdfExtract(salt, seed)
        val infoBytes = IDENTITY_INFO.toByteArray(StandardCharsets.UTF_8)
        val privBytes = hkdfExpand(prk, infoBytes, 32)

        val ecPrivateKey = Curve.decodePrivatePoint(privBytes)
        val ecPublicKey = ecPrivateKey.publicKey()
        val identityKey = IdentityKey(ecPublicKey)

        return IdentityKeyPair(identityKey, ecPrivateKey)
    }

    /**
     * Derives a 14-bit clamped Registration ID (1..16380) from a 64-byte seed using HKDF-SHA256.
     */
    fun deriveRegistrationId(seed: ByteArray): Int {
        val salt = ByteArray(32) // 32 zero bytes
        val prk = hkdfExtract(salt, seed)
        val infoBytes = REGISTRATION_ID_INFO.toByteArray(StandardCharsets.UTF_8)
        val idBytes = hkdfExpand(prk, infoBytes, 32)

        val raw = (((idBytes[0].toInt() and 0x3F) shl 8) or (idBytes[1].toInt() and 0xFF))
        return (raw % 16380) + 1
    }

    /**
     * HKDF-Extract(salt, IKM) -> PRK (RFC 5869)
     */
    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    /**
     * HKDF-Expand(PRK, info, L) -> OKM (RFC 5869, single step for L <= 32)
     */
    fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..32) { "HKDF expand length must be between 1 and 32 bytes for single block" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(0x01.toByte())
        val t1 = mac.doFinal()
        return t1.copyOfRange(0, length)
    }
}

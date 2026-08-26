package org.ghostmessenger.core.model

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey

/**
 * Cryptographic Identity for a Ghost Messenger user.
 *
 * Fully deterministic: derived purely from a 12-word BIP-39 mnemonic.
 * Contains no email, no phone number, and no central identity server account.
 */
data class Identity(
    val mnemonicWords: List<String>,
    val userCode: String,
    val identityKeyPair: IdentityKeyPair,
    val registrationId: Int
) {
    val mnemonicString: String
        get() = mnemonicWords.joinToString(" ")

    val publicKey: IdentityKey
        get() = identityKeyPair.publicKey

    val privateKey: ECPrivateKey
        get() = identityKeyPair.privateKey

    val ecPublicKey: ECPublicKey
        get() = identityKeyPair.publicKey.publicKey

    val publicKeyBytes: ByteArray
        get() = ecPublicKey.serialize()

    val privateKeyBytes: ByteArray
        get() = privateKey.serialize()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Identity) return false

        if (userCode != other.userCode) return false
        if (registrationId != other.registrationId) return false
        if (mnemonicWords != other.mnemonicWords) return false
        if (!publicKeyBytes.contentEquals(other.publicKeyBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mnemonicWords.hashCode()
        result = 31 * result + userCode.hashCode()
        result = 31 * result + registrationId
        result = 31 * result + publicKeyBytes.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "Identity(userCode='$userCode', registrationId=$registrationId, wordsCount=${mnemonicWords.size})"
    }
}

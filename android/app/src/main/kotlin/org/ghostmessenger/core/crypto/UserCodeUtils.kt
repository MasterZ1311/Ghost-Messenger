package org.ghostmessenger.core.crypto

import java.security.MessageDigest

/**
 * Utility for generating and validating human-readable UserCodes (e.g. `5J9L-2P4X`).
 *
 * Derivation:
 * 1. SHA-256(Curve25519 Public Key)
 * 2. Take first 5 bytes (40 bits)
 * 3. Base32 Encode (RFC 4648 without padding -> 8 characters from A-Z2-7)
 * 4. Format as `XXXX-XXXX`
 */
object UserCodeUtils {
    private val USER_CODE_REGEX = Regex("^[A-Z2-7]{8}$")

    /**
     * Generates a formatted UserCode (`XXXX-XXXX`) from a public key byte array.
     */
    fun generateUserCode(publicKey: ByteArray): String {
        require(publicKey.isNotEmpty()) { "Public key cannot be empty" }
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey)
        val prefix5Bytes = hash.copyOfRange(0, 5)
        val base32 = Base32.encode(prefix5Bytes).uppercase()
        require(base32.length == 8) { "Expected 8 Base32 characters, got ${base32.length}" }
        return "${base32.substring(0, 4)}-${base32.substring(4, 8)}"
    }

    /**
     * Checks if a UserCode string is syntactically valid (ignoring hyphens and whitespace).
     */
    fun isValidFormat(code: String): Boolean {
        val clean = cleanCode(code)
        return clean.length == 8 && USER_CODE_REGEX.matches(clean)
    }

    /**
     * Normalizes any valid UserCode string into standard `XXXX-XXXX` format.
     * @throws IllegalArgumentException if the code is not a valid UserCode format.
     */
    fun normalize(code: String): String {
        val clean = cleanCode(code)
        require(isValidFormat(clean)) { "Invalid UserCode format: '$code'" }
        return "${clean.substring(0, 4)}-${clean.substring(4, 8)}"
    }

    /**
     * Cleans hyphens, spaces, and lowercase characters from a user code string.
     */
    fun cleanCode(code: String): String {
        return code.replace("-", "").replace(" ", "").trim().uppercase()
    }
}

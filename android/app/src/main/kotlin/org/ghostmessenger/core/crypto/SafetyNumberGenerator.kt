package org.ghostmessenger.core.crypto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Generates symmetric cryptographic safety numbers (fingerprints) for verifying contacts
 * out-of-band and detecting man-in-the-middle (MITM) key substitutions.
 *
 * Algorithm:
 * - Deterministically sorts both identities by userCode.
 * - Computes SHA-512 over concatenated (userCode + identityKeyBytes) of both peers.
 * - Slices into 5 groups of 6-digit decimal numbers formatted as "XXXXXX XXXXXX ...".
 */
object SafetyNumberGenerator {

    fun generateSafetyNumber(
        localUserCode: String,
        localIdentityKey: ByteArray,
        remoteUserCode: String,
        remoteIdentityKey: ByteArray
    ): String {
        val (firstCode, firstKey, secondCode, secondKey) = if (localUserCode <= remoteUserCode) {
            Tuple4(localUserCode, localIdentityKey, remoteUserCode, remoteIdentityKey)
        } else {
            Tuple4(remoteUserCode, remoteIdentityKey, localUserCode, localIdentityKey)
        }

        val md = MessageDigest.getInstance("SHA-512")
        md.update(firstCode.toByteArray(StandardCharsets.UTF_8))
        md.update(firstKey)
        md.update(secondCode.toByteArray(StandardCharsets.UTF_8))
        md.update(secondKey)
        val hash = md.digest()

        val groups = mutableListOf<String>()
        for (i in 0 until 5) {
            val offset = i * 4
            val b0 = hash[offset].toInt() and 0xFF
            val b1 = hash[offset + 1].toInt() and 0xFF
            val b2 = hash[offset + 2].toInt() and 0xFF
            val b3 = hash[offset + 3].toInt() and 0xFF

            val intVal = ((b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3) and 0x7FFFFFFF
            val formatted = "%06d".format(intVal % 1_000_000)
            groups.add(formatted)
        }

        return groups.joinToString(" ")
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}

package org.ghostmessenger.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class KeyManagerTest {

    @Test
    fun testGenerateMnemonic() {
        val words = KeyManager.generateMnemonic()
        assertEquals("Mnemonic must have exactly 12 words", 12, words.size)
        assertTrue("Generated mnemonic must be valid", KeyManager.validateMnemonic(words))
    }

    @Test
    fun testValidateMnemonic() {
        // Known valid BIP-39 mnemonic
        val validMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        assertTrue(KeyManager.validateMnemonic(validMnemonic))
        assertTrue(KeyManager.validateMnemonic(validMnemonic.split(" ")))

        // Invalid word count (11 words)
        val shortMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"
        assertFalse(KeyManager.validateMnemonic(shortMnemonic))

        // Invalid word not in BIP-39 wordlist
        val invalidWord = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon foobar"
        assertFalse(KeyManager.validateMnemonic(invalidWord))

        // Invalid checksum (last word altered)
        val badChecksum = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"
        assertFalse(KeyManager.validateMnemonic(badChecksum))
    }

    @Test
    fun testDeterministicDerivation() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val words = mnemonic.split(" ")

        val identity1 = KeyManager.deriveIdentity(words)
        val identity2 = KeyManager.deriveIdentity(words)

        assertEquals("UserCode must be identical", identity1.userCode, identity2.userCode)
        assertEquals("RegistrationId must be identical", identity1.registrationId, identity2.registrationId)
        assertArrayEquals("Public key bytes must match", identity1.publicKeyBytes, identity2.publicKeyBytes)
        assertArrayEquals("Private key bytes must match", identity1.privateKeyBytes, identity2.privateKeyBytes)
        assertEquals("Identity objects must equal", identity1, identity2)
    }

    @Test
    fun testRegistrationIdRange() {
        // Registration ID must be in range 1..16380
        for (i in 1..20) {
            val identity = KeyManager.createRandomIdentity()
            assertTrue(
                "Registration ID ${identity.registrationId} must be >= 1 and <= 16380",
                identity.registrationId in 1..16380
            )
        }
    }

    @Test
    fun testDifferentMnemonicsProduceDifferentIdentities() {
        val identity1 = KeyManager.createRandomIdentity()
        val identity2 = KeyManager.createRandomIdentity()

        assertNotEquals("UserCodes must differ", identity1.userCode, identity2.userCode)
        assertFalse("Public keys must differ", identity1.publicKeyBytes.contentEquals(identity2.publicKeyBytes))
        assertFalse("Private keys must differ", identity1.privateKeyBytes.contentEquals(identity2.privateKeyBytes))
    }

    @Test
    fun testUserCodeFormatFromIdentity() {
        val identity = KeyManager.createRandomIdentity()
        assertTrue("UserCode must be valid format", UserCodeUtils.isValidFormat(identity.userCode))
        assertEquals("UserCode must have length 9", 9, identity.userCode.length)
        assertEquals("UserCode hyphen position", '-', identity.userCode[4])
    }

    @Test
    fun testHkdfRfc5869TestVector() {
        // RFC 5869 Test Case 1 (Basic test case with SHA-256)
        val ikm = byteArrayOf(
            0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
            0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
            0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b
        )
        val salt = byteArrayOf(
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b, 0x0c.toByte()
        )
        val info = byteArrayOf(
            0xf0.toByte(), 0xf1.toByte(), 0xf2.toByte(), 0xf3.toByte(),
            0xf4.toByte(), 0xf5.toByte(), 0xf6.toByte(), 0xf7.toByte(),
            0xf8.toByte(), 0xf9.toByte()
        )

        val prk = KeyManager.hkdfExtract(salt, ikm)
        val expectedPrkHex = "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5"
        assertEquals(expectedPrkHex, prk.toHex())

        val okm = KeyManager.hkdfExpand(prk, info, 32)
        // First 32 bytes of RFC 5869 Test Case 1 OKM (T(1))
        val expectedOkmHex = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
        assertEquals(expectedOkmHex, okm.toHex())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

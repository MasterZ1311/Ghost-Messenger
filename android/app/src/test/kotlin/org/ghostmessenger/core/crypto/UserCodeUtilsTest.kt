package org.ghostmessenger.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class UserCodeUtilsTest {

    @Test
    fun testGenerateUserCodeFormat() {
        val random = SecureRandom()
        for (i in 1..50) {
            val pubKey = ByteArray(32)
            random.nextBytes(pubKey)

            val code = UserCodeUtils.generateUserCode(pubKey)

            assertEquals("Code length should be 9 (XXXX-XXXX)", 9, code.length)
            assertEquals("5th character should be hyphen", '-', code[4])
            assertTrue("Generated code '$code' should be valid", UserCodeUtils.isValidFormat(code))
        }
    }

    @Test
    fun testDeterministicUserCode() {
        val pubKey = byteArrayOf(
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
            0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
            0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
        )

        val code1 = UserCodeUtils.generateUserCode(pubKey)
        val code2 = UserCodeUtils.generateUserCode(pubKey)

        assertEquals("Same public key must produce identical user code", code1, code2)
    }

    @Test
    fun testValidFormats() {
        assertTrue(UserCodeUtils.isValidFormat("5JKL-2P4X"))
        assertTrue(UserCodeUtils.isValidFormat("5jkl-2p4x"))
        assertTrue(UserCodeUtils.isValidFormat("5JKL2P4X"))
        assertTrue(UserCodeUtils.isValidFormat("AAAA-AAAA"))
        assertTrue(UserCodeUtils.isValidFormat("7777-7777"))
        assertTrue(UserCodeUtils.isValidFormat("  2345-67AB  "))
    }

    @Test
    fun testInvalidFormats() {
        assertFalse("Contains invalid char 8", UserCodeUtils.isValidFormat("5JK8-2P4X"))
        assertFalse("Contains invalid char 9", UserCodeUtils.isValidFormat("5JK9-2P4X"))
        assertFalse("Contains invalid char 0", UserCodeUtils.isValidFormat("5JK0-2P4X"))
        assertFalse("Contains invalid char 1", UserCodeUtils.isValidFormat("5JK1-2P4X"))
        assertFalse("Too short (7 chars)", UserCodeUtils.isValidFormat("5JKL-2P4"))
        assertFalse("Too long (9 chars)", UserCodeUtils.isValidFormat("5JKL-2P4XX"))
        assertFalse("Empty string", UserCodeUtils.isValidFormat(""))
        assertFalse("Special chars", UserCodeUtils.isValidFormat("5JKL#2P4X"))
    }

    @Test
    fun testNormalize() {
        assertEquals("5JKL-2P4X", UserCodeUtils.normalize("5jkl2p4x"))
        assertEquals("5JKL-2P4X", UserCodeUtils.normalize("5JKL-2P4X"))
        assertEquals("5JKL-2P4X", UserCodeUtils.normalize("  5jkl-2p4x  "))
        assertEquals("AAAA-BBBB", UserCodeUtils.normalize("aaaabbbb"))

        assertThrows(IllegalArgumentException::class.java) {
            UserCodeUtils.normalize("invalid-code")
        }
    }
}

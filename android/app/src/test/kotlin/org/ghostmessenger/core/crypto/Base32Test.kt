package org.ghostmessenger.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

class Base32Test {

    @Test
    fun testRfc4648TestVectors() {
        // RFC 4648 unpadded Base32 test vectors
        val vectors = listOf(
            "" to "",
            "f" to "MY",
            "fo" to "MZXQ",
            "foo" to "MZXW6",
            "foob" to "MZXW6YQ",
            "fooba" to "MZXW6YTB",
            "foobar" to "MZXW6YTBOI"
        )

        for ((input, expected) in vectors) {
            val encoded = Base32.encode(input.toByteArray(StandardCharsets.UTF_8))
            assertEquals("Encoding failed for input: '$input'", expected, encoded)

            val decoded = Base32.decode(expected)
            assertEquals("Decoding failed for expected: '$expected'", input, String(decoded, StandardCharsets.UTF_8))
        }
    }

    @Test
    fun testRoundTripRandomBytes() {
        val random = SecureRandom()
        for (length in 0..100) {
            val original = ByteArray(length)
            random.nextBytes(original)

            val encoded = Base32.encode(original)
            val decoded = Base32.decode(encoded)

            assertArrayEquals("Roundtrip failed for byte array of length $length", original, decoded)
        }
    }

    @Test
    fun testDecodingCaseInsensitiveAndWhitespace() {
        val input = "MZXW6YTB"
        val lowercase = "mzxw6ytb"
        val withWhitespace = "  MZXW 6YTB  \n"

        assertArrayEquals(Base32.decode(input), Base32.decode(lowercase))
        assertArrayEquals(Base32.decode(input), Base32.decode(withWhitespace))
    }

    @Test
    fun testInvalidCharactersThrowException() {
        // '0', '1', '8', '9' are not part of RFC 4648 Base32 alphabet
        assertThrows(IllegalArgumentException::class.java) {
            Base32.decode("MZXW68TB")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Base32.decode("MZXW69TB")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Base32.decode("MZXW61TB")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Base32.decode("MZXW60TB")
        }
    }
}

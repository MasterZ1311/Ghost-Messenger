package org.ghostmessenger.core.crypto

/**
 * RFC 4648 compliant Base32 encoder/decoder (without padding).
 * Alphabet: A-Z (values 0-25) and 2-7 (values 26-31).
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        for (i in ALPHABET.indices) {
            val char = ALPHABET[i]
            this[char.code] = i
            if (char in 'A'..'Z') {
                this[char.lowercaseChar().code] = i
            }
        }
    }

    /**
     * Encodes a byte array into a Base32 string (unpadded).
     */
    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val out = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0

        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                val index = (buffer ushr bitsLeft) and 0x1F
                out.append(ALPHABET[index])
            }
        }

        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            out.append(ALPHABET[index])
        }

        return out.toString()
    }

    /**
     * Decodes a Base32 string into a byte array.
     * Ignores '=' padding and whitespace.
     */
    fun decode(encoded: String): ByteArray {
        val clean = encoded.filter { it != '=' && !it.isWhitespace() }
        if (clean.isEmpty()) return ByteArray(0)

        var buffer = 0
        var bitsLeft = 0
        val output = mutableListOf<Byte>()

        for (char in clean) {
            val code = char.code
            if (code >= DECODE_TABLE.size || DECODE_TABLE[code] < 0) {
                throw IllegalArgumentException("Illegal character in Base32 string: '$char'")
            }
            val value = DECODE_TABLE[code]
            buffer = (buffer shl 5) or value
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer ushr bitsLeft) and 0xFF).toByte())
            }
        }

        return output.toByteArray()
    }
}

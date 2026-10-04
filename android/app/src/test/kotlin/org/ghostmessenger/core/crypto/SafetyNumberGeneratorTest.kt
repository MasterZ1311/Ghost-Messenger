package org.ghostmessenger.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyNumberGeneratorTest {

    @Test
    fun testSymmetricGeneration() {
        val userA = "5JKL-2P4X"
        val keyA = ByteArray(32) { (it + 1).toByte() }

        val userB = "9XYZ-7MNO"
        val keyB = ByteArray(32) { (it + 10).toByte() }

        val numberAB = SafetyNumberGenerator.generateSafetyNumber(userA, keyA, userB, keyB)
        val numberBA = SafetyNumberGenerator.generateSafetyNumber(userB, keyB, userA, keyA)

        assertEquals("Safety numbers must be identical regardless of party ordering", numberAB, numberBA)
        assertEquals(5, numberAB.split(" ").size)

        for (group in numberAB.split(" ")) {
            assertEquals(6, group.length)
            assertTrue("Group must be numeric: $group", group.all { it.isDigit() })
        }
    }

    @Test
    fun testDifferentKeysProduceDifferentSafetyNumbers() {
        val userA = "5JKL-2P4X"
        val keyA = ByteArray(32) { 1.toByte() }

        val userB = "9XYZ-7MNO"
        val keyB1 = ByteArray(32) { 2.toByte() }
        val keyB2 = ByteArray(32) { 3.toByte() }

        val number1 = SafetyNumberGenerator.generateSafetyNumber(userA, keyA, userB, keyB1)
        val number2 = SafetyNumberGenerator.generateSafetyNumber(userA, keyA, userB, keyB2)

        assertNotEquals("Different remote keys must produce different safety numbers", number1, number2)
    }
}

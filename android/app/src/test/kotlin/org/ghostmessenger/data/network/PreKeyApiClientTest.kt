package org.ghostmessenger.data.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ghostmessenger.data.network.model.OneTimePreKeyDto
import org.ghostmessenger.data.network.model.PreKeyBundleDto
import org.ghostmessenger.data.network.model.PreKeyFetchBundleDto
import org.ghostmessenger.data.network.model.PreKeyResponse
import org.ghostmessenger.data.network.model.PreKeyUploadRequest
import org.ghostmessenger.data.network.model.SignedPreKeyDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreKeyApiClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testPreKeyUploadRequestSerialization() {
        val request = PreKeyUploadRequest(
            userCode = "5JKL-2P4X",
            bundle = PreKeyBundleDto(
                identityKey = "BASE64_IDENTITY_KEY",
                registrationId = 1337,
                signedPreKey = SignedPreKeyDto(
                    keyId = 1,
                    publicKey = "BASE64_SIGNED_KEY",
                    signature = "BASE64_SIGNATURE"
                ),
                preKeys = listOf(
                    OneTimePreKeyDto(keyId = 1, publicKey = "KEY_1"),
                    OneTimePreKeyDto(keyId = 2, publicKey = "KEY_2")
                )
            )
        )

        val serialized = json.encodeToString(request)
        assertTrue(serialized.contains("5JKL-2P4X"))
        assertTrue(serialized.contains("BASE64_IDENTITY_KEY"))
        assertTrue(serialized.contains("1337"))

        val deserialized = json.decodeFromString<PreKeyUploadRequest>(serialized)
        assertEquals("5JKL-2P4X", deserialized.userCode)
        assertEquals(1337, deserialized.bundle.registrationId)
        assertEquals(2, deserialized.bundle.preKeys.size)
    }

    @Test
    fun testPreKeyFetchResponseDeserialization() {
        val jsonPayload = """
            {
                "success": true,
                "bundle": {
                    "identityKey": "REMOTE_IDENTITY_KEY",
                    "registrationId": 9999,
                    "signedPreKey": {
                        "keyId": 1,
                        "publicKey": "SIGNED_KEY",
                        "signature": "SIGNED_SIG"
                    },
                    "preKey": {
                        "keyId": 42,
                        "publicKey": "ONE_TIME_42"
                    },
                    "remainingPreKeys": 9
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<PreKeyResponse>(jsonPayload)
        assertTrue(response.success)
        assertNotNull(response.bundle)

        val bundle = response.bundle as PreKeyFetchBundleDto
        assertEquals("REMOTE_IDENTITY_KEY", bundle.identityKey)
        assertEquals(9999, bundle.registrationId)
        assertEquals(1, bundle.signedPreKey.keyId)
        assertNotNull(bundle.preKey)
        assertEquals(42, bundle.preKey?.keyId)
        assertEquals(9, bundle.remainingPreKeys)
    }

    @Test
    fun testPreKeyUploadRequestWithSignatureSerialization() {
        val request = PreKeyUploadRequest(
            userCode = "5JKL-2P4X",
            bundle = PreKeyBundleDto(
                identityKey = "BASE64_IDENTITY_KEY",
                registrationId = 1337,
                signedPreKey = SignedPreKeyDto(
                    keyId = 1,
                    publicKey = "BASE64_SIGNED_KEY",
                    signature = "BASE64_SIGNATURE"
                ),
                preKeys = emptyList()
            ),
            signature = "7e58a2d9c6d162069e0ddbd5bfbdf8f0416c2c9582e6416813c8551d2b522568"
        )

        val serialized = json.encodeToString(request)
        assertTrue(serialized.contains("7e58a2d9c6d162069e0ddbd5bfbdf8f0416c2c9582e6416813c8551d2b522568"))

        val deserialized = json.decodeFromString<PreKeyUploadRequest>(serialized)
        assertEquals("7e58a2d9c6d162069e0ddbd5bfbdf8f0416c2c9582e6416813c8551d2b522568", deserialized.signature)
    }

    @Test
    fun testComputeChallengeSignature() {
        val identityKeyB64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAU="
        val nonce = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val expected = "7e58a2d9c6d162069e0ddbd5bfbdf8f0416c2c9582e6416813c8551d2b522568"

        val signature = org.ghostmessenger.data.network.api.PreKeyApiClient.computeChallengeSignature(identityKeyB64, nonce)
        assertEquals(expected, signature)
    }

    @Test
    fun testPreKeyCountResponseDeserialization() {
        val jsonPayload = """
            {
                "success": true,
                "userCode": "5JKL-2P4X",
                "remainingPreKeys": 42
            }
        """.trimIndent()

        val response = json.decodeFromString<org.ghostmessenger.data.network.model.PreKeyCountResponse>(jsonPayload)
        assertTrue(response.success)
        assertEquals("5JKL-2P4X", response.userCode)
        assertEquals(42, response.remainingPreKeys)
    }

    @Test
    fun testSignedPreKeyUpdateRequestSerialization() {
        val request = org.ghostmessenger.data.network.model.SignedPreKeyUpdateRequest(
            userCode = "5JKL-2P4X",
            identityKey = "BASE64_IDENTITY_KEY",
            signedPreKey = SignedPreKeyDto(
                keyId = 5,
                publicKey = "BASE64_SIGNED_KEY",
                signature = "BASE64_SIGNATURE"
            )
        )

        val serialized = json.encodeToString(request)
        assertTrue(serialized.contains("5JKL-2P4X"))
        assertTrue(serialized.contains("BASE64_IDENTITY_KEY"))
        assertTrue(serialized.contains(""""keyId":5"""))

        val deserialized = json.decodeFromString<org.ghostmessenger.data.network.model.SignedPreKeyUpdateRequest>(serialized)
        assertEquals("5JKL-2P4X", deserialized.userCode)
        assertEquals(5, deserialized.signedPreKey.keyId)
    }
}

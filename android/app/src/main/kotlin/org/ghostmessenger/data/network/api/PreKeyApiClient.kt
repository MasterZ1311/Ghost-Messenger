package org.ghostmessenger.data.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.ghostmessenger.data.network.model.ChallengeResponse
import org.ghostmessenger.data.network.model.HealthStatusDto
import org.ghostmessenger.data.network.model.PreKeyBundleDto
import org.ghostmessenger.data.network.model.PreKeyFetchBundleDto
import org.ghostmessenger.data.network.model.PreKeyResponse
import org.ghostmessenger.data.network.model.PreKeyUploadRequest
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ktor REST API Client for uploading and fetching ephemeral Signal PreKey bundles.
 */
@Singleton
class PreKeyApiClient @Inject constructor(
    private val httpClient: HttpClient
) {

    /**
     * Issues a one-time challenge nonce the client must sign to prove ownership of the identity key.
     */
    suspend fun fetchChallenge(
        baseUrl: String,
        userCode: String
    ): Result<String> {
        return try {
            val url = cleanUrl(baseUrl) + "/api/prekeys/challenge/$userCode"
            val response = httpClient.get(url)
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<ChallengeResponse>()
                if (body.success && !body.nonce.isNullOrBlank()) {
                    Result.success(body.nonce)
                } else {
                    Result.failure(Exception(body.error ?: "Challenge response missing nonce"))
                }
            } else {
                val errorBody = runCatching { response.body<ChallengeResponse>() }.getOrNull()
                val errorMsg = errorBody?.error ?: "Challenge request returned status: ${response.status.value}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Computes the HMAC-SHA256 ownership proof required for PreKey bundle uploads:
         *   signature = HMAC-SHA256(key = SHA256(identityKeyBytes), data = nonce)
         */
        fun computeChallengeSignature(identityKeyBase64: String, nonce: String): String {
            val identityKeyBytes = Base64.getDecoder().decode(identityKeyBase64)
            val sha256 = MessageDigest.getInstance("SHA-256")
            val hmacKey = sha256.digest(identityKeyBytes)
            val mac = Mac.getInstance("HmacSHA256")
            val keySpec = SecretKeySpec(hmacKey, "HmacSHA256")
            mac.init(keySpec)
            val signatureBytes = mac.doFinal(nonce.toByteArray(Charsets.UTF_8))
            return signatureBytes.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Uploads the local user's PreKey bundle to the ephemeral signaling server.
     */
    suspend fun uploadPreKeyBundle(
        baseUrl: String,
        userCode: String,
        bundle: PreKeyBundleDto
    ): Result<Boolean> {
        return try {
            val challengeResult = fetchChallenge(baseUrl, userCode)
            val signature = challengeResult.getOrNull()?.let { nonce ->
                runCatching { computeChallengeSignature(bundle.identityKey, nonce) }.getOrNull()
            }

            val url = cleanUrl(baseUrl) + "/api/prekeys/upload"
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(PreKeyUploadRequest(userCode = userCode, bundle = bundle, signature = signature))
            }

            if (response.status == HttpStatusCode.OK) {
                val body = response.body<PreKeyResponse>()
                if (body.success) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(body.error ?: "Upload failed"))
                }
            } else {
                val errorBody = runCatching { response.body<PreKeyResponse>() }.getOrNull()
                val errorMsg = errorBody?.error ?: "Server returned status: ${response.status.value}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches a one-time PreKey bundle for initiating an X3DH session with [targetUserCode].
     */
    suspend fun fetchPreKeyBundle(
        baseUrl: String,
        targetUserCode: String
    ): Result<PreKeyFetchBundleDto> {
        return try {
            val url = cleanUrl(baseUrl) + "/api/prekeys/$targetUserCode"
            val response = httpClient.get(url)

            if (response.status == HttpStatusCode.OK) {
                val body = response.body<PreKeyResponse>()
                if (body.success && body.bundle != null) {
                    Result.success(body.bundle)
                } else {
                    Result.failure(Exception(body.error ?: "Bundle not found"))
                }
            } else if (response.status == HttpStatusCode.NotFound) {
                Result.failure(Exception("User '$targetUserCode' has no available PreKey bundle"))
            } else {
                Result.failure(Exception("Server returned status: ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks whether the signaling server is reachable and active.
     */
    suspend fun checkHealth(baseUrl: String): Result<HealthStatusDto> {
        return try {
            val url = cleanUrl(baseUrl) + "/api/prekeys/health/status"
            val response = httpClient.get(url)
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body<HealthStatusDto>())
            } else {
                Result.failure(Exception("Health check returned status: ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registers local device's FCM token for zero-knowledge wake-up pings.
     */
    suspend fun registerFcmToken(
        baseUrl: String,
        userCode: String,
        fcmToken: String
    ): Result<Boolean> {
        return try {
            val url = cleanUrl(baseUrl) + "/api/prekeys/fcm-token"
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(org.ghostmessenger.data.network.model.FcmTokenRequest(userCode = userCode, fcmToken = fcmToken))
            }
            if (response.status == HttpStatusCode.OK) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to register FCM token: ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cleanUrl(url: String): String =
        url.trimEnd('/')
}

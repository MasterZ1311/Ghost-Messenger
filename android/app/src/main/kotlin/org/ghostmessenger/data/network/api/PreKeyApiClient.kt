package org.ghostmessenger.data.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.ghostmessenger.data.network.model.HealthStatusDto
import org.ghostmessenger.data.network.model.PreKeyBundleDto
import org.ghostmessenger.data.network.model.PreKeyFetchBundleDto
import org.ghostmessenger.data.network.model.PreKeyResponse
import org.ghostmessenger.data.network.model.PreKeyUploadRequest
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
     * Uploads the local user's PreKey bundle to the ephemeral signaling server.
     */
    suspend fun uploadPreKeyBundle(
        baseUrl: String,
        userCode: String,
        bundle: PreKeyBundleDto
    ): Result<Boolean> {
        return try {
            val url = cleanUrl(baseUrl) + "/api/prekeys/upload"
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(PreKeyUploadRequest(userCode = userCode, bundle = bundle))
            }

            if (response.status == HttpStatusCode.OK) {
                val body = response.body<PreKeyResponse>()
                if (body.success) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(body.error ?: "Upload failed"))
                }
            } else {
                Result.failure(Exception("Server returned status: ${response.status.value}"))
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

    private fun cleanUrl(url: String): String =
        url.trimEnd('/')
}

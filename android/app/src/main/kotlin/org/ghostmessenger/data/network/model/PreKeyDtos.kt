package org.ghostmessenger.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class PreKeyUploadRequest(
    val userCode: String,
    val bundle: PreKeyBundleDto
)

@Serializable
data class PreKeyBundleDto(
    val identityKey: String, // Base64
    val registrationId: Int,
    val signedPreKey: SignedPreKeyDto,
    val preKeys: List<OneTimePreKeyDto> = emptyList(),
    val kyberPreKey: KyberPreKeyDto? = null
)

@Serializable
data class SignedPreKeyDto(
    val keyId: Int,
    val publicKey: String, // Base64
    val signature: String  // Base64
)

@Serializable
data class OneTimePreKeyDto(
    val keyId: Int,
    val publicKey: String  // Base64
)

@Serializable
data class KyberPreKeyDto(
    val keyId: Int,
    val publicKey: String, // Base64
    val signature: String  // Base64
)

@Serializable
data class PreKeyResponse(
    val success: Boolean,
    val userCode: String? = null,
    val preKeyCount: Int? = null,
    val message: String? = null,
    val error: String? = null,
    val bundle: PreKeyFetchBundleDto? = null
)

@Serializable
data class PreKeyFetchBundleDto(
    val identityKey: String, // Base64
    val registrationId: Int,
    val signedPreKey: SignedPreKeyDto,
    val preKey: OneTimePreKeyDto? = null,
    val kyberPreKey: KyberPreKeyDto? = null,
    val remainingPreKeys: Int = 0
)

@Serializable
data class HealthStatusDto(
    val status: String,
    val uptimeSeconds: Long? = null,
    val activeUsers: Int? = null,
    val storedBundles: Int? = null
)

package org.ghostmessenger.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores public identity keys for known remote contacts.
 */
@Entity(tableName = "signal_identities")
data class SignalIdentityEntity(
    @PrimaryKey
    val addressName: String, // Contact's UserCode
    val publicKeyBytes: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalIdentityEntity) return false
        return addressName == other.addressName && publicKeyBytes.contentEquals(other.publicKeyBytes)
    }

    override fun hashCode(): Int {
        var result = addressName.hashCode()
        result = 31 * result + publicKeyBytes.contentHashCode()
        return result
    }
}

/**
 * Stores local one-time Curve25519 prekeys.
 */
@Entity(tableName = "signal_prekeys")
data class SignalPreKeyEntity(
    @PrimaryKey
    val keyId: Int,
    val recordBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalPreKeyEntity) return false
        return keyId == other.keyId && recordBytes.contentEquals(other.recordBytes)
    }

    override fun hashCode(): Int {
        var result = keyId
        result = 31 * result + recordBytes.contentHashCode()
        return result
    }
}

/**
 * Stores local signed prekeys.
 */
@Entity(tableName = "signal_signed_prekeys")
data class SignalSignedPreKeyEntity(
    @PrimaryKey
    val keyId: Int,
    val recordBytes: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalSignedPreKeyEntity) return false
        return keyId == other.keyId && recordBytes.contentEquals(other.recordBytes)
    }

    override fun hashCode(): Int {
        var result = keyId
        result = 31 * result + recordBytes.contentHashCode()
        return result
    }
}

/**
 * Stores Double Ratchet active session records per remote peer.
 */
@Entity(
    tableName = "signal_sessions",
    primaryKeys = ["addressName", "deviceId"]
)
data class SignalSessionEntity(
    val addressName: String, // Contact's UserCode
    val deviceId: Int = 1,
    val recordBytes: ByteArray,
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalSessionEntity) return false
        return addressName == other.addressName &&
                deviceId == other.deviceId &&
                recordBytes.contentEquals(other.recordBytes)
    }

    override fun hashCode(): Int {
        var result = addressName.hashCode()
        result = 31 * result + deviceId
        result = 31 * result + recordBytes.contentHashCode()
        return result
    }
}

/**
 * Stores post-quantum Kyber prekeys.
 */
@Entity(tableName = "signal_kyber_prekeys")
data class SignalKyberPreKeyEntity(
    @PrimaryKey
    val keyId: Int,
    val recordBytes: ByteArray,
    val isUsed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalKyberPreKeyEntity) return false
        return keyId == other.keyId && isUsed == other.isUsed && recordBytes.contentEquals(other.recordBytes)
    }

    override fun hashCode(): Int {
        var result = keyId
        result = 31 * result + isUsed.hashCode()
        result = 31 * result + recordBytes.contentHashCode()
        return result
    }
}

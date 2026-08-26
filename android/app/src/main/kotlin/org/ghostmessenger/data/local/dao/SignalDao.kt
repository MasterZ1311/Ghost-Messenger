package org.ghostmessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ghostmessenger.data.local.entities.SignalIdentityEntity
import org.ghostmessenger.data.local.entities.SignalKyberPreKeyEntity
import org.ghostmessenger.data.local.entities.SignalPreKeyEntity
import org.ghostmessenger.data.local.entities.SignalSessionEntity
import org.ghostmessenger.data.local.entities.SignalSignedPreKeyEntity

@Dao
interface SignalIdentityDao {
    @Query("SELECT * FROM signal_identities WHERE addressName = :addressName LIMIT 1")
    fun getIdentity(addressName: String): SignalIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertIdentity(identity: SignalIdentityEntity)

    @Query("DELETE FROM signal_identities WHERE addressName = :addressName")
    fun deleteIdentity(addressName: String)

    @Query("DELETE FROM signal_identities")
    fun clearAll()
}

@Dao
interface SignalPreKeyDao {
    @Query("SELECT * FROM signal_prekeys WHERE keyId = :keyId LIMIT 1")
    fun getPreKey(keyId: Int): SignalPreKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPreKey(preKey: SignalPreKeyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPreKeys(preKeys: List<SignalPreKeyEntity>)

    @Query("SELECT COUNT(*) > 0 FROM signal_prekeys WHERE keyId = :keyId")
    fun containsPreKey(keyId: Int): Boolean

    @Query("DELETE FROM signal_prekeys WHERE keyId = :keyId")
    fun deletePreKey(keyId: Int)

    @Query("DELETE FROM signal_prekeys")
    fun clearAll()
}

@Dao
interface SignalSignedPreKeyDao {
    @Query("SELECT * FROM signal_signed_prekeys WHERE keyId = :keyId LIMIT 1")
    fun getSignedPreKey(keyId: Int): SignalSignedPreKeyEntity?

    @Query("SELECT * FROM signal_signed_prekeys")
    fun getAllSignedPreKeys(): List<SignalSignedPreKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSignedPreKey(signedPreKey: SignalSignedPreKeyEntity)

    @Query("SELECT COUNT(*) > 0 FROM signal_signed_prekeys WHERE keyId = :keyId")
    fun containsSignedPreKey(keyId: Int): Boolean

    @Query("DELETE FROM signal_signed_prekeys WHERE keyId = :keyId")
    fun deleteSignedPreKey(keyId: Int)

    @Query("DELETE FROM signal_signed_prekeys")
    fun clearAll()
}

@Dao
interface SignalSessionDao {
    @Query("SELECT * FROM signal_sessions WHERE addressName = :addressName AND deviceId = :deviceId LIMIT 1")
    fun getSession(addressName: String, deviceId: Int = 1): SignalSessionEntity?

    @Query("SELECT * FROM signal_sessions WHERE addressName = :addressName")
    fun getSessionsForAddress(addressName: String): List<SignalSessionEntity>

    @Query("SELECT deviceId FROM signal_sessions WHERE addressName = :addressName")
    fun getSubDeviceSessions(addressName: String): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: SignalSessionEntity)

    @Query("SELECT COUNT(*) > 0 FROM signal_sessions WHERE addressName = :addressName AND deviceId = :deviceId")
    fun containsSession(addressName: String, deviceId: Int = 1): Boolean

    @Query("DELETE FROM signal_sessions WHERE addressName = :addressName AND deviceId = :deviceId")
    fun deleteSession(addressName: String, deviceId: Int = 1)

    @Query("DELETE FROM signal_sessions WHERE addressName = :addressName")
    fun deleteAllSessionsForAddress(addressName: String)

    @Query("DELETE FROM signal_sessions")
    fun clearAll()
}

@Dao
interface SignalKyberPreKeyDao {
    @Query("SELECT * FROM signal_kyber_prekeys WHERE keyId = :keyId LIMIT 1")
    fun getKyberPreKey(keyId: Int): SignalKyberPreKeyEntity?

    @Query("SELECT * FROM signal_kyber_prekeys")
    fun getAllKyberPreKeys(): List<SignalKyberPreKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertKyberPreKey(kyberPreKey: SignalKyberPreKeyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertKyberPreKeys(kyberPreKeys: List<SignalKyberPreKeyEntity>)

    @Query("SELECT COUNT(*) > 0 FROM signal_kyber_prekeys WHERE keyId = :keyId")
    fun containsKyberPreKey(keyId: Int): Boolean

    @Query("UPDATE signal_kyber_prekeys SET isUsed = 1 WHERE keyId = :keyId")
    fun markKyberPreKeyUsed(keyId: Int)

    @Query("DELETE FROM signal_kyber_prekeys WHERE keyId = :keyId")
    fun deleteKyberPreKey(keyId: Int)

    @Query("DELETE FROM signal_kyber_prekeys")
    fun clearAll()
}

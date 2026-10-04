package org.ghostmessenger.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import org.ghostmessenger.core.crypto.KeyManager
import org.ghostmessenger.core.model.Identity
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware-backed encrypted shared preferences using Android Keystore & Tink (AES-256-GCM).
 *
 * Stores:
 * - Master SQLCipher database encryption passphrase.
 * - Active user's 12-word mnemonic phrase.
 * - Configurable signaling server URL.
 */
@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            try {
                context.deleteSharedPreferences(PREFS_FILE_NAME)
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (fallbackEx: Exception) {
                context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    val prefs: SharedPreferences by lazy {
        createEncryptedPrefs()
    }

    /**
     * Retrieves or generates a 256-bit cryptographically secure passphrase for SQLCipher.
     */
    fun getOrGenerateDbPassphrase(): ByteArray {
        val existingHex = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existingHex != null) {
            return hexToBytes(existingHex)
        }

        val newPassphrase = ByteArray(32)
        SecureRandom().nextBytes(newPassphrase)
        val hex = bytesToHex(newPassphrase)

        prefs.edit().putString(KEY_DB_PASSPHRASE, hex).apply()
        return newPassphrase
    }

    /**
     * Saves the user's active cryptographic identity (derived from 12 words).
     */
    fun saveIdentity(identity: Identity) {
        prefs.edit()
            .putString(KEY_MNEMONIC, identity.mnemonicString)
            .putString(KEY_USER_CODE, identity.userCode)
            .putInt(KEY_REGISTRATION_ID, identity.registrationId)
            .apply()
    }

    /**
     * Returns true if a local identity is registered on this device.
     */
    fun hasIdentity(): Boolean {
        val mnemonic = prefs.getString(KEY_MNEMONIC, null)
        return !mnemonic.isNullOrBlank()
    }

    /**
     * Loads the active user's [Identity] if one exists.
     */
    fun getIdentity(): Identity? {
        val mnemonic = prefs.getString(KEY_MNEMONIC, null) ?: return null
        return try {
            KeyManager.deriveIdentity(mnemonic)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Clears all identity data (for account destruction / reset).
     */
    fun clearIdentity() {
        prefs.edit()
            .remove(KEY_MNEMONIC)
            .remove(KEY_USER_CODE)
            .remove(KEY_REGISTRATION_ID)
            .apply()
    }

    /**
     * Gets the configured signaling server URL. Defaults to the production server URL.
     */
    fun getSignalingUrl(): String {
        val saved = prefs.getString(KEY_SIGNALING_URL, null)
        return if (saved.isNullOrBlank() || saved.contains("ghost-messenger-fp8w.onrender.com")) {
            DEFAULT_SIGNALING_URL
        } else {
            saved
        }
    }

    fun setSignalingUrl(url: String) {
        prefs.edit().putString(KEY_SIGNALING_URL, url.trim()).apply()
    }

    fun getTurnUsername(): String {
        val saved = prefs.getString(KEY_TURN_USERNAME, null)
        return if (saved.isNullOrBlank()) DEFAULT_TURN_USERNAME else saved
    }

    fun setTurnUsername(username: String) {
        prefs.edit().putString(KEY_TURN_USERNAME, username.trim()).apply()
    }

    fun getTurnPassword(): String {
        val saved = prefs.getString(KEY_TURN_PASSWORD, null)
        return if (saved.isNullOrBlank()) DEFAULT_TURN_PASSWORD else saved
    }

    fun setTurnPassword(password: String) {
        prefs.edit().putString(KEY_TURN_PASSWORD, password.trim()).apply()
    }

    fun getTurnServerUrl(): String {
        val saved = prefs.getString(KEY_TURN_SERVER_URL, null)
        return if (saved.isNullOrBlank()) DEFAULT_TURN_SERVER_URL else saved
    }

    fun setTurnServerUrl(url: String) {
        prefs.edit().putString(KEY_TURN_SERVER_URL, url.trim()).apply()
    }

    fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

    fun setFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token.trim()).apply()
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    companion object {
        private const val PREFS_FILE_NAME = "ghost_secure_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase_hex"
        private const val KEY_MNEMONIC = "user_mnemonic"
        private const val KEY_USER_CODE = "user_code"
        private const val KEY_REGISTRATION_ID = "registration_id"
        private const val KEY_SIGNALING_URL = "signaling_url"
        private const val KEY_TURN_USERNAME = "turn_username"
        private const val KEY_TURN_PASSWORD = "turn_password"
        private const val KEY_TURN_SERVER_URL = "turn_server_url"
        private const val KEY_FCM_TOKEN = "fcm_token"

        const val DEFAULT_SIGNALING_URL = "https://ghost-messenger-iptc.onrender.com"
        const val DEFAULT_TURN_SERVER_URL = "turn:global.relay.metered.ca:80"
        const val DEFAULT_TURN_USERNAME = "d8b6c3ca05209ab02a917f8b"
        const val DEFAULT_TURN_PASSWORD = "1zz/bX4u3W5ILOXV"
    }
}

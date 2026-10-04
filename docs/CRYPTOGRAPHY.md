# Calypso — Cryptographic Protocol & Key Management Specification

This document details the cryptographic architecture, key derivation pipelines, session establishment protocols, and at-rest security mechanisms used in **Calypso**. It is prepared for security researchers, cryptographers, and code reviewers verifying the application's security guarantees.

---

## 1. Cryptographic Primitives & Standards

Calypso uses peer-reviewed, industry-standard cryptographic algorithms:

| Category | Primitive / Standard | Implementation / Library | Function / Purpose |
|---|---|---|---|
| **Identity Derivation** | BIP-39 / PBKDF2-HMAC-SHA512 | `cash.z.ecc.android.bip39` | 12-word mnemonic seed generation and wallet restore |
| **Key Derivation** | HKDF-SHA256 (RFC 5869) | `javax.crypto.Mac` (HmacSHA256) | Deterministic identity key and registration ID derivation |
| **Key Agreement** | X3DH (Curve25519 / X25519) | `org.signal:libsignal-android` | Asynchronous initial key exchange |
| **Symmetric Ratchet** | Double Ratchet Algorithm | `org.signal:libsignal-android` | Forward secrecy and post-compromise recovery per message |
| **Payload Encryption** | AES-256-CBC + HMAC-SHA256 | `org.signal:libsignal-android` | End-to-end encrypted envelope content |
| **Fingerprint Generation**| SHA-512 | `java.security.MessageDigest` | 30-digit symmetric Safety Number calculation |
| **Local Storage (DB)** | SQLCipher (AES-256-CBC) | `net.zetetic:sqlcipher-android` | Database encryption at rest |
| **Local Storage (Keys)**| AES-256-GCM + AES-256-SIV | `androidx.security:security-crypto` | EncryptedSharedPreferences backed by Android Keystore |
| **Public Routing ID** | Base32 (RFC 4648) + SHA-256 | Internal `Base32.kt` & `UserCodeUtils.kt` | 8-character human-readable UserCode (`XXXX-XXXX`) |

---

## 2. Identity & Key Derivation Pipeline

Calypso identities are self-sovereign and deterministic. No centralized authority, email, or telephone number is used.

### 2.1 Mnemonic Generation & Validation

- **Generation (`KeyManager.kt:30-33`)**:
  Generates 128 bits of cryptographically secure entropy, yielding a 12-word BIP-39 mnemonic code:
  ```kotlin
  val mnemonicCode = Mnemonics.MnemonicCode(Mnemonics.WordCount.COUNT_12)
  ```
- **Validation (`KeyManager.kt:38-55`)**:
  Validates input words against the official BIP-39 English wordlist and checks the 4-bit checksum.
- **Seed Derivation (`KeyManager.kt:60-63`)**:
  Derives a 64-byte binary seed using PBKDF2-HMAC-SHA512 (2048 iterations):
  ```kotlin
  val seed = code.toSeed(passphrase.toCharArray())
  ```

### 2.2 Deterministic Key Derivation via HKDF-SHA256

From the 64-byte binary seed, all long-term cryptographic parameters are deterministically generated using HKDF (RFC 5869):

```
                       +-----------------------+
                       |  64-Byte BIP-39 Seed  |
                       +-----------+-----------+
                                   |
         +-------------------------+-------------------------+
         |                                                   |
         v                                                   v
   HKDF-Extract                                        HKDF-Extract
(Salt: 32 Zero Bytes)                               (Salt: 32 Zero Bytes)
         |                                                   |
         v                                                   v
     PRK_Identity                                         PRK_RegID
         |                                                   |
    HKDF-Expand                                         HKDF-Expand
(Info: "GhostMessenger/IdentityKey/v1", L=32)       (Info: "GhostMessenger/RegistrationId/v1", L=32)
         |                                                   |
         v                                                   v
  Curve25519 Private Key                              14-Bit Clamped ID
         |                                            ((raw % 16380) + 1)
         v                                                   |
  Curve25519 Public Key                                      v
         |                                            Registration ID
         v                                                (1..16380)
  SHA-256 (32 Bytes)
         |
    First 5 Bytes (40 Bits)
         |
    Base32 (RFC 4648)
         |
         v
 UserCode: "XXXX-XXXX"
```

1. **Identity Keypair (`KeyManager.kt:111-122`)**:
   - `info = "GhostMessenger/IdentityKey/v1"`
   - Output: 32 private key bytes decoded via `Curve.decodePrivatePoint(privBytes)` into a Curve25519 `IdentityKeyPair`.
2. **Registration ID (`KeyManager.kt:127-135`)**:
   - `info = "GhostMessenger/RegistrationId/v1"`
   - Extracted 16-bit integer is clamped to a 14-bit unsigned integer in range `1..16380`:
     ```kotlin
     val raw = (((idBytes[0].toInt() and 0x3F) shl 8) or (idBytes[1].toInt() and 0xFF))
     return (raw % 16380) + 1
     ```
3. **UserCode Derivation (`UserCodeUtils.kt:20-28`)**:
   - Computes `SHA-256(publicKey.serialize())`.
   - Extracts the first 5 bytes (40 bits).
   - Encodes via unpadded Base32 (alphabet `A-Z2-7`), producing an 8-character string formatted as `XXXX-XXXX`.

---

## 3. Signal Protocol Implementation

Calypso implements the **Signal Protocol** using the official `org.signal:libsignal-android` library.

### 3.1 PreKey Generation and Management

PreKeys allow asynchronous session initiation when the recipient is offline.

- **Batch Generation (`SignalCryptoManager.kt:39-81`)**:
  - **One-Time PreKeys**: 50 ephemeral Curve25519 keypairs stored locally in `SqliteSignalProtocolStore` and uploaded to the signaling server.
  - **Signed PreKey**: A Curve25519 keypair signed by the user's Curve25519 Identity Key using `Curve.calculateSignature()`.
- **Signed PreKey Rotation (`SignalCryptoManager.kt:86-123`)**:
  - Signed PreKeys have a maximum age of 7 days (`7L * 24 * 60 * 60 * 1000L`).
  - Upon expiration, `rotateSignedPreKey()` generates a new signed prekey with an incremented ID, signs it with the identity key, stores it locally, and prunes older records, maintaining only the two most recent keys.
- **Replenishment (`MessageRepository.kt:160-184`)**:
  - At startup, the client queries the server for its remaining one-time PreKey count. If the pool drops below threshold, a fresh batch is generated and uploaded.

### 3.2 X3DH Handshake (Session Building)

When Alice initiates a conversation with Bob (`SignalCryptoManager.kt:128-152`):
1. Alice requests Bob's PreKey bundle from the signaling server (`PreKeyApiClient.kt:57-83`).
2. Bob's bundle includes:
   - `identityKey` ($IK_B$)
   - `signedPreKey` ($SPK_B$) + signature ($Sig_B$)
   - `preKey` ($OPK_B$, optional one-time key)
   - `registrationId`
3. Alice verifies Bob's signature on $SPK_B$ using $IK_B$:
   $$\text{Verify}(IK_B, SPK_B, Sig_B) = \text{true}$$
4. Alice performs Extended Triple Diffie-Hellman (X3DH):
   $$DH_1 = \text{DH}(IK_A, SPK_B)$$
   $$DH_2 = \text{DH}(EK_A, IK_B)$$
   $$DH_3 = \text{DH}(EK_A, SPK_B)$$
   $$DH_4 = \text{DH}(EK_A, OPK_B) \quad \text{(if } OPK_B \text{ was available)}$$
   $$\text{MasterKey} = \text{KDF}(DH_1 \parallel DH_2 \parallel DH_3 \parallel DH_4)$$
5. Alice passes the bundle to `SessionBuilder(store, remoteAddress).process(preKeyBundle)`.
6. Alice's initial message is serialized as a `PreKeySignalMessage` containing Alice's ephemeral public key $EK_A$ and identity key $IK_A$.

### 3.3 Double Ratchet Algorithm (Per-Message Ratchet)

Subsequent messages use the Double Ratchet:
- **KDF Chain Ratchet (Symmetric)**: Each sent or received message advances the sending or receiving chain key, deriving a fresh 256-bit message encryption key. Once used, the message key is deleted immediately, providing **Forward Secrecy**.
- **DH Ratchet (Asymmetric)**: Every round trip includes a new ephemeral Curve25519 Diffie-Hellman public key in the message header. When received, the ratchet advances the root key, providing **Post-Compromise Security** (break-in recovery).

### 3.4 Wire Format: EncryptedEnvelope

All payloads transmitted over WebRTC DataChannels or signaling relays are encapsulated in `EncryptedEnvelope` (`core/model/EncryptedEnvelope.kt:17-33`):

```kotlin
@Serializable
data class EncryptedEnvelope(
    val type: Int,
    val senderUserCode: String,
    val recipientUserCode: String,
    val messageId: String,
    val ciphertext: String, // Base64-encoded serialized Signal Protocol message
    val timestamp: Long = System.currentTimeMillis()
)
```

**Type Identifiers**:
- `TYPE_PREKEY_SIGNAL_MESSAGE (1)`: First message of a session, carrying X3DH parameters.
- `TYPE_SIGNAL_MESSAGE (2)`: Standard Double Ratchet encrypted message.
- `TYPE_DELIVERY_ACK (3)`: End-to-end encrypted delivery confirmation.
- `TYPE_READ_RECEIPT (4)`: End-to-end encrypted read status confirmation.

---

## 4. End-to-End Encrypted Control Signals

To eliminate metadata leakage over transport channels, delivery acknowledgments and read receipts are themselves encrypted through the active Signal Protocol Double Ratchet session (`SignalCryptoManager.kt:196-216`):

```kotlin
fun encryptControlMessage(
    senderUserCode: String,
    recipientUserCode: String,
    envelopeMessageId: String,
    payload: String, // target messageId
    type: Int        // TYPE_DELIVERY_ACK or TYPE_READ_RECEIPT
): EncryptedEnvelope {
    val address = SignalProtocolAddress(recipientUserCode, 1)
    val cipher = SessionCipher(signalProtocolStore, address)
    val plaintextBytes = payload.toByteArray(StandardCharsets.UTF_8)
    val ciphertextMessage = cipher.encrypt(plaintextBytes)

    return EncryptedEnvelope(
        type = type,
        senderUserCode = senderUserCode,
        recipientUserCode = recipientUserCode,
        messageId = envelopeMessageId,
        ciphertext = base64Encode(ciphertextMessage.serialize()),
        timestamp = System.currentTimeMillis()
    )
}
```

### Security Benefits
1. Eavesdroppers on the signaling relay cannot inspect which message ID was acknowledged or read.
2. The control signals advance the Double Ratchet state, reinforcing cryptographic ratchet freshness.
3. Forged control signals from unauthorized third parties are rejected because they cannot produce valid ratchet ciphertexts.

---

## 5. Contact Verification: Cryptographic Safety Numbers

To protect users against adversary-in-the-middle (MitM) key substitutions on the signaling server, Calypso provides 30-digit numeric fingerprints (`SafetyNumberGenerator.kt:15-50`).

### 5.1 Fingerprint Computation Algorithm

1. **Deterministic Canonical Sorting**:
   The two UserCodes are compared lexicographically to guarantee that both Alice and Bob compute the exact same fingerprint regardless of who initiates:
   ```kotlin
   val (firstCode, firstKey, secondCode, secondKey) = if (localUserCode <= remoteUserCode) {
       Tuple4(localUserCode, localIdentityKey, remoteUserCode, remoteIdentityKey)
   } else {
       Tuple4(remoteUserCode, remoteIdentityKey, localUserCode, localIdentityKey)
   }
   ```
2. **SHA-512 Hash Computation**:
   A single SHA-512 digest is computed across the concatenated elements:
   $$\text{Digest} = \text{SHA-512}(Code_1 \parallel Key_1 \parallel Code_2 \parallel Key_2)$$
3. **Decimal Group Slicing**:
   Five 32-bit big-endian integers are extracted from the first 20 bytes of the digest:
   ```kotlin
   for (i in 0 until 5) {
       val offset = i * 4
       val b0 = hash[offset].toInt() and 0xFF
       val b1 = hash[offset + 1].toInt() and 0xFF
       val b2 = hash[offset + 2].toInt() and 0xFF
       val b3 = hash[offset + 3].toInt() and 0xFF

       val intVal = ((b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3) and 0x7FFFFFFF
       val formatted = "%06d".format(intVal % 1_000_000)
       groups.add(formatted)
   }
   ```
4. **Output Format**:
   Joined with whitespace into 5 groups of 6 digits (total 30 digits):
   ```
   123456 789012 345678 901234 567890
   ```

### 5.2 Verification Lifecycle & Identity Change Detection

- **Trust-On-First-Use (TOFU)**:
  On the first session with a peer, the peer's public identity key is recorded in `SignalDao` (`signal_identities` table).
- **Identity Key Change Handling (`MessageRepository.kt:381-388`)**:
  If a peer re-registers with a different identity key, libsignal throws `UntrustedIdentityException`.
  Calypso intercepts this exception:
  1. The message is dropped and NOT persisted.
  2. `SecurityEvent.IdentityChanged(peerUserCode)` is emitted to the UI.
  3. A persistent security banner is displayed on `ChatScreen.kt:95-135`, alerting the user to re-verify the Safety Number.

---

## 6. Local Storage Security at Rest

All data persisted on the Android client device is encrypted.

### 6.1 SQLCipher Database Encryption

- **Database Engine**: Room with `net.zetetic:sqlcipher-android` (`DatabaseModule.kt:28-44`, `AppDatabase.kt:40-105`).
- **Algorithm**: AES-256 in CBC mode with HMAC-SHA256 page integrity checks.
- **Native JNI Initialization**: Invokes `System.loadLibrary("sqlcipher")` in `GhostMessengerApp.onCreate()` and `AppDatabase.companion object init` to link native cryptographic routines (`SQLiteConnection.nativeOpen`) into the ART runtime before database opening.
- **Tables Encrypted**:
  - `messages`: All chat message contents, timestamps, statuses, and delivery states.
  - `conversations`: Conversation summaries and unread counters.
  - `signal_prekeys`: Active one-time prekey private/public records.
  - `signal_signed_prekeys`: Signed prekey private/public records.
  - `signal_sessions`: Double Ratchet active session state, chain keys, and root keys.
  - `signal_identities`: Trusted remote identity keys.
  - `signal_sender_keys`: Signal group sender key distribution records (added in Schema v2 via `MIGRATION_1_2`).

### 6.2 Key Management via Android Keystore

- The database encryption passphrase is generated using `SecureRandom` (32 bytes / 256 bits).
- It is saved in `EncryptedSharedPreferences` (`SecurePreferences.kt:26-55`), which uses:
  - **Key encryption**: AES-256-SIV (Deterministic Authenticated Encryption).
  - **Value encryption**: AES-256-GCM.
  - **Master Key**: Managed by the hardware-backed **Android Keystore System** (TEE or StrongBox Keymaster where available).
- **Self-Healing Keystore Desynchronization**: If the master key is invalidated by OS restore or Keystore corruption throwing `AEADBadTagException`, `createEncryptedPrefs()` automatically deletes corrupted preferences files, creates a fresh master key, and restores operational stability without crash loops.
- The user's BIP-39 mnemonic is similarly stored in `EncryptedSharedPreferences` and cleared from memory when not in use.

---

## 7. Zero-Knowledge Push Notification Isolation (FCM)

Calypso isolates the push notification infrastructure (Google Play Services / Firebase Cloud Messaging) completely from the cryptographic boundary:

1. **Zero Cryptographic Material**: FCM push notifications contain zero encryption keys, zero ciphertext payloads, zero initialization vectors, and zero message identifiers.
2. **Strict Ephemeral Ping**: The wire payload consists entirely of `{"type": "wake_up"}`.
3. **Decoupled Key Exchange**: All end-to-end decryption occurs exclusively through the Double Ratchet pipeline after the client establishes a direct, TLS-encrypted connection to the signaling server or direct WebRTC peer.
4. **Untrusted Transit**: Even in the event of compromised Google Play Services or intermediate routing compromise, adversaries learn nothing about conversations, sender identities, or message content.

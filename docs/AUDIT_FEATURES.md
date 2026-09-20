# Calypso — Source Code Feature Audit

**Auditor**: AI Agent (read-only source audit, no code executed)
**Date**: 2026-09-20
**Scope**: All tracked source files in `android/` and `server/`
**Method**: Line-by-line source reading. No docs consulted; evidence is solely from code.

---

## Verification Matrix

### 1. BIP-39 Seed Generation, Restore, and Deterministic Identity/UserCode Derivation

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| 12-word BIP-39 generation | **VERIFIED** | [KeyManager.kt:30-33](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt#L30-L33) | Uses `Mnemonics.MnemonicCode(WordCount.COUNT_12)` from `cash.z.ecc.android.bip39` |
| BIP-39 validation (checksum + wordlist) | **VERIFIED** | [KeyManager.kt:47-55](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt#L47-L55) | Calls `code.validate()` which verifies both dictionary and checksum |
| Seed derivation (PBKDF2-HMAC-SHA512) | **VERIFIED** | [KeyManager.kt:60-63](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt#L60-L63) | `code.toSeed()` delegates to BIP-39 standard PBKDF2 |
| Deterministic identity key derivation | **VERIFIED** | [KeyManager.kt:111-122](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt#L111-L122) | Custom HKDF-SHA256 → `Curve.decodePrivatePoint()` → Curve25519 keypair. Deterministic from seed |
| Deterministic registration ID | **VERIFIED** | [KeyManager.kt:127-135](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt#L127-L135) | HKDF-SHA256 → 14-bit clamped `(raw % 16380) + 1` |
| Deterministic UserCode derivation | **VERIFIED** | [UserCodeUtils.kt:20-28](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/UserCodeUtils.kt#L20-L28) | `SHA-256(pubKey) → first 5 bytes → Base32 → XXXX-XXXX`. Deterministic from public key |
| Restore from seed phrase | **VERIFIED** | [OnboardingViewModel.kt:87-97](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/onboarding/OnboardingViewModel.kt#L87-L97) | `KeyManager.deriveIdentity(input)` re-derives identical identity from seed |
| Seed stored in EncryptedSharedPreferences | **VERIFIED** | [SecurePreferences.kt:60-66](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/prefs/SecurePreferences.kt#L60-L66) | Mnemonic plaintext saved via `EncryptedSharedPreferences` (AES-256-GCM keys, AES-256-SIV pref keys) |

> [!NOTE]
> **Overall**: **VERIFIED**. The full BIP-39 → HKDF → Curve25519 → UserCode chain is deterministic and implemented correctly.

---

### 2. Signal Protocol: X3DH + Double Ratchet Used for Every Message

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| X3DH session building from PreKey bundle | **VERIFIED** | [SignalCryptoManager.kt:86-110](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L86-L110) | Constructs `PreKeyBundle`, calls `SessionBuilder.process()` — standard X3DH |
| Encryption via `SessionCipher.encrypt()` | **VERIFIED** | [SignalCryptoManager.kt:123-149](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L123-L149) | Every outgoing message goes through `cipher.encrypt(plaintextBytes)` producing `PreKeySignalMessage` or `SignalMessage` |
| Decryption via `SessionCipher.decrypt()` | **VERIFIED** | [SignalCryptoManager.kt:154-170](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L154-L170) | Dispatches on `TYPE_PREKEY_SIGNAL_MESSAGE` vs `TYPE_SIGNAL_MESSAGE` |
| Auto-session establishment before send | **VERIFIED** | [MessageRepository.kt:154-165](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L154-L165) | Checks `hasSession()`, fetches PreKey bundle if missing, calls `buildSession()` before every `encryptMessage()` |
| No plaintext path exists | **PARTIAL** | [MessageRepository.kt:287-303](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L287-L303) | ⚠️ **Delivery ACK envelopes are sent UNENCRYPTED** — `ciphertext = ""`, `type = TYPE_DELIVERY_ACK`. The ACK leaks `senderUserCode`, `recipientUserCode`, and `messageId` in cleartext JSON over the signaling relay |
| libsignal-android used (not a reimplementation) | **VERIFIED** | [build.gradle.kts](file:///e:/Github/Ghost%20Messenger/android/app/build.gradle.kts) | `implementation(libs.libsignal.android)` — official Signal library |

> [!WARNING]
> **Overall**: **PARTIAL**. All content messages are properly encrypted via Signal Protocol. However, delivery ACKs bypass encryption entirely and leak metadata through the server.

---

### 3. PreKey Bundle Upload/Fetch, One-Time PreKey Consumption, Signed PreKey Rotation

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| PreKey bundle generation (50 one-time + 1 signed) | **VERIFIED** | [SignalCryptoManager.kt:39-81](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L39-L81) | Generates 50 Curve25519 one-time prekeys and 1 signed prekey |
| PreKey upload via REST | **VERIFIED** | [PreKeyApiClient.kt:30-55](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/network/api/PreKeyApiClient.kt#L30-L55), [prekeys.js:16-40](file:///e:/Github/Ghost%20Messenger/server/src/routes/prekeys.js#L16-L40) | `POST /api/prekeys/upload` |
| PreKey fetch via REST | **VERIFIED** | [PreKeyApiClient.kt:60-83](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/network/api/PreKeyApiClient.kt#L60-L83), [prekeys.js:47-62](file:///e:/Github/Ghost%20Messenger/server/src/routes/prekeys.js#L47-L62) | `GET /api/prekeys/:userCode` |
| One-time prekey atomic consumption | **VERIFIED** | [InMemoryPreKeyStore.js:58-80](file:///e:/Github/Ghost%20Messenger/server/src/store/InMemoryPreKeyStore.js#L58-L80) | `record.preKeys.shift()` pops one prekey per fetch. Verified by [server.test.js:36-50](file:///e:/Github/Ghost%20Messenger/server/test/server.test.js#L36-L50) |
| Signed prekey rotation | **MISSING** | [SignalCryptoManager.kt:57](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L57) | `signedPreKeyId` is **hardcoded to `1`**. No rotation logic, no periodic re-generation, no timestamp-based expiry check |
| PreKey replenishment when exhausted | **MISSING** | Entire codebase | No logic to detect when the server's one-time prekey pool runs low and re-upload |

> [!WARNING]
> **Overall**: **PARTIAL**. Upload/fetch/consume works correctly. Signed prekey rotation and prekey replenishment are completely missing.

---

### 4. Safety Number Generation and Verification UI

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| Safety number generation code | **MISSING** | No file | No `NumericFingerprint`, `FingerprintGenerator`, SHA-512, or safety number logic exists anywhere in the source code |
| Safety number UI / verification screen | **MISSING** | No file | The `isVerified` field on [ConversationEntity.kt:16](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/entities/ConversationEntity.kt#L16) and `setVerified()` DAO method on [ConversationDao.kt:40-41](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/dao/ConversationDao.kt#L40-L41) exist as database plumbing but are **never called** from any UI or logic code |
| QR code verification | **MISSING** | No file | No QR code scanner or generator is in the codebase |

> [!CAUTION]
> **Overall**: **MISSING**. Safety number verification is entirely absent. Users have no way to verify that they are communicating with the intended party and not a MITM.

---

### 5. WebRTC DataChannel Establishment, ICE/STUN/TURN Config, Reconnection

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| PeerConnection creation with ICE servers | **VERIFIED** | [WebRtcManager.kt:48-76](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L48-L76) | 3 STUN servers + 4 TURN servers (UDP, TCP, TLS) configured |
| TURN credentials | **VERIFIED** (but hardcoded) | [WebRtcManager.kt:57-75](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L57-L75) | ⚠️ TURN username and password are **hardcoded in plain text** in source code committed to a public repo |
| SDP Offer/Answer flow | **VERIFIED** | [WebRtcManager.kt:115-220](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L115-L220) | `createOffer()`, `createAnswer()`, `setRemoteAnswer()` all correctly use `suspendCancellableCoroutine` |
| Ordered reliable DataChannel | **VERIFIED** | [WebRtcManager.kt:265-273](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L265-L273) | `ordered = true`, `maxRetransmits = -1` (reliable SCTP) |
| ICE candidate relay | **VERIFIED** | [MessageRepository.kt:312-319](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L312-L319), [signalingHandler.js:81-91](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L81-L91) | Candidates relayed via Socket.IO with anti-spoofing |
| WebRTC reconnection handling | **MISSING** | [WebRtcManager.kt:251-257](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L251-L257) | On `FAILED` or `CLOSED` state, the code only updates the state flow. **No automatic reconnection attempt** is made |
| Socket.IO signaling reconnection | **VERIFIED** | [SignalingClient.kt:68-70](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/network/socket/SignalingClient.kt#L68-L70) | `reconnection = true`, `reconnectionAttempts = Int.MAX_VALUE`, `reconnectionDelay = 1000` |

> [!WARNING]
> **Overall**: **PARTIAL**. WebRTC works but has no reconnection logic and contains hardcoded TURN credentials in public source.

---

### 6. Message Send/Receive/Delivery Status, Ordering, Duplicate Handling, Out-of-Order Ratchet

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| Message send + encrypt + dispatch | **VERIFIED** | [MessageRepository.kt:147-227](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L147-L227) | Encrypts → tries DataChannel → falls back to relay → saves to DB |
| Message receive + decrypt + persist | **VERIFIED** | [MessageRepository.kt:232-284](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L232-L284) | Decrypts envelope, inserts message, updates conversation |
| Delivery ACK | **PARTIAL** | [MessageRepository.kt:280-303](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L280-L303) | ACK sent back to sender updating status to `DELIVERED`. But ACK itself is **not encrypted** (see §2) |
| Read receipts | **STUBBED** | [EncryptedEnvelope.kt:22](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/model/EncryptedEnvelope.kt#L22) | `TYPE_READ_RECEIPT = 4` is defined but **never sent or handled** |
| Message ordering | **PARTIAL** | [MessageDao.kt:13](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/dao/MessageDao.kt#L13) | `ORDER BY timestamp ASC` — ordered by sender timestamp only. No sequence number. Clock skew between peers can cause misordering |
| Duplicate message handling | **PARTIAL** | [MessageDao.kt:22-23](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/dao/MessageDao.kt#L22-L23) | `OnConflictStrategy.REPLACE` on PK `id` (UUID). Silently overwrites duplicates if received from both relay and DataChannel simultaneously |
| Out-of-order ratchet handling | **VERIFIED** | Handled by `libsignal-android` | Signal Protocol library internally handles out-of-order message decryption via its message key cache |

> [!NOTE]
> **Overall**: **PARTIAL**. Core send/receive works. Read receipts are stubbed, ordering relies on timestamps, and delivery ACKs are unencrypted.

---

### 7. SQLCipher DB Encryption + Key in Keystore-Backed EncryptedSharedPreferences

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| SQLCipher integration | **VERIFIED** | [AppDatabase.kt:56-61](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/db/AppDatabase.kt#L56-L61) | `SupportOpenHelperFactory(passphrase)` from `net.zetetic.database.sqlcipher` |
| AES-256 database encryption at rest | **VERIFIED** | [AppDatabase.kt:25](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/db/AppDatabase.kt#L25) | `net.zetetic:sqlcipher-android` |
| Passphrase generated via SecureRandom | **VERIFIED** | [SecurePreferences.kt:43-55](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/prefs/SecurePreferences.kt#L43-L55) | 32-byte `SecureRandom()` passphrase, hex-encoded |
| Passphrase stored in EncryptedSharedPreferences | **VERIFIED** | [SecurePreferences.kt:26-38](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/prefs/SecurePreferences.kt#L26-L38) | `MasterKey.KeyScheme.AES256_GCM` → `AES256_SIV` key encryption + `AES256_GCM` value encryption |
| DI wiring | **VERIFIED** | [DatabaseModule.kt:29-32](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/di/DatabaseModule.kt#L29-L32) | `securePreferences.getOrGenerateDbPassphrase()` → `AppDatabase.buildEncrypted()` |

> [!TIP]
> **Overall**: **VERIFIED**. The full chain from SecureRandom → EncryptedSharedPreferences → SQLCipher is correctly wired.

---

### 8. "Server Never Sees Plaintext" + Fallback Signaling Path

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| Content messages encrypted before dispatch | **VERIFIED** | [MessageRepository.kt:168-175](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L168-L175) | `signalCryptoManager.encryptMessage()` always called |
| Server does not decrypt payloads | **VERIFIED** | [signalingHandler.js:96-118](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L96-L118) | Server blindly relays `data.envelope` |
| **Metadata leakage in relay** | **BROKEN** | [signalingHandler.js:109-112](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L109-L112) | Server sees `fromUserCode` alongside the envelope. Envelope JSON also contains `senderUserCode`, `recipientUserCode`, `messageId`, and `timestamp` as **unencrypted** fields |
| **Delivery ACKs leak through server** | **BROKEN** | [MessageRepository.kt:289-294](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L289-L294) | ACK has `ciphertext = ""` — entirely plaintext JSON |

> [!CAUTION]
> **Overall**: **PARTIAL**. Message *content* is never plaintext on the server, but *envelope metadata* (who, when, messageId) is fully visible. Delivery ACKs are entirely unencrypted.

---

### 9. Anti-Spoofing in signalingHandler.js

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| Server derives `fromUserCode` from socket registration | **VERIFIED** | [signalingHandler.js:52](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L52), [:67](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L67), [:82](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L82), [:97](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L97) | All event handlers use `presenceManager.getUserCode(socket.id)` |
| Null check for unregistered sockets | **VERIFIED** | [signalingHandler.js:55](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L55) | `if (!fromUserCode ...) return` |
| **Registration has no challenge-response** | **PARTIAL** | [signalingHandler.js:12-31](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L12-L31) | ⚠️ Any client can register **any** UserCode. No cryptographic proof the registrant holds the private key |
| **Multiple sockets per UserCode** | **PARTIAL** | [PresenceManager.js:30-34](file:///e:/Github/Ghost%20Messenger/server/src/store/PresenceManager.js#L30-L34) | Multiple sockets can claim the same UserCode |

> [!CAUTION]
> **Overall**: **PARTIAL**. Server prevents spoofing in subsequent events, but **registration itself is unauthenticated** — anyone can claim any UserCode.

---

### 10. TODO/FIXME/Mock/Hardcoded/Placeholder/Dead Code

| Finding | Status | Evidence | Notes |
|---|---|---|---|
| **Hardcoded TURN credentials** | **BROKEN** | [WebRtcManager.kt:58-75](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L58-L75) | Metered TURN username and password in plain text |
| **Hardcoded signed prekey ID** | **STUBBED** | [SignalCryptoManager.kt:57](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L57) | `val signedPreKeyId = 1` — never rotated |
| **Fallback identity creates random throwaway** | **BROKEN** | [RepositoryModule.kt:33-40](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/di/RepositoryModule.kt#L33-L40) | Random identity generated before onboarding if no saved identity exists |
| **SenderKeyStore in-memory only** | **STUBBED** | [SqliteSignalProtocolStore.kt:64](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/crypto/SqliteSignalProtocolStore.kt#L64) | `ConcurrentHashMap` not backed by Room; lost on process death |
| **Silent exception swallowing** (4 sites) | **BROKEN** | [MessageRepository.kt:282](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L282), [:321](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L321), [:337](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L337), [:345](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L345) | `catch (_: Exception) {}` in critical paths |
| **TYPE_READ_RECEIPT dead code** | **STUBBED** | [EncryptedEnvelope.kt:22](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/model/EncryptedEnvelope.kt#L22) | Defined but never used |
| **`isVerified` field never set** | **STUBBED** | [ConversationEntity.kt:16](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/entities/ConversationEntity.kt#L16) | DB plumbing exists, never called |
| **No logging in production** | **VERIFIED** | Entire `src/main` | Zero `Log.*` or `println` calls — clean |
| **Mock objects in test only** | **VERIFIED** | Test source sets | Appropriate test doubles |

---

## 🔴 Top 10 Risks (Ranked by Severity)

| Rank | Risk | Severity | Evidence |
|---|---|---|---|
| **1** | **No authentication on UserCode registration** — any socket can claim any UserCode and receive that user's messages (MITM/impersonation) | 🔴 **CRITICAL** | [signalingHandler.js:12-31](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L12-L31) |
| **2** | **Safety number verification completely missing** — users have no way to detect MITM identity substitution | 🔴 **CRITICAL** | No files implement this feature |
| **3** | **Hardcoded TURN credentials in public source** — attackers can abuse the relay, or the provider can revoke them at any time | 🔴 **HIGH** | [WebRtcManager.kt:57-75](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L57-L75) |
| **4** | **Delivery ACKs sent unencrypted** through signaling relay, leaking sender/recipient UserCodes and message IDs | 🟠 **HIGH** | [MessageRepository.kt:289-294](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L289-L294) |
| **5** | **Envelope metadata visible to server** on fallback relay — who, when, and message IDs are cleartext JSON fields | 🟠 **HIGH** | [signalingHandler.js:109-112](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L109-L112) |
| **6** | **Fallback identity on first launch** generates random throwaway identity before onboarding, potentially creating unrecoverable Signal sessions | 🟠 **MEDIUM** | [RepositoryModule.kt:33-40](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/di/RepositoryModule.kt#L33-L40) |
| **7** | **No signed prekey rotation** — `signedPreKeyId` hardcoded to `1`, never rotated; weakens forward secrecy | 🟠 **MEDIUM** | [SignalCryptoManager.kt:57](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L57) |
| **8** | **No prekey replenishment** — once 50 one-time prekeys consumed, all new contacts use signed-prekey-only X3DH indefinitely | 🟠 **MEDIUM** | No replenishment logic exists |
| **9** | **Silent exception swallowing** in 4 critical error paths — failures invisible, messages silently lost | 🟡 **MEDIUM** | [MessageRepository.kt:282](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L282), [:321](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L321), [:337](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L337), [:345](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L345) |
| **10** | **No WebRTC DataChannel reconnection** — failed P2P connections remain dead; no offline queueing | 🟡 **MEDIUM** | [WebRtcManager.kt:251-257](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L251-L257) |

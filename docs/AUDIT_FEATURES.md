# Calypso — Source Code Feature Audit

**Auditor**: AI Agent  
**Date**: October 2, 2026  
**Scope**: All source files in `android/` and `server/`  
**Method**: Line-by-line source reading and test-backed execution verification.

---

## Verification Matrix

### 1. BIP-39 Seed Generation, Restore, and Deterministic Identity/UserCode Derivation

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| 12-word BIP-39 generation | **VERIFIED** | [KeyManager.kt:30-33](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt#L30-L33) | Uses `Mnemonics.MnemonicCode(WordCount.COUNT_12)` from `cash.z.ecc.android.bip39` |
| BIP-39 validation (checksum + wordlist) | **VERIFIED** | [KeyManager.kt:47-55](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt#L47-L55) | Calls `code.validate()` verifying both dictionary and checksum |
| Seed derivation (PBKDF2-HMAC-SHA512) | **VERIFIED** | [KeyManager.kt:60-63](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt#L60-L63) | `code.toSeed()` delegates to BIP-39 standard PBKDF2 |
| Deterministic identity key derivation | **VERIFIED** | [KeyManager.kt:111-122](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt#L111-L122) | HKDF-SHA256 → `Curve.decodePrivatePoint()` → Curve25519 keypair. Deterministic from seed |
| Deterministic registration ID | **VERIFIED** | [KeyManager.kt:127-135](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt#L127-L135) | HKDF-SHA256 → 14-bit clamped `(raw % 16380) + 1` |
| Deterministic UserCode derivation | **VERIFIED** | [UserCodeUtils.kt:20-28](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/UserCodeUtils.kt#L20-L28) | `SHA-256(pubKey) → first 5 bytes → Base32 → XXXX-XXXX` |
| Restore from seed phrase | **VERIFIED** | [OnboardingViewModel.kt:87-97](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/onboarding/OnboardingViewModel.kt#L87-L97) | `KeyManager.deriveIdentity(input)` re-derives identical identity from seed |
| Seed stored in EncryptedSharedPreferences | **VERIFIED** | [SecurePreferences.kt:60-66](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/prefs/SecurePreferences.kt#L60-L66) | Mnemonic saved via `EncryptedSharedPreferences` (AES-256-GCM + AES-256-SIV) |

> [!NOTE]
> **Overall**: **VERIFIED**. Full deterministic cryptographic key derivation chain tested and verified.

---

### 2. Signal Protocol: X3DH + Double Ratchet Used for Every Message

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| X3DH session building from PreKey bundle | **VERIFIED** | [SignalCryptoManager.kt:118-142](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L118-L142) | Constructs `PreKeyBundle`, calls `SessionBuilder.process()` |
| Encryption via `SessionCipher.encrypt()` | **VERIFIED** | [SignalCryptoManager.kt:155-181](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L155-L181) | Outgoing messages encrypted into `PreKeySignalMessage` or `SignalMessage` |
| Decryption via `SessionCipher.decrypt()` | **VERIFIED** | [SignalCryptoManager.kt:216-243](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L216-L243) | Dispatches on `TYPE_PREKEY_SIGNAL_MESSAGE`, `TYPE_SIGNAL_MESSAGE`, `TYPE_DELIVERY_ACK`, and `TYPE_READ_RECEIPT` |
| Auto-session establishment before send | **VERIFIED** | [MessageRepository.kt:208-222](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L208-L222) | Checks `hasSession()`, fetches PreKey bundle if missing, calls `buildSession()` before send |
| Delivery ACKs encrypted | **VERIFIED** | [MessageRepository.kt:370-410](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L370-L410) | Encrypted via `signalCryptoManager.encryptControlMessage()` through active session |
| Read Receipts encrypted | **VERIFIED** | [MessageRepository.kt:412-445](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L412-L445) | Encrypted via `signalCryptoManager.encryptControlMessage()` through active session |
| libsignal-android used | **VERIFIED** | [build.gradle.kts](file:///e:/Github/Ghost%20Messenger/android/app/build.gradle.kts) | Official `org.signal:libsignal-android` |

> [!NOTE]
> **Overall**: **VERIFIED**. All payloads (content messages, ACKs, and read receipts) are end-to-end encrypted through the Signal ratchet pipeline.

---

### 3. PreKey Bundle Upload/Fetch, Consumption, and Signed PreKey Rotation

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| PreKey bundle generation | **VERIFIED** | [SignalCryptoManager.kt:39-81](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L39-L81) | Generates 50 Curve25519 one-time prekeys and 1 signed prekey |
| PreKey upload & fetch via REST | **VERIFIED** | [PreKeyApiClient.kt:30-83](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/network/api/PreKeyApiClient.kt#L30-L83), [prekeys.js](file:///e:/Github/Ghost%20Messenger/server/src/routes/prekeys.js) | Endpoints `POST /api/prekeys` and `GET /api/prekeys/:userCode` |
| One-time prekey atomic consumption | **VERIFIED** | [InMemoryPreKeyStore.js:70-85](file:///e:/Github/Ghost%20Messenger/server/src/store/InMemoryPreKeyStore.js#L70-L85) | `record.preKeys.shift()` pops one prekey atomically |
| Signed prekey rotation | **VERIFIED** | [SignalCryptoManager.kt:83-126](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt#L83-L126), [MessageRepository.kt:185-202](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L185-L202) | Dynamic ID incrementation, 7-day TTL expiration check, pruned store |
| PreKey replenishment | **VERIFIED** | [MessageRepository.kt:160-184](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L160-L184) | Checks server pool count on startup and re-uploads when pool falls below threshold |

---

### 4. Safety Number Generation and Verification UI

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| Safety number generation | **VERIFIED** | [SafetyNumberGenerator.kt:15-47](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SafetyNumberGenerator.kt#L15-L47) | Symmetric SHA-512 fingerprint formatted into 5 groups of 6 digits |
| Safety number verification UI | **VERIFIED** | [VerificationScreen.kt](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/verification/VerificationScreen.kt) | Full screen displaying 30-digit fingerprint, verification toggle, and explanation card |
| Chat verification integration | **VERIFIED** | [ChatScreen.kt:180-190](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/chat/ChatScreen.kt#L180-L190) | Shield icon button in top bar indicating verified status and navigating to verification screen |
| TOFU identity change warning | **VERIFIED** | [ChatScreen.kt:95-135](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/chat/ChatScreen.kt#L95-L135) | Security warning banner alert on `SecurityEvent.IdentityChanged` with quick-verify action |

---

### 5. WebRTC DataChannel Establishment, ICE/STUN/TURN Config, Reconnection

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| PeerConnection creation with ICE servers | **VERIFIED** | [WebRtcManager.kt:48-75](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L48-L75) | Google Public STUN + Metered STUN; dynamic TURN from secure storage |
| Zero hardcoded secrets | **VERIFIED** | [WebRtcManager.kt:55-70](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L55-L70) | All hardcoded credentials eliminated; configured via `SecurePreferences` |
| Ordered reliable DataChannel | **VERIFIED** | [WebRtcManager.kt:295-305](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L295-L305) | `ordered = true`, `maxRetransmits = -1` (reliable SCTP) |
| WebRTC exponential backoff reconnection | **VERIFIED** | [WebRtcManager.kt:265-285](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L265-L285) | Exponential retry backoff (2s, 4s, 8s) up to 3 attempts on `FAILED`/`CLOSED` |
| Offline relay message buffering | **VERIFIED** | [OfflineQueueStore.js](file:///e:/Github/Ghost%20Messenger/server/src/store/OfflineQueueStore.js), [signalingHandler.js:140-148](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L140-L148) | Server buffers up to 50 envelopes per user with 24h TTL; flushes on socket registration |

---

### 6. Message Delivery Status & Duplicate Handling

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| Message send/receive/persist | **VERIFIED** | [MessageRepository.kt](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt) | Encrypts → DataChannel / relay fallback → persists to SQLCipher Room DB |
| Delivery ACK status updates | **VERIFIED** | [MessageRepository.kt:312-325](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L312-L325) | Updates status to `STATUS_DELIVERED` upon receiving decrypted ACK |
| Read receipt status updates | **VERIFIED** | [MessageRepository.kt:327-340](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt#L327-L340) | Updates status to `STATUS_READ` upon receiving decrypted read receipt |
| Read receipt UI indicators | **VERIFIED** | [ChatScreen.kt:255-265](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/ui/chat/ChatScreen.kt#L255-L265) | Double checkmark glyphs rendered in NeonCyan for delivered, GhostGreen for read |
| Duplicate message handling | **VERIFIED** | [MessageDao.kt:22-26](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/dao/MessageDao.kt#L22-L26) | `OnConflictStrategy.IGNORE` ensures first arrival wins and prevents status overwriting |

---

### 7. Zero-Knowledge Background Push Notifications (FCM)

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| Ephemeral wake-up push service | **VERIFIED** | [CalypsoFirebaseMessagingService.kt:32-115](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/network/fcm/CalypsoFirebaseMessagingService.kt#L32-L115) | Receives high-priority data pings (`type: wake_up`), wakes up signaling client, and posts local notification |
| Zero metadata / payload in push | **VERIFIED** | [CalypsoFirebaseMessagingService.kt:26-31](file:///e:/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/network/fcm/CalypsoFirebaseMessagingService.kt#L26-L31), [fcmService.js:68-88](file:///e:/Github/Ghost%20Messenger/server/src/services/fcmService.js#L68-L88) | Strictly no plaintext, no sender user code, and no message payloads transmitted over FCM |
| FCM token registration via REST | **VERIFIED** | [PreKeyApiClient.kt:102-124](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/network/api/PreKeyApiClient.kt#L102-L124) | Registers token at `POST /api/prekeys/fcm-token` upon token generation or refresh |
| Real-time socket token registration | **VERIFIED** | [SignalingClient.kt:36-44](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/network/socket/SignalingClient.kt#L36-L44) | Transmits FCM token during initial `register` or via `register-fcm` event |
| Server-side offline push trigger | **VERIFIED** | [signalingHandler.js:203-212](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L203-L212), [signalingHandler.js:286-291](file:///e:/Github/Ghost%20Messenger/server/src/sockets/signalingHandler.js#L286-L291) | Triggers `fcmService.sendWakeUpPing()` when WebRTC offers or encrypted envelopes are routed to offline peers |
| Expired token auto-pruning | **VERIFIED** | [fcmService.js:93-102](file:///e:/Github/Ghost%20Messenger/server/src/services/fcmService.js#L93-L102) | Prunes un-registered tokens from volatile memory on `messaging/registration-token-not-registered` |

---

### 8. SQLCipher Native Linkage, Room Schema Migrations & Keystore Resilience

| Sub-Claim | Status | Evidence | Notes |
|---|---|---|---|
| Explicit SQLCipher JNI loading | **VERIFIED** | [GhostMessengerApp.kt:8-12](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/GhostMessengerApp.kt#L8-L12), [AppDatabase.kt:59-65](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/db/AppDatabase.kt#L59-L65) | `System.loadLibrary("sqlcipher")` loaded before Room open helper calls; resolves `UnsatisfiedLinkError` |
| Non-destructive Room migration (v1->v2) | **VERIFIED** | [AppDatabase.kt:67-82](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/db/AppDatabase.kt#L67-L82) | `MIGRATION_1_2` creates `signal_sender_keys` table without wiping existing sessions or chat history |
| Elimination of destructive migration | **VERIFIED** | [AppDatabase.kt:89-94](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/db/AppDatabase.kt#L89-L94) | `fallbackToDestructiveMigration()` strictly omitted to enforce rule F6 |
| Keystore self-healing recovery | **VERIFIED** | [SecurePreferences.kt:26-55](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/local/prefs/SecurePreferences.kt#L26-L55) | Self-healing instantiation loop catches `AEADBadTagException`, purges corrupted files, and rebuilds MasterKey cleanly |
| Multi-port TURN relay fallback | **VERIFIED** | [WebRtcManager.kt:57-89](file:///e:/Github/Ghost%20Messenger/android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt#L57-L89) | Configures Metered TURN on port 80 (UDP), port 443 (UDP), and port 443 (TCP fallback) |


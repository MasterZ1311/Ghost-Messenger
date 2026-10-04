# Calypso — Future Code Reviewer Onboarding & Inspection Guide

This guide is designed for engineers, security auditors, and future contributors conducting code reviews on the **Calypso** codebase. It outlines the inspection methodology, review criteria, architectural navigation map, and automated verification runbooks.

---

## 1. Code Review Philosophy & Standing Rules

Every pull request submitted to the repository must strictly comply with the **Calypso Standing Rules** defined in [`AGENTS.md`](../AGENTS.md):

1. **Evidence-Based Verification**:
   No PR may be approved based on theoretical claims. Reviewers must require concrete execution logs, unit test reports, or reproducible CLI verification.
2. **Fact-Checked APIs & Policies**:
   Do not introduce unverified library versions, deprecated Android APIs, or non-compliant Google Play Console patterns.
3. **Atomic, Reviewable Changes**:
   Reject PRs that merge unrelated refactors, dependency updates, and feature implementations into monolithic diffs.
4. **Continuous Build, Test & Lint Validation**:
   Every functional PR must pass clean compilation, all unit test suites, and static analysis without suppressing warnings.
5. **Zero-Leakage Privacy & Sensitive Data Protection**:
   Strictly verify that plaintext message content, private keys, BIP-39 mnemonic phrases, identity key pairs, and raw UserCodes are never written to logs or telemetry.
6. **Strict Cryptographic & Zero-Telemetry Integrity**:
   Reject any PR that introduces tracking SDKs, commercial analytics, advertising libraries, or weakens cryptographic parameters.
7. **Radical Transparency & Truthfulness**:
   Never paper over errors or suppress warnings. Deprecations, edge cases, and known limitations must be documented explicitly.

---

## 2. Reviewer Audit Checklists

### 2.1 Cryptographic Subsystem Checklist
- [ ] **Deterministic Derivation**: Does the key derivation path follow BIP-39 -> PBKDF2-HMAC-SHA512 -> HKDF-SHA256 (`KeyManager.kt:111-135`)?
- [ ] **Curve25519 Parameters**: Are all identity and ephemeral keys generated using official Curve25519 / Ed25519 routines from `org.signal:libsignal-android`?
- [ ] **Signed PreKey Rotation**: Is Signed PreKey rotation constrained by a 7-day TTL and verified via `Curve.calculateSignature()` (`SignalCryptoManager.kt:86-123`)?
- [ ] **Double Ratchet Forward Secrecy**: Are message keys discarded immediately upon use?
- [ ] **Encrypted Control Signals**: Are delivery ACKs and read receipts encrypted through active Double Ratchet sessions (`SignalCryptoManager.kt:196-216`)?
- [ ] **Safety Numbers**: Does the fingerprint algorithm sort identifiers lexicographically and compute SHA-512 over concatenated keys (`SafetyNumberGenerator.kt:23-50`)?
- [ ] **Identity Change Detection**: Does the code catch `UntrustedIdentityException` and trigger UI security warnings without logging key bytes (`MessageRepository.kt:381-394`)?

### 2.2 Privacy & Sensitive Data Checklist
- [ ] **Log Auditing**: Are there any instances of `Log.d`, `Log.i`, `println`, or `console.log` emitting message contents, private keys, seed words, or UserCodes?
- [ ] **Debug Gating**: Are all diagnostic log calls wrapped inside `if (BuildConfig.DEBUG)` checks?
- [ ] **Exception Privacy**: Do catch blocks log only class names or generic error messages rather than raw exception strings that may embed cryptographic material?
- [ ] **Screen Protection**: Is `FLAG_SECURE` maintained on window parameters in `MainActivity`?
- [ ] **Telemetry Audit**: Does `build.gradle.kts` contain only approved dependencies (no Firebase Analytics, Crashlytics, adjust, AppsFlyer, etc.)?

### 2.3 Android Architecture & Concurrency Checklist
- [ ] **Dispatcher Usage**: Are all disk, network, and cryptographic operations dispatched to `Dispatchers.IO`?
- [ ] **Room DB Concurrency**: Does message insertion use `OnConflictStrategy.IGNORE` (`MessageDao.kt:24`) to handle simultaneous DataChannel/relay arrivals safely?
- [ ] **Zero Destructive Migrations**: Is `fallbackToDestructiveMigration()` strictly omitted? Are schema changes accompanied by explicit `Migration` instances (e.g. `MIGRATION_1_2` in `AppDatabase.kt:67-82`)?
- [ ] **SQLCipher Native Loading**: Is `System.loadLibrary("sqlcipher")` invoked in `GhostMessengerApp.onCreate()` and `AppDatabase.companion object init` before SQLite connections open?
- [ ] **Keystore Recovery**: Does `SecurePreferences` wrap Keystore decryption in self-healing logic (`createEncryptedPrefs()`) to prevent fatal crash loops on reinstall?
- [ ] **State Flow Observation**: Are UI state mutations performed via unidirectional data flow using `StateFlow` and Compose state collectors?
- [ ] **Resource Disposal**: Are `PeerConnection`, `DataChannel`, and `AppDatabase` resources closed gracefully on teardown?

### 2.4 Server & Wire Protocol Checklist
- [ ] **Identity Key Locking**: Does the server reject bundle re-uploads with differing identity keys (`InMemoryPreKeyStore.js:38-42`)?
- [ ] **Challenge Nonces**: Are challenge nonces consumed immediately upon first verification (`ChallengeStore.js:46-52`)?
- [ ] **Rate Limiting**: Are Socket.IO event streams capped at 20 events/second per socket (`signalingHandler.js:28-59`)?
- [ ] **Payload Bounds**: Are incoming SDP offers (<= 8 KB), ICE candidates (<= 512 B), and envelopes (<= 64 KB) strictly size-checked?
- [ ] **Zero Persistence**: Does the server avoid writing any user data, messages, or keys to disk or persistent databases?

### 2.5 Zero-Knowledge Push Checklist (FCM)
- [ ] **Zero Payload Leak**: Are FCM notifications strictly ephemeral wake-up pings (`{"type": "wake_up"}`) without sender userCodes, recipient keys, or ciphertexts?
- [ ] **Zero Server Logs**: Does `fcmService.js` avoid logging userCodes or device tokens in production?
- [ ] **Decoupled Decryption**: Does the client pull and decrypt messages exclusively over the TLS signaling socket / WebRTC channel after wake-up?

---

## 3. Critical Entry Point & Navigation Map

Use this index to quickly locate and inspect core implementations:

| Subsystem | Source File | Line Range | Key Responsibility |
|---|---|---|---|
| **App Startup & JNI** | [`GhostMessengerApp.kt`](../android/app/src/main/kotlin/org/ghostmessenger/GhostMessengerApp.kt) | `L8-L13` | Application entry point, `System.loadLibrary("sqlcipher")` |
| **BIP-39 Mnemonic** | [`KeyManager.kt`](../android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt) | `L30-L65` | Mnemonic generation, validation, and PBKDF2 seed derivation |
| **Deterministic Keys** | [`KeyManager.kt`](../android/app/src/main/kotlin/org/ghostmessenger/core/crypto/KeyManager.kt) | `L111-L158` | HKDF-SHA256 identity key and clamped 14-bit registration ID |
| **UserCode Generation**| [`UserCodeUtils.kt`](../android/app/src/main/kotlin/org/ghostmessenger/core/crypto/UserCodeUtils.kt) | `L20-L54` | SHA-256 to 40-bit Base32 UserCode derivation (`XXXX-XXXX`) |
| **Signal PreKeys** | [`SignalCryptoManager.kt`](../android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt) | `L39-L123` | PreKey batch generation, Signed PreKey rotation (7-day TTL) |
| **Signal Sessions** | [`SignalCryptoManager.kt`](../android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt) | `L128-L152` | X3DH session building from remote PreKey bundles |
| **Message Encryption**| [`SignalCryptoManager.kt`](../android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt) | `L165-L216` | Double Ratchet encryption for content and control packets |
| **Message Decryption**| [`SignalCryptoManager.kt`](../android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SignalCryptoManager.kt) | `L221-L243` | Envelope decryption (`PreKeySignalMessage` & `SignalMessage`) |
| **Safety Numbers** | [`SafetyNumberGenerator.kt`](../android/app/src/main/kotlin/org/ghostmessenger/core/crypto/SafetyNumberGenerator.kt) | `L17-L50` | 30-digit SHA-512 symmetric fingerprint computation |
| **Room & Migrations** | [`AppDatabase.kt`](../android/app/src/main/kotlin/org/ghostmessenger/data/local/db/AppDatabase.kt) | `L50-L105` | SQLCipher database builder, `MIGRATION_1_2` (`signal_sender_keys`) |
| **Secure Preferences**| [`SecurePreferences.kt`](../android/app/src/main/kotlin/org/ghostmessenger/data/local/prefs/SecurePreferences.kt) | `L23-L65` | Self-healing `EncryptedSharedPreferences` backed by Android Keystore |
| **FCM Push Service** | [`CalypsoFirebaseMessagingService.kt`](../android/app/src/main/kotlin/org/ghostmessenger/data/network/fcm/CalypsoFirebaseMessagingService.kt) | `L32-L115` | Zero-knowledge wake-up push handler & local notifications |
| **WebRTC Lifecycle** | [`WebRtcManager.kt`](../android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt) | `L42-L89` | Multi-port ICE servers, PeerConnectionFactory, DataChannel lifecycle |
| **Reconnection Logic**| [`WebRtcManager.kt`](../android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt) | `L265-L285` | Exponential backoff retry (2s, 4s, 8s) on connection failure |
| **Message Dispatch** | [`MessageRepository.kt`](../android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt) | `L222-L302` | Session check -> Encrypt -> DataChannel / Relay fallback -> Save |
| **Incoming Handling** | [`MessageRepository.kt`](../android/app/src/main/kotlin/org/ghostmessenger/data/repository/MessageRepository.kt) | `L307-L394` | Decrypt -> Persist -> Dispatch ACK -> Identity check alert |
| **Server Challenge** | [`ChallengeStore.js`](../server/src/store/ChallengeStore.js) | `L25-L90` | 64-character nonce issuance, HMAC-SHA256 signature verification |
| **Server PreKey Store**| [`InMemoryPreKeyStore.js`](../server/src/store/InMemoryPreKeyStore.js) | `L38-L165` | In-memory bundle cache, atomic pop, identity key locking |
| **Server Push Service**| [`fcmService.js`](../server/src/services/fcmService.js) | `L10-L105` | Ephemeral wake-up push dispatcher, auto token pruning |
| **Offline Queue** | [`OfflineQueueStore.js`](../server/src/store/OfflineQueueStore.js) | `L16-L128` | 50-envelope cap, 24h TTL, in-memory transient buffer |
| **Socket Limiter** | [`signalingHandler.js`](../server/src/sockets/signalingHandler.js) | `L41-L59` | Per-socket sliding window rate limiter (max 20 events/sec) |

---

## 4. Verification & Testing Runbooks

Before merging any code change, reviewers must execute and verify the following test suites:

### 4.1 Server Unit & Integration Tests

```powershell
cd "e:\Github\Ghost Messenger\server"
npm test
```

- **Expected Output**:
  - `tests 34`
  - `suites 4`
  - `pass 34`
  - `fail 0`
  - `duration_ms < 3000`
- **What is Tested**:
  - Challenge store nonce generation, HMAC validation, replay rejection, TTL expiration.
  - PreKey store bundle storage, identity key locking, hijack prevention, atomic consumption.
  - Presence manager socket caps, IP connection caps.
  - Full REST and Socket.IO integration, rate limiting, oversized SDP rejection, offline message queuing.

---

### 4.2 Android Unit Tests

```powershell
cd "e:\Github\Ghost Messenger\android"
.\gradlew.bat testDebugUnitTest
```

- **Expected Output**:
  - `BUILD SUCCESSFUL`
  - 30 tests passed across 9 suites:
    - `Base32Test` (4 tests)
    - `KeyManagerTest` (7 tests)
    - `SafetyNumberGeneratorTest` (2 tests)
    - `SignalCryptoManagerTest` (2 tests)
    - `UserCodeUtilsTest` (5 tests)
    - `SqliteSignalProtocolStoreTest` (4 tests)
    - `ConversationAndMessageTest` (2 tests)
    - `PreKeyApiClientTest` (2 tests)
    - `SignalingModelTest` (2 tests)

---

### 4.3 Android Static Analysis (Lint)

```powershell
cd "e:\Github\Ghost Messenger\android"
.\gradlew.bat lintDebug
```

- **Expected Output**:
  - `BUILD SUCCESSFUL`
  - Clean lint report at `android/app/build/reports/lint-results-debug.html`.
  - Zero critical errors (`0 errors`).

---

### 4.4 Android Compilation & Artifact Builds

#### Debug APK Build:
```powershell
cd "e:\Github\Ghost Messenger\android"
.\gradlew.bat assembleDebug
```
- **Output Artifact**: `android/app/build/outputs/apk/debug/app-debug.apk`

#### Release Google Play App Bundle (AAB):
```powershell
cd "e:\Github\Ghost Messenger\android"
.\gradlew.bat :app:bundleRelease
```
- **Output Artifact**: `android/app/build/outputs/bundle/release/app-release.aab`
- **Prerequisites**: Valid `upload-keystore.jks` and `key.properties` present in `android/`.

---

### 4.5 Server Deployment Smoke Test

Verify a live or local server deployment using the automated test script:

```powershell
cd "e:\Github\Ghost Messenger\server"
node scripts/verify-deployment.js http://localhost:3000
```

- **What is Verified**:
  - Health check responds with `200 OK`.
  - Challenge nonce issuance functions.
  - PreKey bundle upload with HMAC signature succeeds.
  - PreKey bundle fetch returns valid data and consumes one-time prekeys.
  - Active user count oracle is confirmed absent.

---

## 5. Common Review Pitfalls & Rejection Criteria

Reviewers must reject pull requests exhibiting any of the following anti-patterns:

1. **Unencrypted Control Signals**:
   Creating a new signaling event or message type that passes raw message IDs, user metadata, or ACK status in plaintext rather than encrypting it via `SignalCryptoManager.encryptControlMessage()`.
2. **Logging in Catch Blocks**:
   Adding exception logging like `Log.e(TAG, "Error: $e")` or `e.printStackTrace()` on cryptographic paths. Libsignal and Android Keystore exception messages often contain key representations or ciphertext byte arrays.
3. **Database Race Overwrite**:
   Modifying `MessageDao.insertMessage()` to use `OnConflictStrategy.REPLACE`. This allows late-arriving signaling relay envelopes to overwrite messages that already transitioned to `STATUS_DELIVERED` or `STATUS_READ` via the direct DataChannel.
4. **Hardcoded Secrets or URLs**:
   Adding hardcoded TURN passwords, server IP addresses, or private keys directly in Kotlin or JavaScript source code.
5. **Non-Deterministic Key Generation**:
   Generating identity keys or registration IDs using `SecureRandom` instead of the deterministic derivation chain rooted in the 12-word BIP-39 mnemonic. This breaks wallet restoration across devices.
6. **Telemetry Ingestion**:
   Importing any analytics, tracking, advertising, or external performance monitoring libraries.

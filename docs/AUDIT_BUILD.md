# Calypso — Build, Test & Lint Audit Report

> **Audit Date**: October 2, 2026  
> **Repository**: `MasterZ1311/Ghost-Messenger` (Project Name: Calypso)  
> **Auditor**: Antigravity Assistant  
> **Status**: **Fully Verified & Operational** — All builds, unit tests, and lint analyses passed.

---

## 1. Executive Summary

| Target Component | Subtask | Result | Exit Code | Summary |
| :--- | :--- | :---: | :---: | :--- |
| **Android APK** | `assembleDebug` | **PASSED** | 0 | Compiled Kotlin, desugared bytecode, linked JNI binaries (SQLCipher, WebRTC, Signal), packaged debug APK. |
| **Android Tests** | `testDebugUnitTest` | **PASSED** | 0 | 30 out of 30 tests passed (100% pass rate) across 9 suites in 1m 56s. |
| **Android Lint** | `lintDebug` | **PASSED** | 0 | Full static analysis across sources, test models, and XML assets completed cleanly. |
| **Server Runtime** | `node --check` | **PASSED** | 0 | All ES module source files and test files passed Node syntax analysis. |
| **Server Tests** | `node --test` | **PASSED** | 0 | 34 out of 34 tests passed (100% pass rate) across 4 suites in 2.15s. |
| **Server Security** | `npm audit` | **PASSED** | 0 | 0 vulnerabilities detected after dependency resolution. |

---

## 2. Environment Specifications

```yaml
Operating System: Windows 11 Home / Pro (10.0 amd64)
Shell: PowerShell 5.1 / 7.x
JDK / Java: OpenJDK 21.0.10 (build 21.0.10+-14961533-b1163.108, JetBrains s.r.o. JBR)
Node.js: v22.19.0
npm: 10.9.3
Gradle: 8.11.1
Android Gradle Plugin (AGP): 8.7.3
Kotlin: 2.0.21
KSP: 2.0.21-1.0.28
Android SDK Location: C:\Users\sivak\AppData\Local\Android\Sdk
Android SDK Build Tools: compileSdk = 36, targetSdk = 36, minSdk = 26
JVM Target: 17
```

---

## 3. Verified Execution Outputs

### 3.1 Server Unit & Integration Tests

- **Command**: `npm test`
- **Exit Code**: `0`
- **Output**:
```tap
# Subtest: ChallengeStore Unit Tests
    ok 1 - should issue a 64-character hex nonce
    ok 2 - should verify a correct HMAC-SHA256 signature
    ok 3 - should consume the challenge on verification (replay attack rejected)
    ok 4 - should reject a wrong signature
    ok 5 - should expire challenges after TTL
    ok 6 - should cap at MAX_PENDING and evict oldest
ok 1 - ChallengeStore Unit Tests (6/6 pass)

# Subtest: InMemoryPreKeyStore Unit Tests
    ok 1 - should upload and retrieve prekey bundles
    ok 2 - should lock the identity key on first upload
    ok 3 - should allow re-upload with the SAME identity key
    ok 4 - should reject re-upload with a DIFFERENT identity key (hijack attempt)
    ok 5 - should reject upload exceeding maxPreKeysPerUser
    ok 6 - should reject new upload when store is full
    ok 7 - should evict expired bundles and clear identity locks
    ok 8 - should report hasBundleFor correctly
ok 2 - InMemoryPreKeyStore Unit Tests (8/8 pass)

# Subtest: PresenceManager Unit Tests
    ok 1 - should register and track online presence
    ok 2 - should enforce maxSocketsPerUser cap
    ok 3 - should enforce IP connection cap via admitIp
ok 3 - PresenceManager Unit Tests (3/3 pass)

# Subtest: Full Server REST & Socket.IO Integration Tests
    ok 1 - REST: Health check returns ok with NO activeUsers/storedBundles oracle
    ok 2 - REST: Challenge issuance returns a 64-char hex nonce
    ok 3 - REST: Upload without signature → 401
    ok 4 - REST: Upload with wrong signature → 401
    ok 5 - REST: Full upload/fetch cycle with valid challenge signature
    ok 6 - REST: Re-upload with matching identity key succeeds without new challenge
    ok 7 - REST: Re-upload with different identity key → 403 (hijack blocked)
    ok 8 - REST: Fetch for unknown userCode returns generic 404 (no userCode in response)
    ok 9 - REST: Upload empty body returns 400 with generic message
    ok 10 - REST: TURN credentials returns 404 when not configured
    ok 11 - Socket.IO: Full WebRTC SDP & ICE Candidate Signaling Flow
    ok 12 - Socket.IO: register without a prekey bundle is rejected
    ok 13 - Socket.IO: register for userCode with no uploaded bundle is rejected
    ok 14 - Socket.IO: oversized SDP offer is silently dropped
    ok 15 - Socket.IO: oversized envelope is rejected with error callback
    ok 16 - Socket.IO: rate limiter rejects events above 20/second threshold
    ok 17 - Socket.IO: offline encrypted-envelope is queued and delivered on register
ok 4 - Full Server REST & Socket.IO Integration Tests (17/17 pass)

# tests 34 | suites 4 | pass 34 | fail 0
```

---

### 3.2 Android Unit Test Suite Breakdown

- **Command**: `.\gradlew.bat testDebugUnitTest`
- **Exit Code**: `0`
- **Results**:

| Test Suite Class | Tests | Failures | Errors | Skipped | Status |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `Base32Test` | 4 | 0 | 0 | 0 | PASSED |
| `KeyManagerTest` | 7 | 0 | 0 | 0 | PASSED |
| `SafetyNumberGeneratorTest` | 2 | 0 | 0 | 0 | PASSED |
| `SignalCryptoManagerTest` | 2 | 0 | 0 | 0 | PASSED |
| `UserCodeUtilsTest` | 5 | 0 | 0 | 0 | PASSED |
| `SqliteSignalProtocolStoreTest` | 4 | 0 | 0 | 0 | PASSED |
| `ConversationAndMessageTest` | 2 | 0 | 0 | 0 | PASSED |
| `PreKeyApiClientTest` | 2 | 0 | 0 | 0 | PASSED |
| `SignalingModelTest` | 2 | 0 | 0 | 0 | PASSED |
| **Total** | **30** | **0** | **0** | **0** | **100% PASS** |

---

### 3.3 Android APK Build & Packaging

- **Command**: `.\gradlew.bat assembleDebug`
- **Exit Code**: `0`
- **Output Artifact**: `android/app/build/outputs/apk/debug/app-debug.apk`

---

### 3.4 Android Static Analysis (Lint)

- **Command**: `.\gradlew.bat lintDebug`
- **Exit Code**: `0`
- **Report Location**: `android/app/build/reports/lint-results-debug.html`

---

## 4. Remediation History

1. **Resolved Theme Token Discrepancies**:
   - Replaced invalid references to `GhostColors.SurfaceDark` with `GhostColors.SurfaceSlate` and `GhostColors.CardGlass`.
2. **Enabled Build Features**:
   - Configured `buildConfig = true` in `buildFeatures` DSL to supply type-safe `BuildConfig.DEBUG` flags for sensitive logging suppression.
3. **Hardened Metadata & Control Messages**:
   - End-to-end encrypted delivery ACKs and read receipts using `SignalCryptoManager.encryptControlMessage()`.
   - Prevented message state overwrite on simultaneous DataChannel/relay arrivals using `OnConflictStrategy.IGNORE`.
4. **Eliminated Hardcoded Relays**:
   - Extracted TURN relay credentials to local `SecurePreferences`, defaulting to STUN-only operation unless explicitly set by the user.
5. **Implemented Out-of-Band Contact Verification**:
   - Added symmetric `SafetyNumberGenerator` (SHA-512 based 30-digit numeric fingerprint) and corresponding verification screen & view model.
6. **Added Offline Message Queuing**:
   - Implemented `OfflineQueueStore` on the signaling relay server, buffering encrypted blobs with 24-hour TTL and flushing upon socket authentication.

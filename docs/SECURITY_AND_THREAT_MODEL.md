# Calypso — Security Model, Threat Vectors & Hardening

This document provides a rigorous threat modeling analysis, security architecture breakdown, and mitigation reference for **Calypso**. It is intended for security engineers, cryptanalysts, and code reviewers conducting security evaluations.

---

## 1. Security Philosophy & Foundational Invariants

Calypso is designed around four foundational security invariants:

1. **Zero-Knowledge Signaling**:
   The signaling server is strictly an untrusted routing relay. It never receives plaintext message payloads, private identity keys, ephemeral Diffie-Hellman keys, or mnemonic seed phrases.
2. **Zero-Telemetry Integrity**:
   The codebase contains zero advertising libraries, behavioral tracking SDKs, commercial crash reporters, or usage analytics. All network connections terminate exclusively at the user-configured signaling server or direct peer-to-peer WebRTC endpoints.
3. **Zero-Leakage Sensitive Data Policy**:
   Cryptographic key material, seed phrases, message contents, and user codes are never printed to system logs or exported to unencrypted storage. In production release builds, debug logs are stripped or gated behind `BuildConfig.DEBUG == true`.
4. **Defense in Depth**:
   Multiple overlapping security controls protect communications: TLS for signaling transport, Signal Protocol Double Ratchet for payload confidentiality and integrity, WebRTC DTLS-SRTP for peer-to-peer transport, and SQLCipher AES-256 for local persistence at rest.

---

## 2. Adversary Model & Assumptions

We evaluate Calypso against five classes of adversaries:

```
+-----------------------------------------------------------------------------------+
|                                  ADVERSARY TIERS                                  |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [Tier 1: Passive Network Eavesdropper]                                           |
|  - Observes all traffic between clients and the signaling server.                 |
|  - Observes direct peer-to-peer traffic between client IP addresses.              |
|                                                                                   |
|  [Tier 2: Active Network Adversary (MitM)]                                        |
|  - Can intercept, alter, drop, or inject packets between client and server.        |
|  - Can attempt TLS interception or DNS spoofing.                                  |
|                                                                                   |
|  [Tier 3: Compromised / Malicious Signaling Server]                               |
|  - Full control over the signaling server process and memory.                     |
|  - Can inspect all relayed envelopes, SDP offers, and ICE candidates.             |
|  - Can attempt to substitute public PreKeys or forge connection handshakes.       |
|                                                                                   |
|  [Tier 4: Malicious Peer]                                                         |
|  - Legitimate participant in a conversation.                                      |
|  - Can attempt to replay old messages, alter timestamps, or repudiate actions.   |
|                                                                                   |
|  [Tier 5: Physical Device Access / Forensic Examiner]                             |
|  - Obtains physical access to an unlocked or locked user device.                  |
|  - Attempts forensic recovery of chat history, private keys, or seed phrases.     |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 3. Threat Vector & Mitigation Matrix

| Threat Vector | Adversary Tier | Risk | Technical Mitigation in Calypso |
|---|---|---|---|
| **Eavesdropping on Message Content** | Tier 1, 2, 3 | Critical | **Signal Protocol Double Ratchet**: End-to-end encryption with AES-256-CBC and HMAC-SHA256. Decryption keys exist only on client endpoints. |
| **Signaling Server Compromise** | Tier 3 | High | **Zero Plaintext Knowledge**: Server memory holds only base64 ciphertexts and public PreKey bundles. Server restart purges all state. |
| **Man-in-the-Middle Key Substitution** | Tier 2, 3 | Critical | **Signed PreKeys & Safety Numbers**: Signed PreKeys are verified against the sender's Curve25519 identity key. Users can verify identities out-of-band using 30-digit SHA-512 Safety Numbers (`SafetyNumberGenerator.kt:15-50`). |
| **Identity Key Hijacking on Server** | Tier 2, 3 | High | **Identity Key Locking**: `InMemoryPreKeyStore.js:38-42` binds the first uploaded identity key to the UserCode. Subsequent uploads with differing keys are rejected with HTTP 403. |
| **PreKey Upload Replay / Forgery** | Tier 2, 3 | High | **HMAC Challenge-Response Authentication**: `ChallengeStore.js:25-90` issues single-use 64-char nonces with 60s TTL. Clients prove identity key ownership via `HMAC-SHA256(SHA256(identityKey), nonce)`. |
| **Compromise of Past Keys** | Tier 1, 2, 3, 5 | Critical | **Forward Secrecy**: KDF symmetric ratchet advances with every sent/received message. Old message keys are erased immediately after decryption. |
| **Compromise of Ephemeral Session Key** | Tier 1, 2, 3, 4 | High | **Post-Compromise Security**: Curve25519 DH ratchet introduces fresh entropy with every communication round trip, restoring security even if a temporary key was compromised. |
| **Eavesdropping on ACKs & Read Receipts** | Tier 1, 3 | Medium | **Encrypted Control Signals**: Delivery confirmations and read receipts are encrypted via `SessionCipher.encrypt()` as `EncryptedEnvelope` packets (`MessageRepository.kt:396-445`), revealing zero message IDs to the relay. |
| **Database Extraction from Stolen Device** | Tier 5 | High | **SQLCipher AES-256 Encryption**: All Room database tables are encrypted with 256-bit AES in CBC mode. Master key is secured in hardware-backed Android Keystore via `EncryptedSharedPreferences`. |
| **Screen Snooping & App Switcher Capture** | Tier 5 | Medium | **Android `FLAG_SECURE`**: Enabled on `MainActivity` window to block screen recordings, screenshots, and recents screen previews. |
| **Signaling Denial of Service (DoS)** | Tier 1, 2 | Medium | **Layered Rate Limiting & Caps**: Express rate limiting on REST endpoints, Socket.IO sliding window (max 20 events/sec), IP connection caps (max 5/IP), and memory bounds (max 50 queued envelopes/user). |

---

## 4. Client-Side Security Hardening

### 4.1 Window Protection via `FLAG_SECURE`

To protect message privacy against shoulder surfing, background screen scrapers, and the Android recent apps switcher thumbnail cache, `MainActivity` sets the `FLAG_SECURE` window attribute:

```kotlin
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)
```

This prevents the OS and third-party apps from capturing screenshots, screen recordings, or displaying window previews in the task switcher.

### 4.2 Logging Policy & Information Leakage Prevention

Per Standing Rule #5 (`AGENTS.md`), logging of sensitive material is prohibited in release builds:

- **Banned Log Data**: Plaintext message bodies, BIP-39 mnemonic seed words, private key bytes, registration IDs, and unmasked UserCodes.
- **Debug Gating**: All operational diagnostic logs in `MessageRepository.kt`, `WebRtcManager.kt`, and `SignalingClient.kt` are wrapped in:
  ```kotlin
  if (BuildConfig.DEBUG) {
      android.util.Log.d(TAG, "...")
  }
  ```
- **Untrusted Identity Exception Handling (`MessageRepository.kt:381-394`)**:
  When an identity mismatch occurs (`UntrustedIdentityException`), the exception message itself is suppressed from logs because it may contain serialized public key bytes. The UI is notified via reactive `SecurityEvent.IdentityChanged`.

### 4.3 ProGuard / R8 Obfuscation & Shrinking

Release builds enable R8 code shrinking, tree shaking, and identifier obfuscation (`android/app/build.gradle.kts:33-38`):
- Minification and resource shrinking strip unused classes and dead code.
- Cryptographic library symbols (`org.signal:libsignal-android`, `net.zetetic:sqlcipher-android`) have explicit keep rules in `proguard-rules.pro` to prevent reflection breakage while obfuscating application logic.

---

## 5. Server-Side Security Hardening

### 5.1 HTTP Security Headers (Helmet)

The Express application uses `helmet` (`server/src/app.js:28-40`) to enforce defensive HTTP headers:
- `Content-Security-Policy`: Default `'self'`, restricting unauthorized script execution.
- `Strict-Transport-Security`: HSTS enabled for 1 year (`maxAge = 31536000`, `includeSubDomains = true`).
- `X-Content-Type-Options: nosniff`: Prevents MIME-type sniffing.
- `X-Frame-Options: DENY`: Blocks clickjacking attacks.

### 5.2 Structured Privacy-Safe Logging

The server logger (`server/src/middleware/logger.js:10-38`) outputs structured JSON logs with IP anonymization:
- IP addresses are passed through `hashIp(ip)`, which computes `crypto.createHash('sha256').update(ip + SALT).digest('hex').slice(0, 8)`.
- UserCodes in warnings are truncated to the first 4 characters.
- Log entries never contain message payloads, SDP bodies, or cryptographic keys.

### 5.3 Bound Memory Footprint

To prevent memory exhaustion denial-of-service:
- `InMemoryPreKeyStore`: Maximum 1,000 bundles, 100 PreKeys per user, 30-day automatic expiration.
- `ChallengeStore`: Maximum 5,000 pending nonces, 60-second TTL.
- `OfflineQueueStore`: Maximum 50 envelopes per user, 24-hour TTL, automatic eviction.
- `PresenceManager`: Maximum 3 sockets per user, maximum 5 connections per IP.

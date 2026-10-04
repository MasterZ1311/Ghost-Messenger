# Calypso — System Architecture and Component Design

This document provides a comprehensive technical reference for the system architecture of **Calypso** (formerly Ghost Messenger). It is intended for software engineers, security auditors, and future code reviewers who need an in-depth understanding of the system's design, component interactions, and data flows.

---

## 1. System Overview and Topology

Calypso is a zero-knowledge, peer-to-peer (P2P) messaging system that combines the **Signal Protocol** for asynchronous end-to-end encryption (E2EE) with **WebRTC DataChannels** for direct, serverless transport.

The system comprises two primary components:
1. **Android Client (`android/`)**: A native Android application written in Kotlin and Jetpack Compose, handling cryptographic identity derivation, local encrypted persistence, Signal Protocol session management, and WebRTC peer connection lifecycle.
2. **Ephemeral Signaling Server (`server/`)**: A stateless Node.js / Express / Socket.IO service responsible solely for brokering WebRTC handshakes (SDP offer/answer, ICE candidates), caching public PreKey bundles, and holding transient encrypted offline envelopes in volatile memory.

```
+-----------------------------------------------------------------------------------+
|                                  NETWORK TOPOLOGY                                 |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|    +-----------------------+                         +-----------------------+    |
|    |    Android Client A   |                         |    Android Client B   |    |
|    |  (UserCode: 5J9L-2P4X)|                         |  (UserCode: WXYZ-8901)|    |
|    +-----------+-----------+                         +-----------+-----------+    |
|                |                                                 |                |
|       HTTPS /  |                                        HTTPS /  |                |
|      Socket.IO |                                       Socket.IO |                |
|                v                                                 v                |
|        +-----------------------------------------------------------------+        |
|        |                Calypso Signaling Server (Node.js)              |        |
|        |  - Express REST (PreKey Upload / Challenge / Fetch)             |        |
|        |  - Socket.IO Relay (SDP / ICE / Ephemeral Envelopes)            |        |
|        |  - In-Memory Stores (ChallengeStore, PreKeyStore, Presence)    |        |
|        +-----------------------------------------------------------------+        |
|                :                                                 :                |
|                :..... ICE Candidate / SDP Negotiation Relay .....:                |
|                                                                                   |
|                +=================================================+                |
|                ||     WebRTC DataChannel (RFC 8831 / SCTP)      ||                |
|                ||     - Direct P2P Transport (DTLS-SRTP)        ||                |
|                ||     - Signal Protocol Encrypted Envelopes     ||                |
|                ||     - Encrypted Delivery ACKs & Read Receipts ||                |
|                +=================================================+                |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### Core Architectural Invariants
- **Zero Plaintext on the Server**: The signaling server processes only opaque cryptographic blobs (base64-encoded Signal Protocol ciphertexts). It never possesses identity keys, ephemeral keys, or decryption capability.
- **Direct P2P Priority**: When both peers are active, messages bypass the signaling server entirely and travel directly between devices over an encrypted WebRTC DataChannel.
- **Relay Fallback**: If the DataChannel is not yet open or direct peer connection cannot be established immediately, messages are dispatched through the signaling server as encrypted envelopes.
- **Volatile Server State**: The signaling server maintains no database. All PreKeys, presence records, challenges, and queued offline envelopes reside strictly in memory and are discarded upon server restart.

---

## 2. Android Client Architecture

The Android client is organized according to Modern Android Architecture guidelines, utilizing Model-View-ViewModel (MVVM), unidirectional data flow (UDF), and Dagger Hilt dependency injection.

### 2.1 Package Organization

```
android/app/src/main/kotlin/org/ghostmessenger/
|-- CalypsoApp.kt                     # Application entry point, Hilt initialization
|-- core/
|   |-- crypto/
|   |   |-- Base32.kt                 # RFC 4648 Base32 encoding (5-bit alphabet)
|   |   |-- KeyManager.kt             # BIP-39 mnemonic, HKDF-SHA256 identity derivation
|   |   |-- SafetyNumberGenerator.kt  # SHA-512 symmetric fingerprint computation
|   |   |-- SignalCryptoManager.kt    # libsignal coordinator (X3DH, Double Ratchet)
|   |   `-- UserCodeUtils.kt          # UserCode derivation, validation, normalization
|   `-- model/
|       |-- EncryptedEnvelope.kt      # Universal wire format for P2P and relay messages
|       `-- Identity.kt               # Local user cryptographic identity container
|-- data/
|   |-- crypto/
|   |   `-- SqliteSignalProtocolStore.kt # SignalProtocolStore Room + SQLCipher backing
|   |-- local/
|   |   |-- dao/
|   |   |   |-- ConversationDao.kt    # Conversation thread persistence operations
|   |   |   |-- MessageDao.kt         # Message CRUD and status transitions
|   |   |   `-- SignalDao.kt          # PreKey, SignedPreKey, Session, Identity DAOs
|   |   |-- db/
|   |   |   `-- AppDatabase.kt        # Room database with SQLCipher encryption factory
|   |   |-- entities/
|   |   |   |-- ConversationEntity.kt # Conversation room table
|   |   |   |-- MessageEntity.kt      # Message room table
|   |   |   `-- SignalEntities.kt     # Signal Protocol persistent storage entities
|   |   `-- prefs/
|   |       `-- SecurePreferences.kt  # EncryptedSharedPreferences wrapper
|   |-- network/
|   |   |-- api/
|   |   |   `-- PreKeyApiClient.kt    # HTTP REST client for PreKey exchange
|   |   |-- model/
|   |   |   |-- PreKeyDtos.kt         # REST DTOs for PreKey bundles
|   |   |   `-- SignalingEvents.kt    # Socket.IO event payload definitions
|   |   `-- socket/
|   |       `-- SignalingClient.kt    # Socket.IO client managing presence & signaling
|   |-- repository/
|   |   `-- MessageRepository.kt      # Central coordinator for message send/receive
|   `-- webrtc/
|       `-- WebRtcManager.kt          # PeerConnectionFactory, DataChannel lifecycle
|-- di/
|   |-- DatabaseModule.kt             # Hilt providers for SQLCipher database and DAOs
|   |-- NetworkModule.kt              # Hilt providers for OkHttpClient and JSON
|   `-- RepositoryModule.kt           # Hilt providers for repositories and stores
`-- ui/
    |-- chat/                         # Chat conversation UI and ViewModel
    |-- home/                         # Peer list, QR display, and connection UI
    |-- navigation/                   # Compose Navigation graph and route definitions
    |-- onboarding/                   # BIP-39 mnemonic generation & import flows
    |-- settings/                     # Turn relay config, seed backup, vault purge
    |-- theme/                        # Obsidian dark color system and typography
    `-- verification/                 # Safety number fingerprint comparison UI
```

### 2.2 Dependency Injection Graph (Dagger Hilt)

The dependency graph enforces singleton lifecycles for stateful, resource-heavy subsystems:

- `DatabaseModule` (`android/app/src/main/kotlin/org/ghostmessenger/di/DatabaseModule.kt:18-65`):
  - Provides `AppDatabase`: Instantiated via `Room.databaseBuilder()` and configured with `SupportFactory(passphrase)` from SQLCipher.
  - Derives `MessageDao`, `ConversationDao`, and `SignalDao`.
  - Provides `SqliteSignalProtocolStore`: Implements `SignalProtocolStore` backed by `SignalDao`.
- `NetworkModule` (`android/app/src/main/kotlin/org/ghostmessenger/di/NetworkModule.kt:15-32`):
  - Provides `OkHttpClient` with configured timeouts (30-second connect, read, and write).
  - Provides `Json` instance configured with `ignoreUnknownKeys = true` and `encodeDefaults = true`.
- `RepositoryModule` (`android/app/src/main/kotlin/org/ghostmessenger/di/RepositoryModule.kt:16-56`):
  - Provides `SignalCryptoManager`, injecting `SqliteSignalProtocolStore`.
  - Provides `SecurePreferences` using `EncryptedSharedPreferences`.
  - Provides `WebRtcManager` and `SignalingClient`.
  - Provides `MessageRepository`, binding together crypto, network, database, and WebRTC.

### 2.3 Component Interaction Flow

The diagram below illustrates how an outgoing message originates in the UI layer and traverses the repository, cryptographic subsystem, and transport channels:

```
[ChatScreen / ChatViewModel]
             |
             | sendMessage(userCode, text)
             v
    [MessageRepository]
             |
             +---> [SignalCryptoManager.hasSession()]
             |          |
             |          | If false: fetch PreKeyBundle via [PreKeyApiClient]
             |          v
             |     [SignalCryptoManager.buildSession()]
             |
             +---> [SignalCryptoManager.encryptMessage()]
             |          |
             |          | Encrypts plaintext via SessionCipher.encrypt()
             |          v
             |     Returns [EncryptedEnvelope]
             |
             +---> Check [WebRtcManager.isDataChannelOpen()]
             |          |
             |          |--[True]---> [WebRtcManager.sendData()] ----> Direct P2P Channel
             |          |
             |          `--[False]--> [SignalingClient.sendEncryptedEnvelope()]
             |                             |
             |                             +---> Ephemeral Relay on Server
             |                             `---> [initiateP2PConnection()]
             |
             +---> [MessageDao.insertMessage()] (Persisted to SQLCipher Room DB)
             `---> [ConversationDao.updateLastMessage()]
```

---

## 3. Ephemeral Signaling Server Architecture

The signaling server is built with Node.js using ES Modules and runs on Express and Socket.IO.

### 3.1 Architectural Structure

```
server/
|-- Dockerfile                        # Production container specification
|-- package.json                      # Node.js dependencies and test runner scripts
|-- src/
|   |-- app.js                        # Express application factory, middleware, CORS
|   |-- server.js                     # HTTP server startup, Socket.IO binding, shutdown
|   |-- middleware/
|   |   |-- logger.js                 # Privacy-safe structured JSON logger (hashed IPs)
|   |   `-- turnCredentials.js        # Ephemeral HMAC-SHA1 TURN credential generator
|   |-- routes/
|   |   `-- prekeys.js                # PreKey upload, challenge, and fetch REST API
|   |-- sockets/
|   |   `-- signalingHandler.js       # WebRTC signaling router, rate limiter, admission
|   `-- store/
|       |-- ChallengeStore.js         # Single-use HMAC challenge nonces with 60s TTL
|       |-- InMemoryPreKeyStore.js    # PreKey cache with identity key locking & eviction
|       |-- OfflineQueueStore.js      # Bounded in-memory FIFO buffer for offline peers
|       `-- PresenceManager.js        # UserCode-to-socket mapping and IP cap tracking
`-- test/
    `-- server.test.js                # Native Node.js test runner integration test suite
```

### 3.2 In-Memory Storage Subsystems

All server data stores are strictly volatile:

1. **`InMemoryPreKeyStore` (`server/src/store/InMemoryPreKeyStore.js:14-165`)**:
   - Stores published PreKey bundles indexed by normalized `UserCode`.
   - **Identity Key Locking**: On first bundle upload, the client's public identity key is locked to that UserCode. Subsequent bundle uploads with differing identity keys are rejected with HTTP 403, preventing account hijacking.
   - **One-Time PreKey Consumption**: When a peer fetches a bundle via `GET /api/prekeys/:userCode`, one one-time PreKey is popped atomically (`record.preKeys.shift()`). If the pool is exhausted, the Signed PreKey is returned alone.
   - **Memory Bounds**: Maximum 1,000 active bundles, maximum 100 one-time PreKeys per user, and 30-day bundle expiration.

2. **`ChallengeStore` (`server/src/store/ChallengeStore.js:12-95`)**:
   - Issues 64-character cryptographic hexadecimal nonces via `crypto.randomBytes(32).toString('hex')`.
   - Binds nonces to a specific UserCode with a 60-second time-to-live (TTL).
   - Validates HMAC-SHA256 signatures: `HMAC-SHA256(key = SHA256(identityKey), data = nonce)`.
   - Single-use consumption: Nonces are immediately deleted upon validation, mitigating replay attacks.
   - Capacity capped at 5,000 pending challenges.

3. **`OfflineQueueStore` (`server/src/store/OfflineQueueStore.js:16-128`)**:
   - Holds encrypted envelopes addressed to offline peers.
   - Implements a per-user limit of 50 envelopes and a 24-hour TTL.
   - Envelopes are dequeued and delivered when the target user connects and registers on the Socket.IO signaling channel.

4. **`PresenceManager` (`server/src/store/PresenceManager.js:12-140`)**:
   - Maintains mappings between `UserCode` and connected `socket.id` instances.
   - Enforces limits: maximum 3 concurrent sockets per UserCode, maximum 5 concurrent connections per IP address.
   - Admission guard (`admitIp(ip)`) drops unauthorized connections during the initial handshake.

---

## 4. End-to-End Signaling & WebRTC Negotiation Flow

The signaling server coordinates the discovery and negotiation required to establish a direct WebRTC peer-to-peer connection:

```
Client A (5J9L-2P4X)             Signaling Server              Client B (WXYZ-8901)
        |                               |                               |
        |=== 1. Challenge & PreKey Upload ==============================|
        |                               |                               |
        |-- GET /challenge/:userCode -> |                               |
        |<- { nonce } ------------------|                               |
        |-- POST /upload (bundle+sig) ->|                               |
        |<- 200 OK ---------------------|                               |
        |                               |                               |
        |=== 2. WebRTC Signaling Handshake =============================|
        |                               |                               |
        |-- register(5J9L-2P4X) ------> | <---- register(WXYZ-8901) ----|
        |<- registered -----------------| ----> registered ------------>|
        |                               |                               |
        |-- signal(type: 'offer', SDP)->|                               |
        |                               | ---- signal(type: 'offer') -->|
        |                               |                               |
        |                               | <--- signal(type: 'answer') --|
        |<- signal(type: 'answer', SDP)-|                               |
        |                               |                               |
        |-- signal(candidate) --------> |                               |
        |                               | ---- signal(candidate) ------>|
        |                               | <--- signal(candidate) ------|
        |<- signal(candidate) ----------|                               |
        |                               |                               |
        |=== 3. Direct Peer-to-Peer Transport ==========================|
        |                                                               |
        | <============ DTLS-SRTP Handshake & SCTP Init ==============> |
        | <============ WebRTC DataChannel OPEN ("chat") =============> |
        |                                                               |
        |-- EncryptedEnvelope (Signal Ciphertext) --------------------> |
        |<- EncryptedEnvelope (Delivery ACK) -------------------------- |
```

---

## 5. State Machine Specifications

### 5.1 WebRtcManager Connection Lifecycle

`WebRtcManager` (`android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt`) manages `PeerConnection` and `DataChannel` lifecycles per peer:

```
   [INIT]
      |
      | createOffer() / handleOffer()
      v
  [CONNECTING] <-------------+
      |                      |
      | IceConnection        | Exponential Retry
      | CONNECTED / COMPLETED| (Attempt 1..3, delay 2s, 4s, 8s)
      v                      |
  [CONNECTED]                |
      |                      |
      | DataChannel OPEN     |
      v                      |
    [OPEN]                   |
      |                      |
      | PeerConnection FAILED|
      | or CLOSED            |
      v                      |
   [FAILED] -----------------+ (If attempts < 3)
      |
      | Exhausted retries
      v
   [CLOSED]
```

### 5.2 Message Delivery Status Transitions

Each message in `MessageEntity` transitions through discrete states:

```
  [OUTGOING MESSAGE]
         |
         v
    STATUS_PENDING (0)
         |
         | Dispatched via DataChannel or Signaling Relay
         v
    STATUS_SENT (1)
         |
         | Encrypted Delivery ACK received and decrypted
         v
    STATUS_DELIVERED (2)
         |
         | Encrypted Read Receipt received and decrypted
         v
    STATUS_READ (3)
```

If an transmission error occurs during initial dispatch, the message transitions to `STATUS_FAILED` (`-1`).

---

## 6. Threading and Concurrency Model

### Android Client
- **Coroutines & Dispatchers**:
  - `Dispatchers.IO`: Used for all database I/O, network requests, Signal Protocol cryptographic operations, and serialization.
  - `Dispatchers.Main`: Reserved exclusively for Jetpack Compose UI state observation and state mutation.
- **Thread-Safe Collections**:
  - `WebRtcManager` uses `ConcurrentHashMap` for `peerConnections`, `dataChannels`, `dataChannelStates`, and `reconnectAttempts`.
- **Database Concurrency**:
  - Room with SQLCipher handles database connections. `OnConflictStrategy.IGNORE` is enforced on message insertion (`MessageDao.kt:24`) to eliminate race conditions between simultaneous DataChannel and relay arrivals.

### Ephemeral Signaling Server
- **Single-Threaded Event Loop**: Standard Node.js non-blocking asynchronous I/O.
- **Race Condition Mitigations**:
  - In-memory lookups, array shifts, and map deletions execute synchronously within tick boundaries, avoiding multi-threaded mutex contention.
- **Per-Socket Sliding Window Rate Limiting**: Max 20 events/second per socket. Exceeding the threshold triggers socket termination after 3 violations.

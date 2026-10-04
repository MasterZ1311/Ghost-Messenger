

<p align="center">
  <img src="calypso_logo.png" alt="Calypso Logo" width="140"/>
</p>


<h1 align="center">Calypso</h1>
<h3 align="center">Zero Metadata · Zero Logs · True Peer-to-Peer Encryption</h3>

---

## Overview

**Calypso** (derived from the Greek *kalyptō* [καλύπτω] — *"to conceal"*, *"to veil"*) is a privacy-first, peer-to-peer encrypted messaging application. Messages are transmitted directly between peers over an end-to-end encrypted WebRTC DataChannel. The application uses the Signal Protocol for message-level encryption; the signaling server only brokers the WebRTC handshake (SDP offer/answer and ICE candidates) and does not access message content.

The signaling server is intentionally designed to be metadata-minimal: it brokers connections and manages presence but does not persist or inspect message payloads.

### Key Principles

- Signal Protocol end-to-end encryption (Double Ratchet)
- Minimal server knowledge: the signaling backend is metadata-limited
- True peer-to-peer transport via WebRTC DataChannel
- BIP39 mnemonic identity backup and account recovery
- Encrypted local storage using SQLCipher for message history
- Serverless identities: no accounts, email addresses, or phone numbers required

---

## Architecture

```
Calypso
├── android/                  # Native Android client (Kotlin + Jetpack Compose)
│   └── app/src/main/kotlin/org/ghostmessenger/
│       ├── core/
│       │   ├── crypto/       # BIP39 mnemonic derivation, KeyManager, SignalCryptoManager
│       │   └── model/        # Identity, EncryptedEnvelope models
│       ├── data/
│       │   ├── crypto/       # SqliteSignalProtocolStore (Room-backed)
│       │   ├── local/        # Room database, Encrypted SQLCipher, EncryptedSharedPreferences
│       │   ├── network/      # PreKey REST client, Socket.IO SignalingClient
│       │   ├── repository/   # MessageRepository, reactive message streams
│       │   └── webrtc/       # WebRtcManager (P2P DataChannel & ICE handling)
│       └── ui/
│           ├── onboarding/   # BIP39 seed generation & wallet recovery
│           ├── home/         # Active peer list, QR codes, presence indicators
│           ├── chat/         # End-to-end encrypted messaging view
│           ├── verification/ # Safety number numeric fingerprint & contact verification
│           ├── settings/     # Key backup, TURN relay setup, vault purge
│           └── theme/        # Obsidian dark theme & design tokens
│
├── server/                   # Ephemeral Node.js signaling & prekey broker
│   ├── src/
│   │   ├── routes/           # PreKey bundle upload/fetch REST API
│   │   ├── sockets/          # Socket.IO WebRTC signaling relay (SDP/ICE)
│   │   └── store/            # In-Memory PreKey store, PresenceManager, OfflineQueueStore
│   ├── scripts/              # Automated deployment verification
│   └── test/                 # Server REST & Socket.IO test suite
│
├── docs/                     # Release runbooks, Google Play compliance & legal policies
│   ├── PLAY_STORE_RELEASE_V1.0.2_GUIDE.md
│   └── playstore/            # Privacy Policy, Terms, Data Safety, Store Listing & Assets
│
├── Dockerfile                # Root Dockerfile for cloud deployment (Railway/Render)
└── railway.json              # Railway deployment manifest
```

### Connection Flow

```
  Client A                   Signaling Server                  Client B
     │                            │                               │
     │---- register(userCode) ---▶ │                               │
     │                            │ ◀---- register(userCode) ---- │
     │                            │                               │
     │---- webrtc-offer ---------▶ │ ---- webrtc-offer ----------▶ │
     │                            │                               │
     │ ◀--- webrtc-answer ------- │ ◀--- webrtc-answer ---------- │
     │                            │                               │
     │ ◀════════ E2EE WebRTC DataChannel (P2P) ═════════════════▶ │
     │                   (Server no longer participates)           │
```

---

## Security Model

| Layer | Technology | Detail |
|---|---|---|
| Message Encryption | Signal Protocol (Double Ratchet) | Forward secrecy and post-compromise recovery per-message |
| Key Agreement | Curve25519 (X3DH) | Asynchronous handshake for establishing shared secrets |
| Contact Verification | Safety Numbers (SHA-512) | 30-digit numeric fingerprints for out-of-band MITM detection |
| Transport | WebRTC DataChannel + DTLS | Direct peer-to-peer data transport with automatic reconnection |
| Control Signals | Encrypted ACKs & Read Receipts | All acknowledgments end-to-end encrypted via Double Ratchet |
| Identity Keys | libsignal-android | Curve25519 keypairs |
| Identity Backup | BIP39 Mnemonic (12 words) | Offline recovery of identity |
| Local Storage | SQLCipher (net.zetetic:sqlcipher-android) | AES-256 encrypted Room database |
| Key Storage | EncryptedSharedPreferences | OS-level Android Keystore secure storage |
| Server Side | Zero plaintext knowledge | Ephemeral relay buffers encrypted blobs only; never touches plaintext |
| Relay Privacy | Zero hardcoded credentials | STUN-only by default; custom TURN credentials encrypted in preferences |

---

## Getting Started

### Prerequisites

- Android Studio (Ladybug / Meerkat or newer) with Android SDK 26–36
- JDK 17
- Node.js >= 18.0.0
- Docker (optional, for server container deployment)

### 1. Clone the repository

```bash
git clone https://github.com/MasterZ1311/Ghost-Messenger.git
cd Ghost-Messenger
```

### 2. Start the signaling server

```bash
cd server

# Install dependencies
npm install

# Run in development mode
npm start

# Run test suite
npm test
```

By default the server starts on `http://localhost:3000`.

Verify the server is running:

```bash
curl http://localhost:3000/health
# → {"status":"ok", ...}
```

### 3. Run the Android client

Open the `android/` directory in **Android Studio**, or build from the command line:

```bash
cd android

# Build debug APK
./gradlew assembleDebug

# Build release bundle (AAB) for Google Play
./gradlew :app:bundleRelease
```

---

## Configuration

### Signaling Server

Key environment variables:

| Variable | Default | Description |
|---|---:|---|
| PORT | 3000 | HTTP server port |
| NODE_ENV | development | Runtime environment |
| CORS_ORIGINS | (empty = all) | Comma-separated allowed origins |
| STUN_URLS | Google STUN | Comma-separated STUN URLs |
| TURN_URLS | (optional) | TURN relay URL(s) |
| TURN_SECRET | (optional) | Shared auth secret for ephemeral TURN credentials |

### Android Client

To point the client at a deployed signaling server, configure the signaling URL in the app's **Settings** screen or update `DEFAULT_SIGNALING_URL` in `SecurePreferences.kt`:

```kotlin
const val DEFAULT_SIGNALING_URL = "https://your-signaling-server.com"
```

---

## Production Deployment

Deploy the signaling server to Railway or Render using the included root `Dockerfile`:

```bash
# Build Docker image
docker build -t calypso-signaling .

# Run container
docker run -p 3000:3000 calypso-signaling
```

---

## Signaling API Reference

### HTTP Endpoints

| Method | Path | Description |
|---|---|---|
| GET | / | Service info |
| GET | /health | Liveness probe |
| GET | /ready | Readiness probe |
| GET | /api/prekeys/challenge/:userCode | Issue cryptographic challenge nonce |
| POST | /api/prekeys/upload | Upload PreKey bundle with HMAC signature |
| GET | /api/prekeys/:userCode | Fetch PreKey bundle for peer (consumes one-time prekey) |
| POST | /api/prekeys/fcm-token | Register device FCM token for zero-knowledge wake-up pings |
| POST | /api/turn-credentials | Generate ephemeral TURN credentials |

### Socket.IO Events

Client → Server

| Event | Payload | Notes |
|---|---|---|
| register | { userCode, fcmToken? } | Register presence and optional FCM wake-up token |
| register-fcm | { fcmToken } | Register or update device FCM token |
| signal | { toCode, fromCode, signalData } | Relay SDP/ICE or offline envelope |
| check_presence | userCode | Query whether a user is currently online |

Server → Client

| Event | Payload | Notes |
|---|---|---|
| registered | { userCode, success } | Registration acknowledged |
| signal | { fromCode, signalData } | Incoming relayed signal or envelope |
| peer_status | { toCode, status: 'queued' } | Recipient offline; signal buffered in volatile memory |
| presence_result | { userCode, online } | Presence query result |
| offline_messages | Array<{ fromUserCode, envelope }> | Flushed upon registration for offline queue draining |
| error_message | { code, message } | Error response |

---

## Tech Stack

### Client (`android/`)

| Technology | Purpose |
|---|---|
| Kotlin + Jetpack Compose | Modern declarative native Android UI |
| Material 3 | Obsidian dark theme & design tokens |
| libsignal-android | Signal Protocol Double Ratchet & PreKey management |
| Stream WebRTC Android | WebRTC DataChannel P2P transport |
| Room + SQLCipher | Hardware-accelerated AES-256 encrypted local SQLite database |
| EncryptedSharedPreferences | OS-level Android Keystore secure storage |
| BIP39 (Bip39 library) | 12-word cryptographic seed phrase generation & restore |
| Socket.IO Client Java | Real-time WebSocket connection to signaling server |
| Dagger Hilt | Dependency injection |
| Kotlin Coroutines & Flow | Asynchronous reactive programming |

### Server (`server/`)

| Technology | Purpose |
|---|---|
| Node.js (ES Modules) | Lightweight asynchronous runtime |
| Socket.IO | Real-time WebRTC handshake event relay (SDP/ICE) |
| Express | HTTP server & PreKey REST endpoints |
| Helmet | HTTP security headers |
| express-rate-limit | Endpoint rate limiting |
| Native Node Test Runner | Fast, dependency-free test execution |

---

## Documentation & Technical Reference

Comprehensive documentation for developers, security researchers, and code reviewers is maintained in the `docs/` directory:

### Architecture & Security Specifications
- **System Architecture**: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — System topology, Android MVVM/Compose layers, server modules, and WebRTC DataChannel state machines.
- **Cryptographic Protocol**: [docs/CRYPTOGRAPHY.md](docs/CRYPTOGRAPHY.md) — Deterministic BIP-39 derivation, Signal Double Ratchet, X3DH, Safety Numbers, encrypted control packets, and SQLCipher at-rest encryption.
- **Signaling & Wire Protocol**: [docs/SIGNALING_AND_NETWORKING.md](docs/SIGNALING_AND_NETWORKING.md) — HTTP REST endpoints, Socket.IO wire events, HMAC challenge auth, WebRTC SDP/ICE negotiation, and offline queuing.
- **Security & Threat Model**: [docs/SECURITY_AND_THREAT_MODEL.md](docs/SECURITY_AND_THREAT_MODEL.md) — Zero-knowledge server proofs, threat vector matrix, replay protection, and client hardening.
- **Code Reviewer Guide**: [docs/CODE_REVIEW_GUIDE.md](docs/CODE_REVIEW_GUIDE.md) — File-by-file inspection guide, verification procedures, testing runbooks, and common review pitfalls.
- **Build Audit Report**: [docs/AUDIT_BUILD.md](docs/AUDIT_BUILD.md) — Full build, unit test, and lint execution verification logs.
- **Features Verification Matrix**: [docs/AUDIT_FEATURES.md](docs/AUDIT_FEATURES.md) — Line-by-line evidence mapping for all cryptographic and networking features.

### Google Play & Compliance Documentation
- **Release Runbook (v1.0.2)**: [Google Play Store Release & Update Guide](docs/PLAY_STORE_RELEASE_V1.0.2_GUIDE.md)
- **Privacy Policy**: [Privacy Policy](docs/playstore/PRIVACY_POLICY.md)
- **Terms of Service**: [Terms of Service](docs/playstore/TERMS_OF_SERVICE.md)
- **Data Safety Declaration**: [Data Safety Details](docs/playstore/DATA_SAFETY_DECLARATION.md)
- **Store Listing Copy**: [Store Listing](docs/playstore/STORE_LISTING.md)

---

## Roadmap

- Push notifications for wake-up delivery (FCM / unified push)
- Group messaging support (sender keys / multi-party sessions)
- Encrypted file and media transfer over WebRTC DataChannels
- Cross-platform desktop and iOS clients
- Self-hosted signaling bundle and one-click container deployments

---

## Contributing

Contributions are welcome. Please open an issue to discuss proposed changes before submitting a pull request.

1. Fork the repository
2. Create a feature branch: git checkout -b feat/your-feature
3. Commit your changes: git commit -m "feat: add your feature"
4. Push the branch: git push origin feat/your-feature
5. Open a Pull Request against MZ-Main

---

## License

This project is licensed under the MIT License.

---

<p align="center">
  Built for privacy by <a href="https://github.com/MasterZ1311">MasterZ1311</a>
</p>

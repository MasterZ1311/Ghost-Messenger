<p align="center">
  <img src="banner.png" alt="Ghost Messenger Banner" width="100%"/>
</p>

<p align="center">
  <a href="https://github.com/MasterZ1311/Ghost-Messenger/blob/MZ-Main/codechat_signaling/package.json">
    <img src="https://img.shields.io/badge/signaling-v2.0.0-00FF41?style=for-the-badge&logo=node.js&logoColor=white" alt="Signaling Version"/>
  </a>
  <a href="https://github.com/MasterZ1311/Ghost-Messenger/blob/MZ-Main/codechat_client/pubspec.yaml">
    <img src="https://img.shields.io/badge/client-v1.0.0-00FF41?style=for-the-badge&logo=flutter&logoColor=white" alt="Client Version"/>
  </a>
  <img src="https://img.shields.io/badge/license-MIT-blue?style=for-the-badge" alt="License"/>
  <img src="https://img.shields.io/badge/platform-Flutter%20Web-02569B?style=for-the-badge&logo=flutter&logoColor=white" alt="Platform"/>
  <img src="https://img.shields.io/badge/E2EE-Signal%20Protocol-red?style=for-the-badge&logo=signal&logoColor=white" alt="E2EE"/>
</p>

<h3 align="center">Zero Metadata &nbsp;·&nbsp; Zero Logs &nbsp;·&nbsp; True P2P Encryption</h3>

---

## 🔍 Overview

**Ghost Messenger** is a privacy-first, peer-to-peer encrypted messaging application. Messages travel **directly between peers over an end-to-end encrypted WebRTC DataChannel** using the **Signal Protocol (Double Ratchet)** — the same cryptographic foundation used by Signal and WhatsApp.

The signaling server **never sees message content**. It exists only to broker the WebRTC handshake (SDP offer/answer + ICE candidates) needed to establish the direct P2P channel. Once connected, the server is out of the picture entirely.

### Key Principles

- 🔐 **Signal Protocol E2EE** — Double Ratchet encryption on every message
- 👻 **Zero-knowledge server** — the signaling backend is metadata-blind
- 📡 **True P2P** — messages flow directly between devices via WebRTC DataChannel
- 🌱 **BIP39 Identity** — 12-word mnemonic backup & account recovery
- 🗄️ **Encrypted local storage** — SQLCipher database for message history
- 🆔 **Serverless identity** — no accounts, emails, or phone numbers required

---

## 🏗️ Architecture

```
Ghost Messenger
├── codechat_client/          # Flutter (Web) application
│   └── lib/
│       ├── core/
│       │   ├── crypto/       # BIP39 key management, Curve25519 identity
│       │   ├── signal/       # Signal Protocol session store (SQLite-backed)
│       │   └── storage/      # SQLCipher database helper
│       ├── services/
│       │   ├── app_state.dart        # Global app state + lifecycle
│       │   ├── connection_manager.dart # P2P orchestration
│       │   ├── p2p_service.dart       # WebRTC DataChannel management
│       │   └── signal_service.dart    # Signal Protocol encrypt/decrypt
│       └── ui/
│           ├── onboarding/   # Identity creation + mnemonic restore
│           ├── home/         # Chat list (recent conversations)
│           ├── chat/         # Real-time chat screen
│           └── theme/        # Dark theme & design system
│
└── codechat_signaling/       # Node.js signaling server
    └── src/
        ├── socket/           # Socket.IO event handlers
        ├── services/         # Presence registry, offline queue, ICE
        ├── routes/           # Health, readiness, metrics, ICE endpoints
        ├── middleware/        # Socket rate limiting
        └── utils/            # Logging, validation
```

### Connection Flow

```
  Client A                   Signaling Server                  Client B
     │                            │                               │
     │──── join(userCode) ───────▶│                               │
     │                            │◀──── join(userCode) ──────────│
     │                            │                               │
     │──── signal(offer) ────────▶│──── signal(offer) ───────────▶│
     │                            │                               │
     │◀─── signal(answer) ────────│◀─── signal(answer) ───────────│
     │                            │                               │
     │◀═══════════ E2EE WebRTC DataChannel (P2P) ════════════════▶│
     │                   [Server is now out of the loop]          │
```

---

## 🔒 Security Model

| Layer | Technology | Detail |
|---|---|---|
| **Message Encryption** | Signal Protocol (Double Ratchet) | Forward secrecy + break-in recovery on every message |
| **Key Agreement** | Curve25519 (X3DH) | Async handshake for establishing shared secrets |
| **Transport** | WebRTC DataChannel + DTLS | Encrypted P2P data channel |
| **Identity Keys** | `libsignal_protocol_dart` | Curve25519 keypairs |
| **Identity Backup** | BIP39 Mnemonic (12 words) | Offline recovery of identity |
| **Local Storage** | SQLCipher (`sqflite_sqlcipher`) | AES-256 encrypted message database |
| **Key Storage** | `flutter_secure_storage` | OS keychain / secure enclave |
| **Server Side** | Zero-knowledge | Server only relays SDP/ICE, never message content |
| **Anti-Spoofing** | `fromCode` validation | A socket can only emit as its own joined `UserCode` |

---

## 🚀 Getting Started

### Prerequisites

- [Flutter](https://flutter.dev/docs/get-started/install) `>=3.0.0` with **web** support enabled
- [Node.js](https://nodejs.org/) `>=18.0.0`
- [Docker](https://www.docker.com/) *(optional, for production deployment)*

---

### 1. Clone the Repository

```bash
git clone https://github.com/MasterZ1311/Ghost-Messenger.git
cd Ghost-Messenger
```

---

### 2. Start the Signaling Server

```bash
cd codechat_signaling

# Copy and configure environment variables
cp .env.example .env

# Install dependencies
npm install

# Run in development mode (auto-reload)
npm run dev

# Or run in production mode
npm start
```

The server starts on `http://localhost:3000` by default.

**Verify it's running:**
```bash
curl http://localhost:3000/health
# → {"status":"ok", ...}
```

---

### 3. Run the Flutter Client

```bash
cd codechat_client

# Get dependencies
flutter pub get

# Run on web (Chrome)
flutter run -d chrome
```

On first launch, you'll be prompted to **create a new identity** or **restore from mnemonic**.

---

## ⚙️ Configuration

### Signaling Server (`.env`)

Key environment variables — see [`.env.example`](codechat_signaling/.env.example) for the full annotated list:

| Variable | Default | Description |
|---|---|---|
| `PORT` | `3000` | HTTP server port |
| `NODE_ENV` | `development` | Runtime environment |
| `CORS_ORIGINS` | *(empty = all)* | Comma-separated allowed origins |
| `STUN_URLS` | Google STUN | Comma-separated STUN URLs |
| `TURN_URLS` | *(optional)* | TURN relay URL(s) |
| `TURN_SECRET` | *(optional)* | coturn `static-auth-secret` for ephemeral credentials |
| `SIGNAL_RATE_MAX` | `30` | Max signals per socket per window |
| `QUEUE_TTL_MS` | `30000` | Offline handshake buffer TTL |
| `MAX_SOCKETS_PER_USER` | `3` | Max concurrent devices per user |

### Flutter Client

To point the client at your deployed signaling server, update the URL in [`app_state.dart`](codechat_client/lib/services/app_state.dart):

```dart
// lib/services/app_state.dart
_connectionManager!.connect('https://your-signaling-server.com');
```

---

## 🐳 Production Deployment

The repository includes a full production stack with Docker Compose:

```bash
cd codechat_signaling

# Configure your environment
cp .env.example .env
# Edit .env: set CORS_ORIGINS, TURN_SECRET, TURN_REALM, etc.

# Start all services
docker compose up --build -d
```

The stack includes:

| Service | Description |
|---|---|
| `signaling` | Node.js Socket.IO signaling server |
| `redis` | Redis 7 (for horizontal scaling via `@socket.io/redis-adapter`) |
| `turn` | coturn TURN server for NAT traversal |
| `caddy` | Caddy reverse proxy with automatic HTTPS/TLS |

> **Note:** The container runs as a non-root user and includes a Docker `HEALTHCHECK`.

### Why TURN?
STUN alone fails for ~10–20% of connections behind symmetric NATs. Configure coturn with `--use-auth-secret` and set `TURN_SECRET` to enable time-limited ephemeral TURN credentials — strongly recommended for production.

---

## 📡 Signaling API Reference

### HTTP Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Service info |
| `GET` | `/health` | Liveness probe |
| `GET` | `/ready` | Readiness probe |
| `GET` | `/metrics` | Counts only (no PII): presence, queue, memory |
| `GET` | `/api/ice-servers` | STUN + optional TURN ICE server list |

### Socket.IO Events

**Client → Server**

| Event | Payload | Notes |
|---|---|---|
| `join` | `userCode: string` | Register presence |
| `signal` | `{ toCode, fromCode, signalData }` | Relay SDP/ICE; `fromCode` must match joined code |
| `check_presence` | `userCode: string` | Query whether a user is online |

**Server → Client**

| Event | Payload | Notes |
|---|---|---|
| `joined` | `{ userCode }` | Join acknowledged |
| `signal` | `{ fromCode, signalData }` | Incoming relayed signal |
| `peer_status` | `{ toCode, status: 'queued' }` | Recipient offline; signal buffered |
| `presence_result` | `{ userCode, online }` | Presence query result |
| `error_message` | `{ code, message }` | Error response |

---

## 🛠️ Tech Stack

### Client (`codechat_client`)

| Technology | Purpose |
|---|---|
| [Flutter](https://flutter.dev/) | Cross-platform UI framework |
| [flutter_webrtc](https://pub.dev/packages/flutter_webrtc) | WebRTC DataChannel P2P transport |
| [socket_io_client](https://pub.dev/packages/socket_io_client) | Signaling server connection |
| [libsignal_protocol_dart](https://pub.dev/packages/libsignal_protocol_dart) | Signal Protocol E2EE (pure Dart, auditable) |
| [sqflite_sqlcipher](https://pub.dev/packages/sqflite_sqlcipher) | AES-256 encrypted local message store |
| [flutter_secure_storage](https://pub.dev/packages/flutter_secure_storage) | OS-level secure key storage |
| [bip39](https://pub.dev/packages/bip39) | BIP39 mnemonic generation & validation |

### Server (`codechat_signaling`)

| Technology | Purpose |
|---|---|
| [Node.js](https://nodejs.org/) | Runtime |
| [Socket.IO](https://socket.io/) | Real-time WebSocket events |
| [Express](https://expressjs.com/) | HTTP server & REST endpoints |
| [Helmet](https://helmetjs.github.io/) | HTTP security headers |
| [express-rate-limit](https://www.npmjs.com/package/express-rate-limit) | HTTP rate limiting |
| [pino](https://getpino.io/) | Structured JSON logging |
| [ioredis](https://www.npmjs.com/package/ioredis) + `@socket.io/redis-adapter` | Horizontal scaling adapter |

---

## 🗺️ Roadmap

- [ ] **Push notifications** — offline message delivery via FCM/APNS
- [ ] **Group chats** — multi-party Signal Protocol sessions
- [ ] **File sharing** — encrypted binary transfer over DataChannel
- [ ] **Mnemonic → deterministic keys** — true BIP39 KDF derivation for identity keypair
- [ ] **Join challenge-response** — server-verifiable `UserCode` ownership (sign server nonce with identity key)
- [ ] **Mobile build** — Android & iOS targets via Flutter
- [ ] **Self-hosted relay** — packaged one-click self-hosting guide

---

## 🤝 Contributing

Contributions are welcome! Please open an issue to discuss your idea before submitting a pull request.

1. Fork the repository
2. Create a feature branch: `git checkout -b feat/your-feature`
3. Commit your changes: `git commit -m "feat: add your feature"`
4. Push to the branch: `git push origin feat/your-feature`
5. Open a Pull Request against `MZ-Main`

---

## 📄 License

This project is licensed under the **MIT License**.

---

<p align="center">
  Built with 💚 for privacy, by <a href="https://github.com/MasterZ1311">MasterZ1311</a>
</p>

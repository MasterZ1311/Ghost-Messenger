# 👻 Ghost Messenger — Android Native: Build From Scratch

> **Mission**: Build Ghost Messenger as a **100% Android-native** app (Kotlin + Jetpack Compose), delivering true peer-to-peer E2E encrypted messaging with **zero registration**, **zero server logs**, and **zero metadata leakage**. This guide covers everything — tech stack, credentials, project structure, phases, and deployment.

---

## Table of Contents

1. [Core Philosophy & MVP Scope](#1-core-philosophy--mvp-scope)
2. [Tech Stack](#2-tech-stack)
3. [Credentials & API Keys to Acquire](#3-credentials--api-keys-to-acquire)
4. [Project Hierarchy](#4-project-hierarchy)
5. [Architecture & Data Flow](#5-architecture--data-flow)
6. [Security Model](#6-security-model)
7. [Phase-by-Phase Build Plan (MVP)](#7-phase-by-phase-build-plan-mvp)
8. [Key Gradle Dependencies](#8-key-gradle-dependencies)
9. [Environment & Dev Setup](#9-environment--dev-setup)
10. [Backend (Signaling Server)](#10-backend-signaling-server)
11. [Free Cloud Deployment](#11-free-cloud-deployment)
12. [Agile Upgrade Roadmap (Post-MVP)](#12-agile-upgrade-roadmap-post-mvp)
13. [Common Pitfalls to Avoid](#13-common-pitfalls-to-avoid)

---

## 1. Core Philosophy & MVP Scope

### What Ghost Messenger Does (MVP Only)

| Feature | In MVP | Notes |
|---|---|---|
| Mnemonic identity creation (BIP-39, 12 words) | ✅ | No phone/email/username |
| UserCode generation (8-char, e.g. `5J9L-2P4X`) | ✅ | Derived from public key |
| Add contact by UserCode | ✅ | Manual entry |
| 1-to-1 real-time text chat | ✅ | P2P via WebRTC DataChannel |
| Signal Protocol E2EE (X3DH + Double Ratchet) | ✅ | Every message |
| Encrypted local storage (SQLCipher) | ✅ | AES-256 |
| Ephemeral signaling server (WebRTC broker only) | ✅ | Node.js + Socket.IO |

### What is OUT of MVP (upgrade later)

- Group chats
- File/media transfer
- Push notifications (FCM)
- Disappearing messages timer
- QR-code contact scanning
- Safety number verification UI
- iOS build

---

## 2. Tech Stack

### Android Client (Kotlin + Jetpack Compose)

| Layer | Technology | Why |
|---|---|---|
| **Language** | Kotlin 2.x | First-class Android, coroutines, null safety |
| **UI Framework** | Jetpack Compose (Material 3) | Declarative, modern, no XML hell |
| **DI (Dependency Injection)** | Hilt (Dagger) | Standard Android DI, testable |
| **Async** | Kotlin Coroutines + Flow | Structured concurrency, reactive streams |
| **Navigation** | Jetpack Navigation Compose | Type-safe, single-activity |
| **Cryptography** | Signal Protocol Android (libsignal) | Battle-tested, production X3DH + Double Ratchet |
| **BIP-39 Mnemonic** | `cash.z.ecc.android:kotlin-bip39` | Kotlin-native BIP-39 |
| **WebRTC P2P** | `io.getstream:stream-webrtc-android` | Maintained WebRTC fork for Android |
| **Signaling Socket** | `io.socket:socket.io-client` | Socket.IO Java/Android client |
| **Encrypted DB** | SQLCipher for Android + Room | AES-256 encrypted local storage |
| **Secure Key Storage** | Android Keystore System (EncryptedSharedPreferences) | Hardware-backed key vault |
| **Serialization** | `kotlinx.serialization` | Kotlin-idiomatic JSON |
| **Image Loading** | Coil 3 | Kotlin-first, Compose-friendly |
| **Build System** | Gradle (KTS) | Kotlin build scripts |

### Backend (Signaling Server — unchanged)

| Technology | Purpose |
|---|---|
| Node.js 20 LTS | Runtime |
| Express.js | HTTP server |
| Socket.IO 4.x | WebSocket event broker |
| Helmet.js | Security headers |
| express-rate-limit | Rate limiting |
| Pino | Structured JSON logging |
| Docker + Caddy + CoTURN | Production deployment stack |

> **No database on the backend.** Prekey bundles live in an in-memory Map (TTL = 7 days). No logs of IPs, no message content ever touches the server.

---

## 3. Credentials & API Keys to Acquire

> [!IMPORTANT]
> Acquire these **before** starting development. Most are free. Set them up in order.

### 3.1 Google / Firebase (For FCM — needed later, skip for MVP)
- **When**: Post-MVP, for push notifications
- **How**: [console.firebase.google.com](https://console.firebase.google.com) → New Project → Add Android App → Download `google-services.json`
- **Cost**: 100% Free (FCM has no message limits for data messages)

### 3.2 STUN/TURN Server

#### Free Option A — Use Google's Public STUN (MVP default, no signup needed)
```
stun:stun.l.google.com:19302
stun:stun1.l.google.com:19302
```
> Good enough for ~70% of connections (fails under symmetric NAT).

#### Free Option B — Self-hosted CoTURN on Oracle Cloud Free VM (Recommended for production)
- **When**: When you deploy the signaling server
- **Credential needed**: A `TURN_SECRET` — a random 32-byte hex string you generate yourself:
  ```bash
  openssl rand -hex 32
  ```
- **No third-party account needed** — you run your own TURN server

#### Paid Option — Metered.ca (if you don't want to self-host)
- [metered.ca](https://www.metered.ca) → Free plan: 50 GB/month TURN relay bandwidth
- **Credentials given**: `TURN_URL`, `TURN_USERNAME`, `TURN_CREDENTIAL`

### 3.3 Signaling Server Hosting

| Option | Cost | Sign-up Required | Notes |
|---|---|---|---|
| Oracle Cloud Always Free VM | **Free Forever** | Yes (OCI account, credit card for identity) | 4 OCPUs ARM + 24 GB RAM |
| Fly.io | Free hobby tier | Yes (GitHub login) | 3 shared VMs free |
| Render.com | Free web service | Yes (GitHub login) | Sleeps after 15 min inactivity |
| Railway.app | $5 credit/month | Yes (GitHub login) | Simple Docker deploy |

### 3.4 Dynamic DNS (for your server hostname)
- [DuckDNS](https://www.duckdns.org) — 100% Free, 5 subdomains, no credit card
  - Sign up with GitHub/Google
  - Create subdomain: `ghostmessenger.duckdns.org`
  - **Credential**: Your DuckDNS token (shown in dashboard)

### 3.5 Google Play Console (for distribution — not needed for dev)
- **Cost**: One-time $25 USD registration fee
- [play.google.com/console](https://play.google.com/console)

### 3.6 Summary Table

| Credential | Required For | Cost | Where to Get |
|---|---|---|---|
| DuckDNS token | Server domain | Free | duckdns.org |
| Oracle Cloud account | Server hosting | Free (card for identity) | cloud.oracle.com/free |
| `TURN_SECRET` (self-generated) | TURN server auth | Free | `openssl rand -hex 32` |
| Firebase project + `google-services.json` | Push (Post-MVP) | Free | console.firebase.google.com |
| Google Play Console | App distribution | $25 one-time | play.google.com/console |

---

## 4. Project Hierarchy

```
GhostMessenger/                          ← Root repo
│
├── android/                             ← Android native app (Kotlin + Compose)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   └── kotlin/org/ghostmessenger/
│   │   │       │
│   │   │       ├── core/                ← Pure business logic, no Android deps
│   │   │       │   ├── crypto/
│   │   │       │   │   ├── KeyManager.kt          ← BIP-39 → HKDF → Curve25519
│   │   │       │   │   ├── UserCodeUtils.kt       ← UserCode generation
│   │   │       │   │   └── SignalCryptoManager.kt ← X3DH + Double Ratchet wrapper
│   │   │       │   ├── model/
│   │   │       │   │   ├── Identity.kt            ← UserCode + keypair
│   │   │       │   │   ├── Conversation.kt
│   │   │       │   │   └── Message.kt
│   │   │       │   └── protocol/
│   │   │       │       └── EncryptedEnvelope.kt   ← Wire format (kotlinx.serialization)
│   │   │       │
│   │   │       ├── data/                ← Data layer: database + secure prefs
│   │   │       │   ├── db/
│   │   │       │   │   ├── AppDatabase.kt         ← Room + SQLCipher setup
│   │   │       │   │   ├── dao/
│   │   │       │   │   │   ├── ConversationDao.kt
│   │   │       │   │   │   └── MessageDao.kt
│   │   │       │   │   └── entity/
│   │   │       │   │       ├── ConversationEntity.kt
│   │   │       │   │       └── MessageEntity.kt
│   │   │       │   ├── signal/
│   │   │       │   │   └── SqliteSignalStore.kt   ← Signal Protocol store (Room-backed)
│   │   │       │   └── prefs/
│   │   │       │       └── SecurePreferences.kt   ← EncryptedSharedPreferences wrapper
│   │   │       │
│   │   │       ├── network/             ← Signaling + WebRTC layer
│   │   │       │   ├── SignalingClient.kt         ← Socket.IO client
│   │   │       │   ├── WebRtcManager.kt           ← PeerConnection + DataChannel
│   │   │       │   └── PrekeyApiClient.kt         ← REST: prekey publish/fetch
│   │   │       │
│   │   │       ├── service/             ← Application-level orchestration
│   │   │       │   ├── ConnectionService.kt       ← Ties signaling + WebRTC together
│   │   │       │   └── MessageService.kt          ← Encrypt → send / receive → decrypt
│   │   │       │
│   │   │       ├── ui/                  ← Jetpack Compose screens + ViewModels
│   │   │       │   ├── theme/
│   │   │       │   │   ├── GhostTheme.kt          ← Colors, typography, shapes
│   │   │       │   │   └── Type.kt
│   │   │       │   ├── onboarding/
│   │   │       │   │   ├── OnboardingScreen.kt    ← Mnemonic creation + restore
│   │   │       │   │   └── OnboardingViewModel.kt
│   │   │       │   ├── home/
│   │   │       │   │   ├── HomeScreen.kt          ← Conversation list
│   │   │       │   │   └── HomeViewModel.kt
│   │   │       │   └── chat/
│   │   │       │       ├── ChatScreen.kt          ← Real-time message view
│   │   │       │       └── ChatViewModel.kt
│   │   │       │
│   │   │       ├── di/                  ← Hilt modules
│   │   │       │   ├── CryptoModule.kt
│   │   │       │   ├── DatabaseModule.kt
│   │   │       │   ├── NetworkModule.kt
│   │   │       │   └── ServiceModule.kt
│   │   │       │
│   │   │       └── GhostMessengerApp.kt           ← @HiltAndroidApp application class
│   │   │
│   │   ├── build.gradle.kts
│   │   └── proguard-rules.pro
│   │
│   ├── build.gradle.kts                 ← Root Gradle config
│   ├── settings.gradle.kts
│   └── gradle/
│       └── libs.versions.toml           ← Version catalog (BOM-style)
│
├── signaling-server/                    ← Node.js signaling backend
│   ├── src/
│   │   ├── index.js                     ← Entry point
│   │   ├── socket/
│   │   │   └── signaling.js             ← Socket.IO events
│   │   ├── routes/
│   │   │   ├── prekey.js                ← POST /api/prekey/publish, GET /api/prekey/:code
│   │   │   ├── ice.js                   ← GET /api/ice/credentials
│   │   │   └── health.js               ← GET /health
│   │   ├── services/
│   │   │   ├── PresenceRegistry.js      ← In-memory online-user map
│   │   │   └── PrekeyStore.js           ← In-memory prekey bundles + TTL
│   │   └── middleware/
│   │       └── rateLimiter.js
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── Caddyfile
│   ├── package.json
│   └── .env.example
│
└── docs/
    ├── architecture.md
    ├── crypto-spec.md
    └── api-reference.md
```

---

## 5. Architecture & Data Flow

### Layer Dependency Rule

```
UI (Compose) → ViewModel → Service → Network/Data → Core
```
**No layer may import from a layer above it.** Core has zero Android dependencies.

### Connection Flow

```
Android App A                  Signaling Server             Android App B
      │                               │                            │
      │── Socket: join(userCode) ────▶│                            │
      │                               │◀── Socket: join(userCode) ─│
      │                               │                            │
      │── REST: GET /api/prekey/B ───▶│── returns B's PreKey bundle│
      │                               │                            │
      │── X3DH session init ──────────────────────────────────────▶│
      │── Socket: offer(SDP) ────────▶│── Socket: offer(SDP) ─────▶│
      │◀── Socket: answer(SDP) ────────│◀── Socket: answer(SDP) ───│
      │── Socket: ice_candidate ──────▶│── ice_candidate ──────────▶│
      │                               │                            │
      ╔═══════════════════════════════════════════════════════════════╗
      ║         DIRECT WebRTC SCTP DataChannel (P2P)                 ║
      ║         Signal Protocol ciphertext flows peer-to-peer         ║
      ╚═══════════════════════════════════════════════════════════════╝
      │                                                            │
      │══ { type, ciphertext, timestamp } (Binary DataChannel) ══▶│
```

### Message Send Path

```
User types → ChatViewModel.sendMessage()
  → MessageService.encrypt(plaintext, peerCode)
    → SignalCryptoManager.encrypt()  [Signal Protocol → ciphertext]
  → WebRtcManager.sendRaw(ciphertext bytes)
    → RTCDataChannel.send()          [DataChannel send]
  → MessageDao.insert(message, status=SENT)
  → ChatViewModel UI state updated
```

### Message Receive Path

```
WebRtcManager.onDataChannelMessage()
  → EncryptedEnvelope.deserialize()
  → MessageService.decrypt(envelope)
    → SignalCryptoManager.decrypt()  [Signal Protocol → plaintext]
  → MessageDao.insert(message, status=DELIVERED)
  → ChatViewModel Flow emits new message → UI recompose
```

---

## 6. Security Model

| Layer | Technology | Detail |
|---|---|---|
| Identity | BIP-39 (12-word mnemonic) | Never leaves the device |
| Key Derivation | PBKDF2-HMAC-SHA512 → HKDF-SHA256 | Deterministic Curve25519 keypair |
| Message Encryption | Signal Protocol (X3DH + Double Ratchet) | Forward secrecy per-message |
| Transport | WebRTC DTLS 1.2 + SCTP DataChannel | Encrypted P2P tunnel |
| Storage | SQLCipher AES-256-GCM | DB unreadable without key |
| Key Storage | Android Keystore (hardware-backed) | SQLCipher passkey never in RAM |
| Signaling | Ephemeral in-memory only | Zero persistent logs |

### UserCode Derivation

```
[12-Word Mnemonic]
       │
       ▼  PBKDF2-HMAC-SHA512
[64-byte seed]
       │
       ├─▶ HKDF-SHA256 (info: "GhostMessenger/IdentityKey/v1")
       │         → 32-byte Curve25519 private key
       │         → Curve25519 public key
       │         → SHA-256(pubkey)[0..5] → Base32 → "XXXX-XXXX" UserCode
       │
       └─▶ HKDF-SHA256 (info: "GhostMessenger/RegistrationId/v1")
                 → 14-bit Registration ID (1..16380)
```

---

## 7. Phase-by-Phase Build Plan (MVP)

> [!NOTE]
> Each phase produces working, testable code. Never skip phases.

### Phase 1 — Project Setup (Day 1)

- [ ] Create Android project: **Empty Compose Activity**, package `org.ghostmessenger`, min SDK 26 (Android 8.0), target SDK 35
- [ ] Set up `libs.versions.toml` version catalog
- [ ] Add all Gradle dependencies (see §8)
- [ ] Set up Hilt `@HiltAndroidApp` in `GhostMessengerApp.kt`
- [ ] Apply `GhostTheme` with color tokens (Obsidian Black `#0A0A0A`, Ghost Green `#00FF41`, Neon Cyan `#00E5FF`)
- [ ] Configure ProGuard rules for libsignal and SQLCipher
- [ ] Verify: clean build, app launches on emulator

### Phase 2 — Crypto Core (Day 2–3)

- [ ] Implement `KeyManager.kt`:
  - `generateMnemonic(): List<String>` (BIP-39, 12 words)
  - `validateMnemonic(words: List<String>): Boolean`
  - `deriveIdentityKeyPair(mnemonic: String): IdentityKeyPair`
  - `deriveRegistrationId(mnemonic: String): Int`
- [ ] Implement `UserCodeUtils.kt`:
  - `generateUserCode(publicKey: ByteArray): String` → `XXXX-XXXX`
  - `isValidFormat(code: String): Boolean`
- [ ] Unit-test both with known BIP-39 test vectors
- [ ] Verify: deterministic — same mnemonic always produces same UserCode

### Phase 3 — Encrypted Database (Day 4)

- [ ] Set up Room + SQLCipher:
  - `AppDatabase.kt` — open with SQLCipher passkey from Android Keystore
  - `SecurePreferences.kt` — EncryptedSharedPreferences for identity fields
- [ ] Create entities: `ConversationEntity`, `MessageEntity`
- [ ] Create DAOs: `ConversationDao`, `MessageDao`
- [ ] Create `SqliteSignalStore.kt` implementing `SignalProtocolStore`:
  - `IdentityKeyStore`, `PreKeyStore`, `SignedPreKeyStore`, `SessionStore`
  - All backed by Room tables
- [ ] Verify: DB file is unreadable in file manager / ADB pull without key

### Phase 4 — Signaling Server (Day 5)

- [ ] Initialize Node.js project in `signaling-server/`
- [ ] Implement Socket.IO events:
  - `join(userCode)` → marks presence
  - `offer(toCode, sdp)` → forwards to peer
  - `answer(toCode, sdp)` → forwards to peer
  - `ice_candidate(toCode, candidate)` → forwards to peer
  - `disconnect` → removes presence
- [ ] Implement REST endpoints:
  - `POST /api/prekey/publish` → stores prekey bundle in memory
  - `GET /api/prekey/:userCode` → returns + deletes one-time prekey
  - `GET /api/ice/credentials` → returns STUN URLs (TURN later)
  - `GET /health` → returns `{ status: "ok" }`
- [ ] Add rate limiting (express-rate-limit)
- [ ] Run locally: `npm run dev` → `http://localhost:3000/health`
- [ ] Verify: Two browser console tabs can exchange socket messages

### Phase 5 — WebRTC DataChannel (Day 6–7)

- [ ] Implement `SignalingClient.kt`:
  - Connect to signaling server via Socket.IO
  - Emit `join`, `offer`, `answer`, `ice_candidate`
  - Receive and forward events to `WebRtcManager`
- [ ] Implement `WebRtcManager.kt`:
  - Create `PeerConnectionFactory`
  - Create `RTCPeerConnection` with STUN config
  - Create ordered, reliable `RTCDataChannel` (label: `"ghost"`)
  - Offer/answer SDP negotiation
  - ICE candidate gathering + exchange
  - `onDataChannelMessage` callback → raw bytes
- [ ] Implement `PrekeyApiClient.kt`:
  - `publishPrekeyBundle(bundle)` on app startup
  - `fetchPrekeyBundle(userCode)` before initiating session
- [ ] Verify: Two Android emulators can connect via DataChannel and exchange raw "hello" bytes

### Phase 6 — Signal Protocol Sessions (Day 8–9)

- [ ] Implement `SignalCryptoManager.kt`:
  - `initSession(peerCode, prekeyBundle)` → X3DH session init
  - `encryptMessage(peerCode, plaintext: String): ByteArray`
  - `decryptMessage(peerCode, ciphertext: ByteArray): String`
- [ ] Implement `EncryptedEnvelope.kt` (wire format):
  ```kotlin
  @Serializable
  data class EncryptedEnvelope(
      val type: String = "encrypted_envelope",
      val fromCode: String,
      val toCode: String,
      val msgType: Int,        // 3 = PreKeySignalMessage, 2 = SignalMessage
      val ciphertext: String,  // Base64
      val timestamp: Long
  )
  ```
- [ ] Implement `MessageService.kt`:
  - `sendMessage(peerCode, text)` → encrypt → DataChannel → save to DB
  - `onIncomingRaw(bytes)` → deserialize → decrypt → save to DB → emit Flow
- [ ] Implement `ConnectionService.kt`:
  - Orchestrates: publish prekeys → connect socket → manage WebRTC lifecycle
- [ ] Verify: Encrypted text messages flow between two emulators end-to-end

### Phase 7 — Compose UI (Day 10–13)

- [ ] **GhostTheme**: MaterialTheme with custom colors, `JetBrains Mono` for codes, `Inter` for body
- [ ] **OnboardingScreen**:
  - New identity: displays 12-word grid, copy button, "I've saved my phrase" CTA
  - Restore: 12 text fields for word entry, validate on submit
- [ ] **HomeScreen**:
  - LazyColumn of conversations
  - Each tile: UserCode, last message snippet, P2P status badge (● DIRECT / ○ OFFLINE)
  - FAB: "Add Contact" (enter UserCode dialog)
- [ ] **ChatScreen**:
  - LazyColumn of message bubbles (sent = right, received = left)
  - Connection status banner at top
  - Text input + Send button
- [ ] Wire up ViewModels → Services via Hilt injection
- [ ] Navigation graph: Onboarding → Home → Chat

---

## 8. Key Gradle Dependencies

### `gradle/libs.versions.toml`

```toml
[versions]
kotlin = "2.0.21"
compose-bom = "2024.12.01"
hilt = "2.52"
room = "2.6.1"
sqlcipher = "4.5.7"
webrtc = "1.1.4"
socket-io = "2.1.0"
libsignal = "0.71.0"
bip39 = "1.0.6"
coroutines = "1.8.1"
serialization = "1.7.3"
ktor = "2.3.12"
coil = "3.0.4"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version = "2.8.4" }
compose-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version = "2.8.7" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Room + SQLCipher
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
sqlcipher = { group = "net.zetetic", name = "android-database-sqlcipher", version.ref = "sqlcipher" }
sqlite-android = { group = "androidx.sqlite", name = "sqlite-android", version = "2.4.0" }

# Crypto
libsignal-android = { group = "org.signal", name = "libsignal-android", version.ref = "libsignal" }
bip39 = { group = "cash.z.ecc.android", name = "kotlin-bip39", version.ref = "bip39" }
security-crypto = { group = "androidx.security", name = "security-crypto", version = "1.1.0-alpha06" }

# Networking
webrtc = { group = "io.getstream", name = "stream-webrtc-android", version.ref = "webrtc" }
socket-io = { group = "io.socket", name = "socket.io-client", version.ref = "socket-io" }
ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }

# Serialization
kotlinx-serialization = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }

# Coroutines
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Image
coil = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }

[plugins]
android-application = { id = "com.android.application", version = "8.7.3" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.21-1.0.27" }
```

### `app/build.gradle.kts` (key snippets)

```kotlin
android {
    namespace = "org.ghostmessenger"
    compileSdk = 35
    defaultConfig {
        minSdk = 26    // Android 8.0 — required for EncryptedSharedPreferences stable
        targetSdk = 35
    }
    buildFeatures { compose = true }
    packagingOptions {
        // Needed for libsignal native libs
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // ... all from version catalog
}
```

### `AndroidManifest.xml` Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CAMERA" />   <!-- QR scanning — post-MVP -->
```

---

## 9. Environment & Dev Setup

### Required Tools

| Tool | Version | Install |
|---|---|---|
| Android Studio | Ladybug (2024.2) or newer | [developer.android.com](https://developer.android.com/studio) |
| JDK | 17 (bundled with Android Studio) | Bundled |
| Android SDK | API 35 | SDK Manager in Android Studio |
| Node.js | 20 LTS | [nodejs.org](https://nodejs.org) |
| Docker Desktop | Latest | [docker.com](https://www.docker.com/products/docker-desktop) |
| Git | Latest | [git-scm.com](https://git-scm.com) |

### Dev Machine Setup (Windows)

```powershell
# 1. Install Node.js via winget
winget install OpenJS.NodeJS.LTS

# 2. Install signaling server deps
cd signaling-server
npm install

# 3. Start signaling server locally
npm run dev   # runs on http://localhost:3000

# 4. Android emulator
# Open Android Studio → Device Manager → Create Virtual Device
# Use Pixel 8 Pro with API 35 (Google APIs) image

# 5. Point Android app at local server:
# In SignalingClient.kt, set SIGNALING_URL = "http://10.0.2.2:3000"
# (10.0.2.2 is the emulator's alias for localhost on the host machine)
```

### Environment Variables for Signaling Server (`.env`)

```env
PORT=3000
NODE_ENV=development
CORS_ORIGINS=*
STUN_URLS=stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302
TURN_URLS=                 # Leave empty for local dev; fill in on production
TURN_SECRET=               # Leave empty for local dev
SIGNAL_RATE_MAX=30
QUEUE_TTL_MS=30000
```

---

## 10. Backend (Signaling Server)

### Minimal Viable Server — `src/index.js`

```javascript
import express from 'express';
import { createServer } from 'http';
import { Server } from 'socket.io';
import helmet from 'helmet';
import pino from 'pino';
import prekeyRouter from './routes/prekey.js';
import iceRouter from './routes/ice.js';
import healthRouter from './routes/health.js';
import { setupSignaling } from './socket/signaling.js';

const log = pino({ level: 'info' });
const app = express();
const server = createServer(app);
const io = new Server(server, {
  cors: { origin: process.env.CORS_ORIGINS?.split(',') || '*' }
});

app.use(helmet());
app.use(express.json({ limit: '50kb' })); // Small payloads only
app.use('/api/prekey', prekeyRouter);
app.use('/api/ice', iceRouter);
app.use('/health', healthRouter);

setupSignaling(io, log);

server.listen(process.env.PORT || 3000, () => {
  log.info(`Ghost Messenger signaling server running on port ${process.env.PORT || 3000}`);
});
```

### Socket Events — `src/socket/signaling.js`

```javascript
const presence = new Map(); // userCode → socketId

export function setupSignaling(io, log) {
  io.on('connection', (socket) => {
    let myCode = null;

    socket.on('join', (userCode) => {
      if (!isValidCode(userCode)) return;
      myCode = userCode;
      presence.set(userCode, socket.id);
      socket.join(userCode);
      socket.emit('joined', { userCode });
      log.info({ event: 'join', code: myCode }); // No IPs logged
    });

    socket.on('offer', ({ toCode, sdp }) => {
      if (!myCode) return;
      io.to(toCode).emit('offer', { fromCode: myCode, sdp });
    });

    socket.on('answer', ({ toCode, sdp }) => {
      if (!myCode) return;
      io.to(toCode).emit('answer', { fromCode: myCode, sdp });
    });

    socket.on('ice_candidate', ({ toCode, candidate }) => {
      if (!myCode) return;
      io.to(toCode).emit('ice_candidate', { fromCode: myCode, candidate });
    });

    socket.on('disconnect', () => {
      if (myCode) presence.delete(myCode);
    });
  });
}

function isValidCode(code) {
  return /^[A-Z2-7]{4}-[A-Z2-7]{4}$/.test(code);
}
```

---

## 11. Free Cloud Deployment

### Step 1 — Get the Server

1. Sign up at [cloud.oracle.com/free](https://cloud.oracle.com/free) (credit card for identity, never charged)
2. Create **Always Free VM**:
   - Shape: `VM.Standard.A1.Flex` — 4 OCPUs, 24 GB RAM
   - Image: Ubuntu 22.04
3. Open inbound ports in OCI Security Rules:
   - `22/TCP` (SSH)
   - `80/TCP` (HTTP)
   - `443/TCP` (HTTPS)
   - `3478/UDP+TCP` (STUN/TURN)

### Step 2 — Point a Domain to Your VM

1. Sign up at [duckdns.org](https://www.duckdns.org)
2. Create subdomain: `ghostmessenger` → paste your OCI VM public IP
3. On the VM, set up a cron to auto-update the IP:
   ```bash
   echo "*/5 * * * * curl -s 'https://www.duckdns.org/update?domains=ghostmessenger&token=YOUR_TOKEN&ip=' > /tmp/duck.log 2>&1" | crontab -
   ```

### Step 3 — Deploy with Docker Compose

```yaml
# docker-compose.yml
services:
  caddy:
    image: caddy:2-alpine
    restart: always
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data

  signaling:
    build: .
    restart: always
    env_file: .env
    environment:
      PORT: "3000"
      CORS_ORIGINS: "https://ghostmessenger.duckdns.org"
      TURN_URLS: "turn:ghostmessenger.duckdns.org:3478"
      TURN_SECRET: "${TURN_SECRET}"

  coturn:
    image: coturn/coturn:latest
    restart: always
    network_mode: host
    command:
      - "--listening-port=3478"
      - "--realm=ghostmessenger.duckdns.org"
      - "--use-auth-secret"
      - "--static-auth-secret=${TURN_SECRET}"

volumes:
  caddy_data:
```

```caddyfile
# Caddyfile
ghostmessenger.duckdns.org {
    reverse_proxy signaling:3000
}
```

### Step 4 — Launch

```bash
# On the Oracle VM
git clone https://github.com/YOUR_REPO/GhostMessenger.git
cd GhostMessenger/signaling-server
cp .env.example .env
# Edit .env: set TURN_SECRET=$(openssl rand -hex 32)
docker compose up -d --build
```

Verify:
```bash
curl https://ghostmessenger.duckdns.org/health
# → {"status":"ok"}
```

### Step 5 — Update Android App

In `SignalingClient.kt`:
```kotlin
const val SIGNALING_URL = "https://ghostmessenger.duckdns.org"
```

---

## 12. Agile Upgrade Roadmap (Post-MVP)

> Add these as separate sprints — never break the MVP core.

| Sprint | Feature | Dependencies |
|---|---|---|
| Sprint 2 | Disappearing messages (per-chat timer) | No new credentials |
| Sprint 3 | QR code contact scanning | Camera permission already in manifest |
| Sprint 4 | Safety number verification UI | No new credentials |
| Sprint 5 | FCM push notifications (wake offline peer) | Firebase project + `google-services.json` |
| Sprint 6 | Encrypted file/image transfer over DataChannel | No new credentials |
| Sprint 7 | Group chat (multi-party Signal sessions) | Significant crypto work |
| Sprint 8 | Play Store release | Google Play Console ($25) |
| Sprint 9 | iOS (Kotlin Multiplatform or React Native bridge) | Apple Developer account ($99/yr) |

---

## 13. Common Pitfalls to Avoid

> [!WARNING]
> These are the most common sources of errors in P2P crypto messenger builds.

| Pitfall | How to Avoid |
|---|---|
| **libsignal ProGuard stripping native symbols** | Add `-keep class org.signal.**` and `-keepclasseswithmembernames class * { native <methods>; }` in ProGuard rules |
| **SQLCipher passkey in SharedPreferences (plaintext)** | Always use `EncryptedSharedPreferences` backed by Android Keystore |
| **WebRTC DataChannel `ordered = false`** | Use `ordered = true, maxRetransmits = null` — Signal Protocol requires ordered delivery |
| **Socket.IO reconnect losing `join` state** | Re-emit `join(userCode)` inside `socket.on("connect")`, not just once on startup |
| **One-time prekeys not deleted server-side** | Server MUST delete OPK on first GET — replay attacks steal future messages |
| **Signaling server logging IP addresses** | Explicitly set `pino` to never log `req.ip` or `socket.handshake.address` |
| **Room + SQLCipher SupportFactory** | Must set `Room.databaseBuilder(...).openHelperFactory(SupportFactory(passphrase))` |
| **Emulator WebRTC localhost** | Use `10.0.2.2` not `localhost` for signaling URL on emulator |
| **Mnemonic on clipboard** | Clear clipboard after copy (use `ClipboardManager` + 30-second clear timer) |
| **Large ciphertext chunks on DataChannel** | Signal Protocol ciphertexts are small (< 1 KB for text) — no chunking needed for MVP |

---

*Built for privacy — Ghost Messenger Android Native Edition.*

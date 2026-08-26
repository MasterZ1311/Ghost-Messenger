# 👻 Ghost Messenger: Master Engineering Prompt & Architecture Specification

> **Mission**: Build an ultra-private, zero-knowledge, peer-to-peer (P2P) encrypted messenger from scratch. The app requires **no phone numbers, no emails, no central message storage, and zero account registration**. Users are identified purely by cryptographic public keys derived from a 12-word BIP-39 mnemonic seed phrase. Direct message communication travels over peer-to-peer WebRTC DataChannels, end-to-end encrypted with the **Signal Protocol (X3DH + Double Ratchet)**.

---

## 📋 Table of Contents
1. [AI Persona & Implementation Directives](#1-ai-persona--implementation-directives)
2. [Product Vision & Security Invariants](#2-product-vision--security-invariants)
3. [Researched 100% Free & Best-in-Class Tech Stack](#3-researched-100-free--best-in-class-tech-stack)
4. [Visual Design System & Cyberpunk Aesthetic](#4-visual-design-system--cyberpunk-aesthetic)
5. [Cryptographic Identity & Key Derivation Specification](#5-cryptographic-identity--key-derivation-specification)
6. [Signal Protocol & End-to-End Encryption Engine](#6-signal-protocol--end-to-end-encryption-engine)
7. [WebRTC Peer-to-Peer DataChannel Networking](#7-webrtc-peer-to-peer-datachannel-networking)
8. [Ephemeral Signaling Server Specification](#8-ephemeral-signaling-server-specification)
9. [Encrypted Local Storage & Disappearing Message Shredder](#9-encrypted-local-storage--disappearing-message-shredder)
10. [Zero-Metadata Push Wakeup Protocol](#10-zero-metadata-push-wakeup-protocol)
11. [Complete UI/UX Screen Specifications](#11-complete-uiux-screen-specifications)
12. [Step-by-Step Implementation Roadmap](#12-step-by-step-implementation-roadmap)
13. [Zero-Knowledge Security & Privacy Checklist](#13-zero-knowledge-security--privacy-checklist)
14. [100% Free Cloud Deployment Guide](#14-100-free-cloud-deployment-guide)

---

## 1. AI Persona & Implementation Directives

When using this document to prompt an AI coding assistant (or human engineering team), enforce the following strict directives:

```text
You are a Principal Cryptography & Distributed Systems Engineer and Elite Mobile UI Designer.
Your task is to build the "Ghost Messenger" application according to this specification.

Key Rules:
1. NEVER compromise on Zero-Knowledge principles. No plaintext or decryption keys may EVER leave the device.
2. NEVER store messages on the signaling server. The signaling server is an ephemeral broker for SDP/ICE and prekey bundles only.
3. Use production-grade crypto primitives with constant-time operations where applicable.
4. Deliver a visually stunning, futuristic cyberpunk glassmorphism UI with dark Obsidian Black (#0A0A0A), Terminal Ghost Green (#00FF41), and Neon Cyan (#00E5FF) accents.
5. All code must be strongly typed, modular, and adhere to clean architectural separation (Core Crypto -> Storage -> Networking -> Services -> UI).
```

---

## 2. Product Vision & Security Invariants

### 🔒 Core Invariants
1. **Zero Registration**: No phone numbers, email addresses, usernames, or SMS verification.
2. **Deterministic Cryptographic Identity**: 
   - A single 12-word BIP-39 mnemonic generates the root seed.
   - HKDF-SHA256 derives the Curve25519 / Ed25519 identity keypair and Registration ID.
   - A human-readable **UserCode** (e.g. `5J9L-2P4X`) is derived from `Base32(SHA256(PublicKey)[0..5])`.
3. **True Peer-to-Peer (WebRTC DataChannel)**:
   - When both peers are online, messages travel directly device-to-device with **sub-50ms latency** and zero intermediary relay server.
4. **Signal Protocol Encryption**:
   - **X3DH** (Extended Triple Diffie-Hellman) for asynchronous mutual authentication and initial key agreement.
   - **Double Ratchet Algorithm** for forward secrecy (compromised past keys cannot decrypt past messages) and break-in recovery / post-compromise security (compromised state self-heals on subsequent DH ratchets).
5. **Zero-Log Ephemeral Signaling**:
   - The signaling server stores only short-lived prekey bundles (auto-evicted after 7 days, one-time prekeys deleted upon first retrieval) and transient handshake queues.
   - **No chat logs, no message queues for established sessions, no IP logging**.
6. **Local Encryption at Rest**:
   - Device database encrypted using **SQLCipher (AES-256-GCM)**. The database key is stored in hardware keystores (Android Keystore / iOS Keychain).
7. **Disappearing Messages & Local Shredding**:
   - Configurable per-chat timers (e.g., 30s, 5m, 1h, 24h, 7d). Upon expiration, records are purged from the database and the SQLite file is vacuumed.
8. **Contact Verification via QR & Safety Numbers**:
   - 12-digit safety numbers computed from sorted identity public keys.
   - Animated visual QR code for instant zero-trust in-person verification.

---

## 3. Researched 100% Free & Best-in-Class Tech Stack

| Layer | Recommended Technology | Free Tier / Open Source Status | Why It's the Best Choice |
|---|---|---|---|
| **Mobile Framework** | **Flutter 3.x+ with Dart 3** | 100% Free & Open Source | Cross-platform (Android/iOS/Desktop) with native 60/120 FPS rendering, robust WebRTC, and SQLCipher support. |
| **Cryptography Core** | `libsignal_protocol_dart` + `crypto` + `bip39` | Open Source (GPLv3 / BSD) | Official port of Signal's battle-tested Double Ratchet and X3DH protocol. |
| **P2P Transport** | `flutter_webrtc` (DataChannels) | Open Source (BSD-3) | Hardware-accelerated, robust SCTP DataChannel support across all mobile platforms. |
| **Signaling Protocol** | `socket_io_client` (Flutter) + `socket.io` (Node) | Open Source (MIT) | Automatic reconnection, room multiplexing, binary payloads, and low overhead. |
| **Encrypted Database** | `sqflite_sqlcipher` | Open Source | Transparent AES-256 database encryption at rest. |
| **Hardware Key Vault** | `flutter_secure_storage` | Open Source | Android Keystore / iOS Keychain integration for SQLCipher master passkey storage. |
| **QR Code Engine** | `qr_flutter` + `mobile_scanner` | Open Source | Fast QR rendering and hardware-accelerated camera scanning. |
| **Signaling Backend** | **Node.js 20+ LTS + Express** | Open Source | Minimal memory footprint (<30 MB RAM), event-driven, handles thousands of concurrent socket connections. |
| **Logging & Metrics** | `pino` structured logger | Open Source | High performance, zero-allocation structured JSON logger with sanitizers for PII. |
| **Free Cloud VM** | **Oracle Cloud Always Free Tier** | **100% Free Forever** | 4 OCPUs ARM, 24 GB RAM, 200 GB Storage, 10 TB/month egress (or 2 AMD x86 VMs). |
| **Free Hosting (Alt)** | **Fly.io** / **Render.com** / **Koyeb** | Free tier available | Zero-config Docker deployment options. |
| **Free STUN/TURN** | **CoTURN (Self-hosted on Oracle Free VM)** + Google Public STUN (`stun:stun.l.google.com:19302`) + **Metered.ca Free TURN** (50GB free/mo) | 100% Free | Solves Symmetric NAT / CGNAT traversal without spending a dime. |
| **Free Dynamic DNS** | **DuckDNS** (`*.duckdns.org`) or **Cloudflare DNS** | 100% Free | Dynamic DNS updates in 1 minute, full IPv4/IPv6 support. |
| **Free SSL / Reverse Proxy** | **Caddy Server** | Open Source | Automatic Let's Encrypt TLS issuance, renewal, and HTTP/2 / HTTP/3 reverse proxy. |
| **Free Push Wakeup** | **Firebase Cloud Messaging (FCM)** | 100% Free Unlimited | Unlimited silent background data messages to wake up offline Android/iOS peers. |

---

## 4. Visual Design System & Cyberpunk Aesthetic

The app uses a **Dark Stealth / Cyberpunk Glassmorphism** visual theme. The logo must remain the iconic **Ghost silhouette with green glowing cybernetic core**.

### 🎨 Color Palette Tokens

```dart
// app_theme.dart
class GhostTheme {
  static const Color bgObsidian   = Color(0xFF0A0A0A); // Primary Background
  static const Color surfaceSlate = Color(0xFF141414); // Surface Panels / AppBars
  static const Color cardGlass    = Color(0xFF1E1E1E); // Glass Cards & Bubble Base
  static const Color borderGlow   = Color(0xFF2E2E2E); // Subtle Card Borders
  static const Color ghostGreen   = Color(0xFF00FF41); // Terminal Green / Primary Accent
  static const Color neonCyan     = Color(0xFF00E5FF); // P2P Direct & Security Verified
  static const Color warningRed   = Color(0xFFFF3B30); // Destruct Timers & Danger
  static const Color textMuted    = Color(0xFF8E8E93); // Secondary Labels
}
```

### 🔤 Typography
- **Headings & Body**: `Outfit` or `Inter` (Google Fonts) for sleek modern readability.
- **Keys, Hashes & Code**: `JetBrains Mono` for UserCodes (`5J9L-2P4X`), Safety Numbers (`9281 4820 1849`), cryptographic hashes, and timestamps.

### 🖼️ Asset Specifications (Preserving the Logo)
- **App Icon**: `docs/playstore/assets/app_icon_512.png` (512x512 PNG, dark rounded shield with glowing green ghost).
- **Feature Graphic**: `docs/playstore/assets/feature_graphic_1024x500.png` (1024x500 PNG banner).
- **Screenshots**: High-contrast dark mockups showcasing:
  1. Onboarding & 12-Word Mnemonic Vault.
  2. Home Conversations with live P2P Direct badge (`DIRECT P2P`).
  3. Real-Time Chat with disappearing message indicators.
  4. Safety Number Verification with animated QR scanning.

---

## 5. Cryptographic Identity & Key Derivation Specification

### Identity Derivation Flowchart

```
[12-Word BIP-39 Mnemonic] 
         │
         ▼ (PBKDF2-HMAC-SHA512)
[64-Byte Seed]
         │
         ├───────────────────────────────────────────┐
         ▼ (HKDF-SHA256 info: "GhostMessenger/IdentityKey/v1")  ▼ (HKDF-SHA256 info: "GhostMessenger/RegistrationId/v1")
[32-Byte Curve25519 Private Key]            [14-Bit Clamped Registration ID (1..16380)]
         │
         ▼ (Curve25519 Point Multiply)
[32-Byte Public Key]
         │
         ▼ (SHA-256 -> Prefix 5 Bytes -> RFC4648 Base32)
[8-Character UserCode: "XXXX-XXXX"]
```

### Complete Dart Implementation Interface (`key_manager.dart`)

```dart
import 'dart:convert';
import 'dart:typed_data';
import 'package:bip39/bip39.dart' as bip39;
import 'package:crypto/crypto.dart';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';

class KeyManager {
  static const String _identityInfo = 'GhostMessenger/IdentityKey/v1';
  static const String _regInfo = 'GhostMessenger/RegistrationId/v1';

  static String generateMnemonic() => bip39.generateMnemonic();

  static bool validateMnemonic(String mnemonic) => 
      bip39.validateMnemonic(mnemonic.trim().toLowerCase());

  static Uint8List deriveSeed(String mnemonic) => 
      Uint8List.fromList(bip39.mnemonicToSeed(mnemonic));

  static IdentityKeyPair generateIdentityFromMnemonic(String mnemonic) {
    final seed = deriveSeed(mnemonic);
    final salt = Uint8List(32);
    final prk = Hmac(sha256, salt).convert(seed).bytes;
    
    // HKDF Expand for 32-byte scalar
    final info = utf8.encode(_identityInfo);
    final hmac = Hmac(sha256, prk);
    final privBytes = Uint8List.fromList(hmac.convert([...info, 0x01]).bytes);
    
    final ecKeyPair = Curve.generateKeyPairFromPrivate(privBytes);
    return IdentityKeyPair(IdentityKey(ecKeyPair.publicKey), ecKeyPair.privateKey);
  }

  static int deriveRegistrationIdFromMnemonic(String mnemonic) {
    final seed = deriveSeed(mnemonic);
    final salt = Uint8List(32);
    final prk = Hmac(sha256, salt).convert(seed).bytes;
    final info = utf8.encode(_regInfo);
    final idBytes = Hmac(sha256, prk).convert([...info, 0x01]).bytes;
    final raw = ((idBytes[0] & 0x3F) << 8) | idBytes[1];
    return (raw % 16380) + 1;
  }
}
```

### UserCode Generator (`user_code_utils.dart`)

```dart
import 'dart:typed_data';
import 'package:crypto/crypto.dart';
import 'package:base32/base32.dart';

class UserCodeUtils {
  static String generateUserCode(Uint8List publicKey) {
    final hash = sha256.convert(publicKey).bytes;
    final prefix = Uint8List.fromList(hash.sublist(0, 5));
    String encoded = base32.encode(prefix).toUpperCase().replaceAll('=', '');
    return '${encoded.substring(0, 4)}-${encoded.substring(4, 8)}';
  }

  static bool isValidFormat(String code) {
    final clean = code.replaceAll('-', '').trim().toUpperCase();
    return clean.length == 8 && RegExp(r'^[A-Z2-7]{8}$').hasMatch(clean);
  }
}
```

---

## 6. Signal Protocol & End-to-End Encryption Engine

### 🔄 Encryption & Session Lifecycle

1. **PreKey Publication**:
   - Each client generates:
     - 1 Identity Key ($IK$)
     - 1 Signed PreKey ($SPK$) signed by $IK$ with a signature ($Sig$)
     - 100 One-Time PreKeys ($OPK_1 \dots OPK_{100}$)
   - Bundle published to signaling server: `{ identityKey, signedPreKey, signature, preKey, registrationId }`.
2. **Initiating Session (Alice -> Bob)**:
   - Alice fetches Bob's PreKey Bundle from the server.
   - Alice performs the **X3DH** handshake:
     $$DH_1 = DH(IK_A, SPK_B)$$
     $$DH_2 = DH(EK_A, IK_B)$$
     $$DH_3 = DH(EK_A, SPK_B)$$
     $$DH_4 = DH(EK_A, OPK_B)$$
     $$SK = KDF(DH_1 \parallel DH_2 \parallel DH_3 \parallel DH_4)$$
   - Alice initializes the Double Ratchet session and encrypts the first message inside a `PreKeySignalMessage`.
3. **Subsequent Messages**:
   - Encrypted inside a standard `SignalMessage` advancing the ratchet. Each message has a unique ephemeral symmetric key.

### Encrypted Wire Payload Format

```json
{
  "type": "encrypted_envelope",
  "fromCode": "5J9L-2P4X",
  "toCode": "9M2K-7X1L",
  "msgType": 3, // 3 = PreKeySignalMessage, 2 = SignalMessage
  "ciphertext": "BASE64_SIGNAL_CIPHERTEXT",
  "timestamp": 1787334000000,
  "destructDuration": 300 // Self-destruct in seconds (0 = never)
}
```

---

## 7. WebRTC Peer-to-Peer DataChannel Networking

### 🌐 Direct P2P Negotiation Flow

```
[Peer A (Alice)]                [Signaling Server]                [Peer B (Bob)]
      │                                 │                               │
      │── 1. Create DataChannel ────────┼───────────────────────────────│
      │── 2. Create Offer SDP ─────────▶│── Forward Offer SDP ─────────▶│
      │                                 │◀── Send Answer SDP ───────────│
      │◀── Forward Answer SDP ──────────│                               │
      │── 3. Exchange ICE Candidates ──▶│── Forward ICE Candidates ────▶│
      │◀── Forward ICE Candidates ──────│◀── Exchange ICE Candidates ───│
      │                                 │                               │
      ▼                                 ▼                               ▼
  ╔═════════════════════════════════════════════════════════════════════════╗
  ║                DIRECT WEBRTC SCTP DATACHANNEL ESTABLISHED              ║
  ║                   (ZERO RELAY / SUB-50ms LATENCY)                      ║
  ╚═════════════════════════════════════════════════════════════════════════╝
      │                                                                 │
      │═════════════════ Encrypted Signal Protocol Messages ═══════════▶│
      │◀════════════════ Encrypted Signal Protocol Messages ════════════│
```

### STUN / TURN Configuration

```dart
final Map<String, dynamic> rtcConfiguration = {
  'iceServers': [
    {'urls': 'stun:stun.l.google.com:19302'},
    {'urls': 'stun:stun1.l.google.com:19302'},
    {
      'urls': 'turn:ghostmessenger.duckdns.org:3478',
      'username': dynamicTurnUsername,
      'credential': dynamicTurnPassword,
    }
  ],
  'sdpSemantics': 'unified-plan',
};
```

---

## 8. Ephemeral Signaling Server Specification

### 🚀 Server Responsibilities
- Act solely as a transient signaling exchange (SDP offer/answer, ICE candidates, prekey store).
- **Hard rule**: Do not log IP addresses, do not store message plaintext, do not persist message history.
- Run automatically with auto-eviction of stale prekeys (7-day max age) and sliding-window rate limiting.

### REST API Endpoints

| Method | Endpoint | Description | Rate Limit |
|---|---|---|---|
| `POST` | `/api/prekey/publish` | Upload/update X3DH PreKey bundle | 10 req / 5 min |
| `GET` | `/api/prekey/:userCode` | Fetch & consume one-time PreKey | 30 req / 1 min |
| `POST` | `/api/push/register` | Register FCM push device token | 5 req / 5 min |
| `GET` | `/api/ice/credentials` | Generate time-limited HMAC TURN credentials | 20 req / 1 min |
| `GET` | `/health` | Server health & uptime probe | Unlimited |

### Socket.io Event Matrix

| Event Name | Direction | Payload | Behavior |
|---|---|---|---|
| `join` | Client -> Server | `{ userCode: string }` | Joins socket room and marks presence. |
| `offer` | Client -> Server | `{ toCode, sdp }` | Forwards WebRTC offer to `toCode` or queues handshake if offline. |
| `answer` | Client -> Server | `{ toCode, sdp }` | Forwards WebRTC answer to `toCode`. |
| `ice_candidate` | Client -> Server | `{ toCode, candidate }` | Forwards ICE candidate to peer. |
| `check_presence` | Client -> Server | `(userCode, callback)` | Returns `{ online: boolean }`. Rate limited. |
| `disconnect` | Auto | N/A | Removes socket from room and updates presence store. |

---

## 9. Encrypted Local Storage & Disappearing Message Shredder

### SQLite Schema with SQLCipher (`database_helper.dart`)

```sql
-- Conversations Table
CREATE TABLE conversations (
  id TEXT PRIMARY KEY,
  user_code TEXT UNIQUE NOT NULL,
  created_at INTEGER NOT NULL,
  last_seen INTEGER,
  is_verified INTEGER DEFAULT 0,
  disappearing_seconds INTEGER DEFAULT 0
);

-- Messages Table
CREATE TABLE messages (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL,
  sender_code TEXT NOT NULL,
  content_ciphertext TEXT NOT NULL,
  timestamp INTEGER NOT NULL,
  status TEXT NOT NULL, -- 'sending', 'sent', 'delivered', 'read'
  expires_at INTEGER,    -- Unix epoch ms when message must be shredded (NULL = permanent)
  FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);

-- Signal Sessions Table (Double Ratchet State)
CREATE TABLE signal_sessions (
  address_name TEXT PRIMARY KEY,
  device_id INTEGER NOT NULL,
  session_data BLOB NOT NULL,
  updated_at INTEGER NOT NULL
);
```

### Auto-Shredder Background Engine

```dart
class MessageShredder {
  static void startPeriodicShredding(Database db) {
    Timer.periodic(const Duration(seconds: 5), (timer) async {
      final now = DateTime.now().millisecondsSinceEpoch;
      final deletedCount = await db.delete(
        'messages',
        where: 'expires_at IS NOT NULL AND expires_at <= ?',
        whereArgs: [now],
      );
      if (deletedCount > 0) {
        // Force cryptographic overwrite & disk page vacuum if needed
        await db.rawQuery('PRAGMA incremental_vacuum(5);');
      }
    });
  }
}
```

---

## 10. Zero-Metadata Push Wakeup Protocol

When Alice wants to message Bob and Bob's P2P DataChannel is disconnected:
1. Alice sends a silent push trigger request to the signaling server:
   `POST /api/push/wake { toCode: "9M2K-7X1L" }`
2. The server dispatches a high-priority FCM/APNs message with **ZERO PII**:
   ```json
   {
     "content_available": true,
     "priority": "high",
     "data": {
       "type": "p2p_wakeup",
       "timestamp": "1787334000000"
     },
     "notification": {
       "title": "Encrypted Connection Request",
       "body": "A peer is requesting a secure P2P session"
     }
   }
   ```
3. Bob's app wakes up in the background, reconnects to the signaling socket, receives the WebRTC offer, and establishes the direct P2P DataChannel.

---

## 11. Complete UI/UX Screen Specifications

```
  ┌────────────────────────────────────────────────────────┐
  │                    GHOST MESSENGER                     │
  │                     SCREEN FLOW                        │
  └────────────────────────────────────────────────────────┘
                              │
               ┌──────────────┴──────────────┐
               ▼                             ▼
       [1. Onboarding Screen]        [2. Restore Screen]
       - 12-Word Mnemonic Vault      - 12-Word Phrase Import
       - Copy & Verification Check   - Key Regeneration
               │                             │
               └──────────────┬──────────────┘
                              ▼
                    [3. Home Dashboard]
                    - Active Sessions List
                    - Live P2P / Relay Badges
                    - Floating Action Bar:
                      * Direct Connect via UserCode
                      * Scan QR Code
                              │
               ┌──────────────┴──────────────┐
               ▼                             ▼
       [4. Active Chat Screen]       [5. Safety Verification Modal]
       - P2P Status Indicator        - 12-Digit Fingerprint
       - Encrypted Bubble Feed       - Visual Animated QR Code
       - Self-Destruct Timer Menu    - Camera Scanner
       - Direct Media Attachment
```

### 1. Onboarding Screen (`onboarding_screen.dart`)
- **Visuals**: Obsidian black background, neon glowing ghost logo, animated terminal text introduction.
- **Components**:
  - `MnemonicGrid`: Displays 12 words in a 3x4 grid with pill borders (`#2E2E2E`) and monospace font.
  - `SecurityBadge`: "Write these words down. They never leave your device."
  - `ActionButton`: "I Have Saved My Secret Phrase" -> navigates to Home.

### 2. Home Dashboard (`home_screen.dart`)
- **AppBar**: Ghost logo + UserCode badge (`[ 5J9L-2P4X ]` clickable to copy).
- **Session List Tile**:
  - Avatar: Deterministic color-gradient avatar derived from peer's UserCode.
  - Title: Peer UserCode or custom local nickname.
  - Subtitle: Last message snippet + timestamp.
  - Badge: Green `● P2P DIRECT` badge when live WebRTC data channel is open, or Grey `○ OFFLINE`.
- **Floating Actions**:
  - Scan QR (Camera scanner).
  - Add Peer (Modal input for 8-char `XXXX-XXXX` UserCode).

### 3. Chat Screen (`chat_screen.dart`)
- **Top Status Banner**:
  - Connected: `● DIRECT P2P ENCRYPTED` (Neon Cyan).
  - Relayed/Handshake: `▲ SIGNALING RELAY` (Amber).
- **Message Bubbles**:
  - Sent: Surface slate (`#141414`) with ghost green right border.
  - Received: Card grey (`#1E1E1E`) with subtle white border.
  - Metadata: Timestamp, delivery double-tick (`✓✓`), and self-destruct flame icon with live countdown (`🔥 00:24`).
- **Input Bar**:
  - Attachment button (Images/Files encrypted chunk-by-chunk over DataChannel).
  - Self-destruct duration selector (Off, 30s, 5m, 1h, 24h).
  - Glowing Green Send button.

### 4. Safety Verification Modal (`contact_dialog.dart`)
- Generates 12-digit safety number:
  $$\text{SafetyNumber} = \text{Format12Digits}(SHA512(\text{Sort}(IK_A, IK_B)))$$
- Side-by-side verification: Display QR code or toggle camera to scan peer's QR code for instant cryptographic green checkmark verification.

---

## 12. Step-by-Step Implementation Roadmap

```text
[Phase 1: Crypto Core] ───────▶ [Phase 2: Database & Storage]
          │                                   │
          ▼                                   ▼
[Phase 3: Ephemeral Server] ───▶ [Phase 4: WebRTC DataChannel]
          │                                   │
          ▼                                   ▼
[Phase 5: Signal Protocol Engine] ▶ [Phase 6: Flutter UI & Themes]
          │                                   │
          ▼                                   ▼
[Phase 7: Push & Background] ──▶ [Phase 8: Free Cloud Deployment]
```

1. **Step 1: Setup Flutter Client Project**
   - Initialize Flutter app: `flutter create --org org.ghostmessenger codechat_client`.
   - Add core dependencies to `pubspec.yaml`:
     `libsignal_protocol_dart`, `flutter_webrtc`, `sqflite_sqlcipher`, `flutter_secure_storage`, `socket_io_client`, `bip39`, `crypto`, `base32`, `mobile_scanner`, `qr_flutter`, `google_fonts`.
2. **Step 2: Implement Cryptographic Identity**
   - Build `KeyManager` and `UserCodeUtils` with HKDF-SHA256 derivation and Base32 UserCode encoding.
3. **Step 3: Build Ephemeral Signaling Server**
   - Create Node.js Express + Socket.io server with rate-limiting, prekey bundle storage, and presence tracking.
4. **Step 4: Build WebRTC P2P DataChannel Engine**
   - Implement `ConnectionManager` and `P2PService` to manage SDP negotiation and SCTP binary channels.
5. **Step 5: Wire Up Signal Protocol Sessions**
   - Implement `SqliteSignalStore` to back `libsignal_protocol_dart` with encrypted SQLite.
6. **Step 6: Build Cyberpunk UI & Screens**
   - Implement `GhostTheme`, `OnboardingScreen`, `HomeScreen`, `ChatScreen`, and `VerificationDialog`.
7. **Step 7: Configure Auto-Shredder & Push Notifications**
   - Add timer-based SQLite purger and Firebase Cloud Messaging wakeup handlers.
8. **Step 8: Deploy 100% Free Infrastructure**
   - Setup DuckDNS, Caddy Server, CoTURN, and Node.js on an Oracle Cloud Free VM.

---

## 13. Zero-Knowledge Security & Privacy Checklist

- [ ] **No Server Plaintext**: Ensure all message payloads over WebRTC and fallback signaling are binary Signal Protocol ciphertexts.
- [ ] **One-Time PreKey Consumption**: Ensure the server deletes one-time prekeys immediately upon first retrieval to prevent replay attacks.
- [ ] **Forward Secrecy Verified**: Ratchet keys update with every single message.
- [ ] **Zero Database Plaintext at Rest**: SQLite database file must be unreadable without the SQLCipher key stored in hardware keystores.
- [ ] **Metadata-Free Notifications**: Push notification payloads must never contain the sender's UserCode or message content.
- [ ] **Rate-Limiting Active**: Protect signaling server from socket floods and prekey harvesting.
- [ ] **Memory Protection**: Ephemeral keys and decrypted byte buffers are zeroed out after consumption.

---

## 14. 100% Free Cloud Deployment Guide

### Option A: Oracle Cloud Always Free VM (Recommended)
1. Create an **Always Free VM** (Ubuntu 22.04) on [Oracle Cloud Free Tier](https://cloud.oracle.com/free).
2. Open ports `80` (HTTP), `443` (HTTPS), `3000` (Signaling), `3478` (STUN/TURN UDP/TCP).
3. Create a free subdomain on [DuckDNS](https://www.duckdns.org) (e.g. `ghostmessenger.duckdns.org`) pointing to your VM IP.
4. Run with Docker Compose:

```yaml
# docker-compose.yml
version: '3.8'

services:
  caddy:
    image: caddy:2-alpine
    restart: always
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data

  signaling:
    build: ./codechat_signaling
    restart: always
    environment:
      - PORT=3000
      - CORS_ORIGINS=https://ghostmessenger.duckdns.org
      - TURN_SECRET=YOUR_RANDOM_32_BYTE_HEX_SECRET
      - TURN_REALM=ghostmessenger.duckdns.org
      - TURN_URLS=turn:ghostmessenger.duckdns.org:3478

  coturn:
    image: coturn/coturn:latest
    restart: always
    network_mode: host
    command:
      - -n
      - --listening-port=3478
      - --realm=ghostmessenger.duckdns.org
      - --use-auth-secret
      - --static-auth-secret=YOUR_RANDOM_32_BYTE_HEX_SECRET
      - --verbose

volumes:
  caddy_data:
```

```caddyfile
# Caddyfile
ghostmessenger.duckdns.org {
    reverse_proxy signaling:3000
}
```

5. Start the stack: `docker compose up -d`. You now have a fully operational, zero-cost, encrypted P2P messaging infrastructure!

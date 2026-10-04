# Calypso — Signaling Protocol, Networking & Wire Formats

This document specifies the network transport architecture, wire formats, REST APIs, and Socket.IO protocols for **Calypso**. It is prepared for system architects, backend engineers, and code reviewers auditing network security and wire protocol compliance.

---

## 1. Network Architecture Overview

Calypso uses a hybrid network model:

1. **Signaling Plane (Client-Server)**:
   - **REST (HTTPS)**: Used for stateless operations including cryptographic challenge issuance, PreKey bundle uploads, and PreKey bundle retrieval.
   - **Socket.IO (WSS)**: Used for bidirectional real-time signaling (WebRTC SDP offer/answer exchange, ICE candidate trickle), presence notifications, and fallback encrypted envelope delivery.
2. **Media/Data Plane (Peer-to-Peer)**:
   - **WebRTC DataChannels (RFC 8831)**: Carried over DTLS-SRTP and SCTP. All active chat messages, delivery acknowledgments, and read receipts flow directly between peer devices once established.

```
       +-------------------------------------------------------------+
       |                        NETWORK STACK                        |
       +-------------------------------------------------------------+
       |                                                             |
       |  [ Application Layer ]                                      |
       |  - Signal Protocol Double Ratchet (EncryptedEnvelope)        |
       |                                                             |
       |  +------------------------------+------------------------+  |
       |  |  Signaling Plane (Relay)     |  Data Plane (P2P Mesh) |  |
       |  |  - HTTPS (REST Endpoints)    |  - WebRTC DataChannel  |  |
       |  |  - WSS (Socket.IO Events)    |  - SCTP over DTLS      |  |
       |  +------------------------------+------------------------+  |
       |                                                             |
       |  [ Transport Layer ]                                        |
       |  - TCP / TLS 1.3 (Signaling)    - UDP / DTLS (WebRTC)       |
       |                                                             |
       +-------------------------------------------------------------+
```

---

## 2. HTTP REST API Reference

The signaling backend exposes REST endpoints under `/api/prekeys` and `/` (`server/src/routes/prekeys.js`).

### 2.1 Health & Readiness

#### `GET /health` (or `/api/prekeys/health/status`)
- **Purpose**: Liveness probe for deployment monitors (Render, Railway, Docker).
- **Privacy Design**: Deliberately omits active user counts, connection numbers, or bundle totals to eliminate timing and activity oracles.
- **Response `200 OK`**:
  ```json
  {
    "status": "ok",
    "uptimeSeconds": 1420
  }
  ```

#### `GET /ready`
- **Purpose**: Readiness probe verifying memory constraints and process responsiveness.
- **Response `200 OK`**:
  ```json
  {
    "ready": true
  }
  ```

---

### 2.2 PreKey Management & Challenge Authentication

#### `GET /api/prekeys/challenge/:userCode`
- **Purpose**: Issues a single-use cryptographic challenge nonce required before publishing or updating PreKey bundles.
- **Path Parameters**:
  - `userCode` (string): Target UserCode in standard `XXXX-XXXX` format.
- **Validation**: UserCode format must match `^[A-Z2-7]{4}-[A-Z2-7]{4}$`.
- **Response `200 OK`**:
  ```json
  {
    "success": true,
    "userCode": "5J9L-2P4X",
    "nonce": "a3f5b9c1d2e4...64-char-hex-nonce..."
  }
  ```
- **Error Codes**:
  - `400 Bad Request`: Invalid UserCode format.

---

#### `POST /api/prekeys/upload`
- **Purpose**: Uploads a new PreKey bundle for the specified UserCode.
- **Authentication**:
  - **First Upload**: Must supply `signature = HMAC-SHA256(key = SHA256(identityKey), data = nonce)`.
  - **Subsequent Uploads**: Accepted without new signature if `bundle.identityKey` matches the previously locked identity key.
- **Request Body**:
  ```json
  {
    "userCode": "5J9L-2P4X",
    "signature": "8f3b...64-char-hex-hmac...",
    "bundle": {
      "identityKey": "base64-encoded-curve25519-public-key",
      "registrationId": 14285,
      "signedPreKey": {
        "keyId": 1,
        "publicKey": "base64-encoded-public-key",
        "signature": "base64-encoded-signature"
      },
      "preKeys": [
        {
          "keyId": 1,
          "publicKey": "base64-encoded-one-time-prekey"
        }
      ]
    }
  }
  ```
- **Validation Rules**:
  - `userCode` must be syntactically valid Base32.
  - `preKeys` array must not exceed 100 entries (`maxPreKeysPerUser`).
  - Total bundles on server must not exceed 1,000 (`maxBundles`).
- **Response `200 OK`**:
  ```json
  {
    "success": true,
    "userCode": "5J9L-2P4X",
    "preKeyCount": 50
  }
  ```
- **Error Codes**:
  - `400 Bad Request`: Missing fields or oversized payload.
  - `401 Unauthorized`: Missing or invalid challenge signature on first registration.
  - `403 Forbidden`: `bundle.identityKey` does not match the locked identity key for this UserCode.
  - `507 Insufficient Storage`: Server bundle storage capacity reached.

---

#### `GET /api/prekeys/:userCode`
- **Purpose**: Fetches a PreKey bundle to build a Signal Protocol Double Ratchet session with a remote peer.
- **Consumption Semantics**: Atomically consumes (pops) one one-time PreKey from the target user's pool. If no one-time PreKeys remain, returns the Signed PreKey alone.
- **Response `200 OK`**:
  ```json
  {
    "success": true,
    "bundle": {
      "identityKey": "base64-encoded-curve25519-public-key",
      "registrationId": 14285,
      "signedPreKey": {
        "keyId": 1,
        "publicKey": "base64-encoded-public-key",
        "signature": "base64-encoded-signature"
      },
      "preKey": {
        "keyId": 1,
        "publicKey": "base64-encoded-one-time-prekey"
      }
    }
  }
  ```
- **Error Codes**:
  - `400 Bad Request`: Invalid UserCode format.
  - `404 Not Found`: No bundle registered for this UserCode (returns generic error without echoing the identifier).

---

#### `POST /api/turn-credentials`
- **Purpose**: Issues time-limited ephemeral TURN relay credentials according to RFC 5766.
- **Requirements**: Server must have `TURN_SECRET` and `TURN_URLS` configured.
- **Response `200 OK`**:
  ```json
  {
    "username": "1727900000:5J9L-2P4X",
    "password": "base64-encoded-hmac-sha1",
    "ttl": 86400,
    "uris": ["turn:turn.example.com:3478?transport=udp"]
  }
  ```
- **Error Codes**:
  - `404 Not Found`: TURN relay not configured on this server.

---

#### `POST /api/prekeys/fcm-token`
- **Purpose**: Registers an FCM registration token for zero-knowledge wake-up pings.
- **Request Body**:
  ```json
  {
    "userCode": "5J9L-2P4X",
    "fcmToken": "c_2v7X...device-token..."
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "success": true
  }
  ```
- **Error Codes**:
  - `400 Bad Request`: Invalid UserCode or missing `fcmToken`.

---

## 3. Socket.IO Real-Time Event Protocol

Real-time signaling is managed by `setupSignalingHandlers()` (`server/src/sockets/signalingHandler.js:83-285`).

### 3.1 Connection Admission & Rate Limiting

1. **IP Admission Guard (`PresenceManager.js:108-135`)**:
   - Limits connections to a maximum of 5 concurrent sockets per IP address (`MAX_CONNECTIONS_PER_IP`).
   - Violating connections are immediately disconnected without processing events.
2. **Per-Socket Sliding Window Rate Limiter (`signalingHandler.js:41-59`)**:
   - Caps event throughput to **20 events per second** per socket.
   - Sockets exceeding this threshold receive an error. After 3 rate limit violations, the socket is forcibly dropped.

### 3.2 Payload Size Constraints

The server enforces strict payload size limits (`signalingHandler.js:32-35`):
- `offer.sdp` / `answer.sdp`: Maximum 8 KB (`MAX_SDP_BYTES`).
- `candidate.candidate`: Maximum 512 Bytes (`MAX_ICE_BYTES`).
- `envelope`: Maximum 64 KB (`MAX_ENVELOPE_BYTES`).

Oversized payloads are dropped with an error callback and not forwarded to peers.

---

### 3.3 Event Specifications

#### Client-to-Server Events

| Event Name | Arguments | Description & Validation |
|---|---|---|
| `register` | `{ userCode: string, fcmToken?: string }, callback: function` | Registers socket presence. Optional `fcmToken` registers device for zero-knowledge wake-up pings. Requires that the UserCode has an existing PreKey bundle uploaded. Fails if user is already connected on 3 sockets. |
| `register-fcm` | `{ fcmToken: string }` | Registers or updates device FCM token for an already connected socket. |
| `signal` | `data: object, callback: function` | Relays WebRTC signaling or offline envelopes to `data.toUserCode`. Sender must be registered. |
| `check_presence` | `userCode: string, callback: function` | Queries online status for a specific peer. Returns `{ online: boolean }`. |

#### Server-to-Client Events

| Event Name | Payload Structure | Description |
|---|---|---|
| `registered` | `{ success: true, userCode: string }` | Acknowledgment of successful socket registration. |
| `signal` | `{ fromUserCode: string, signalData: object }` | Relayed SDP offer/answer, ICE candidate, or fallback envelope. |
| `peer_status` | `{ toCode: string, status: "queued" }` | Indicates that the recipient was offline and the envelope was buffered. |
| `presence_result` | `{ userCode: string, online: boolean }` | Result of a presence query. |
| `offline_messages` | `Array<{ fromUserCode: string, envelope: string }>` | Flushed upon registration if the user had queued offline envelopes. |
| `error_message` | `{ code: string, message: string }` | Emitted when an unrecoverable validation error occurs. |

---

## 4. WebRTC Transport & DataChannel Layer

`WebRtcManager` (`android/app/src/main/kotlin/org/ghostmessenger/data/webrtc/WebRtcManager.kt`) configures and maintains the peer connection.

### 4.1 ICE Server Configuration

Default STUN servers are configured in `WebRtcManager.kt:52-89`:
- `stun:stun.l.google.com:19302`
- `stun:stun1.l.google.com:19302`
- `stun:stun.relay.metered.ca:80`

If TURN credentials are configured in `SecurePreferences` (`DEFAULT_TURN_USERNAME`, `DEFAULT_TURN_PASSWORD`, or user-specified custom relays), multi-port fallback ICE servers are loaded:
- `turn:global.relay.metered.ca:80` (Standard UDP relay)
- `turn:global.relay.metered.ca:443` (HTTPS-port UDP relay, bypassing restrictive firewall blocks)
- `turns:global.relay.metered.ca:443?transport=tcp` (Encrypted TLS over TCP fallback for symmetric/corporate NATs)

### 4.2 DataChannel Parameters

The chat DataChannel is created with SCTP reliable transport settings (`WebRtcManager.kt:295-305`):
- **Label**: `"chat"`
- **Ordered**: `true` (enforces in-order packet delivery)
- **Max Retransmits**: `-1` (unlimited retransmissions / reliable mode)
- **Max Packet Life Time**: `-1` (unlimited lifetime)

### 4.3 Reconnection with Exponential Backoff

If an active peer connection transitions to `PeerConnection.PeerConnectionState.FAILED` or `DISCONNECTED` (`WebRtcManager.kt:265-285`):
1. Connection attempt count is incremented up to a maximum of 3 attempts.
2. Backoff delay is calculated:
   $$\text{Delay} = 2^{\text{attempt}} \times 1000 \text{ ms} \quad (2\text{s}, 4\text{s}, 8\text{s})$$
3. A coroutine executes `initiateP2PConnection(peerUserCode)` after the backoff delay.
4. If all 3 attempts fail, the peer connection is closed and the client falls back to ephemeral signaling relay for subsequent messages.

---

## 5. Ephemeral Offline Message Queuing

When Client A sends an encrypted message to Client B, but Client B has no active socket connection, the server uses `OfflineQueueStore` (`server/src/store/OfflineQueueStore.js:16-128`).

### 5.1 Storage Constraints
- **Capacity**: Maximum 50 envelopes per user (`MAX_ENVELOPES_PER_USER`).
- **TTL**: 24 hours (`DEFAULT_TTL_MS = 86_400_000 ms`).
- **Storage Medium**: Volatile in-memory `Map<string, Array<QueueItem>>`. Zero disk writes.

### 5.2 Delivery Lifecycle
1. Client A emits `signal` with an `envelope` addressed to offline user B.
2. `signalingHandler.js:235-245` detects user B is offline.
3. Envelope is enqueued via `offlineQueueStore.enqueue(toUserCode, { fromUserCode, envelope })`.
4. Client A receives an acknowledgment callback: `{ success: true, queued: true }`.
5. Simultaneously, `fcmService.sendWakeUpPing(targetUserCode)` sends an ephemeral wake-up notification to device B.
6. When Client B wakes up and registers:
   - `signalingHandler.js:140-148` calls `offlineQueueStore.dequeueAll(userCode)`.
   - All buffered envelopes are emitted to Client B in an `offline_messages` event.
   - The queue for Client B is immediately deleted from server memory.

### 5.3 Zero-Knowledge Push Protocol
- **Wire Payload**: `{"data": {"type": "wake_up"}}` (Priority: High).
- **Zero Information Leak**: FCM contains no sender UserCode, no recipient UserCode, no timestamp, and no message ciphertext.
- **Auto-Pruning**: If Firebase reports `messaging/registration-token-not-registered` or `invalid-registration-token`, the token is immediately pruned from the server's in-memory cache without logging or persisting the erroring identifier.


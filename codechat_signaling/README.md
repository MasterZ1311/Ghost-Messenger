# CodeChat Signaling Server

The signaling backend for **Ghost Messenger** — a privacy-first, end-to-end
encrypted P2P messenger. This server brokers WebRTC connection setup between
peers. **It never sees message content.** Chat messages flow E2EE (Signal
Protocol) directly over the peer-to-peer WebRTC DataChannel; the server only
relays SDP offers/answers and ICE candidates needed to establish that channel.

## What it does

- **Presence registry** — maps a user's derived `UserCode` to live socket(s),
  supporting multiple concurrent devices per user.
- **WebRTC signal relay** — forwards `offer` / `answer` / `candidate` envelopes
  between peers, with anti-spoofing (a socket can only signal as its own joined
  `UserCode`).
- **Offline handshake queue** — briefly buffers handshake signals (default 30s)
  for a recipient who is momentarily offline, then flushes them on join.
- **ICE configuration endpoint** — serves STUN servers and optional ephemeral
  TURN credentials (coturn REST API) via `GET /api/ice-servers`.
- **Hardening** — per-socket rate limiting, payload validation and size caps,
  Helmet headers, CORS allowlist, HTTP rate limiting.
- **Operability** — structured logging (pino), health/readiness/metrics
  endpoints, graceful shutdown, Docker + healthcheck, non-root container.

## Architecture

```
src/
├── index.js              Entry point: starts server, wires signal handlers
├── server.js             HTTP + Socket.IO bootstrap, graceful shutdown
├── app.js                Express app (security middleware + routes)
├── config/index.js       Env-driven configuration (validated, frozen)
├── socket/
│   ├── index.js          Socket.IO server setup
│   └── handlers.js       join / signal / check_presence / disconnect
├── services/
│   ├── presence.js       UserCode -> socket registry
│   ├── messageQueue.js   Transient offline handshake buffer
│   └── iceServers.js     STUN/TURN ICE server list builder
├── routes/
│   ├── health.js         /health, /ready, /metrics
│   └── ice.js            /api/ice-servers
├── middleware/
│   └── socketRateLimiter.js
└── utils/
    ├── logger.js         pino logger
    └── validation.js     UserCode + signal envelope validation
```

## Getting started

```bash
cd codechat_signaling
cp .env.example .env      # adjust as needed
npm install
npm run dev               # auto-reload (nodemon)
# or
npm start                 # production
```

Run the test suite (Node's built-in test runner):

```bash
npm test
```

## HTTP API

| Method | Path                 | Description                                   |
|--------|----------------------|-----------------------------------------------|
| GET    | `/`                  | Service info                                  |
| GET    | `/health`            | Liveness probe                                |
| GET    | `/ready`             | Readiness probe                               |
| GET    | `/metrics`           | Counts only (no PII): presence, queue, memory |
| GET    | `/api/ice-servers`   | ICE server list (STUN + optional TURN)        |

`GET /api/ice-servers?userCode=<code>` optionally binds ephemeral TURN
credentials to a user.

## Socket.IO protocol

Client → Server:

| Event            | Payload                                             | Notes                                  |
|------------------|-----------------------------------------------------|----------------------------------------|
| `join`           | `userCode: string`                                  | Register presence for this socket      |
| `signal`         | `{ toCode, fromCode, signalData }`                  | Relay SDP/ICE; `fromCode` must match   |
| `check_presence` | `userCode: string` (+ optional ack callback)        | Query whether a user is online         |

Server → Client:

| Event             | Payload                              | Notes                                  |
|-------------------|--------------------------------------|----------------------------------------|
| `joined`          | `{ userCode }`                       | Join acknowledged                      |
| `signal`          | `{ fromCode, signalData }`           | Incoming relayed signal                |
| `peer_status`     | `{ toCode, status: 'queued' }`       | Recipient offline; signal buffered     |
| `presence_result` | `{ userCode, online }`               | Presence query result (non-ack form)   |
| `error_message`   | `{ code, message }`                  | 400/401/403/404/429 style errors       |
| `server_shutdown` | `{ message }`                        | Sent during graceful shutdown          |

`signalData` shapes:
- Offer/Answer: `{ type: 'offer' | 'answer', sdp: string }`
- Candidate: `{ type: 'candidate', candidate, sdpMid, sdpMLineIndex }`

## Configuration

All configuration is via environment variables — see `.env.example` for the
full annotated list. Highlights:

- `CORS_ORIGINS` — comma-separated allowed origins (empty = allow all, dev only).
- `STUN_URLS` — comma-separated STUN URLs.
- `TURN_URLS` + `TURN_SECRET` — enable ephemeral TURN credentials (recommended).
- `TURN_URLS` + `TURN_USERNAME`/`TURN_CREDENTIAL` — static TURN credentials.
- `QUEUE_*` — offline handshake queue tuning.
- `SIGNAL_RATE_*` / `HTTP_RATE_*` — rate limiting.

## TURN / NAT traversal

STUN alone fails for symmetric NATs (~10-20% of connections). For production
reliability you need a TURN relay. This server can mint **time-limited ephemeral
TURN credentials** using coturn's REST API scheme:

1. Run coturn with `--use-auth-secret --static-auth-secret=<SECRET>`.
2. Set `TURN_URLS` and `TURN_SECRET` (matching `<SECRET>`) on this server.
3. Clients fetch `GET /api/ice-servers` and feed the result into their
   `RTCPeerConnection` config.

A commented `coturn` service is included in `docker-compose.yml`.

## Deployment

```bash
docker compose up --build -d
```

The container runs as a non-root user and includes a Docker `HEALTHCHECK`.

### Scaling beyond one instance

Presence and the handshake queue are in-memory, so a single instance is
authoritative. To scale horizontally, add the
[`@socket.io/redis-adapter`](https://socket.io/docs/v4/redis-adapter/) and back
presence with a shared store (Redis). This is the documented next step; the
service layer (`services/presence.js`, `services/messageQueue.js`) is isolated
to make that swap straightforward.

## Security notes

- The server is **zero-knowledge** with respect to message content — it only
  relays connection-setup metadata.
- `fromCode` spoofing is blocked: a socket may only emit signals as the
  `UserCode` it joined with.
- For stronger guarantees, add a challenge–response on `join` where the client
  signs a server nonce with its Signal identity key and the server verifies the
  derived `UserCode`. This requires a matching client change and is left as a
  documented enhancement.
```

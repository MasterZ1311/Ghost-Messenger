'use strict';

require('dotenv').config();

/**
 * Parses a comma-separated env var into a trimmed array.
 * Returns an empty array if undefined/empty.
 */
function parseList(value) {
  if (!value) return [];
  return value
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean);
}

function parseBool(value, fallback = false) {
  if (value === undefined || value === null || value === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(String(value).toLowerCase());
}

function parseInteger(value, fallback) {
  const n = parseInt(value, 10);
  return Number.isFinite(n) ? n : fallback;
}

const nodeEnv = process.env.NODE_ENV || 'development';

// CORS / Socket.IO allowed origins. '*' allows any origin (dev only).
const corsOrigins = parseList(process.env.CORS_ORIGINS);

const config = Object.freeze({
  env: nodeEnv,
  isProduction: nodeEnv === 'production',

  // HTTP server
  host: process.env.HOST || '0.0.0.0',
  port: parseInteger(process.env.PORT, 3000),

  // CORS: if no origins configured, default to '*' in dev and deny-by-config in prod.
  cors: {
    origins: corsOrigins.length > 0 ? corsOrigins : ['*'],
  },

  // Logging
  log: {
    level: process.env.LOG_LEVEL || (nodeEnv === 'production' ? 'info' : 'debug'),
    pretty: parseBool(process.env.LOG_PRETTY, nodeEnv !== 'production'),
  },

  // Presence registry behaviour
  presence: {
    // Max active sessions a single UserCode may hold simultaneously.
    maxSocketsPerUser: parseInteger(process.env.MAX_SOCKETS_PER_USER, 3),
  },

  // Offline handshake queue: store signals briefly for a peer that is
  // momentarily offline so the WebRTC handshake can still complete.
  queue: {
    enabled: parseBool(process.env.QUEUE_ENABLED, true),
    ttlMs: parseInteger(process.env.QUEUE_TTL_MS, 30000),
    maxPerUser: parseInteger(process.env.QUEUE_MAX_PER_USER, 50),
    sweepIntervalMs: parseInteger(process.env.QUEUE_SWEEP_INTERVAL_MS, 10000),
  },

  // Rate limiting (per-socket signal flood protection)
  rateLimit: {
    // Max signaling events per window per socket.
    windowMs: parseInteger(process.env.SIGNAL_RATE_WINDOW_MS, 1000),
    maxEvents: parseInteger(process.env.SIGNAL_RATE_MAX, 30),
    // HTTP API rate limit
    httpWindowMs: parseInteger(process.env.HTTP_RATE_WINDOW_MS, 60000),
    httpMax: parseInteger(process.env.HTTP_RATE_MAX, 100),
  },

  // PreKey bundle store: X3DH key distribution
  prekey: {
    // Hard cap on total PreKey bundles held in memory across all users.
    maxBundlesTotal: parseInteger(process.env.PREKEY_MAX_BUNDLES, 10000),
  },

  // Graceful shutdown grace period
  shutdownTimeoutMs: parseInteger(process.env.SHUTDOWN_TIMEOUT_MS, 10000),

  // Validation limits
  validation: {
    // UserCode format: alphanumeric (base32-ish), reasonable length bounds.
    userCodeMinLen: parseInteger(process.env.USER_CODE_MIN_LEN, 4),
    userCodeMaxLen: parseInteger(process.env.USER_CODE_MAX_LEN, 64),
    // Max serialized size of a single signal payload (bytes).
    maxSignalBytes: parseInteger(process.env.MAX_SIGNAL_BYTES, 65536),
  },

  // STUN/TURN ICE configuration served to clients via /api/ice-servers
  ice: {
    stunUrls: parseList(process.env.STUN_URLS).length
      ? parseList(process.env.STUN_URLS)
      : ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'],
    turn: {
      // Static TURN credentials (simple deployments)
      urls: parseList(process.env.TURN_URLS),
      username: process.env.TURN_USERNAME || '',
      credential: process.env.TURN_CREDENTIAL || '',
      // coturn REST API ephemeral credentials (recommended for production)
      // When TURN_SECRET is set, time-limited credentials are generated per request.
      secret: process.env.TURN_SECRET || '',
      ttlSeconds: parseInteger(process.env.TURN_TTL_SECONDS, 86400),
    },
  },

  // Redis configuration for adapter
  redis: {
    enabled: parseBool(process.env.REDIS_ENABLED, false),
    host: process.env.REDIS_HOST || '127.0.0.1',
    port: parseInteger(process.env.REDIS_PORT, 6379),
  },
});

module.exports = config;

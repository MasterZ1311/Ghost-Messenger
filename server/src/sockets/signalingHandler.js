/**
 * Socket.IO Signaling Handler for Calypso.
 *
 * Security controls applied to every socket:
 *  1. Admission guard: IP cap enforced at connection time.
 *  2. register: gated on prekey bundle existence (proof of prior identity upload).
 *  3. Per-socket rate limiter: max 20 events/second sliding window.
 *     After 3 violations the socket is forcibly disconnected.
 *  4. Payload validation + size limits on every event:
 *       offer.sdp / answer.sdp  : string, max 8 KB
 *       candidate.candidate     : string, max 512 B
 *       envelope                : string, max 64 KB
 *       targetUserCode          : must normalise to valid format
 *  5. All relay events reject unregistered senders (fromUserCode must be set).
 *  6. Error callbacks never leak internal state.
 *
 * @param {import('socket.io').Server} io
 * @param {import('../store/PresenceManager.js').PresenceManager} presenceManager
 * @param {import('../store/InMemoryPreKeyStore.js').InMemoryPreKeyStore} preKeyStore
 */

import { logger, hashIp } from '../middleware/logger.js';
import { OfflineQueueStore } from '../store/OfflineQueueStore.js';
import { fcmService } from '../services/fcmService.js';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
const MAX_EVENTS_PER_SECOND = 20;
const RATE_WINDOW_MS        = 1_000;
const MAX_VIOLATIONS        = 3;

const MAX_SDP_BYTES         = 8 * 1024;        // 8 KB
const MAX_ICE_BYTES         = 512;             // 512 B
const MAX_ENVELOPE_BYTES    = 64 * 1024;       // 64 KB

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Creates a per-socket sliding window rate limiter. */
function makeRateLimiter() {
  let windowStart  = Date.now();
  let eventCount   = 0;
  let violations   = 0;

  return function check() {
    const now = Date.now();
    if (now - windowStart > RATE_WINDOW_MS) {
      windowStart = now;
      eventCount  = 0;
    }
    eventCount++;
    if (eventCount > MAX_EVENTS_PER_SECOND) {
      violations++;
      return { allowed: false, violations };
    }
    return { allowed: true, violations };
  };
}

/**
 * Validates a string's byte length.
 * @param {*}      value
 * @param {number} maxBytes
 * @returns {boolean}
 */
function withinSize(value, maxBytes) {
  if (typeof value !== 'string') return false;
  return Buffer.byteLength(value, 'utf8') <= maxBytes;
}

/**
 * Returns a safe error callback response — never includes internal details.
 */
function errCb(callback, message) {
  if (typeof callback === 'function') callback({ success: false, error: message });
}

// ---------------------------------------------------------------------------
// Main export
// ---------------------------------------------------------------------------

export function setupSignalingHandlers(io, presenceManager, preKeyStore, offlineQueueStore = new OfflineQueueStore()) {
  io.on('connection', (socket) => {
    const ip     = socket.handshake.address;
    const ipHash = hashIp(ip);

    // -- IP Admission Guard --------------------------------------------------
    if (!presenceManager.admitIp(ip)) {
      logger.warn('connection_rejected_ip_cap', { ipHash });
      socket.disconnect(true);
      return;
    }

    logger.info('socket_connected', { ipHash });

    // -- Per-socket rate limiter ---------------------------------------------
    const checkRate = makeRateLimiter();

    // Middleware: runs before every event on this socket
    socket.use(([_eventName, ..._args], next) => {
      const { allowed, violations } = checkRate();
      if (!allowed) {
        logger.warn('rate_limit_exceeded', { ipHash, violations });
        if (violations >= MAX_VIOLATIONS) {
          socket.disconnect(true);
        }
        return; // drop the event silently (no callback here — middleware signature)
      }
      next();
    });

    // -----------------------------------------------------------------------
    // register — Gate: bundle must exist for userCode
    // -----------------------------------------------------------------------
    socket.on('register', (data, callback) => {
      const userCode   = typeof data === 'string' ? data : data?.userCode;
      const normalized = presenceManager.normalizeUserCode(userCode);

      if (!normalized) {
        return errCb(callback, 'Invalid userCode format');
      }

      // Require the client to have uploaded a prekey bundle first
      if (!preKeyStore.hasBundleFor(normalized)) {
        logger.warn('register_no_bundle', { ipHash });
        return errCb(callback, 'No prekey bundle found for this userCode');
      }

      const result = presenceManager.register(normalized, socket.id);
      if (!result.ok) {
        logger.warn('register_rejected', { reason: result.reason, ipHash });
        return errCb(callback, 'Registration rejected');
      }

      socket.join(normalized);
      logger.info('socket_registered', { ipHash });

      if (data?.fcmToken && typeof data.fcmToken === 'string') {
        fcmService.registerToken(normalized, data.fcmToken);
      }

      if (typeof callback === 'function') {
        callback({ success: true, userCode: normalized });
      }
      socket.emit('registered', { success: true, userCode: normalized });

      // Deliver any buffered offline envelopes
      if (offlineQueueStore.hasQueued(normalized)) {
        const pending = offlineQueueStore.dequeueAll(normalized);
        for (const item of pending) {
          socket.emit('encrypted-envelope', item);
        }
      }
    });

    // -----------------------------------------------------------------------
    // register-fcm
    // -----------------------------------------------------------------------
    socket.on('register-fcm', (data) => {
      const fromUserCode = presenceManager.getUserCode(socket.id);
      if (fromUserCode && data?.fcmToken && typeof data.fcmToken === 'string') {
        fcmService.registerToken(fromUserCode, data.fcmToken);
      }
    });

    // -----------------------------------------------------------------------
    // check-presence
    // -----------------------------------------------------------------------
    socket.on('check-presence', (data, callback) => {
      const raw        = typeof data === 'string' ? data : data?.targetUserCode;
      const normalized = presenceManager.normalizeUserCode(raw);

      if (!normalized) {
        return errCb(callback, 'Invalid targetUserCode');
      }

      const online   = presenceManager.isOnline(normalized);
      const response = { targetUserCode: normalized, online };

      if (typeof callback === 'function') callback(response);
      socket.emit('presence-result', response);
    });

    // -----------------------------------------------------------------------
    // webrtc-offer  (SDP offer relay)
    // -----------------------------------------------------------------------
    socket.on('webrtc-offer', (data) => {
      const fromUserCode   = presenceManager.getUserCode(socket.id);
      const targetUserCode = presenceManager.normalizeUserCode(data?.targetUserCode);
      const offer          = data?.offer;

      if (!fromUserCode) return; // unregistered sender — silent drop

      if (!targetUserCode) {
        logger.warn('offer_invalid_target', { ipHash });
        return;
      }

      if (!offer || typeof offer.sdp !== 'string' || !withinSize(offer.sdp, MAX_SDP_BYTES)) {
        logger.warn('offer_invalid_sdp', { ipHash });
        return;
      }

      const isOnline = presenceManager.isOnline(targetUserCode);
      if (isOnline) {
        io.to(targetUserCode).emit('webrtc-offer', { fromUserCode, offer });
      } else {
        fcmService.sendWakeUpPing(targetUserCode);
      }
    });

    // -----------------------------------------------------------------------
    // webrtc-answer  (SDP answer relay)
    // -----------------------------------------------------------------------
    socket.on('webrtc-answer', (data) => {
      const fromUserCode   = presenceManager.getUserCode(socket.id);
      const targetUserCode = presenceManager.normalizeUserCode(data?.targetUserCode);
      const answer         = data?.answer;

      if (!fromUserCode) return;

      if (!targetUserCode) {
        logger.warn('answer_invalid_target', { ipHash });
        return;
      }

      if (!answer || typeof answer.sdp !== 'string' || !withinSize(answer.sdp, MAX_SDP_BYTES)) {
        logger.warn('answer_invalid_sdp', { ipHash });
        return;
      }

      io.to(targetUserCode).emit('webrtc-answer', { fromUserCode, answer });
    });

    // -----------------------------------------------------------------------
    // ice-candidate  (ICE candidate relay)
    // -----------------------------------------------------------------------
    socket.on('ice-candidate', (data) => {
      const fromUserCode   = presenceManager.getUserCode(socket.id);
      const targetUserCode = presenceManager.normalizeUserCode(data?.targetUserCode);
      const candidate      = data?.candidate;

      if (!fromUserCode) return;

      if (!targetUserCode) {
        logger.warn('ice_invalid_target', { ipHash });
        return;
      }

      if (
        !candidate ||
        typeof candidate.candidate !== 'string' ||
        !withinSize(candidate.candidate, MAX_ICE_BYTES)
      ) {
        logger.warn('ice_invalid_candidate', { ipHash });
        return;
      }

      io.to(targetUserCode).emit('ice-candidate', { fromUserCode, candidate });
    });

    // -----------------------------------------------------------------------
    // encrypted-envelope  (Fallback relay for Signal Protocol ciphertext)
    // -----------------------------------------------------------------------
    socket.on('encrypted-envelope', (data, callback) => {
      const fromUserCode   = presenceManager.getUserCode(socket.id);
      const targetUserCode = presenceManager.normalizeUserCode(data?.targetUserCode);
      const envelope       = data?.envelope;

      if (!fromUserCode) {
        return errCb(callback, 'Not registered');
      }

      if (!targetUserCode) {
        return errCb(callback, 'Invalid targetUserCode');
      }

      if (typeof envelope !== 'string' || !withinSize(envelope, MAX_ENVELOPE_BYTES)) {
        return errCb(callback, 'Invalid or oversized envelope');
      }

      const isDelivered = presenceManager.isOnline(targetUserCode);
      if (isDelivered) {
        io.to(targetUserCode).emit('encrypted-envelope', { fromUserCode, envelope });
      } else {
        offlineQueueStore.enqueue(targetUserCode, { fromUserCode, envelope });
        fcmService.sendWakeUpPing(targetUserCode);
      }

      if (typeof callback === 'function') {
        callback({ success: true, delivered: isDelivered, queued: !isDelivered });
      }
    });

    // -----------------------------------------------------------------------
    // disconnect — clean up presence and IP counter
    // -----------------------------------------------------------------------
    socket.on('disconnect', () => {
      presenceManager.unregister(socket.id);
      presenceManager.releaseIp(ip);
      logger.info('socket_disconnected', { ipHash });
    });
  });
}

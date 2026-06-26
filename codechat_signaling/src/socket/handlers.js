'use strict';

const presence = require('../services/presence');
const messageQueue = require('../services/messageQueue');
const logger = require('../utils/logger');
const SocketRateLimiter = require('../middleware/socketRateLimiter');
const { validateUserCode, validateSignalEnvelope } = require('../utils/validation');

/**
 * Wires up all event handlers for a freshly connected socket.
 * @param {import('socket.io').Server} io
 * @param {import('socket.io').Socket} socket
 */
function registerSocketHandlers(io, socket) {
  const limiter = new SocketRateLimiter();
  logger.debug({ socketId: socket.id }, 'socket connected');

  /**
   * Helper to deliver a signal envelope to every active session of a recipient.
   * @returns {boolean} true if delivered to at least one live socket.
   */
  function deliver(toCode, envelope) {
    const targets = presence.getSockets(toCode);
    if (targets.length === 0) return false;
    for (const targetSocketId of targets) {
      io.to(targetSocketId).emit('signal', envelope);
    }
    return true;
  }

  // --- join: register the client's UserCode -------------------------------
  socket.on('join', (userCode) => {
    const check = validateUserCode(userCode);
    if (!check.ok) {
      logger.warn({ socketId: socket.id, reason: check.reason }, 'join rejected');
      socket.emit('error_message', { code: 400, message: check.reason });
      return;
    }

    const code = userCode.trim();
    const result = presence.register(code, socket.id);
    if (!result.ok) {
      socket.emit('error_message', { code: 429, message: result.reason });
      return;
    }

    socket.data.userCode = code;
    socket.emit('joined', { userCode: code });
    logger.info({ userCode: code, socketId: socket.id }, 'user joined');

    // Flush any handshake signals that arrived while the user was offline.
    const pending = messageQueue.drain(code);
    if (pending.length > 0) {
      logger.info({ userCode: code, count: pending.length }, 'flushing queued signals');
      for (const envelope of pending) {
        socket.emit('signal', envelope);
      }
    }
  });

  // --- signal: relay WebRTC SDP / ICE between peers -----------------------
  socket.on('signal', (payload) => {
    if (!limiter.allow()) {
      logger.warn({ socketId: socket.id }, 'signal rate-limited');
      socket.emit('error_message', { code: 429, message: 'Rate limit exceeded' });
      return;
    }

    const check = validateSignalEnvelope(payload);
    if (!check.ok) {
      logger.warn({ socketId: socket.id, reason: check.reason }, 'signal rejected');
      socket.emit('error_message', { code: 400, message: check.reason });
      return;
    }

    const { toCode, fromCode, signalData } = payload;

    // Anti-spoofing: the sender must have joined and the envelope's fromCode
    // must match the authenticated UserCode bound to this socket.
    if (!socket.data.userCode) {
      socket.emit('error_message', { code: 401, message: 'Join before signaling' });
      return;
    }
    if (socket.data.userCode !== fromCode.trim()) {
      logger.warn(
        { socketId: socket.id, claimed: fromCode, actual: socket.data.userCode },
        'fromCode spoof attempt blocked'
      );
      socket.emit('error_message', { code: 403, message: 'fromCode does not match session' });
      return;
    }

    const envelope = { fromCode: fromCode.trim(), signalData };
    const delivered = deliver(toCode.trim(), envelope);

    if (delivered) {
      logger.debug(
        { from: fromCode, to: toCode, type: signalData.type },
        'signal relayed'
      );
      return;
    }

    // Recipient offline: try to buffer the handshake for a short window.
    const queued = messageQueue.enqueue(toCode.trim(), envelope);
    if (queued.ok) {
      socket.emit('peer_status', { toCode: toCode.trim(), status: 'queued' });
      logger.debug({ from: fromCode, to: toCode }, 'recipient offline, signal queued');
    } else {
      socket.emit('error_message', { code: 404, message: 'Recipient offline' });
      logger.debug({ to: toCode, reason: queued.reason }, 'recipient offline, not queued');
    }
  });

  // --- presence query -----------------------------------------------------
  socket.on('check_presence', (userCode, ack) => {
    const check = validateUserCode(userCode);
    const online = check.ok ? presence.isOnline(userCode.trim()) : false;
    if (typeof ack === 'function') {
      ack({ userCode, online });
    } else {
      socket.emit('presence_result', { userCode, online });
    }
  });

  // --- disconnect ---------------------------------------------------------
  socket.on('disconnect', (reason) => {
    const userCode = presence.unregister(socket.id);
    logger.info({ socketId: socket.id, userCode, reason }, 'socket disconnected');
  });
}

module.exports = { registerSocketHandlers };

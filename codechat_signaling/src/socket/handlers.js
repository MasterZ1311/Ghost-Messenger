'use strict';

const presence = require('../services/presence');
const messageQueue = require('../services/messageQueue');
const preKeyBundleStore = require('../services/preKeyBundleStore');
const pushTokenStore = require('../services/pushTokenStore');
const pushNotification = require('../services/pushNotification');
const logger = require('../utils/logger');
const SocketRateLimiter = require('../middleware/socketRateLimiter');
const { validateUserCode, validateSignalEnvelope, validatePreKeyBundle } = require('../utils/validation');

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

  // --- register_push_token: save FCM/APNs push token for session ---------
  socket.on('register_push_token', (data) => {
    if (!socket.data.userCode) {
      socket.emit('error_message', { code: 401, message: 'Join before registering push token' });
      return;
    }
    const { token, platform } = data || {};
    if (!token || typeof token !== 'string') {
      socket.emit('error_message', { code: 400, message: 'Invalid push token' });
      return;
    }
    pushTokenStore.registerToken(socket.data.userCode, token, platform);
    socket.emit('push_token_registered', { status: 'ok', userCode: socket.data.userCode });
    logger.info({ userCode: socket.data.userCode, platform }, 'push token registered via socket');
  });

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

    // Trigger FCM/APNs background wakeup push notification when an offer is sent to an offline peer
    if (signalData.type === 'offer') {
      pushNotification.sendWakeupNotification(toCode.trim(), fromCode.trim(), signalData).catch((err) => {
        logger.error({ err, toCode }, 'failed to send wakeup push notification');
      });
    }
  });

  // --- presence query — requires join, rate-limited ----------------------
  socket.on('check_presence', (userCode, ack) => {
    if (!socket.data.userCode) {
      if (typeof ack === 'function') ack({ online: false });
      else socket.emit('error_message', { code: 401, message: 'Join before querying presence' });
      return;
    }
    if (!limiter.allow()) {
      if (typeof ack === 'function') ack({ online: false });
      return;
    }
    const check = validateUserCode(userCode);
    const online = check.ok ? presence.isOnline(userCode.trim()) : false;
    if (typeof ack === 'function') {
      ack({ userCode, online });
    } else {
      socket.emit('presence_result', { userCode, online });
    }
  });

  // --- prekey bundle management ------------------------------------------
  socket.on('publish_prekey', (payload, ack) => {
    if (!limiter.allow()) {
      if (typeof ack === 'function') ack({ ok: false, error: 'Rate limit exceeded' });
      return;
    }
    const { userCode, ...bundle } = payload || {};
    if (!socket.data.userCode || socket.data.userCode !== (userCode && userCode.trim())) {
      if (typeof ack === 'function') ack({ ok: false, error: 'Unauthorized userCode for session' });
      return;
    }
    const check = validatePreKeyBundle(bundle);
    if (!check.ok) {
      if (typeof ack === 'function') ack({ ok: false, error: check.reason });
      return;
    }
    const result = preKeyBundleStore.put(userCode.trim(), bundle);
    if (typeof ack === 'function') ack(result);
  });

  socket.on('get_prekey', (userCode, ack) => {
    if (!socket.data.userCode) {
      if (typeof ack === 'function') ack({ ok: false, error: 'Join before fetching prekey bundles' });
      return;
    }
    if (!limiter.allow()) {
      if (typeof ack === 'function') ack({ ok: false, error: 'Rate limit exceeded' });
      return;
    }
    if (typeof userCode !== 'string') {
      if (typeof ack === 'function') ack({ ok: false, error: 'Invalid userCode' });
      return;
    }
    const bundle = preKeyBundleStore.get(userCode.trim());
    if (!bundle) {
      if (typeof ack === 'function') ack({ ok: false, error: 'No PreKey bundle found' });
      return;
    }
    if (typeof ack === 'function') ack({ ok: true, bundle });
  });

  // --- disconnect ---------------------------------------------------------
  socket.on('disconnect', (reason) => {
    const userCode = presence.unregister(socket.id);
    logger.info({ socketId: socket.id, userCode, reason }, 'socket disconnected');
  });
}

module.exports = { registerSocketHandlers };

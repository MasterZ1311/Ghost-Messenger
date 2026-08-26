/**
 * Sets up Socket.IO signaling event listeners for WebRTC negotiation and ephemeral messaging.
 *
 * @param {import('socket.io').Server} io
 * @param {import('../store/PresenceManager.js').PresenceManager} presenceManager
 */
export function setupSignalingHandlers(io, presenceManager) {
  io.on('connection', (socket) => {
    // ----------------------------------------------------
    // Registration: User registers their public UserCode
    // ----------------------------------------------------
    socket.on('register', (data, callback) => {
      const userCode = typeof data === 'string' ? data : data?.userCode;
      const normalized = presenceManager.normalizeUserCode(userCode);

      if (!normalized) {
        if (typeof callback === 'function') {
          callback({ success: false, error: 'Invalid userCode format' });
        }
        return;
      }

      socket.join(normalized);
      presenceManager.register(normalized, socket.id);

      if (typeof callback === 'function') {
        callback({ success: true, userCode: normalized });
      }

      socket.emit('registered', { success: true, userCode: normalized });
    });

    // ----------------------------------------------------
    // Presence: Query if a peer is currently connected
    // ----------------------------------------------------
    socket.on('check-presence', (data, callback) => {
      const targetUserCode = typeof data === 'string' ? data : data?.targetUserCode;
      const normalized = presenceManager.normalizeUserCode(targetUserCode);
      const online = presenceManager.isOnline(normalized);

      const response = { targetUserCode: normalized, online };
      if (typeof callback === 'function') {
        callback(response);
      }
      socket.emit('presence-result', response);
    });

    // ----------------------------------------------------
    // WebRTC: SDP Offer Relay
    // ----------------------------------------------------
    socket.on('webrtc-offer', (data) => {
      const fromUserCode = presenceManager.getUserCode(socket.id);
      const targetUserCode = presenceManager.normalizeUserCode(data?.targetUserCode);

      if (!fromUserCode || !targetUserCode || !data?.offer) return;

      io.to(targetUserCode).emit('webrtc-offer', {
        fromUserCode,
        offer: data.offer
      });
    });

    // ----------------------------------------------------
    // WebRTC: SDP Answer Relay
    // ----------------------------------------------------
    socket.on('webrtc-answer', (data) => {
      const fromUserCode = presenceManager.getUserCode(socket.id);
      const targetUserCode = presenceManager.normalizeUserCode(data?.targetUserCode);

      if (!fromUserCode || !targetUserCode || !data?.answer) return;

      io.to(targetUserCode).emit('webrtc-answer', {
        fromUserCode,
        answer: data.answer
      });
    });

    // ----------------------------------------------------
    // WebRTC: ICE Candidate Relay
    // ----------------------------------------------------
    socket.on('ice-candidate', (data) => {
      const fromUserCode = presenceManager.getUserCode(socket.id);
      const targetUserCode = presenceManager.normalizeUserCode(data?.targetUserCode);

      if (!fromUserCode || !targetUserCode || !data?.candidate) return;

      io.to(targetUserCode).emit('ice-candidate', {
        fromUserCode,
        candidate: data.candidate
      });
    });

    // ----------------------------------------------------
    // Ephemeral Relay: Encrypted Envelope Relay Fallback
    // ----------------------------------------------------
    socket.on('encrypted-envelope', (data, callback) => {
      const fromUserCode = presenceManager.getUserCode(socket.id);
      const targetUserCode = presenceManager.normalizeUserCode(data?.targetUserCode);

      if (!fromUserCode || !targetUserCode || !data?.envelope) {
        if (typeof callback === 'function') {
          callback({ success: false, error: 'Invalid message envelope' });
        }
        return;
      }

      const isDelivered = presenceManager.isOnline(targetUserCode);
      if (isDelivered) {
        io.to(targetUserCode).emit('encrypted-envelope', {
          fromUserCode,
          envelope: data.envelope
        });
      }

      if (typeof callback === 'function') {
        callback({ success: isDelivered, delivered: isDelivered });
      }
    });

    // ----------------------------------------------------
    // Disconnect: Clean up presence registry
    // ----------------------------------------------------
    socket.on('disconnect', () => {
      presenceManager.unregister(socket.id);
    });
  });
}

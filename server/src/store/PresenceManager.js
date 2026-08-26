/**
 * In-Memory Presence Manager.
 *
 * Tracks active socket connections by UserCode for real-time online/offline presence
 * and targeted WebRTC signaling routing.
 */
export class PresenceManager {
  constructor() {
    this.userCodeToSockets = new Map(); // Map<userCode, Set<socketId>>
    this.socketToUserCode = new Map();  // Map<socketId, userCode>
  }

  normalizeUserCode(code) {
    if (!code || typeof code !== 'string') return '';
    const clean = code.replace(/[- ]/g, '').toUpperCase();
    if (clean.length !== 8) return '';
    return `${clean.substring(0, 4)}-${clean.substring(4, 8)}`;
  }

  /**
   * Registers a socket connection to a userCode.
   */
  register(userCode, socketId) {
    const normalized = this.normalizeUserCode(userCode);
    if (!normalized || !socketId) return false;

    // Remove any prior registration for this socket
    this.unregister(socketId);

    if (!this.userCodeToSockets.has(normalized)) {
      this.userCodeToSockets.set(normalized, new Set());
    }

    this.userCodeToSockets.get(normalized).add(socketId);
    this.socketToUserCode.set(socketId, normalized);
    return true;
  }

  /**
   * Unregisters a socket ID. Returns the userCode if found, or null.
   */
  unregister(socketId) {
    const userCode = this.socketToUserCode.get(socketId);
    if (!userCode) return null;

    this.socketToUserCode.delete(socketId);
    const sockets = this.userCodeToSockets.get(userCode);
    if (sockets) {
      sockets.delete(socketId);
      if (sockets.size === 0) {
        this.userCodeToSockets.delete(userCode);
      }
    }

    return userCode;
  }

  /**
   * Checks whether a user is currently online.
   */
  isOnline(userCode) {
    const normalized = this.normalizeUserCode(userCode);
    return this.userCodeToSockets.has(normalized) && this.userCodeToSockets.get(normalized).size > 0;
  }

  /**
   * Returns all active socket IDs for a given userCode.
   */
  getSockets(userCode) {
    const normalized = this.normalizeUserCode(userCode);
    const sockets = this.userCodeToSockets.get(normalized);
    return sockets ? Array.from(sockets) : [];
  }

  /**
   * Returns the userCode associated with a given socket ID.
   */
  getUserCode(socketId) {
    return this.socketToUserCode.get(socketId) || null;
  }

  /**
   * Returns count of currently active online users.
   */
  count() {
    return this.userCodeToSockets.size;
  }

  clear() {
    this.userCodeToSockets.clear();
    this.socketToUserCode.clear();
  }
}

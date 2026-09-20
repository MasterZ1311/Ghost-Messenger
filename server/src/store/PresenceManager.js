/**
 * In-Memory Presence Manager.
 *
 * Tracks active socket connections by UserCode for real-time online/offline
 * presence and targeted WebRTC signaling routing.
 *
 * Security bounds:
 *  - Max MAX_SOCKETS_PER_USERCODE sockets per user (prevents flooding presence).
 *  - IP connection counts tracked for use by the socket admission guard.
 */

const MAX_SOCKETS_PER_USERCODE = 3;
const MAX_CONNECTIONS_PER_IP   = 5;

export class PresenceManager {
  constructor(
    maxSocketsPerUser = MAX_SOCKETS_PER_USERCODE,
    maxPerIp          = MAX_CONNECTIONS_PER_IP
  ) {
    this.maxSocketsPerUser = maxSocketsPerUser;
    this.maxPerIp          = maxPerIp;

    /** @type {Map<string, Set<string>>} userCode → Set<socketId> */
    this.userCodeToSockets = new Map();

    /** @type {Map<string, string>} socketId → userCode */
    this.socketToUserCode  = new Map();

    /** @type {Map<string, number>} ip → connection count */
    this.ipConnectionCount = new Map();
  }

  // ---------------------------------------------------------------------------
  // Normalisation
  // ---------------------------------------------------------------------------

  normalizeUserCode(code) {
    if (!code || typeof code !== 'string') return '';
    const clean = code.replace(/[- ]/g, '').toUpperCase();
    if (clean.length !== 8) return '';
    return `${clean.substring(0, 4)}-${clean.substring(4, 8)}`;
  }

  // ---------------------------------------------------------------------------
  // IP-level admission
  // ---------------------------------------------------------------------------

  /**
   * Increments the connection counter for an IP.
   * Returns false if the IP has reached the cap (caller should disconnect the socket).
   * @param {string} ip
   * @returns {boolean}  true = admitted, false = rejected.
   */
  admitIp(ip) {
    if (!ip) return true; // allow if IP unknown (defensive)
    const count = this.ipConnectionCount.get(ip) || 0;
    if (count >= this.maxPerIp) return false;
    this.ipConnectionCount.set(ip, count + 1);
    return true;
  }

  /**
   * Decrements the connection counter for an IP on disconnect.
   * @param {string} ip
   */
  releaseIp(ip) {
    if (!ip) return;
    const count = this.ipConnectionCount.get(ip) || 0;
    if (count <= 1) {
      this.ipConnectionCount.delete(ip);
    } else {
      this.ipConnectionCount.set(ip, count - 1);
    }
  }

  /**
   * Returns current connection count for an IP (for testing).
   * @param {string} ip
   * @returns {number}
   */
  ipCount(ip) {
    return this.ipConnectionCount.get(ip) || 0;
  }

  // ---------------------------------------------------------------------------
  // Socket registration
  // ---------------------------------------------------------------------------

  /**
   * Registers a socket connection to a userCode.
   *
   * @param {string} userCode
   * @param {string} socketId
   * @returns {{ ok: boolean, reason?: string }}
   */
  register(userCode, socketId) {
    const normalized = this.normalizeUserCode(userCode);
    if (!normalized || !socketId) return { ok: false, reason: 'invalid_args' };

    // Remove any prior registration for this socket
    this.unregister(socketId);

    const existingSockets = this.userCodeToSockets.get(normalized);
    if (existingSockets && existingSockets.size >= this.maxSocketsPerUser) {
      return { ok: false, reason: 'too_many_connections' };
    }

    if (!this.userCodeToSockets.has(normalized)) {
      this.userCodeToSockets.set(normalized, new Set());
    }

    this.userCodeToSockets.get(normalized).add(socketId);
    this.socketToUserCode.set(socketId, normalized);
    return { ok: true };
  }

  /**
   * Unregisters a socket ID.
   * @param {string} socketId
   * @returns {string|null}  The userCode that was associated, or null.
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

  // ---------------------------------------------------------------------------
  // Presence queries
  // ---------------------------------------------------------------------------

  /** @returns {boolean} */
  isOnline(userCode) {
    const normalized = this.normalizeUserCode(userCode);
    const sockets    = this.userCodeToSockets.get(normalized);
    return Boolean(sockets && sockets.size > 0);
  }

  /**
   * @param {string} userCode
   * @returns {string[]}
   */
  getSockets(userCode) {
    const normalized = this.normalizeUserCode(userCode);
    const sockets    = this.userCodeToSockets.get(normalized);
    return sockets ? Array.from(sockets) : [];
  }

  /**
   * @param {string} socketId
   * @returns {string|null}
   */
  getUserCode(socketId) {
    return this.socketToUserCode.get(socketId) || null;
  }

  /** @returns {number} */
  count() {
    return this.userCodeToSockets.size;
  }

  clear() {
    this.userCodeToSockets.clear();
    this.socketToUserCode.clear();
    this.ipConnectionCount.clear();
  }
}

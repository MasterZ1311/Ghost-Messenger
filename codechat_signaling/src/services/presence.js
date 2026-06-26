'use strict';

const config = require('../config');
const logger = require('../utils/logger');

/**
 * Tracks which UserCodes are currently online and on which socket(s).
 *
 * A single UserCode may be connected from multiple devices, so we map
 * UserCode -> Set<socketId>. Reverse lookup socketId -> UserCode is kept
 * for O(1) cleanup on disconnect.
 *
 * This is intentionally in-memory. For horizontal scaling, back this with
 * the Socket.IO Redis adapter + a shared presence store (see README).
 */
class PresenceRegistry {
  constructor() {
    /** @type {Map<string, Set<string>>} */
    this._userToSockets = new Map();
    /** @type {Map<string, string>} */
    this._socketToUser = new Map();
  }

  /**
   * Registers a socket for a UserCode.
   * @returns {{ ok: boolean, reason?: string }}
   */
  register(userCode, socketId) {
    let sockets = this._userToSockets.get(userCode);
    if (!sockets) {
      sockets = new Set();
      this._userToSockets.set(userCode, sockets);
    }

    if (
      !sockets.has(socketId) &&
      sockets.size >= config.presence.maxSocketsPerUser
    ) {
      return {
        ok: false,
        reason: `Max concurrent sessions (${config.presence.maxSocketsPerUser}) reached for this UserCode`,
      };
    }

    sockets.add(socketId);
    this._socketToUser.set(socketId, userCode);
    logger.debug({ userCode, socketId, sessions: sockets.size }, 'presence registered');
    return { ok: true };
  }

  /**
   * Removes a socket from the registry. Returns the UserCode it belonged to.
   * @returns {string | undefined}
   */
  unregister(socketId) {
    const userCode = this._socketToUser.get(socketId);
    if (!userCode) return undefined;

    this._socketToUser.delete(socketId);
    const sockets = this._userToSockets.get(userCode);
    if (sockets) {
      sockets.delete(socketId);
      if (sockets.size === 0) {
        this._userToSockets.delete(userCode);
      }
    }
    logger.debug({ userCode, socketId }, 'presence unregistered');
    return userCode;
  }

  /** Returns all socket IDs registered for a UserCode. */
  getSockets(userCode) {
    const sockets = this._userToSockets.get(userCode);
    return sockets ? Array.from(sockets) : [];
  }

  /** Returns the UserCode associated with a socket. */
  getUserCode(socketId) {
    return this._socketToUser.get(socketId);
  }

  isOnline(userCode) {
    const sockets = this._userToSockets.get(userCode);
    return Boolean(sockets && sockets.size > 0);
  }

  stats() {
    return {
      onlineUsers: this._userToSockets.size,
      activeSockets: this._socketToUser.size,
    };
  }
}

module.exports = new PresenceRegistry();

'use strict';

const config = require('../config');

/**
 * Lightweight sliding-window-ish rate limiter scoped to a single socket.
 * Tracks event timestamps and rejects bursts that exceed the configured
 * threshold. Designed to throttle signal floods without external deps.
 */
class SocketRateLimiter {
  constructor() {
    this._timestamps = [];
  }

  /**
   * Records an event and reports whether it is allowed.
   * @returns {boolean} true if allowed, false if rate-limited.
   */
  allow() {
    const now = Date.now();

    // Remove expired entries. _max is small (default 30) so this is O(1) in practice.
    this._timestamps = this._timestamps.filter(t => now - t < config.rateLimit.windowMs);

    if (this._timestamps.length >= config.rateLimit.maxEvents) {
      return false;
    }

    this._timestamps.push(now);
    return true;
  }
}

module.exports = SocketRateLimiter;

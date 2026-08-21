'use strict';

const config = require('../config');
const logger = require('../utils/logger');

const MAX_TOTAL_QUEUED = 10000;

/**
 * Transient handshake queue.
 *
 * The signaling server never stores chat messages (those flow E2EE over the
 * P2P DataChannel). However, the WebRTC *handshake* signals (offer/answer/ICE)
 * may arrive a moment before the recipient connects. To avoid dropping a
 * connection attempt, we buffer signals for a short TTL and flush them once
 * the recipient registers.
 *
 * Entries are strictly bounded in both count and lifetime to prevent abuse.
 */
class HandshakeQueue {
  constructor() {
    /** @type {Map<string, Array<{ payload: object, expiresAt: number }>>} */
    this._queues = new Map();
    this._sweepTimer = null;
    this._totalQueued = 0;
  }

  start() {
    if (!config.queue.enabled || this._sweepTimer) return;
    this._sweepTimer = setInterval(
      () => this._sweep(),
      config.queue.sweepIntervalMs
    );
    // Don't keep the event loop alive solely for the sweeper.
    if (this._sweepTimer.unref) this._sweepTimer.unref();
    logger.info('handshake queue sweeper started');
  }

  stop() {
    if (this._sweepTimer) {
      clearInterval(this._sweepTimer);
      this._sweepTimer = null;
    }
    this._queues.clear();
    this._totalQueued = 0;
  }

  /**
   * Enqueues a signal for an offline recipient.
   * @returns {{ ok: boolean, reason?: string }}
   */
  enqueue(toCode, payload) {
    if (!config.queue.enabled) {
      return { ok: false, reason: 'queue disabled' };
    }

    if (this._totalQueued >= MAX_TOTAL_QUEUED) {
      return { ok: false, reason: 'server_queue_full' };
    }

    let queue = this._queues.get(toCode);
    if (!queue) {
      queue = [];
      this._queues.set(toCode, queue);
    }

    if (queue.length >= config.queue.maxPerUser) {
      return { ok: false, reason: 'queue full' };
    }

    queue.push({
      payload,
      expiresAt: Date.now() + config.queue.ttlMs,
    });
    this._totalQueued++;
    logger.debug({ toCode, depth: queue.length }, 'handshake queued');
    return { ok: true };
  }

  /**
   * Drains and returns all non-expired queued signals for a recipient.
   * @returns {Array<object>}
   */
  drain(toCode) {
    const queue = this._queues.get(toCode);
    if (!queue || queue.length === 0) return [];

    this._queues.delete(toCode);
    const now = Date.now();
    const valid = queue.filter((entry) => entry.expiresAt > now);
    this._totalQueued -= queue.length;
    return valid.map((entry) => entry.payload);
  }

  _sweep() {
    const now = Date.now();
    let removed = 0;
    for (const [code, queue] of this._queues.entries()) {
      const fresh = queue.filter((entry) => entry.expiresAt > now);
      removed += queue.length - fresh.length;
      if (fresh.length === 0) {
        this._queues.delete(code);
      } else {
        this._queues.set(code, fresh);
      }
    }
    if (removed > 0) {
      this._totalQueued -= removed;
      logger.debug({ removed }, 'handshake queue swept expired entries');
    }
  }

  stats() {
    let pending = 0;
    for (const queue of this._queues.values()) pending += queue.length;
    return { queuedRecipients: this._queues.size, pendingSignals: pending };
  }
}

module.exports = new HandshakeQueue();

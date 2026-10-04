/**
 * In-Memory Ephemeral Offline Queue Store for Calypso.
 *
 * Temporarily stores Signal-encrypted envelopes for offline recipients until they reconnect.
 *
 * Security & Privacy constraints:
 *  - Envelopes are opaque Signal Protocol ciphertext (the server never has decryption keys).
 *  - Max 50 envelopes per recipient.
 *  - 24-hour TTL (86,400,000 ms).
 *  - Automatically evicted upon delivery or TTL expiry.
 */

const MAX_ENVELOPES_PER_USER = 50;
const DEFAULT_TTL_MS         = 24 * 60 * 60 * 1_000; // 24 hours

export class OfflineQueueStore {
  constructor(maxPerUser = MAX_ENVELOPES_PER_USER, ttlMs = DEFAULT_TTL_MS) {
    this.maxPerUser = maxPerUser;
    this.ttlMs      = ttlMs;

    /** @type {Map<string, Array<{ fromUserCode: string, envelope: string, timestamp: number }>>} */
    this.queues = new Map();
  }

  /**
   * Enqueues an encrypted envelope for an offline recipient.
   *
   * @param {string} targetUserCode
   * @param {{ fromUserCode: string, envelope: string }} item
   * @returns {boolean} true if enqueued, false if queue is full
   */
  enqueue(targetUserCode, { fromUserCode, envelope }) {
    if (!targetUserCode || !fromUserCode || !envelope) return false;

    this.evictExpiredFor(targetUserCode);

    let queue = this.queues.get(targetUserCode);
    if (!queue) {
      queue = [];
      this.queues.set(targetUserCode, queue);
    }

    if (queue.length >= this.maxPerUser) {
      return false; // Queue full
    }

    queue.push({
      fromUserCode,
      envelope,
      timestamp: Date.now()
    });

    return true;
  }

  /**
   * Retrieves and clears all queued envelopes for a recipient upon reconnect.
   *
   * @param {string} targetUserCode
   * @returns {Array<{ fromUserCode: string, envelope: string }>}
   */
  dequeueAll(targetUserCode) {
    if (!targetUserCode) return [];

    this.evictExpiredFor(targetUserCode);

    const queue = this.queues.get(targetUserCode);
    if (!queue || queue.length === 0) return [];

    this.queues.delete(targetUserCode);
    return queue.map(({ fromUserCode, envelope }) => ({ fromUserCode, envelope }));
  }

  /**
   * Checks if any queued envelopes exist for [targetUserCode].
   * @param {string} targetUserCode
   * @returns {boolean}
   */
  hasQueued(targetUserCode) {
    if (!targetUserCode) return false;
    this.evictExpiredFor(targetUserCode);
    const queue = this.queues.get(targetUserCode);
    return Boolean(queue && queue.length > 0);
  }

  /**
   * Evicts expired messages for a specific user.
   * @param {string} userCode
   */
  evictExpiredFor(userCode) {
    const queue = this.queues.get(userCode);
    if (!queue) return;

    const now = Date.now();
    const valid = queue.filter(item => now - item.timestamp <= this.ttlMs);

    if (valid.length === 0) {
      this.queues.delete(userCode);
    } else if (valid.length !== queue.length) {
      this.queues.set(userCode, valid);
    }
  }

  /**
   * Periodic eviction across all users.
   */
  evictExpired() {
    for (const userCode of this.queues.keys()) {
      this.evictExpiredFor(userCode);
    }
  }

  /**
   * Returns total count of envelopes across all queues.
   * @returns {number}
   */
  count() {
    let total = 0;
    for (const queue of this.queues.values()) {
      total += queue.length;
    }
    return total;
  }

  clear() {
    this.queues.clear();
  }
}

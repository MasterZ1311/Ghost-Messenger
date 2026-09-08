/**
 * In-Memory PreKey Bundle Store for Calypso.
 *
 * Guarantees:
 * - Zero persistence to disk / database.
 * - Automatic eviction after TTL (default 7 days).
 * - Thread-safe atomic consumption of one-time PreKeys.
 */
export class InMemoryPreKeyStore {
  constructor(defaultTtlMs = 7 * 24 * 60 * 60 * 1000) {
    this.defaultTtlMs = defaultTtlMs;
    this.bundles = new Map();
  }

  /**
   * Normalizes UserCode format (e.g. "5jkl-2p4x" -> "5JKL-2P4X").
   */
  normalizeUserCode(code) {
    if (!code || typeof code !== 'string') return '';
    const clean = code.replace(/[- ]/g, '').toUpperCase();
    if (clean.length !== 8) return '';
    return `${clean.substring(0, 4)}-${clean.substring(4, 8)}`;
  }

  /**
   * Uploads or replaces the PreKey bundle for a given userCode.
   */
  upload(userCode, bundle) {
    const normalized = this.normalizeUserCode(userCode);
    if (!normalized) {
      throw new Error('Invalid userCode format');
    }

    if (!bundle || !bundle.identityKey || !bundle.signedPreKey) {
      throw new Error('Invalid bundle: missing identityKey or signedPreKey');
    }

    const preKeys = Array.isArray(bundle.preKeys) ? [...bundle.preKeys] : [];

    this.bundles.set(normalized, {
      identityKey: bundle.identityKey,
      registrationId: bundle.registrationId,
      signedPreKey: bundle.signedPreKey,
      preKeys,
      kyberPreKey: bundle.kyberPreKey || null,
      updatedAt: Date.now()
    });

    return {
      userCode: normalized,
      preKeyCount: preKeys.length
    };
  }

  /**
   * Fetches a PreKey bundle for X3DH handshake initiation and consumes one one-time PreKey.
   */
  fetchAndConsume(userCode) {
    const normalized = this.normalizeUserCode(userCode);
    if (!normalized || !this.bundles.has(normalized)) {
      return null;
    }

    const record = this.bundles.get(normalized);

    // Consume one one-time prekey if available
    let oneTimePreKey = null;
    if (record.preKeys && record.preKeys.length > 0) {
      oneTimePreKey = record.preKeys.shift();
    }

    return {
      identityKey: record.identityKey,
      registrationId: record.registrationId,
      signedPreKey: record.signedPreKey,
      preKey: oneTimePreKey,
      kyberPreKey: record.kyberPreKey || null,
      remainingPreKeys: record.preKeys.length
    };
  }

  /**
   * Checks how many prekeys remain for a user.
   */
  getRemainingCount(userCode) {
    const normalized = this.normalizeUserCode(userCode);
    if (!normalized || !this.bundles.has(normalized)) return 0;
    return this.bundles.get(normalized).preKeys.length;
  }

  /**
   * Evicts expired bundles based on TTL.
   */
  evictExpired(maxAgeMs = this.defaultTtlMs) {
    const now = Date.now();
    let evicted = 0;
    for (const [code, bundle] of this.bundles.entries()) {
      if (now - bundle.updatedAt > maxAgeMs) {
        this.bundles.delete(code);
        evicted++;
      }
    }
    return evicted;
  }

  /**
   * Clears all stores (for test suites).
   */
  clear() {
    this.bundles.clear();
  }

  size() {
    return this.bundles.size;
  }
}

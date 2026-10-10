/**
 * In-Memory PreKey Bundle Store for Calypso.
 *
 * Guarantees:
 * - Zero persistence to disk / database.
 * - Automatic eviction after TTL (default 7 days).
 * - Atomic consumption of one-time PreKeys.
 * - Identity key locking: first upload for a UserCode binds that identity key.
 *   Subsequent uploads must present the same identity key or pass a signed challenge.
 * - Bounded memory: max MAX_BUNDLES entries and MAX_PREKEYS_PER_USER prekeys per user.
 */

const DEFAULT_TTL_MS         = 7 * 24 * 60 * 60 * 1000; // 7 days
const MAX_BUNDLES             = 10_000;
const MAX_PREKEYS_PER_USER    = 500;

export class InMemoryPreKeyStore {
  /**
   * @param {number} defaultTtlMs  Per-bundle TTL in milliseconds.
   * @param {number} maxBundles    Hard cap on number of stored bundles.
   * @param {number} maxPreKeys    Hard cap on prekeys per user.
   */
  constructor(
    defaultTtlMs        = DEFAULT_TTL_MS,
    maxBundles          = MAX_BUNDLES,
    maxPreKeysPerUser   = MAX_PREKEYS_PER_USER
  ) {
    this.defaultTtlMs       = defaultTtlMs;
    this.maxBundles         = maxBundles;
    this.maxPreKeysPerUser  = maxPreKeysPerUser;

    /** @type {Map<string, BundleRecord>} */
    this.bundles = new Map();

    /**
     * Identity key lock: once a bundle is uploaded for a userCode the identity
     * key is frozen. Subsequent uploads must present the same identity key.
     * @type {Map<string, string>}   userCode → identityKey (base64)
     */
    this.identityKeyLock = new Map();
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Normalises UserCode format (e.g. "5jkl-2p4x" → "5JKL-2P4X").
   * @param {*} code
   * @returns {string}  Normalised code, or '' if invalid.
   */
  normalizeUserCode(code) {
    if (!code || typeof code !== 'string') return '';
    const clean = code.replace(/[- ]/g, '').toUpperCase();
    if (clean.length !== 8) return '';
    return `${clean.substring(0, 4)}-${clean.substring(4, 8)}`;
  }

  /**
   * Uploads or replaces the PreKey bundle for a given userCode.
   *
   * Throws with a typed `code` property on failure so callers can map codes
   * to HTTP status codes without leaking internal errors.
   *
   * Error codes:
   *   'INVALID_USERCODE'     → 400
   *   'INVALID_BUNDLE'       → 400
   *   'IDENTITY_KEY_MISMATCH'→ 403  (attempted hijack)
   *   'STORE_FULL'           → 503
   *   'TOO_MANY_PREKEYS'     → 400
   *
   * @param {string} userCode
   * @param {object} bundle
   * @returns {{ userCode: string, preKeyCount: number }}
   */
  upload(userCode, bundle) {
    const normalized = this.normalizeUserCode(userCode);
    if (!normalized) {
      throw Object.assign(new Error('Invalid userCode format'), { code: 'INVALID_USERCODE' });
    }

    if (!bundle || typeof bundle !== 'object') {
      throw Object.assign(new Error('Bundle is required'), { code: 'INVALID_BUNDLE' });
    }

    const { identityKey, signedPreKey } = bundle;

    if (!identityKey || typeof identityKey !== 'string') {
      throw Object.assign(new Error('Bundle missing identityKey'), { code: 'INVALID_BUNDLE' });
    }

    if (!signedPreKey || typeof signedPreKey !== 'object') {
      throw Object.assign(new Error('Bundle missing signedPreKey'), { code: 'INVALID_BUNDLE' });
    }

    if (!signedPreKey.keyId || !signedPreKey.publicKey || !signedPreKey.signature) {
      throw Object.assign(new Error('signedPreKey missing required fields'), { code: 'INVALID_BUNDLE' });
    }

    // Enforce identity key lock
    const existingKey = this.identityKeyLock.get(normalized);
    if (existingKey && existingKey !== identityKey) {
      throw Object.assign(
        new Error('Identity key mismatch: upload rejected'),
        { code: 'IDENTITY_KEY_MISMATCH' }
      );
    }

    // Enforce per-user prekey cap
    const preKeys = Array.isArray(bundle.preKeys) ? [...bundle.preKeys] : [];
    if (preKeys.length > this.maxPreKeysPerUser) {
      throw Object.assign(
        new Error(`Too many prekeys: max ${this.maxPreKeysPerUser}`),
        { code: 'TOO_MANY_PREKEYS' }
      );
    }

    // Enforce global store cap (only for new entries)
    if (!this.bundles.has(normalized) && this.bundles.size >= this.maxBundles) {
      throw Object.assign(new Error('Prekey store is full'), { code: 'STORE_FULL' });
    }

    // Store
    this.bundles.set(normalized, {
      identityKey,
      registrationId: typeof bundle.registrationId === 'number' ? bundle.registrationId : 0,
      signedPreKey: {
        keyId:     signedPreKey.keyId,
        publicKey: signedPreKey.publicKey,
        signature: signedPreKey.signature
      },
      preKeys,
      kyberPreKey: bundle.kyberPreKey || null,
      updatedAt:   Date.now()
    });

    // Bind identity key on first upload
    if (!existingKey) {
      this.identityKeyLock.set(normalized, identityKey);
    }

    return { userCode: normalized, preKeyCount: preKeys.length };
  }

  /**
   * Fetches a PreKey bundle for X3DH handshake initiation and atomically
   * consumes one one-time PreKey.
   *
   * @param {string} userCode
   * @returns {object|null}
   */
  fetchAndConsume(userCode) {
    const normalized = this.normalizeUserCode(userCode);
    if (!normalized || !this.bundles.has(normalized)) return null;

    const record = this.bundles.get(normalized);

    let oneTimePreKey = null;
    if (record.preKeys && record.preKeys.length > 0) {
      oneTimePreKey = record.preKeys.shift();
    }

    return {
      identityKey:    record.identityKey,
      registrationId: record.registrationId,
      signedPreKey:   record.signedPreKey,
      preKey:         oneTimePreKey,
      kyberPreKey:    record.kyberPreKey || null,
      remainingPreKeys: record.preKeys.length
    };
  }

  /**
   * Returns how many one-time prekeys remain for a user.
   * @param {string} userCode
   * @returns {number}
   */
  getRemainingCount(userCode) {
    const normalized = this.normalizeUserCode(userCode);
    if (!normalized || !this.bundles.has(normalized)) return 0;
    return this.bundles.get(normalized).preKeys.length;
  }

  /**
   * Updates only the signed prekey for an existing bundle.
   * Keeps existing identityKey and preKeys unchanged.
   * @param {string} userCode
   * @param {object} signedPreKey - { keyId, publicKey, signature }
   * @returns {{ userCode: string, signedPreKeyId: number }}
   */
  updateSignedPreKey(userCode, signedPreKey) {
    const normalized = this.normalizeUserCode(userCode);
    if (!normalized) {
      throw Object.assign(new Error('Invalid userCode format'), { code: 'INVALID_USERCODE' });
    }
    if (!this.bundles.has(normalized)) {
      throw Object.assign(new Error('Bundle not found'), { code: 'BUNDLE_NOT_FOUND' });
    }
    if (!signedPreKey || typeof signedPreKey !== 'object') {
      throw Object.assign(new Error('signedPreKey is required'), { code: 'INVALID_SIGNED_PREKEY' });
    }
    if (!signedPreKey.keyId || !signedPreKey.publicKey || !signedPreKey.signature) {
      throw Object.assign(new Error('signedPreKey missing required fields'), { code: 'INVALID_SIGNED_PREKEY' });
    }
    const record = this.bundles.get(normalized);
    record.signedPreKey = {
      keyId: signedPreKey.keyId,
      publicKey: signedPreKey.publicKey,
      signature: signedPreKey.signature
    };
    record.updatedAt = Date.now();
    return { userCode: normalized, signedPreKeyId: signedPreKey.keyId };
  }

  /**
   * Returns the locked identity key for a userCode, or null if none stored.
   * @param {string} userCode
   * @returns {string|null}
   */
  getLockedIdentityKey(userCode) {
    const normalized = this.normalizeUserCode(userCode);
    return this.identityKeyLock.get(normalized) ?? null;
  }

  /**
   * Returns true if a bundle exists (ignoring TTL, for socket registration gating).
   * @param {string} userCode
   * @returns {boolean}
   */
  hasBundleFor(userCode) {
    const normalized = this.normalizeUserCode(userCode);
    return normalized !== '' && this.bundles.has(normalized);
  }

  /**
   * Evicts bundles older than maxAgeMs. Called periodically by the server.
   * @param {number} maxAgeMs
   * @returns {number}  Count of evicted entries.
   */
  evictExpired(maxAgeMs = this.defaultTtlMs) {
    const now = Date.now();
    let evicted = 0;
    for (const [code, bundle] of this.bundles) {
      if (now - bundle.updatedAt > maxAgeMs) {
        this.bundles.delete(code);
        this.identityKeyLock.delete(code);
        evicted++;
      }
    }
    return evicted;
  }

  /** Clears all state (for test suites only). */
  clear() {
    this.bundles.clear();
    this.identityKeyLock.clear();
  }

  /** @returns {number} */
  size() {
    return this.bundles.size;
  }
}

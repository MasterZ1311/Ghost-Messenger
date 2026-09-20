/**
 * In-Memory Challenge Store for Calypso UserCode Ownership Proofs.
 *
 * Flow:
 *   1. GET /api/prekeys/challenge/:userCode → issues a 32-byte hex nonce, stored here for 60s.
 *   2. Client computes: HMAC-SHA256(SHA256(identityKeyBytes), nonce) and sends it as `signature`.
 *   3. Server verifies using the identityKey from the submitted bundle.
 *
 * Guarantees:
 *   - Each nonce is one-time-use (consumed immediately on first verification attempt).
 *   - Bounded to MAX_PENDING challenges; oldest evicted when full.
 *   - Expired challenges are auto-evicted on each get/issue call.
 */

import { randomBytes, createHash, createHmac, timingSafeEqual } from 'node:crypto';

const DEFAULT_TTL_MS   = 60_000;   // 60 seconds
const MAX_PENDING      = 1_000;    // Hard cap on outstanding challenges

export class ChallengeStore {
  /**
   * @param {number} ttlMs  How long a challenge is valid in milliseconds.
   */
  constructor(ttlMs = DEFAULT_TTL_MS) {
    this.ttlMs      = ttlMs;
    /** @type {Map<string, {nonce: string, expiresAt: number}>} */
    this.challenges = new Map();
  }

  /**
   * Issues a new 32-byte hex nonce for the given userCode.
   * If one already exists it is replaced (forces client to restart the flow).
   *
   * @param {string} userCode   Normalised UserCode (e.g. "5JKL-2P4X").
   * @returns {string}          The hex-encoded nonce to return to the client.
   */
  issue(userCode) {
    this._evictExpired();

    // Evict oldest entry if at cap
    if (this.challenges.size >= MAX_PENDING) {
      const firstKey = this.challenges.keys().next().value;
      this.challenges.delete(firstKey);
    }

    const nonce      = randomBytes(32).toString('hex');
    const expiresAt  = Date.now() + this.ttlMs;
    this.challenges.set(userCode, { nonce, expiresAt });
    return nonce;
  }

  /**
   * Verifies an HMAC-SHA256 signature against the pending challenge for `userCode`.
   *
   * The expected signature is:
   *   HMAC-SHA256(key = SHA256(identityKeyBytes), data = nonce)
   *
   * where `identityKeyBytes` = base64-decode of bundle.identityKey (the 33-byte
   * Signal Protocol serialised Curve25519 public key: 0x05 || 32-byte key).
   *
   * Uses `timingSafeEqual` to prevent timing attacks.
   *
   * @param {string} userCode         Normalised UserCode.
   * @param {string} identityKeyB64   base64-encoded identity public key from the bundle.
   * @param {string} signatureHex     Hex-encoded HMAC-SHA256 from the client.
   * @returns {{ ok: boolean, reason?: string }}
   */
  verify(userCode, identityKeyB64, signatureHex) {
    const entry = this.challenges.get(userCode);
    if (!entry) {
      return { ok: false, reason: 'no_challenge' };
    }

    // Check expiry BEFORE consuming so the reason is 'expired', not 'no_challenge'
    if (Date.now() > entry.expiresAt) {
      this.challenges.delete(userCode);
      return { ok: false, reason: 'expired' };
    }

    // Consume immediately — prevents replay
    this.challenges.delete(userCode);

    let identityKeyBytes;
    try {
      identityKeyBytes = Buffer.from(identityKeyB64, 'base64');
    } catch {
      return { ok: false, reason: 'invalid_identity_key' };
    }

    // key = SHA256(identityKeyBytes)
    const hmacKey      = createHash('sha256').update(identityKeyBytes).digest();
    const expected     = createHmac('sha256', hmacKey).update(entry.nonce).digest('hex');

    let clientBuf, expectedBuf;
    try {
      clientBuf   = Buffer.from(signatureHex, 'hex');
      expectedBuf = Buffer.from(expected, 'hex');
    } catch {
      return { ok: false, reason: 'invalid_signature_format' };
    }

    if (clientBuf.length !== expectedBuf.length) {
      return { ok: false, reason: 'signature_mismatch' };
    }

    const match = timingSafeEqual(clientBuf, expectedBuf);
    return match ? { ok: true } : { ok: false, reason: 'signature_mismatch' };
  }

  /**
   * Returns the pending nonce for a userCode without consuming it. For testing.
   * @param {string} userCode
   * @returns {string|null}
   */
  peekNonce(userCode) {
    return this.challenges.get(userCode)?.nonce ?? null;
  }

  /** @returns {number} */
  size() {
    return this.challenges.size;
  }

  // -------------------------------------------------------------------------
  // Private
  // -------------------------------------------------------------------------

  _evictExpired() {
    const now = Date.now();
    for (const [code, entry] of this.challenges) {
      if (now > entry.expiresAt) {
        this.challenges.delete(code);
      }
    }
  }
}

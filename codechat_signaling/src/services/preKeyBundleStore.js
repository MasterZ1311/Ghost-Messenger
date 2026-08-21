'use strict';

const config = require('../config');
const logger = require('../utils/logger');

/** Maximum age for a stored bundle before it is considered stale (7 days). */
const MAX_BUNDLE_AGE_MS = 7 * 24 * 60 * 60 * 1000;

/**
 * Transient in-memory store for X3DH PreKey bundles.
 *
 * Each UserCode may hold exactly one active bundle. Clients upload their
 * bundle via PUT /api/prekey-bundle after generating keys, and initiating
 * peers fetch it via GET /api/prekey-bundle/:userCode before starting a
 * Signal session.
 *
 * This store is intentionally ephemeral (in-memory). For horizontal
 * scaling, replace with a shared Redis/DB backend (see README).
 */
class PreKeyBundleStore {
  constructor() {
    /** @type {Map<string, object>} */
    this._bundles = new Map();
    /** @type {ReturnType<typeof setInterval> | null} */
    this._sweepInterval = null;
  }

  /**
   * Stores a PreKey bundle for a UserCode, replacing any existing entry.
   * Returns false if the total bundle cap would be exceeded by a new insert.
   *
   * @param {string} userCode
   * @param {object} bundle  – validated PreKey bundle object
   * @returns {{ ok: boolean, reason?: string }}
   */
  put(userCode, bundle) {
    const isNew = !this._bundles.has(userCode);
    const max = config.prekey.maxBundlesTotal;

    if (isNew && this._bundles.size >= max) {
      logger.warn({ userCode, max }, 'prekey bundle store capacity reached');
      return { ok: false, reason: 'Server prekey capacity reached; try again later' };
    }

    this._bundles.set(userCode, { ...bundle, storedAt: Date.now() });
    logger.debug({ userCode }, 'prekey bundle stored');
    return { ok: true };
  }

  /**
   * Retrieves the PreKey bundle for a UserCode.
   *
   * - If the bundle is older than MAX_BUNDLE_AGE_MS it is deleted and null is returned.
   * - The one-time preKey is consumed (nulled out) after it is served so that each
   *   one-time pre-key is used only once. The rest of the bundle
   *   (identityKey, signedPreKey, registrationId) remains available.
   *
   * @param {string} userCode
   * @returns {object | null}
   */
  get(userCode) {
    const bundle = this._bundles.get(userCode);
    if (!bundle) return null;

    // Evict stale bundle.
    if (Date.now() - bundle.storedAt > MAX_BUNDLE_AGE_MS) {
      this._bundles.delete(userCode);
      logger.debug({ userCode }, 'prekey bundle evicted (stale)');
      return null;
    }

    // Consume the one-time preKey so it cannot be reused.
    const result = { ...bundle };
    if (bundle.preKey) {
      bundle.preKey = null;
      logger.debug({ userCode }, 'one-time preKey consumed');
    }

    return result;
  }

  /**
   * Removes the PreKey bundle for a UserCode (e.g. on logout or key rotation).
   * @param {string} userCode
   */
  delete(userCode) {
    const existed = this._bundles.delete(userCode);
    if (existed) logger.debug({ userCode }, 'prekey bundle deleted');
  }

  /**
   * Evicts all bundles older than MAX_BUNDLE_AGE_MS.
   */
  evictStale() {
    const now = Date.now();
    let evicted = 0;
    for (const [userCode, bundle] of this._bundles) {
      if (now - bundle.storedAt > MAX_BUNDLE_AGE_MS) {
        this._bundles.delete(userCode);
        evicted++;
      }
    }
    if (evicted > 0) {
      logger.info({ evicted }, 'prekey bundle sweep: evicted stale bundles');
    }
  }

  /**
   * Starts the hourly background sweep that removes stale bundles.
   * Safe to call multiple times — only one interval will run at a time.
   */
  startSweeper() {
    if (this._sweepInterval) return;
    this._sweepInterval = setInterval(() => this.evictStale(), 60 * 60 * 1000);
    // Allow the Node.js process to exit even if the interval is still running.
    if (this._sweepInterval.unref) this._sweepInterval.unref();
    logger.debug('prekey bundle sweeper started (interval: 1 h)');
  }

  /**
   * Stops the background sweep interval.
   */
  stopSweeper() {
    if (this._sweepInterval) {
      clearInterval(this._sweepInterval);
      this._sweepInterval = null;
      logger.debug('prekey bundle sweeper stopped');
    }
  }

  /** Returns aggregate metrics. */
  stats() {
    return { storedBundles: this._bundles.size };
  }
}

const store = new PreKeyBundleStore();

// Start the hourly eviction sweep automatically when the module is loaded.
store.startSweeper();

module.exports = store;

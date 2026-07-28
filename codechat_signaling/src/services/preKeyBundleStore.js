'use strict';

const config = require('../config');
const logger = require('../utils/logger');

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
   * @param {string} userCode
   * @returns {object | null}
   */
  get(userCode) {
    return this._bundles.get(userCode) ?? null;
  }

  /**
   * Removes the PreKey bundle for a UserCode (e.g. on logout or key rotation).
   * @param {string} userCode
   */
  delete(userCode) {
    const existed = this._bundles.delete(userCode);
    if (existed) logger.debug({ userCode }, 'prekey bundle deleted');
  }

  /** Returns aggregate metrics. */
  stats() {
    return { storedBundles: this._bundles.size };
  }
}

module.exports = new PreKeyBundleStore();

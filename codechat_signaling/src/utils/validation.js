'use strict';

const config = require('../config');

// UserCodes are derived from a public identity key (base32 over a hash),
// so we accept upper/lowercase alphanumerics with optional separators.
const USER_CODE_REGEX = /^[A-Za-z0-9_-]+$/;

const ALLOWED_SIGNAL_TYPES = new Set(['offer', 'answer', 'candidate']);

// Base64 alphabet (standard + URL-safe) for quick sanity-checks on key fields.
const BASE64_REGEX = /^[A-Za-z0-9+/\-_]+=*$/;

/**
 * Validates a UserCode string.
 * @returns {{ ok: boolean, reason?: string }}
 */
function validateUserCode(code) {
  if (typeof code !== 'string') {
    return { ok: false, reason: 'UserCode must be a string' };
  }
  const trimmed = code.trim();
  if (trimmed.length < config.validation.userCodeMinLen) {
    return { ok: false, reason: 'UserCode too short' };
  }
  if (trimmed.length > config.validation.userCodeMaxLen) {
    return { ok: false, reason: 'UserCode too long' };
  }
  if (!USER_CODE_REGEX.test(trimmed)) {
    return { ok: false, reason: 'UserCode contains invalid characters' };
  }
  return { ok: true };
}

/**
 * Validates an inbound signal envelope: { toCode, fromCode, signalData }.
 * Does NOT inspect the cryptographic contents — only structural safety.
 * @returns {{ ok: boolean, reason?: string }}
 */
function validateSignalEnvelope(payload) {
  if (!payload || typeof payload !== 'object') {
    return { ok: false, reason: 'Signal payload must be an object' };
  }

  const { toCode, fromCode, signalData } = payload;

  const toCheck = validateUserCode(toCode);
  if (!toCheck.ok) return { ok: false, reason: `toCode: ${toCheck.reason}` };

  const fromCheck = validateUserCode(fromCode);
  if (!fromCheck.ok) return { ok: false, reason: `fromCode: ${fromCheck.reason}` };

  if (!signalData || typeof signalData !== 'object') {
    return { ok: false, reason: 'signalData must be an object' };
  }

  if (!ALLOWED_SIGNAL_TYPES.has(signalData.type)) {
    return { ok: false, reason: 'signalData.type must be offer|answer|candidate' };
  }

  // Enforce a maximum serialized size to prevent memory abuse.
  let serializedSize;
  try {
    serializedSize = Buffer.byteLength(JSON.stringify(signalData), 'utf8');
  } catch (_) {
    return { ok: false, reason: 'signalData is not serializable' };
  }
  if (serializedSize > config.validation.maxSignalBytes) {
    return { ok: false, reason: 'signalData exceeds maximum allowed size' };
  }

  return { ok: true };
}

/**
 * Validates an inbound X3DH PreKey bundle.
 *
 * Required fields:
 *   registrationId      – positive integer
 *   identityKey         – base64 string (33-byte compressed Curve25519 public key)
 *   signedPreKey.keyId  – non-negative integer
 *   signedPreKey.publicKey – base64 string
 *   signedPreKey.signature – base64 string
 *
 * Optional field:
 *   preKey.keyId        – non-negative integer
 *   preKey.publicKey    – base64 string
 *
 * @param {object} bundle
 * @returns {{ ok: boolean, reason?: string }}
 */
function validatePreKeyBundle(bundle) {
  if (!bundle || typeof bundle !== 'object') {
    return { ok: false, reason: 'Bundle must be an object' };
  }

  const { registrationId, identityKey, signedPreKey, preKey } = bundle;

  if (!Number.isInteger(registrationId) || registrationId <= 0) {
    return { ok: false, reason: 'registrationId must be a positive integer' };
  }

  if (typeof identityKey !== 'string' || !BASE64_REGEX.test(identityKey) || identityKey.length === 0) {
    return { ok: false, reason: 'identityKey must be a non-empty base64 string' };
  }

  if (!signedPreKey || typeof signedPreKey !== 'object') {
    return { ok: false, reason: 'signedPreKey must be an object' };
  }
  if (!Number.isInteger(signedPreKey.keyId) || signedPreKey.keyId < 0) {
    return { ok: false, reason: 'signedPreKey.keyId must be a non-negative integer' };
  }
  if (typeof signedPreKey.publicKey !== 'string' || !BASE64_REGEX.test(signedPreKey.publicKey) || signedPreKey.publicKey.length === 0) {
    return { ok: false, reason: 'signedPreKey.publicKey must be a non-empty base64 string' };
  }
  if (typeof signedPreKey.signature !== 'string' || !BASE64_REGEX.test(signedPreKey.signature) || signedPreKey.signature.length === 0) {
    return { ok: false, reason: 'signedPreKey.signature must be a non-empty base64 string' };
  }

  // Optional one-time preKey — if present, both sub-fields are required.
  if (preKey !== undefined && preKey !== null) {
    if (typeof preKey !== 'object') {
      return { ok: false, reason: 'preKey must be an object if provided' };
    }
    if (!Number.isInteger(preKey.keyId) || preKey.keyId < 0) {
      return { ok: false, reason: 'preKey.keyId must be a non-negative integer' };
    }
    if (typeof preKey.publicKey !== 'string' || !BASE64_REGEX.test(preKey.publicKey) || preKey.publicKey.length === 0) {
      return { ok: false, reason: 'preKey.publicKey must be a non-empty base64 string' };
    }
  }

  return { ok: true };
}

module.exports = {
  validateUserCode,
  validateSignalEnvelope,
  validatePreKeyBundle,
  ALLOWED_SIGNAL_TYPES,
};

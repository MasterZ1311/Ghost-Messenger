'use strict';

const config = require('../config');

// UserCodes are derived from a public identity key (base32 over a hash),
// so we accept upper/lowercase alphanumerics with optional separators.
const USER_CODE_REGEX = /^[A-Za-z0-9_-]+$/;

const ALLOWED_SIGNAL_TYPES = new Set(['offer', 'answer', 'candidate']);

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

module.exports = {
  validateUserCode,
  validateSignalEnvelope,
  ALLOWED_SIGNAL_TYPES,
};

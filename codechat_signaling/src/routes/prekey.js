'use strict';

const express = require('express');
const config = require('../config');
const logger = require('../utils/logger');
const preKeyBundleStore = require('../services/preKeyBundleStore');
const { validateUserCode, validatePreKeyBundle } = require('../utils/validation');

const router = express.Router();

// ─────────────────────────────────────────────────────────────────────────────
//  Internal-API authentication middleware
//
//  Checks the X-Signaling-Token header against the INTERNAL_API_TOKEN env var.
//  - If INTERNAL_API_TOKEN is not configured, the endpoint is disabled (501).
//  - If the header is missing or wrong, the request is rejected (403).
//
//  NOTE: The PRIMARY path for prekey upload is via the socket `publish_prekey`
//  event, which is already authenticated via the joined socket session.
//  These REST endpoints are intended for admin tooling / internal services only.
// ─────────────────────────────────────────────────────────────────────────────
function requireInternalToken(req, res, next) {
  const secret = process.env.INTERNAL_API_TOKEN;
  if (!secret) {
    return res.status(501).json({ error: 'Endpoint disabled: INTERNAL_API_TOKEN not configured' });
  }
  const provided = req.headers['x-signaling-token'];
  if (!provided || provided !== secret) {
    logger.warn({ ip: req.ip, path: req.path }, 'internal API token check failed');
    return res.status(403).json({ error: 'Forbidden' });
  }
  return next();
}

// ─────────────────────────────────────────────────────────────────────────────
//  PUT /api/prekey-bundle
//  Upload (or refresh) the caller's own X3DH PreKey bundle.
//  Requires X-Signaling-Token header (admin / internal tooling only).
//
//  Body: {
//    userCode:       string,          // the uploader's own UserCode
//    registrationId: number,
//    identityKey:    string,          // base64 Curve25519 public key (33 bytes)
//    signedPreKey: {
//      keyId:     number,
//      publicKey: string,             // base64
//      signature: string,             // base64
//    },
//    preKey?: {                       // optional one-time preKey
//      keyId:     number,
//      publicKey: string,             // base64
//    },
//  }
// ─────────────────────────────────────────────────────────────────────────────
router.put('/api/prekey-bundle', requireInternalToken, (req, res) => {
  // Enforce overall payload size (express.json() limit is set in app.js, but
  // we add an explicit field-level check here for belt-and-suspenders safety).
  let serializedSize;
  try {
    serializedSize = Buffer.byteLength(JSON.stringify(req.body), 'utf8');
  } catch (_) {
    return res.status(400).json({ error: 'Payload is not JSON-serializable' });
  }
  if (serializedSize > config.validation.maxSignalBytes) {
    return res.status(413).json({ error: 'Payload too large' });
  }

  const { userCode, ...bundle } = req.body;

  // Validate the UserCode.
  const codeCheck = validateUserCode(userCode);
  if (!codeCheck.ok) {
    return res.status(400).json({ error: `userCode: ${codeCheck.reason}` });
  }

  // Validate bundle structure.
  const bundleCheck = validatePreKeyBundle(bundle);
  if (!bundleCheck.ok) {
    return res.status(400).json({ error: bundleCheck.reason });
  }

  const result = preKeyBundleStore.put(userCode.trim(), bundle);
  if (!result.ok) {
    logger.warn({ userCode }, 'prekey bundle put rejected');
    return res.status(503).json({ error: result.reason });
  }

  logger.info({ userCode }, 'prekey bundle uploaded via REST');
  return res.status(200).json({ ok: true });
});

// ─────────────────────────────────────────────────────────────────────────────
//  DELETE /api/prekey-bundle/:userCode
//  Remove a user's own bundle (logout / key rotation).
//  Requires X-Signaling-Token header (admin / internal tooling only).
// ─────────────────────────────────────────────────────────────────────────────
router.delete('/api/prekey-bundle/:userCode', requireInternalToken, (req, res) => {
  const { userCode } = req.params;

  const codeCheck = validateUserCode(userCode);
  if (!codeCheck.ok) {
    return res.status(400).json({ error: `userCode: ${codeCheck.reason}` });
  }

  preKeyBundleStore.delete(userCode.trim());
  logger.info({ userCode }, 'prekey bundle deleted via REST');
  return res.status(200).json({ ok: true });
});

// ─────────────────────────────────────────────────────────────────────────────
//  GET /api/prekey-bundle/:userCode
//  Fetch another peer's PreKey bundle to initiate an X3DH session.
//  Public endpoint — clients need to retrieve peer bundles without auth.
//  Returns 404 if the peer has not uploaded a bundle yet.
// ─────────────────────────────────────────────────────────────────────────────
router.get('/api/prekey-bundle/:userCode', (req, res) => {
  const { userCode } = req.params;

  const codeCheck = validateUserCode(userCode);
  if (!codeCheck.ok) {
    return res.status(400).json({ error: `userCode: ${codeCheck.reason}` });
  }

  const bundle = preKeyBundleStore.get(userCode.trim());
  if (!bundle) {
    return res.status(404).json({ error: 'No PreKey bundle found for this UserCode' });
  }

  logger.debug({ userCode }, 'prekey bundle served');
  return res.status(200).json({ bundle });
});

module.exports = router;

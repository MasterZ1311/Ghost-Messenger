'use strict';

const express = require('express');
const config = require('../config');
const logger = require('../utils/logger');
const preKeyBundleStore = require('../services/preKeyBundleStore');
const { validateUserCode, validatePreKeyBundle } = require('../utils/validation');

const router = express.Router();

// ─────────────────────────────────────────────────────────────────────────────
//  PUT /api/prekey-bundle
//  Upload (or refresh) the caller's own X3DH PreKey bundle.
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
router.put('/api/prekey-bundle', (req, res) => {
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

  logger.info({ userCode }, 'prekey bundle uploaded');
  return res.status(200).json({ ok: true });
});

// ─────────────────────────────────────────────────────────────────────────────
//  DELETE /api/prekey-bundle/:userCode
//  Remove a user's own bundle (logout / key rotation).
//  In a production system this endpoint would be authenticated; for now it is
//  open because the server has no auth surface — peers are identified only by
//  their UserCode and possession of the corresponding private key.
// ─────────────────────────────────────────────────────────────────────────────
router.delete('/api/prekey-bundle/:userCode', (req, res) => {
  const { userCode } = req.params;

  const codeCheck = validateUserCode(userCode);
  if (!codeCheck.ok) {
    return res.status(400).json({ error: `userCode: ${codeCheck.reason}` });
  }

  preKeyBundleStore.delete(userCode.trim());
  logger.info({ userCode }, 'prekey bundle deleted');
  return res.status(200).json({ ok: true });
});

// ─────────────────────────────────────────────────────────────────────────────
//  GET /api/prekey-bundle/:userCode
//  Fetch another peer's PreKey bundle to initiate an X3DH session.
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

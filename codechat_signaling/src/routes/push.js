'use strict';

const express = require('express');
const pushTokenStore = require('../services/pushTokenStore');
const { validateUserCode } = require('../utils/validation');
const logger = require('../utils/logger');

const router = express.Router();

// ─────────────────────────────────────────────────────────────────────────────
//  Internal-API authentication middleware
//
//  Checks the X-Signaling-Token header against the INTERNAL_API_TOKEN env var.
//  - If INTERNAL_API_TOKEN is not configured, the endpoint is disabled (501).
//  - If the header is missing or wrong, the request is rejected (403).
//
//  NOTE: The PRIMARY path for push token registration is via the socket
//  `register_push_token` event, which is already authenticated via the joined
//  socket session. These REST endpoints are for admin/internal tooling only.
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

/**
 * POST /api/push-token
 * Body: { userCode: string, token: string, platform?: 'android' | 'ios' }
 * Requires X-Signaling-Token header (admin / internal tooling only).
 */
router.post('/push-token', requireInternalToken, (req, res) => {
  const { userCode, token, platform } = req.body || {};

  const check = validateUserCode(userCode);
  if (!check.ok) {
    return res.status(400).json({ error: check.reason });
  }

  if (!token || typeof token !== 'string' || token.trim().length === 0) {
    return res.status(400).json({ error: 'Push token is required' });
  }

  pushTokenStore.registerToken(userCode, token, platform);
  return res.status(200).json({ status: 'ok', userCode: userCode.trim() });
});

/**
 * DELETE /api/push-token
 * Body or Query: { userCode: string }
 * Requires X-Signaling-Token header (admin / internal tooling only).
 */
router.delete('/push-token', requireInternalToken, (req, res) => {
  const userCode = req.body?.userCode || req.query?.userCode;
  const check = validateUserCode(userCode);
  if (!check.ok) {
    return res.status(400).json({ error: check.reason });
  }

  const removed = pushTokenStore.removeToken(userCode);
  return res.status(200).json({ status: 'ok', removed });
});

module.exports = router;

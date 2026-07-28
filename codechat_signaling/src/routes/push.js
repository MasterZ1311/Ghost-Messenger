'use strict';

const express = require('express');
const pushTokenStore = require('../services/pushTokenStore');
const { validateUserCode } = require('../utils/validation');

const router = express.Router();

/**
 * POST /api/push-token
 * Body: { userCode: string, token: string, platform?: 'android' | 'ios' }
 */
router.post('/push-token', (req, res) => {
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
 */
router.delete('/push-token', (req, res) => {
  const userCode = req.body?.userCode || req.query?.userCode;
  const check = validateUserCode(userCode);
  if (!check.ok) {
    return res.status(400).json({ error: check.reason });
  }

  const removed = pushTokenStore.removeToken(userCode);
  return res.status(200).json({ status: 'ok', removed });
});

module.exports = router;

'use strict';

const express = require('express');
const presence = require('../services/presence');
const messageQueue = require('../services/messageQueue');
const preKeyBundleStore = require('../services/preKeyBundleStore');

const router = express.Router();

const startedAt = Date.now();

/** Liveness probe: process is up. */
router.get('/health', (req, res) => {
  res.json({ status: 'ok', uptimeSeconds: Math.floor((Date.now() - startedAt) / 1000) });
});

/** Readiness probe: ready to accept signaling traffic. */
router.get('/ready', (req, res) => {
  res.json({ status: 'ready' });
});

/** Operational metrics (no PII; counts only). */
router.get('/metrics', (req, res) => {
  const token = process.env.METRICS_TOKEN;
  if (token) {
    const authHeader = req.headers['authorization'] || '';
    const provided = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : '';
    if (provided !== token) {
      return res.status(401).json({ error: 'unauthorized' });
    }
  }
  res.json({
    uptimeSeconds: Math.floor((Date.now() - startedAt) / 1000),
    presence: presence.stats(),
    queue: messageQueue.stats(),
    preKeyBundles: preKeyBundleStore.stats(),
    memory: process.memoryUsage(),
  });
});

module.exports = router;

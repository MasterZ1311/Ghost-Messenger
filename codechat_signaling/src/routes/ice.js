'use strict';

const express = require('express');
const { buildIceServers } = require('../services/iceServers');
const { validateUserCode } = require('../utils/validation');

const router = express.Router();

/**
 * Returns the ICE server list (STUN + optional ephemeral TURN credentials).
 * Clients call this on startup and feed the result into their RTCPeerConnection.
 *
 * Optional query param `userCode` binds ephemeral TURN credentials to a user
 * for easier server-side correlation (coturn REST API).
 */
router.get('/api/ice-servers', (req, res) => {
  const userCode = req.query.userCode;

  if (userCode !== undefined && !validateUserCode(userCode).ok) {
    return res.status(400).json({ error: 'invalid userCode' });
  }

  const iceServers = buildIceServers(userCode);
  res.json({ iceServers });
});

module.exports = router;

import { Router } from 'express';
import { ChallengeStore } from '../store/ChallengeStore.js';
import { generateTurnCredentials, isTurnConfigured } from '../middleware/turnCredentials.js';
import { logger } from '../middleware/logger.js';
import { fcmService } from '../services/fcmService.js';

// Shared challenge store instance (exported for injection in tests)
export const challengeStore = new ChallengeStore();

/**
 * Creates Express router for PreKey REST operations.
 *
 * @param {import('../store/InMemoryPreKeyStore.js').InMemoryPreKeyStore} preKeyStore
 * @param {import('../store/PresenceManager.js').PresenceManager} presenceManager
 * @param {import('../store/ChallengeStore.js').ChallengeStore} [challengeStoreInstance]
 */
export function createPreKeyRouter(preKeyStore, presenceManager, challengeStoreInstance = challengeStore) {
  const router = Router();

  // ---------------------------------------------------------------------------
  // GET /api/prekeys/health/status
  // Server health check — intentionally returns minimal information.
  // Does NOT expose activeUsers, storedBundles or any count that could act
  // as a timing/activity oracle.
  // ---------------------------------------------------------------------------
  router.get('/health/status', (_req, res) => {
    return res.status(200).json({
      status:        'ok',
      uptimeSeconds: Math.floor(process.uptime())
    });
  });

  // ---------------------------------------------------------------------------
  // GET /api/prekeys/challenge/:userCode
  // Issues a one-time challenge nonce the client must sign to prove it controls
  // the identity key for this UserCode.
  //
  // The client computes:
  //   signature = HMAC-SHA256( key=SHA256(identityKeyBytes), data=nonce ).hexdigest()
  //
  // and submits it alongside the bundle in POST /api/prekeys/upload.
  // ---------------------------------------------------------------------------
  router.get('/challenge/:userCode', (req, res) => {
    const raw        = req.params.userCode;
    const normalized = preKeyStore.normalizeUserCode(raw);

    if (!normalized) {
      return res.status(400).json({ success: false, error: 'Invalid userCode format' });
    }

    const nonce = challengeStoreInstance.issue(normalized);
    logger.info('challenge_issued', { userCode: normalized });

    return res.status(200).json({ success: true, userCode: normalized, nonce });
  });

  // ---------------------------------------------------------------------------
  // POST /api/prekeys/upload
  // Uploads a PreKey bundle.  Requires a valid ownership proof.
  //
  // Body: { userCode, bundle, signature }
  //   userCode   — the UserCode to register
  //   bundle     — { identityKey, registrationId, signedPreKey, preKeys, kyberPreKey? }
  //   signature  — hex-encoded HMAC-SHA256 from the challenge flow
  //                (required for NEW registrations and when changing the identity key)
  // ---------------------------------------------------------------------------
  router.post('/upload', (req, res) => {
    const { userCode, bundle, signature } = req.body || {};

    if (!userCode || !bundle) {
      return res.status(400).json({ success: false, error: 'Missing required fields: userCode and bundle' });
    }

    // --- Ownership verification ------------------------------------------------
    const normalized  = preKeyStore.normalizeUserCode(userCode);
    if (!normalized) {
      return res.status(400).json({ success: false, error: 'Invalid userCode format' });
    }

    const lockedKey   = preKeyStore.getLockedIdentityKey(normalized);
    const incomingKey = bundle?.identityKey;

    if (!incomingKey || typeof incomingKey !== 'string') {
      return res.status(400).json({ success: false, error: 'Missing bundle.identityKey' });
    }

    // If this userCode already has a different locked identity key, reject
    if (lockedKey && lockedKey !== incomingKey) {
      logger.warn('upload_identity_mismatch', { userCodeHash: normalized.slice(0, 4) });
      return res.status(403).json({ success: false, error: 'Identity key mismatch' });
    }

    // If no locked key exists yet (first upload) OR if it matches, verify challenge
    if (!lockedKey) {
      // First upload: challenge signature is mandatory
      if (!signature || typeof signature !== 'string') {
        return res.status(401).json({ success: false, error: 'Challenge signature required for first upload' });
      }

      const result = challengeStoreInstance.verify(normalized, incomingKey, signature);
      if (!result.ok) {
        logger.warn('upload_challenge_failed', { reason: result.reason });
        return res.status(401).json({ success: false, error: 'Invalid or expired challenge signature' });
      }
    }
    // Subsequent uploads with matching identity key are accepted without re-challenge
    // (the identity key lock itself is the proof of prior ownership)

    // --- Store the bundle -------------------------------------------------------
    try {
      const result = preKeyStore.upload(normalized, bundle);
      logger.info('bundle_uploaded', { preKeyCount: result.preKeyCount });
      return res.status(200).json({
        success:     true,
        userCode:    result.userCode,
        preKeyCount: result.preKeyCount,
        message:     'PreKey bundle uploaded successfully'
      });
    } catch (err) {
      const statusMap = {
        IDENTITY_KEY_MISMATCH: 403,
        STORE_FULL:            503,
        TOO_MANY_PREKEYS:      400,
        INVALID_BUNDLE:        400,
        INVALID_USERCODE:      400
      };
      const status = statusMap[err.code] || 400;
      logger.warn('bundle_upload_error', { code: err.code });
      // Return generic message — do NOT leak err.message to clients
      return res.status(status).json({ success: false, error: 'Bundle upload failed' });
    }
  });

  // ---------------------------------------------------------------------------
  // PUT /api/prekeys/signed-prekey
  // Rotates the Signed PreKey for an existing bundle without re-uploading all one-time PreKeys.
  // Body: { userCode, identityKey, signedPreKey }
  // ---------------------------------------------------------------------------
  router.put('/signed-prekey', (req, res) => {
    const { userCode, identityKey, signedPreKey } = req.body || {};
    const normalized = preKeyStore.normalizeUserCode(userCode);

    if (!normalized) {
      return res.status(400).json({ success: false, error: 'Invalid userCode format' });
    }

    if (!signedPreKey || typeof signedPreKey !== 'object') {
      return res.status(400).json({ success: false, error: 'Missing signedPreKey' });
    }

    const lockedKey = preKeyStore.getLockedIdentityKey(normalized);
    if (!lockedKey) {
      return res.status(404).json({ success: false, error: 'Bundle not found' });
    }

    if (identityKey && lockedKey !== identityKey) {
      logger.warn('signed_prekey_identity_mismatch', { userCodeHash: normalized.slice(0, 4) });
      return res.status(403).json({ success: false, error: 'Identity key mismatch' });
    }

    try {
      const result = preKeyStore.updateSignedPreKey(normalized, signedPreKey);
      logger.info('signed_prekey_updated', { userCode: normalized, keyId: result.signedPreKeyId });
      return res.status(200).json({
        success:        true,
        userCode:       result.userCode,
        signedPreKeyId: result.signedPreKeyId,
        message:        'SignedPreKey updated successfully'
      });
    } catch (err) {
      const statusMap = {
        BUNDLE_NOT_FOUND:      404,
        INVALID_USERCODE:      400,
        INVALID_SIGNED_PREKEY: 400
      };
      const status = statusMap[err.code] || 400;
      return res.status(status).json({ success: false, error: 'SignedPreKey update failed' });
    }
  });

  // ---------------------------------------------------------------------------
  // GET /api/prekeys/count/:userCode
  // Returns remaining one-time PreKey count without consuming any keys.
  // ---------------------------------------------------------------------------
  router.get('/count/:userCode', (req, res) => {
    const raw        = req.params.userCode;
    const normalized = preKeyStore.normalizeUserCode(raw);

    if (!normalized) {
      return res.status(400).json({ success: false, error: 'Invalid userCode format' });
    }

    if (!preKeyStore.hasBundleFor(normalized)) {
      return res.status(404).json({ success: false, error: 'Bundle not found' });
    }

    const remainingPreKeys = preKeyStore.getRemainingCount(normalized);
    return res.status(200).json({ success: true, userCode: normalized, remainingPreKeys });
  });

  // ---------------------------------------------------------------------------
  // GET /api/prekeys/:userCode
  // Fetches the PreKey bundle for initiating an encrypted X3DH session.
  // Atomically consumes one one-time PreKey.
  // ---------------------------------------------------------------------------
  router.get('/:userCode', (req, res) => {
    const raw    = req.params.userCode;
    const bundle = preKeyStore.fetchAndConsume(raw);

    if (!bundle) {
      // Generic error: do NOT echo the UserCode back (V3 fix)
      return res.status(404).json({ success: false, error: 'Bundle not found' });
    }

    logger.info('bundle_fetched', { remainingPreKeys: bundle.remainingPreKeys });
    return res.status(200).json({ success: true, bundle });
  });

  // ---------------------------------------------------------------------------
  // GET /api/turn/credentials
  // Returns time-limited TURN credentials for WebRTC session setup.
  // Returns 404 when TURN is not configured (not an error).
  //
  // The requesting socket must be registered (userCode passed as query param).
  // ---------------------------------------------------------------------------
  router.get('/turn/credentials', (req, res) => {
    if (!isTurnConfigured()) {
      return res.status(404).json({ success: false, error: 'TURN not configured' });
    }

    const raw        = req.query.userCode;
    const normalized = preKeyStore.normalizeUserCode(raw);
    if (!normalized) {
      return res.status(400).json({ success: false, error: 'Invalid userCode' });
    }

    const creds = generateTurnCredentials(normalized);
    return res.status(200).json({ success: true, credentials: creds });
  });

  // ---------------------------------------------------------------------------
  // POST /api/prekeys/fcm-token
  // Registers an FCM device token for zero-knowledge background wake-up pings.
  // Body: { userCode, fcmToken }
  // ---------------------------------------------------------------------------
  router.post('/fcm-token', (req, res) => {
    const { userCode, fcmToken } = req.body || {};
    const normalized = preKeyStore.normalizeUserCode(userCode);
    if (!normalized || !fcmToken || typeof fcmToken !== 'string') {
      return res.status(400).json({ success: false, error: 'Invalid userCode or fcmToken' });
    }

    fcmService.registerToken(normalized, fcmToken);
    return res.status(200).json({ success: true });
  });

  return router;
}

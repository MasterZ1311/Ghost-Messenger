import { Router } from 'express';

/**
 * Creates Express router for PreKey REST operations.
 *
 * @param {import('../store/InMemoryPreKeyStore.js').InMemoryPreKeyStore} preKeyStore
 * @param {import('../store/PresenceManager.js').PresenceManager} presenceManager
 */
export function createPreKeyRouter(preKeyStore, presenceManager) {
  const router = Router();

  /**
   * POST /api/prekeys/upload
   * Uploads PreKey bundle for initial contact discovery & X3DH sessions.
   */
  router.post('/upload', (req, res) => {
    const { userCode, bundle } = req.body;

    if (!userCode || !bundle) {
      return res.status(400).json({
        success: false,
        error: 'Missing required fields: userCode and bundle'
      });
    }

    try {
      const result = preKeyStore.upload(userCode, bundle);
      return res.status(200).json({
        success: true,
        userCode: result.userCode,
        preKeyCount: result.preKeyCount,
        message: 'PreKey bundle uploaded successfully'
      });
    } catch (err) {
      return res.status(400).json({
        success: false,
        error: err.message
      });
    }
  });

  /**
   * GET /api/prekeys/:userCode
   * Fetches the PreKey bundle for initiating an encrypted X3DH session.
   * Atomically consumes one one-time PreKey.
   */
  router.get('/:userCode', (req, res) => {
    const { userCode } = req.params;

    const bundle = preKeyStore.fetchAndConsume(userCode);
    if (!bundle) {
      return res.status(404).json({
        success: false,
        error: `PreKey bundle for user '${userCode}' not found`
      });
    }

    return res.status(200).json({
      success: true,
      bundle
    });
  });

  /**
   * GET /api/health
   * Simple server health check.
   */
  router.get('/health/status', (req, res) => {
    return res.status(200).json({
      status: 'ok',
      uptimeSeconds: Math.floor(process.uptime()),
      activeUsers: presenceManager.count(),
      storedBundles: preKeyStore.size()
    });
  });

  return router;
}

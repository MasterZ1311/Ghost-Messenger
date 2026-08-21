'use strict';

const http = require('http');

// Prevent a single unhandled rejection from killing the server
process.on('uncaughtException', (err) => {
  // Use console.error here since logger may not be initialized yet
  console.error({ err }, 'uncaughtException — process will continue');
});

process.on('unhandledRejection', (reason) => {
  console.error({ reason }, 'unhandledRejection — process will continue');
});

const config = require('./config');
const logger = require('./utils/logger');
const { createApp } = require('./app');
const { createSocketServer } = require('./socket');
const messageQueue = require('./services/messageQueue');
const pushNotification = require('./services/pushNotification');

// ── Firebase Admin SDK dispatcher ────────────────────────────────────────────
// Initialised from env vars set in .env (FIREBASE_PROJECT_ID, etc.)
function initFirebase() {
  const {
    FIREBASE_PROJECT_ID,
    FIREBASE_CLIENT_EMAIL,
    FIREBASE_PRIVATE_KEY,
  } = process.env;

  if (!FIREBASE_PROJECT_ID || !FIREBASE_CLIENT_EMAIL || !FIREBASE_PRIVATE_KEY) {
    logger.warn('Firebase env vars not set — push notifications disabled');
    return;
  }

  try {
    const admin = require('firebase-admin');
    admin.initializeApp({
      credential: admin.credential.cert({
        projectId: FIREBASE_PROJECT_ID,
        clientEmail: FIREBASE_CLIENT_EMAIL,
        // .env stores \n as literal \n — replace to get real newlines
        privateKey: FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n'),
      }),
    });

    pushNotification.setDispatcher(async ({ token, payload, platform }) => {
      if (platform === 'ios') {
        // APNs via Firebase (requires APNs key configured in Firebase Console)
        await admin.messaging().send({
          token,
          apns: {
            headers: { 'apns-priority': '10' },
            payload: { aps: { contentAvailable: true }, ...payload.data },
          },
        });
      } else {
        // Android FCM
        await admin.messaging().send({
          token,
          data: payload.data,
          android: { priority: 'high' },
        });
      }
      return true;
    });

    logger.info({ projectId: FIREBASE_PROJECT_ID }, 'Firebase Admin SDK initialised — push notifications active');
  } catch (err) {
    logger.error({ err }, 'Failed to initialise Firebase Admin SDK — push notifications disabled');
  }
}


/**
 * Creates and starts the HTTP + Socket.IO server.
 * @returns {{ httpServer: import('http').Server, io: import('socket.io').Server, shutdown: () => Promise<void> }}
 */
function startServer() {
  initFirebase();

  const app = createApp();
  const httpServer = http.createServer(app);
  const io = createSocketServer(httpServer);

  messageQueue.start();

  httpServer.listen(config.port, config.host, () => {
    logger.info(
      { host: config.host, port: config.port, env: config.env },
      'CodeChat signaling server listening'
    );
  });

  // Graceful shutdown on container signals
  process.on('SIGTERM', () => shutdown('SIGTERM').then(() => process.exit(0)).catch(() => process.exit(1)));
  process.on('SIGINT',  () => shutdown('SIGINT').then(() => process.exit(0)).catch(() => process.exit(1)));

  let shuttingDown = false;

  async function shutdown(signal) {
    if (shuttingDown) return;
    shuttingDown = true;
    logger.info({ signal }, 'graceful shutdown initiated');

    // Stop background sweeper and notify connected clients.
    messageQueue.stop();
    io.emit('server_shutdown', { message: 'Server is shutting down' });

    const forceTimer = setTimeout(() => {
      logger.warn('shutdown grace period elapsed, forcing exit');
      process.exit(1);
    }, config.shutdownTimeoutMs);
    if (forceTimer.unref) forceTimer.unref();

    await new Promise((resolve) => io.close(resolve));
    await new Promise((resolve) => httpServer.close(resolve));

    clearTimeout(forceTimer);
    logger.info('shutdown complete');
  }

  return { httpServer, io, shutdown };
}

module.exports = { startServer };

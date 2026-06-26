'use strict';

const http = require('http');

const config = require('./config');
const logger = require('./utils/logger');
const { createApp } = require('./app');
const { createSocketServer } = require('./socket');
const messageQueue = require('./services/messageQueue');

/**
 * Creates and starts the HTTP + Socket.IO server.
 * @returns {{ httpServer: import('http').Server, io: import('socket.io').Server, shutdown: () => Promise<void> }}
 */
function startServer() {
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

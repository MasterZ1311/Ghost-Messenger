'use strict';

const { Server } = require('socket.io');
const config = require('../config');
const logger = require('../utils/logger');
const { registerSocketHandlers } = require('./handlers');

/**
 * Attaches a configured Socket.IO server to the given HTTP server.
 * @param {import('http').Server} httpServer
 * @returns {import('socket.io').Server}
 */
function createSocketServer(httpServer) {
    const io = new Server(httpServer, {
      cors: {
        origin: config.cors.origins.includes('*') ? '*' : config.cors.origins,
        methods: ['GET', 'POST'],
      },
      // WebRTC handshake payloads can be sizeable; cap to protect memory.
      maxHttpBufferSize: config.validation.maxSignalBytes * 2,
      pingTimeout: 20000,
      pingInterval: 25000,
    });
    // Optional Redis adapter for horizontal scaling
    if (config.redis && config.redis.enabled) {
      try {
        const { createAdapter } = require('@socket.io/redis-adapter');
        const Redis = require('ioredis');
        const pubClient = new Redis({
          host: config.redis.host,
          port: config.redis.port,
          retryStrategy: (times) => Math.min(times * 100, 3000),
          lazyConnect: true,
        });
        pubClient.on('error', (err) => {
          logger.error({ err: err.message }, 'Redis pub client error');
        });
        const subClient = pubClient.duplicate();
        subClient.on('error', (err) => {
          logger.error({ err: err.message }, 'Redis sub client error');
        });
        pubClient.connect().catch((err) => logger.warn({ err: err.message }, 'Redis connect error'));
        subClient.connect().catch((err) => logger.warn({ err: err.message }, 'Redis connect error'));
        io.adapter(createAdapter(pubClient, subClient));
        logger.info({ host: config.redis.host, port: config.redis.port }, 'Redis adapter enabled');
      } catch (err) {
        logger.warn({ err: err.message }, 'Failed to initialize Redis adapter, using in-memory');
      }
    }

  io.on('connection', (socket) => {
    registerSocketHandlers(io, socket);
  });

  logger.info(
    { origins: config.cors.origins },
    'Socket.IO server initialized'
  );

  return io;
}

module.exports = { createSocketServer };

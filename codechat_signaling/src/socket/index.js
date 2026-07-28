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
    // Redis adapter for horizontal scaling
    const { createAdapter } = require('@socket.io/redis-adapter');
    const Redis = require('ioredis');
    const pubClient = new Redis({ host: process.env.REDIS_HOST || 'redis', port: process.env.REDIS_PORT || 6379 });
    const subClient = pubClient.duplicate();
    io.adapter(createAdapter(pubClient, subClient));

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

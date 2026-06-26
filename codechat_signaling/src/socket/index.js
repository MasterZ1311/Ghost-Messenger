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

import http from 'http';
import { Server } from 'socket.io';
import dotenv from 'dotenv';
import { createApp } from './app.js';
import { setupSignalingHandlers } from './sockets/signalingHandler.js';
import { logger } from './middleware/logger.js';

dotenv.config();

const PORT = process.env.PORT || 3000;

const { app, preKeyStore, presenceManager } = createApp();

const server = http.createServer(app);

const io = new Server(server, {
  cors: {
    // Use the same ALLOWED_ORIGINS logic as the HTTP layer.
    // For Android native clients CORS is irrelevant; this prevents
    // browser-based WebSocket connections from arbitrary origins.
    origin: (() => {
      const raw = process.env.ALLOWED_ORIGINS || '';
      if (!raw.trim()) return false;
      return raw.split(',').map(o => o.trim()).filter(Boolean);
    })(),
    methods: ['GET', 'POST']
  },
  // Increase timeouts for mobile networks (was 5s ping timeout — too aggressive)
  pingInterval:      25_000,
  pingTimeout:       20_000,
  // Cap WebSocket payload at 64 KB to prevent memory exhaustion
  maxHttpBufferSize: 64 * 1024
});

setupSignalingHandlers(io, presenceManager, preKeyStore);

// ---------------------------------------------------------------------------
// Eviction scheduler — cleans expired bundles every 5 minutes.
// Previously evictExpired() was defined but never called; this fixes V9.
// ---------------------------------------------------------------------------
const evictionInterval = setInterval(() => {
  const evicted = preKeyStore.evictExpired();
  if (evicted > 0) {
    logger.info('bundles_evicted', { count: evicted });
  }
}, 5 * 60 * 1000);

server.listen(PORT, '0.0.0.0', () => {
  logger.info('server_started', { port: PORT });
  console.log(`[Calypso Signaling] Ephemeral Signaling Server active on port ${PORT}`);
  console.log(`[Calypso Signaling] Local: http://localhost:${PORT}`);
  console.log(`[Calypso Signaling] Android Emulator: http://10.0.2.2:${PORT}`);
});

// ---------------------------------------------------------------------------
// Graceful shutdown — SIGTERM (Render/Railway) and SIGINT (Ctrl+C dev)
// Clears the eviction interval, closes Socket.IO connections, then closes
// the HTTP server. Waits up to 10s for in-flight requests to complete.
// ---------------------------------------------------------------------------
function shutdown(signal) {
  logger.info('shutdown_initiated', { signal });
  clearInterval(evictionInterval);

  io.close(() => {
    server.close(() => {
      logger.info('shutdown_complete');
      process.exit(0);
    });

    // Force-exit after 10s if connections won't drain
    setTimeout(() => {
      logger.warn('shutdown_forced');
      process.exit(1);
    }, 10_000).unref();
  });
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT',  () => shutdown('SIGINT'));

export { server, io };

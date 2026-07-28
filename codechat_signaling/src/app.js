'use strict';

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const rateLimit = require('express-rate-limit');
const pinoHttp = require('pino-http');

const config = require('./config');
const logger = require('./utils/logger');
const healthRoutes = require('./routes/health');
const iceRoutes = require('./routes/ice');
const preKeyRoutes = require('./routes/prekey');
const pushRoutes = require('./routes/push');

/**
 * Builds the Express application (HTTP surface: health, metrics, ICE config).
 * Socket.IO is attached separately to the underlying HTTP server.
 * @returns {import('express').Express}
 */
function createApp() {
  const app = express();

  // Behind a reverse proxy / load balancer (correct client IPs for rate limiting).
  app.set('trust proxy', 1);

  app.use(helmet());
  app.use(
    cors({
      origin: config.cors.origins.includes('*') ? true : config.cors.origins,
      methods: ['GET', 'POST', 'PUT', 'DELETE'],
    })
  );
  app.use(express.json({ limit: '64kb' }));
  app.use(pinoHttp({ logger, autoLogging: { ignore: (req) => req.url === '/health' } }));

  // HTTP API rate limiting (does not apply to the Socket.IO transport).
  const apiLimiter = rateLimit({
    windowMs: config.rateLimit.httpWindowMs,
    max: config.rateLimit.httpMax,
    standardHeaders: true,
    legacyHeaders: false,
  });
  app.use('/api', apiLimiter);

  app.use(healthRoutes);
  app.use(iceRoutes);
  app.use(preKeyRoutes);
  app.use('/api', pushRoutes);

  app.get('/', (req, res) => {
    res.json({
      name: 'codechat-signaling',
      version: require('../package.json').version,
      status: 'running',
    });
  });

  // 404
  app.use((req, res) => {
    res.status(404).json({ error: 'not found' });
  });

  // Centralized error handler
  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    logger.error({ err }, 'unhandled HTTP error');
    res.status(500).json({ error: 'internal server error' });
  });

  return app;
}

module.exports = { createApp };

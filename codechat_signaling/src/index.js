'use strict';

const { startServer } = require('./server');
const logger = require('./utils/logger');

const { shutdown } = startServer();

const handleExit = (signal) => {
  logger.info({ signal }, 'Received termination signal, shutting down...');
  shutdown(signal).then(() => {
    process.exit(0);
  }).catch((err) => {
    logger.error({ err }, 'Error during graceful shutdown');
    process.exit(1);
  });
};

process.on('SIGINT', () => handleExit('SIGINT'));
process.on('SIGTERM', () => handleExit('SIGTERM'));

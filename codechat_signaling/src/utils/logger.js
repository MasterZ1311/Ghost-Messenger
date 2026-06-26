'use strict';

const pino = require('pino');
const config = require('../config');

const options = {
  level: config.log.level,
  base: { service: 'codechat-signaling' },
  redact: {
    // Never log raw SDP/candidate payloads at info level; they can be large.
    paths: ['signalData.sdp', 'signalData.candidate'],
    censor: '[redacted]',
  },
};

let logger;

if (config.log.pretty) {
  logger = pino({
    ...options,
    transport: {
      target: 'pino-pretty',
      options: {
        colorize: true,
        translateTime: 'SYS:standard',
        ignore: 'pid,hostname,service',
      },
    },
  });
} else {
  logger = pino(options);
}

module.exports = logger;

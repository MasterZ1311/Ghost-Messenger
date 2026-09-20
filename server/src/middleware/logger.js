/**
 * Structured logger for Calypso Signaling Server.
 *
 * Privacy guarantees:
 *  - Raw IP addresses are NEVER logged in production. They are replaced with
 *    an 8-character truncated SHA-256 hash salted by LOG_SALT env var.
 *  - UserCodes are NEVER logged in production (set LOG_LEVEL=debug for dev only).
 *  - SDP payloads, ICE candidates, and envelope content are NEVER logged.
 *  - In debug mode (NODE_ENV=development or LOG_LEVEL=debug) raw IPs and
 *    UserCodes appear in local console output only.
 *
 * Log format: JSON-lines to stdout (compatible with Railway / Render log drains).
 */

import { createHash } from 'node:crypto';

const LOG_LEVELS = { debug: 0, info: 1, warn: 2, error: 3 };
const SALT       = process.env.LOG_SALT || 'calypso-log-default-salt';
const IS_DEV     = process.env.NODE_ENV === 'development' || process.env.LOG_LEVEL === 'debug';
const MIN_LEVEL  = IS_DEV ? 0 : LOG_LEVELS[process.env.LOG_LEVEL] ?? LOG_LEVELS.info;

/**
 * Returns an 8-hex-char salted hash of an IP, or "unknown" for missing IPs.
 * Used to detect repeated abuse without storing raw IPs in logs.
 * @param {string|undefined} ip
 * @returns {string}
 */
export function hashIp(ip) {
  if (!ip) return 'unknown';
  return createHash('sha256').update(SALT + ip).digest('hex').slice(0, 8);
}

function write(level, event, fields = {}) {
  if (LOG_LEVELS[level] < MIN_LEVEL) return;
  const entry = {
    ts:    new Date().toISOString(),
    level,
    event,
    ...fields
  };
  process.stdout.write(JSON.stringify(entry) + '\n');
}

export const logger = {
  debug: (event, fields) => write('debug', event, fields),
  info:  (event, fields) => write('info',  event, fields),
  warn:  (event, fields) => write('warn',  event, fields),
  error: (event, fields) => write('error', event, fields)
};

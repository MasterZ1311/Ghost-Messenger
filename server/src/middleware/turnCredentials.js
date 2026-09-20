/**
 * TURN Credential Generator — Calypso Signaling Server.
 *
 * Generates time-limited TURN credentials using RFC 8489 long-term credential
 * mechanism (compatible with coturn, Cloudflare TURN, Twilio TURN).
 *
 * How it works:
 *   username  = "<unixTimestampExpiry>:<userCode>"
 *   password  = base64(HMAC-SHA1(TURN_SECRET, username))
 *
 * The TURN server validates the credential by recomputing the HMAC.
 * Credentials expire at `now + TURN_CREDENTIAL_TTL_SECONDS`.
 *
 * Disabled by default — returns null when TURN_SECRET or TURN_URLS are not set.
 *
 * To enable: set environment variables:
 *   TURN_SECRET=<your-coturn-shared-secret>
 *   TURN_URLS=turn:yourserver.example.com:3478?transport=udp,turns:yourserver.example.com:5349
 *   TURN_CREDENTIAL_TTL_SECONDS=86400  (optional, default 24h)
 */

import { createHmac } from 'node:crypto';

const TURN_SECRET   = process.env.TURN_SECRET;
const TURN_URLS_RAW = process.env.TURN_URLS;
const TTL_SECONDS   = parseInt(process.env.TURN_CREDENTIAL_TTL_SECONDS || '86400', 10);

/**
 * Generates a set of time-limited TURN credentials for a client.
 *
 * @param {string} userCode  The requesting UserCode (embedded in username for tracing).
 * @returns {{
 *   username: string,
 *   password: string,
 *   urls: string[],
 *   ttl: number
 * } | null}  Null if TURN is not configured.
 */
export function generateTurnCredentials(userCode) {
  if (!TURN_SECRET || !TURN_URLS_RAW) return null;

  const expiresAt  = Math.floor(Date.now() / 1000) + TTL_SECONDS;
  const username   = `${expiresAt}:${userCode}`;
  const password   = createHmac('sha1', TURN_SECRET)
    .update(username)
    .digest('base64');

  const urls = TURN_URLS_RAW.split(',').map(u => u.trim()).filter(Boolean);

  return { username, password, urls, ttl: TTL_SECONDS };
}

/**
 * Returns true when TURN is configured.
 */
export function isTurnConfigured() {
  return Boolean(TURN_SECRET && TURN_URLS_RAW);
}

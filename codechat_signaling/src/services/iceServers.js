'use strict';

const crypto = require('crypto');
const config = require('../config');

/**
 * Builds the ICE server list returned to clients.
 *
 * Always includes the configured STUN servers. TURN entries are added when:
 *  - TURN_SECRET is set  -> time-limited ephemeral credentials (coturn REST API)
 *  - static TURN creds   -> fixed username/credential
 *
 * The coturn REST API scheme (RFC 7635 / TURN long-term credential mechanism):
 *   username   = "<expiryUnixTs>:<optionalUserId>"
 *   credential = base64( HMAC-SHA1( turnSecret, username ) )
 */
function buildIceServers(userId) {
  const servers = config.ice.stunUrls.map((urls) => ({ urls }));

  const { turn } = config.ice;

  if (turn.urls.length > 0) {
    if (turn.secret) {
      const expiry = Math.floor(Date.now() / 1000) + turn.ttlSeconds;
      const username = userId ? `${expiry}:${userId}` : `${expiry}`;
      const hmac = crypto.createHmac('sha1', turn.secret);
      hmac.update(username);
      const credential = hmac.digest('base64');

      servers.push({
        urls: turn.urls,
        username,
        credential,
      });
    } else if (turn.username && turn.credential) {
      servers.push({
        urls: turn.urls,
        username: turn.username,
        credential: turn.credential,
      });
    } else {
      // URLs configured but no credentials; still expose them (e.g. for
      // anonymous TURN or testing). Clients may ignore unusable entries.
      servers.push({ urls: turn.urls });
    }
  }

  return servers;
}

module.exports = { buildIceServers };
